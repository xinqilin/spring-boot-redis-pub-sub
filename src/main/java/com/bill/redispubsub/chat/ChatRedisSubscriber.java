package com.bill.redispubsub.chat;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 訂閱共用的 Redis 聊天 channel（在 {@code RedisConfig} 裡註冊），把每則訊息轉發給連線在這個 instance
 * 上的 WebSocket session。這是跨 instance 廣播的「接收端」：每個 instance 都會觸發，包含原本 publish
 * 這則訊息的那個 instance 自己。
 */
@Component
@RequiredArgsConstructor
public class ChatRedisSubscriber implements MessageListener {

  private final ChatWebSocketHandler chatWebSocketHandler;

  /**
   * 把訊息內容用 UTF-8 解碼後原封不動轉發出去；payload 本身已經是序列化好的 {@link ChatMessage} JSON
   * 字串，不需要再做一次反序列化再序列化的來回。
   *
   * @param message 從訂閱的 channel 收到的 Redis pub/sub 訊息
   * @param pattern 命中的 channel pattern，這裡只訂閱單一固定 channel，用不到
   */
  @Override
  public void onMessage(Message message, byte[] pattern) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    chatWebSocketHandler.broadcastLocally(json);
  }
}
