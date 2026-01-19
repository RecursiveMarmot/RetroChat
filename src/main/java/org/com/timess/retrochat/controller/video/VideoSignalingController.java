    package org.com.timess.retrochat.controller.video;

    import lombok.extern.slf4j.Slf4j;
    import org.com.timess.retrochat.service.VideoSignalingService;
    import org.springframework.messaging.handler.annotation.MessageMapping;
    import org.springframework.messaging.handler.annotation.Payload;
    import org.springframework.messaging.simp.annotation.SendToUser;
    import org.springframework.messaging.simp.annotation.SubscribeMapping;
    import org.springframework.stereotype.Controller;

    import java.security.Principal;
    import java.util.Map;

    /**
     * 视频信令控制器
     */
    @Controller
    @Slf4j
    public class VideoSignalingController {

        private final VideoSignalingService signalingService;

        public VideoSignalingController(VideoSignalingService signalingService) {
            this.signalingService = signalingService;
        }

        /**
         * 处理视频通话请求
         * 客户端发送到 /app/queue/video-call
         */
        @MessageMapping("/queue/video-call")
        public void handleVideoCall(@Payload Map<String, Object> payload, Principal principal) {
            signalingService.handleVideoCallAction(payload, principal);
        }

        /**
         * 处理视频通话请求
         * 客户端发送到 /app/queue/video-call
         */
        @MessageMapping("/queue/video-connect")
        public void handleWebrtc(@Payload Map<String, Object> payload, Principal principal) {
            signalingService.handleWebRtcConnect(payload, principal);
        }

        /**
         * 用户订阅视频通知
         * 客户端订阅 /user/queue/video
         */
        @SubscribeMapping("/user/queue/video-call")
        public Map<String, Object> subscribeVideo(Principal principal) {
            log.info("用户 {} 订阅了视频通知", principal.getName());
            return Map.of(
                "type", "subscription-confirmed",
                "message", "视频通知订阅成功",
                "timestamp", System.currentTimeMillis()
            );
        }

        /**
         * 获取ICE服务器配置
         * 客户端发送到 /app/video/ice-servers
         * 返回到 /user/queue/ice-servers
         */
        @MessageMapping("/video/ice-servers")
        @SendToUser("/queue/ice-servers")
        public Map<String, Object> getIceServers(Principal principal) {
            // 返回ICE服务器配置
            // 生产环境建议使用配置化的STUN/TURN服务器
            return Map.of(
                "iceServers", new Object[] {
    //                Map.of("urls", "stun:stun.l.google.com:19302"),
    //                Map.of("urls", "stun:stun1.l.google.com:19302"),
    //                Map.of("urls", "stun:stun2.l.google.com:19302")
                },
                "iceTransportPolicy", "all",
                "rtcpMuxPolicy", "require",
                "bundlePolicy", "max-bundle"
            );
        }
    }