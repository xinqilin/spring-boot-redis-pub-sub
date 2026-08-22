package com.bill.redispubsub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 應用程式進入點。
 *
 * <p>這是一個練習專案：示範如何用 Redis pub/sub 解決 WebSocket 在多後端 instance 水平擴展下的訊息同步
 * 問題。核心機制與範圍見專案 README；設定見 {@code com.bill.redispubsub.config}，聊天相關邏輯見
 * {@code com.bill.redispubsub.chat}。
 */
@SpringBootApplication
public class RedisPubSubApplication {

  public static void main(String[] args) {
    SpringApplication.run(RedisPubSubApplication.class, args);
  }

}
