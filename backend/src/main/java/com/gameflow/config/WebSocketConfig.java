package com.gameflow.config;

import com.gameflow.websocket.WsEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/** Maps /ws/events to the WebSocket event publisher. */
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(WsEventPublisher wsEventPublisher) {
        Map<String, WebSocketHandler> urlMap = new HashMap<>();
        urlMap.put("/ws/events", wsEventPublisher);
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(urlMap);
        mapping.setOrder(-1); // before the annotation-based handler mapping
        mapping.setCorsConfigurations(Map.of("/ws/events", corsConfig()));
        return mapping;
    }

    private org.springframework.web.cors.CorsConfiguration corsConfig() {
        var config = new org.springframework.web.cors.CorsConfiguration();
        config.addAllowedOrigin("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        return config;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
