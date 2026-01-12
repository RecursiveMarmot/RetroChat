package org.com.timess.retrochat.config;

import org.com.timess.retrochat.aop.AuthHandshakeInterceptor;
import org.com.timess.retrochat.aop.CustomHandshakeHandler;
import org.com.timess.retrochat.aop.VideoHandshakeHandler;
import org.com.timess.retrochat.aop.VideoHandshakeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * @author 33363
 * webSocket核心配置类
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 聊天WebSocket端点
        registry.addEndpoint("/chat-ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor())
                .setHandshakeHandler(customHandshakeHandler());
    }

    @Bean
    public AuthHandshakeInterceptor authHandshakeInterceptor() {
        return new AuthHandshakeInterceptor();
    }

    @Bean
    public VideoHandshakeInterceptor videoHandshakeInterceptor() {
        return new VideoHandshakeInterceptor();
    }

    @Bean
    public CustomHandshakeHandler customHandshakeHandler() {
        return new CustomHandshakeHandler();
    }

    @Bean
    public VideoHandshakeHandler videoHandshakeHandler() {
        return new VideoHandshakeHandler();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用简单内存消息代理
        // /video 前缀用于视频相关主题
        registry.enableSimpleBroker("/topic", "/queue", "/video");
        // 客户端发送消息的前缀
        registry.setApplicationDestinationPrefixes("/app");
        // 点对点消息前缀
        registry.setUserDestinationPrefix("/user");
    }

    // 配置消息缓冲区大小 - 视频需要更大的缓冲区
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        // 视频通话需要更大的缓冲区
        registry.setMessageSizeLimit(512 * 1024); // 512KB
        registry.setSendBufferSizeLimit(1024 * 1024); // 1MB
        registry.setTimeToFirstMessage(20000); // 设置首次消息超时时间（毫秒）
    }
}