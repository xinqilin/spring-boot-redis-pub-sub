package com.bill.redispubsub.chat;

import java.time.Instant;

public record ChatMessage(String username, String content, Instant timestamp, String sourceInstanceId) {
}
