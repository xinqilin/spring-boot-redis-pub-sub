# redis-pub-sub

用 Redis Pub/Sub 解決 WebSocket 在多後端 instance 水平擴展下的訊息同步問題的練習專案。

## 問題與目標

WebSocket 連線是有狀態的：client 會固定連在某一個後端 instance 上。當服務水平擴展成多個 instance 時，client A 連到 instance 1、client B 連到 instance 2，若 A 發的訊息只在 instance 1 本機廣播，instance 2 上的 B 完全收不到——這是 WebSocket 水平擴展的核心難題。

這個專案用 Redis Pub/Sub 當作 instance 之間的訊息匯流排來解決它：每個 instance 收到 client 的訊息後 publish 到 Redis 的同一個 channel；每個 instance（包含自己）都訂閱這個 channel，收到後再廣播給「自己本機」目前連線中的所有 client。

```
 client A ──WS──▶ Instance 1 ──publish──▶ ┌──────────────┐
                                            │ Redis Pub/Sub │
 client B ──WS──▶ Instance 2 ◀──subscribe──│  (single      │
                       ▲                    │   channel)    │
                       └───subscribe────────└──────────────┘
                                                    ▲
                                       Instance 1 ──┘ (subscribe)
                                       也會收到自己發出的訊息，
                                       再廣播給自己本機的 session
```

## 架構決策與範圍

| 決策 | 選擇 | 理由 |
|---|---|---|
| WebSocket 整合方式 | 原生 `WebSocketHandler`，不用 STOMP | Spring 沒有內建的 Redis STOMP broker relay（只有 RabbitMQ），原生方式能讓 pub/sub 廣播機制完全透明，不被框架包裝模糊掉 |
| Channel 設計 | 單一 global broadcast channel | 先把「多 instance 間如何同步」這個核心機制搞懂，不用多 room 訂閱管理分散注意力 |
| 使用者識別 | WebSocket connect 時的 `?username=` query string | 不做真正的認證，保持練習聚焦在 pub/sub + 水平擴展本身 |
| 訊息持久化 | 不做 | 純 pub/sub 語意，離線收不到歷史訊息是預期行為（若要持久化該用 Redis Streams，不在本練習範圍） |
| 水平擴展驗證方式 | docker-compose 起 2 個 instance，各自不同 host port，不加負載平衡器 | client 手動選擇連到哪個 instance，更直接驗證跨 instance 同步是否生效；不引入 Nginx 這類額外變因 |

明確排除的範圍：Spring Security / 真正的登入認證、Spring Session 跨 instance 共享、多聊天室（room）、上線名單（presence）、離線訊息、自動重連。

完整設計脈絡與各項決策的 trade-off 說明見 [`/Users/bill/.claude/plans/redis-pub-sub-mellow-wombat.md`](file:///Users/bill/.claude/plans/redis-pub-sub-mellow-wombat.md)。

## 技術棧

- Java 25（Gradle toolchain）
- Spring Boot 4.1.1（`spring-boot-starter-websocket` + `spring-boot-starter-data-redis`）
- Jackson 3.x（Spring Boot 4.1 起的變更，`ObjectMapper` 的 package 是 `tools.jackson.databind`）
- Redis 7（Lettuce 作為 client，由 Spring Boot 自動配置）
- Docker / Docker Compose

## 專案結構

```
src/main/java/com/bill/redispubsub/
├── RedisPubSubApplication.java
├── config/
│   ├── WebSocketConfig.java        # 註冊 /ws endpoint + handshake interceptor
│   └── RedisConfig.java            # ChannelTopic + RedisMessageListenerContainer
└── chat/
    ├── ChatMessage.java                    # record(username, content, timestamp, sourceInstanceId)
    ├── ChatWebSocketHandler.java           # 管理本機 session、收訊息後 publish 到 Redis、廣播
    ├── ChatRedisSubscriber.java            # 訂閱 Redis channel，收到後轉發給本機 session
    └── UsernameHandshakeInterceptor.java   # 握手時驗證/擷取 username

src/main/resources/
├── application.yaml
└── static/index.html                # 純 HTML + inline JS 的測試頁

docker-compose.yml   # redis + app-1(8081) + app-2(8082)
Dockerfile            # multi-stage build
```

## WebSocket 協定

**Endpoint**：`ws://<host>:<port>/ws?username=<username>`

- 握手時缺少 `username`（空字串或未帶）會直接回 `400 Bad Request`，拒絕升級成 WebSocket。
- Client 送出的訊息內容(`ws.send(...)`)是**純文字**，不需要自己組 JSON。
- Client 收到的訊息是 JSON，格式如下（`timestamp`/`username`/`sourceInstanceId` 皆由 server 端補上，client 無法偽造）：

```json
{
  "username": "alice",
  "content": "hello from alice",
  "timestamp": "2026-08-22T14:39:26.689573838Z",
  "sourceInstanceId": "app-1"
}
```

`sourceInstanceId` 是廣播該訊息的 client 原本連線的 instance（透過 `INSTANCE_ID` 環境變數設定，本機執行預設為 `instance-<port>`）。

## 開發指令

```bash
./gradlew build          # 編譯 + 測試 + 打包
./gradlew bootRun         # 啟動單一 instance（預設 port 8080）
./gradlew test            # 執行全部測試
```

> `RedisMessageListenerContainer` 在應用啟動時就會連線 Redis（`SmartLifecycle`），因此**跑測試或 `bootRun` 前必須先有本機可連的 Redis**：
> ```bash
> docker run --rm -p 6379:6379 redis:7-alpine
> ```

## 用 Docker Compose 驗證水平擴展

```bash
docker compose up --build
```

會啟動：

| Service | 說明 | 對外 port |
|---|---|---|
| `redis` | Redis 7，含 healthcheck | 6379 |
| `app-1` | `INSTANCE_ID=app-1` | 8081 → 容器內 8080 |
| `app-2` | `INSTANCE_ID=app-2` | 8082 → 容器內 8080 |

驗證步驟：

1. 開兩個瀏覽器分頁，分別連 `http://localhost:8081` 與 `http://localhost:8082`。
2. 各自輸入不同 username 並 Connect。
3. 在其中一個分頁發送訊息。
4. **兩個分頁都應該收到該訊息**，且畫面上的 instance 標籤正確對應到發話那一邊的 instance（`app-1` 或 `app-2`）——這就是 Redis pub/sub 讓水平擴展下的 WebSocket 訊息同步生效的直接證據。

用完後：

```bash
docker compose down
```

## 環境變數

| 變數 | 預設值 | 說明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 應用監聽的 port |
| `REDIS_HOST` | `localhost` | Redis 主機 |
| `REDIS_PORT` | `6379` | Redis port |
| `INSTANCE_ID` | `instance-${server.port}` | 用來標示訊息是哪個 instance 廣播的，docker-compose 中設為 `app-1`/`app-2` |
