package com.bill.redispubsub.config;

import com.bill.redispubsub.chat.ChatRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 設定聊天功能用到的 Redis pub/sub 元件。
 *
 * <p>{@link org.springframework.data.redis.connection.RedisConnectionFactory} 與 {@link
 * org.springframework.data.redis.core.StringRedisTemplate} 已由 Spring Boot 自動配置提供（只要
 * classpath 有 {@code spring-boot-starter-data-redis} 且設定了 {@code spring.data.redis.*}），這裡
 * 不需要再手動宣告。{@link RedisMessageListenerContainer} 則沒有被自動配置，必須自己註冊。
 */
@Configuration
public class RedisConfig {

  /** 聊天訊息廣播用的單一 global channel，channel 名稱來自設定檔的 {@code app.chat.channel}。 */
  @Bean
  ChannelTopic chatTopic(@Value("${app.chat.channel}") String channel) {
    return new ChannelTopic(channel);
  }

  /** 讓 {@link ChatRedisSubscriber} 訂閱 {@link #chatTopic} 這個 channel，收到訊息時觸發它的回呼。 */
  @Bean
  RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory, ChatRedisSubscriber subscriber, ChannelTopic chatTopic) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber, chatTopic);
    return container;
  }
}
