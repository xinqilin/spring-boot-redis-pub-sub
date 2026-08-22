package com.bill.redispubsub.chat;

import java.time.Instant;

/**
 * 一則聊天訊息，會經由 Redis pub/sub channel 廣播，最終送到各 WebSocket client。
 *
 * <p>{@code username}、{@code timestamp}、{@code sourceInstanceId} 一律由 server 端組裝（見 {@link
 * ChatWebSocketHandler#handleTextMessage}），client 只會提供 {@code content}，因此無法偽造發話者身分。
 *
 * @param username 發話者，來自 WebSocket 握手時的 {@code username} query string
 * @param content client 送出的原始文字內容
 * @param timestamp 這個 instance 收到訊息的時間
 * @param sourceInstanceId 發話者當下連線的 instance id，讓 client 能看出這則訊息是從哪個後端 instance 廣播出來的
 */
public record ChatMessage(String username, String content, Instant timestamp, String sourceInstanceId) {

}
