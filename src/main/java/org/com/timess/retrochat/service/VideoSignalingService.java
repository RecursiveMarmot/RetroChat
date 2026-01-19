package org.com.timess.retrochat.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.com.timess.retrochat.aop.AuthHandshakeInterceptor;
import org.com.timess.retrochat.exception.BusinessException;
import org.com.timess.retrochat.exception.ErrorCode;
import org.com.timess.retrochat.exception.ThrowUtils;
import org.com.timess.retrochat.model.entity.user.User;
import org.com.timess.retrochat.model.vo.UserVO;
import org.com.timess.retrochat.model.vo.VideoSignalingVO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Map;
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
//    private final Map<String, VideoRoom> videoRooms = new ConcurrentHashMap<>();
    
    public VideoSignalingService(SimpMessagingTemplate messagingTemplate, 
                                 ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public VideoSignalingVO handleCallMessage(String toUserId, HttpServletRequest request) {
        UserVO loginUser = userService.getLoginUser(request);
        if(ObjectUtil.isEmpty(loginUser)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前登陆用户错误");
        }
        //判定接电话方的userId是否存在
        User byId = userService.getById(Long.parseLong(toUserId));
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(loginUser, userVO);
        ThrowUtils.throwIf(ObjectUtil.isEmpty(byId), ErrorCode.PARAMS_ERROR, "待通话用户不存在");
        //通过WebSocket发送通话请求给被叫方
        Map<String, Object> incomingCall = Map.of(
                "type", "incoming-call",
                "from", loginUser.getId(),
                "to", toUserId,
                "timestamp", System.currentTimeMillis(),
                "callerInfo", userVO
        );
        try {
            messagingTemplate.convertAndSendToUser(
                    toUserId,
                    "/queue/video-call",
                    incomingCall);
            return new VideoSignalingVO(true, "通话请求已发送到:"+ toUserId);
        } catch (Exception e) {
            log.error("发送通话请求失败", e);
            return null;
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
            if (principal instanceof AuthHandshakeInterceptor.StompPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "call-accepted",
                        "from", from,
                        "to", to,
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
            if (principal instanceof AuthHandshakeInterceptor.StompPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "call-rejected",
                        "from", from,
                        "to", to,
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
            if (principal instanceof AuthHandshakeInterceptor.StompPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "call-ended",
                        "from", from,
                        "to", to,
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-call", message);
                log.info("用户 {} 结束了与 {} 的视频通话", from, to);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
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
            if (principal instanceof AuthHandshakeInterceptor.StompPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "offer",
                        "from", from,
                        "to", to,
                        "offer", payload.get("offer"),
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-connect", message);
                log.info("用户 {} 向 {} 发送了offer", from, to);
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
            if (principal instanceof AuthHandshakeInterceptor.StompPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "answer",
                        "from", from,
                        "to", to,
                        "answer", payload.get("answer"),
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-connect", message);
                log.info("用户 {} 向 {} 发送了answer ", from, to);
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
            if (principal instanceof AuthHandshakeInterceptor.StompPrincipal videoPrincipal) {
                String from = videoPrincipal.getName();
                Map<String, Object> message = Map.of(
                        "type", "ice-candidate",
                        "from", from,
                        "to", to,
                        "candidate", payload.get("ice-candidate"),
                        "timestamp", System.currentTimeMillis()
                );
                messagingTemplate.convertAndSendToUser(to, "/queue/video-connect", message);
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    public void handleVideoCallAction(Map<String, Object> payload, Principal principal) {
        try {
            String action = (String) payload.get("type");
            switch (action) {
                case "call-accepted":
                    handleAcceptMessage(payload, principal);
                    break;
                case "call-rejected":
                    handleRejectMessage(payload, principal);
                    break;
                case "call-ended":
                    handleEndMessage(payload, principal);
                    break;
            }
        } catch (Exception e) {
            log.error("处理视频信令消息失败", e);
        }
    }

    /**
     * 建立webrtc连接
     * @param payload
     * @param principal
     */
    public void handleWebRtcConnect(Map<String, Object> payload, Principal principal) {
        try {
            String action = (String) payload.get("type");
            switch (action) {
                case "offer":
                    handleOfferMessage(payload, principal);
                    break;
                case "answer":
                    handleAnswerMessage(payload, principal);
                    break;
                case "ice-candidate":
                    handleCandidateMessage(payload, principal);
                    break;
            }
        } catch (Exception e) {
            log.error("建立webrtc异常", e);
        }
    }


}