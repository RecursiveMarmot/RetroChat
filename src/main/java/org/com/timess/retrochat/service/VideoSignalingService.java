package org.com.timess.retrochat.service;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.com.timess.retrochat.aop.VideoHandshakeHandler.VideoPrincipal;
import org.com.timess.retrochat.exception.BusinessException;
import org.com.timess.retrochat.exception.ErrorCode;
import org.com.timess.retrochat.exception.ThrowUtils;
import org.com.timess.retrochat.model.entity.user.User;
import org.com.timess.retrochat.model.vo.UserVO;
import org.com.timess.retrochat.model.vo.VideoSignalingVO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 视频信令服务
 */
@Service
@Slf4j
public class VideoSignalingService {
    
    private final SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper;

    @Resource
    private UserService userService;
    
    // 存储用户会话信息
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    // 存储通话房间信息
    private final Map<String, VideoRoom> videoRooms = new ConcurrentHashMap<>();
    
    public VideoSignalingService(SimpMessagingTemplate messagingTemplate, 
                                 ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public VideoSignalingVO handleSignaling(String toUserId, HttpServletRequest request) {
        UserVO loginUser = userService.getLoginUser(request);
        if(ObjectUtil.isEmpty(loginUser)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前登陆用户错误");
        }
        //判定接电话方的userId是否存在
        User byId = userService.getById(Long.parseLong(toUserId));
        ThrowUtils.throwIf(ObjectUtil.isEmpty(byId), ErrorCode.PARAMS_ERROR, "待通话用户不存在");
        //通过WebSocket发送通话请求给被叫方
        Map<String, Object> incomingCall = Map.of(
                "type", "incoming-call",
                "from", loginUser.getId(),
                "to", toUserId,
                "timestamp", System.currentTimeMillis()
        );
        try {
            messagingTemplate.convertAndSendToUser(
                    toUserId,
                    "/queue/video",
                    incomingCall);
            // 生成唯一的 roomId（使用双方用户ID和时间戳）
            String fromUserId = String.valueOf(loginUser.getId());
            String roomId = generateRoomId(fromUserId, toUserId);
            return new VideoSignalingVO(true, "通话请求已发送到:"+ toUserId, roomId);
        } catch (Exception e) {
            log.error("发送通话请求失败", e);
            return null;
        }
    }

    /**
     * 建立WebRTC连接提议
     * @param payload
     * @param principal
     */
    public void handleOfferMessage(Map<String, Object> payload, Principal principal) {
        try{
            String to = (String) payload.get("to");
            String roomId = (String) payload.get("roomId");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "offer",
                        "from", from,
                        "offer", payload.get("offer"),
                        "roomId", roomId,
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-call", message);
                log.info("用户 {} 向 {} 发送了offer，房间: {}", from, to, roomId);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    /**
     * WebRTC连接应答
     * @param payload
     * @param principal
     */
    public void handleAnswerMessage(Map<String, Object> payload, Principal principal) {
        try{
            String to = (String) payload.get("to");
            String roomId = (String) payload.get("roomId");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "answer",
                        "from", from,
                        "answer", payload.get("answer"),
                        "roomId", roomId,
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-call", message);
                log.info("用户 {} 向 {} 发送了answer，房间: {}", from, to, roomId);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    /**
     * ICE候选交换
     * @param payload
     * @param principal
     */
    public void handleCandidateMessage(Map<String, Object> payload, Principal principal) {
        try{
            String to = (String) payload.get("to");
            String roomId = (String) payload.get("roomId");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "ice-candidate",
                        "from", from,
                        "candidate", payload.get("ice-candidate"),
                        "roomId", roomId,
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video", message);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    /**
     * 用户加入房间
     * @param payload
     * @param principal
     */
    public void handleJoinMessage(Map<String, Object> payload, Principal principal) {
        try{
            String roomId = (String) payload.get("roomId");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                VideoRoom room = videoRooms.computeIfAbsent(roomId, id -> new VideoRoom(id));
                String userId = from;
                room.addUser(userId);
                // 通知房间内其他用户
                room.getUsers().stream()
                        .filter(u -> !u.equals(userId))
                        .forEach(u -> {
                            Map<String, Object> message = Map.of(
                                    "type", "user-joined",
                                    "userId", userId,
                                    "roomId", roomId,
                                    "timestamp", System.currentTimeMillis()
                            );
                            messagingTemplate.convertAndSendToUser(u, "/queue/video", message);
                        });
                log.info("用户 {} 加入了房间 {}", userId, roomId);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    /**
     * 用户离开房间
     * @param payload
     * @param principal
     */
    public void handleLeaveMessage(Map<String, Object> payload, Principal principal) {
        try{
            String to = (String) payload.get("to");
            String roomId = (String) payload.get("roomId");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String userId = videoPrincipal.getName();
                VideoRoom room = videoRooms.get(roomId);
                if (room != null) {
                    room.removeUser(userId);

                    if (room.isEmpty()) {
                        videoRooms.remove(roomId);
                    }
                }
                log.info("用户 {} 离开了房间 {}", userId, roomId);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    /**
     * 发起视频通话
     * @param payload
     * @param principal
     */
    public void handleCallMessage(Map<String, Object> payload, Principal principal) {
        try{
            String to = (String) payload.get("to");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                String callId = (String) payload.get("callId");
                String roomId = "room_" + callId;
                Map<String, Object> message = Map.of(
                        "type", "incoming-call",
                        "from", from,
                        "to", to,
                        "callId", callId,
                        "roomId", roomId,
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video", message);
                log.info("用户 {} 向 {} 发起视频呼叫，呼叫ID: {}", from, to, callId);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    /**
     * 接受视频通话
     * @param payload
     * @param principal
     */
    public void handleAcceptMessage(Map<String, Object> payload, Principal principal) {
        try{
            String to = (String) payload.get("to");
            String roomId = (String) payload.get("roomId");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "call-accepted",
                        "from", from,
                        "to", to,
                        "roomId", roomId,
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-call", message);
                log.info("用户 {} 接受了 {} 的视频呼叫", from, to);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

   /**
     * 拒绝视频通话
     * @param payload
     * @param principal
     */
    public void handleRejectMessage(Map<String, Object> payload, Principal principal) {
        try{
            String to = (String) payload.get("to");
            String roomId = (String) payload.get("roomId");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "call-rejected",
                        "from", from,
                        "to", to,
                        "roomId", roomId,
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-call", message);
                log.info("用户 {} 拒绝了 {} 的视频呼叫", from, to);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    /**
     * 结束视频通话
     * @param payload
     * @param principal
     */
    public void handleEndMessage(Map<String, Object> payload, Principal principal) {
        try{
            String to = (String) payload.get("to");
            String roomId = (String) payload.get("roomId");
            if (principal instanceof VideoPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "call-ended",
                        "from", from,
                        "to", to,
                        "roomId", roomId,
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-call", message);
                // 清理房间
                videoRooms.remove(roomId);
                log.info("用户 {} 结束了与 {} 的视频通话", from, to);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    // 生成唯一的 roomId
    private String generateRoomId(String fromUserId, String toUserId) {
        // 确保同一对用户的 roomId 一致，按用户ID排序
        String sortedUsers = Stream.of(fromUserId, toUserId)
                .sorted()
                .collect(Collectors.joining("_"));
        return "room_" + sortedUsers + "_" + System.currentTimeMillis();
    }

    /**
     * 视频房间类
     */
    private static class VideoRoom {
        private final String roomId;
        private final Set<String> users = new ConcurrentHashSet<>();
        
        public VideoRoom(String roomId) {
            this.roomId = roomId;
        }
        
        public void addUser(String userId) {
            users.add(userId);
        }
        
        public void removeUser(String userId) {
            users.remove(userId);
        }
        
        public Set<String> getUsers() {
            return Collections.unmodifiableSet(users);
        }
        
        public boolean isEmpty() {
            return users.isEmpty();
        }
        
        public String getRoomId() {
            return roomId;
        }
    }
}