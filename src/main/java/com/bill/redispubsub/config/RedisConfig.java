package com.bill.redispubsub.config;

import com.bill.redispubsub.chat.ChatRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

  @Bean
  ChannelTopic chatTopic(@Value("${app.chat.channel}") String channel) {
    return new ChannelTopic(channel);
  }

  @Bean
  RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory, ChatRedisSubscriber subscriber, ChannelTopic chatTopic) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber, chatTopic);
    return container;
  }
}
