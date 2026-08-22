package com.bill.redispubsub.chat;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRedisSubscriber implements MessageListener {

  private final ChatWebSocketHandler chatWebSocketHandler;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    chatWebSocketHandler.broadcastLocally(json);
  }
}
