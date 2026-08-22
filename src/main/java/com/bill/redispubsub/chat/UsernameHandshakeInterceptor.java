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

@Component
public class UsernameHandshakeInterceptor implements HandshakeInterceptor {

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

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
  }
}
