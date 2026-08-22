package com.bill.redispubsub.chat;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private static final int SEND_TIME_LIMIT_MS = 10_000;
  private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final ChannelTopic chatTopic;
  private final String instanceId;

  public ChatWebSocketHandler(
      StringRedisTemplate stringRedisTemplate,
      ObjectMapper objectMapper,
      ChannelTopic chatTopic,
      @Value("${app.instance-id}") String instanceId) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.objectMapper = objectMapper;
    this.chatTopic = chatTopic;
    this.instanceId = instanceId;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.put(
        session.getId(),
        new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String username = (String) session.getAttributes().get("username");
    ChatMessage chatMessage = new ChatMessage(username, message.getPayload(), Instant.now(), instanceId);
    try {
      String json = objectMapper.writeValueAsString(chatMessage);
      stringRedisTemplate.convertAndSend(chatTopic.getTopic(), json);
    } catch (Exception e) {
      log.warn("Failed to publish chat message to Redis", e);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
  }

  void broadcastLocally(String json) {
    TextMessage message = new TextMessage(json);
    for (WebSocketSession session : sessions.values()) {
      if (session.isOpen()) {
        try {
          session.sendMessage(message);
        } catch (IOException e) {
          log.warn("Failed to send message to session {}", session.getId(), e);
        }
      }
    }
  }
}
