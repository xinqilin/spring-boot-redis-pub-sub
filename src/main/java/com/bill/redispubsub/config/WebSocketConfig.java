package com.bill.redispubsub.config;

import com.bill.redispubsub.chat.ChatWebSocketHandler;
import com.bill.redispubsub.chat.UsernameHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 註冊原生 WebSocket endpoint（不使用 STOMP）。採用原生 {@link
 * org.springframework.web.socket.WebSocketHandler} 是刻意的選擇：Spring 沒有內建的 Redis STOMP
 * broker relay（只有 RabbitMQ），原生方式能讓「訊息如何經由 Redis pub/sub 在 instance 間同步」這個
 * 核心機制保持透明，不被框架包裝模糊掉。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final ChatWebSocketHandler chatWebSocketHandler;
  private final UsernameHandshakeInterceptor usernameHandshakeInterceptor;

  /** 把聊天 handler 掛到 {@code /ws}，並在握手階段先跑過 {@link UsernameHandshakeInterceptor}。 */
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(chatWebSocketHandler, "/ws").addInterceptors(usernameHandshakeInterceptor);
  }
}
