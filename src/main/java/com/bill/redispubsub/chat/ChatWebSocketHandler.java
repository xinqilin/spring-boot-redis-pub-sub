package com.bill.redispubsub.chat;

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
import tools.jackson.databind.ObjectMapper;

/**
 * 處理聊天 demo 的 WebSocket 連線，並把它們接上 Redis pub/sub。
 *
 * <p>這個 handler 是 Spring singleton，會被每一條 WebSocket 連線的執行緒併發呼叫。它只管理「實際連在這個
 * JVM 上」的 session，從不直接對其他 instance 上的 session 動作。跨 instance 的訊息傳遞完全透過 Redis 完成：
 * {@link #handleTextMessage} 把訊息 publish 到共用 channel，而 {@link ChatRedisSubscriber} 會在每個
 * instance（包含自己）收到訊息從 Redis 繞回來後，呼叫 {@link #broadcastLocally} 轉發給本機 session。
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  /**
   * 單一 session 送訊息的逾時上限。用來保護「Redis 訂閱廣播」這條共用執行緒不會被某個慢速 client 卡住。
   */
  private static final int SEND_TIME_LIMIT_MS = 10_000;

  /**
   * 單一 session 允許緩衝的位元組上限，超過就會被強制關閉。
   */
  private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

  /**
   * 目前連線在這個 instance 上的 session，key 是 session id。
   */
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  private final StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper;
  private final ChannelTopic chatTopic;

  /**
   * 這個 instance 的識別碼，會被埋進這個 instance 發出的每則訊息裡。
   */
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

  /**
   * 註冊一個剛建立的連線，存入前先包一層 {@link ConcurrentWebSocketSessionDecorator}，避免廣播路徑上的
   * 併發/慢速寫入把底層 session 弄壞，或拖累其他 session。
   */
  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.put(
        session.getId(),
        new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES));
  }

  /**
   * 把 client 送來的純文字內容包成 {@link ChatMessage}（補上發話者 username、目前時間、本 instance
   * id），再 publish 到共用的 Redis channel。publish 失敗只記 log 吞掉、不往外拋，因為讓例外傳出去會讓
   * Spring 直接把這個 client 的連線關掉——不該因為 Redis 一次短暫抖動就把使用者踢下線。
   */
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

  /**
   * 把 session 從本機清單移除。這個動作是必要的：不做的話會讓 {@code sessions} 無限增長造成記憶體洩漏，
   * 也會讓 {@link #broadcastLocally} 對著已關閉的 session 送訊息而噴例外。
   */
  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
  }

  /**
   * 把已經序列化好的 {@link ChatMessage} JSON 轉發給目前連線在這個 instance 上的所有 session。由
   * {@link ChatRedisSubscriber} 在訊息從 Redis channel 繞回來後呼叫——包含這個 instance 自己剛剛發出去的
   * 訊息，所以發話者也會透過這趟 Redis 來回收到自己發的訊息。
   *
   * <p>單一 session 送失敗只記 log，不會中斷其他 session 的傳送。
   *
   * @param json 已序列化成 JSON 的 {@link ChatMessage}
   */
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
