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
            return new VideoSignalingVO(true, "通话请求已发送到:"+ toUserId);
        } catch (Exception e) {
            log.error("发送通话请求失败", e);
            return null;
        }
    }

    /**
     * 处理视频通话信令消息
     */
    public void handleSignalingMessage(Map<String, Object> payload, Principal principal) {
        try {
            String type = (String) payload.get("type");
            String to = (String) payload.get("to");
            String roomId = (String) payload.get("roomId");
            
            if (principal instanceof VideoPrincipal) {
                VideoPrincipal videoPrincipal = (VideoPrincipal) principal;
                String from = videoPrincipal.getName();
                
                switch (type) {
                    case "offer":
                        sendOffer(from, to, payload.get("offer"), roomId);
                        break;
                    case "answer":
                        sendAnswer(from, to, payload.get("answer"), roomId);
                        break;
                    case "ice-candidate":
                        sendIceCandidate(from, to, payload.get("candidate"), roomId);
                        break;
                    case "join":
                        handleUserJoin(from, roomId);
                        break;
                    case "leave":
                        handleUserLeave(from, roomId);
                        break;
                    case "call":
                        initiateCall(from, to, payload);
                        break;
                    case "accept":
                        acceptCall(from, to, roomId);
                        break;
                    case "reject":
                        rejectCall(from, to, roomId);
                        break;
                    case "end":
                        endCall(from, to, roomId);
                        break;
                }
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }
    
    /**
     * 发送Offer
     */
    private void sendOffer(String from, String to, Object offer, String roomId) {
        Map<String, Object> message = Map.of(
            "type", "offer",
            "from", from,
            "offer", offer,
            "roomId", roomId,
            "timestamp", System.currentTimeMillis()
        );
        
        messagingTemplate.convertAndSendToUser(to, "/queue/video-call", message);
        log.info("用户 {} 向 {} 发送了offer，房间: {}", from, to, roomId);
    }
    
    /**
     * 发送Answer
     */
    private void sendAnswer(String from, String to, Object answer, String roomId) {
        Map<String, Object> message = Map.of(
            "type", "answer",
            "from", from,
            "answer", answer,
            "roomId", roomId,
            "timestamp", System.currentTimeMillis()
        );
        
        messagingTemplate.convertAndSendToUser(to, "/queue/video-call", message);
        log.info("用户 {} 向 {} 发送了answer，房间: {}", from, to, roomId);
    }
    
    /**
     * 发送ICE候选
     */
    private void sendIceCandidate(String from, String to, Object candidate, String roomId) {
        Map<String, Object> message = Map.of(
            "type", "ice-candidate",
            "from", from,
            "candidate", candidate,
            "roomId", roomId,
            "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSendToUser(to, "/queue/video", message);
    }
    
    /**
     * 用户加入房间
     */
    private void handleUserJoin(String userId, String roomId) {
        VideoRoom room = videoRooms.computeIfAbsent(roomId, id -> new VideoRoom(id));
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
    
    /**
     * 发起呼叫
     */
    private void initiateCall(String from, String to, Map<String, Object> payload) {
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
    
    /**
     * 接受呼叫
     */
    private void acceptCall(String from, String to, String roomId) {
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
    
    /**
     * 拒绝呼叫
     */
    private void rejectCall(String from, String to, String roomId) {
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
    
    /**
     * 结束通话
     */
    private void endCall(String from, String to, String roomId) {
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
    
    /**
     * 用户离开房间
     */
    private void handleUserLeave(String userId, String roomId) {
        VideoRoom room = videoRooms.get(roomId);
        if (room != null) {
            room.removeUser(userId);
            
            if (room.isEmpty()) {
                videoRooms.remove(roomId);
            }
        }
        
        log.info("用户 {} 离开了房间 {}", userId, roomId);
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