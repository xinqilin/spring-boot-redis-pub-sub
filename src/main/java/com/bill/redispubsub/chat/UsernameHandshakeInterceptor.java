package com.bill.redispubsub.chat;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 從 WebSocket 握手請求的 {@code username} query string 取值，若有帶就寫入 session attributes，
 * 讓 {@link ChatWebSocketHandler} 之後能透過 {@code session.getAttributes()} 讀回來。
 *
 * <p><b>這只是「識別」，不是「認證」。</b>{@code username} 完全由 client 提供、從未被驗證，因此任何 client
 * 都可以冒用任意 username（identity spoofing）。這是本 demo 刻意接受的範圍限制——見專案 README 的
 * 「架構決策與範圍」一節——不是遺漏。若要在 username 必須可信任的場景重用這個類別，前面需要接上真正的
 * 認證機制（例如 Spring Security）。
 */
@Component
public class UsernameHandshakeInterceptor implements HandshakeInterceptor {

  /**
   * 當 {@code username} 缺少或空白時，直接以 {@code 400 Bad Request} 拒絕握手，確保連線不可能在沒有
   * username 的情況下抵達 {@link ChatWebSocketHandler}；成功的話則把值存入 handshake attributes 的
   * {@code "username"} key 底下。
   */
  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      response.setStatusCode(HttpStatus.BAD_REQUEST);
      return false;
    }

    String username = servletRequest.getServletRequest().getParameter("username");
    if (!StringUtils.hasText(username)) {
      response.setStatusCode(HttpStatus.BAD_REQUEST);
      return false;
    }

    attributes.put("username", username);
    return true;
  }

  /**
   * 握手後不需要額外處理，僅為了滿足 {@link HandshakeInterceptor} 介面而存在。
   */
  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
  }
}
