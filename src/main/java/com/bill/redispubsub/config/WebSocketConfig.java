package com.bill.redispubsub.config;

import com.bill.redispubsub.chat.ChatWebSocketHandler;
import com.bill.redispubsub.chat.UsernameHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final ChatWebSocketHandler chatWebSocketHandler;
  private final UsernameHandshakeInterceptor usernameHandshakeInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(chatWebSocketHandler, "/ws").addInterceptors(usernameHandshakeInterceptor);
  }
}
