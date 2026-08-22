# redis-pub-sub

用 Redis Pub/Sub 解決 WebSocket 在多後端 instance 水平擴展下的訊息同步問題的專案。

## 問題與目標

WebSocket 連線是有狀態的：client 會固定連在某一個後端 instance 上。當服務水平擴展成多個 instance 時，client A 連到 instance 1、client B 連到 instance 2，若 A 發的訊息只在 instance 1 本機廣播，instance 2 上的 B 完全收不到——這是 WebSocket 水平擴展的核心難題。

這個專案用 Redis Pub/Sub 當作 instance 之間的訊息匯流排來解決它：每個 instance 收到 client 的訊息後 publish 到 Redis 的同一個 channel；每個 instance（包含自己）都訂閱這個 channel，收到後再廣播給「自己本機」目前連線中的所有 client。

```
 client A ──WS──▶ Instance 1 ──publish──▶   ┌───────────────┐
                                            │ Redis Pub/Sub │
 client B ──WS──▶ Instance 2 ◀──subscribe── │  (single      │
                       ▲                    │   channel)    │
                       └───subscribe────────└───────────────┘
                                                    ▲
                                       Instance 1 ──┘ (subscribe)
                                       也會收到自己發出的訊息，
                                       再廣播給自己本機的 session
```

## 為什麼 Redis 能同時做 Cache 跟 Pub/Sub

Redis 本質上是一個**單執行緒事件迴圈 + 記憶體內資料結構**的伺服器，cache 和 pub/sub 只是操作在這個共用引擎上的兩種不同指令集，不是兩套系統拼起來的。

**Cache 那一面**：`SET`/`GET`/`EXPIRE` 操作的是 keyspace——一個巨大的 hash table，key 對應到 value（string/list/hash/...），可選擇性地透過 RDB/AOF 持久化到磁碟。

**Pub/Sub 那一面**：Redis 內部另外維護一個「channel → 訂閱者連線清單」的對照表（本質上也是個 hash table，value 是這個 channel 上所有還開著的 client socket）。

- `SUBSCRIBE channel` 做的事：把這條 client 連線加進該 channel 的訂閱清單，並把這條連線切換成「訂閱模式」（之後這條連線只能收 push、不能再下一般指令，這是 RESP 協定層面的限制，也是為什麼 `RedisMessageListenerContainer` 要另外拉一條專用連線來監聽，不能跟平常下 command 的連線共用）。
- `PUBLISH channel msg` 做的事：查一下該 channel 的訂閱清單，對清單上每一條連線的 socket 直接寫入這則訊息。就這樣，單執行緒依序處理完就結束。

**關鍵差異，也是本專案「不持久化」的根本原因**：pub/sub 的訊息**完全不進 keyspace**，不寫 RDB、不寫 AOF、不佔用任何 key。`PUBLISH` 純粹是「當下有誰在聽就發給誰」，沒人訂閱就直接丟掉，訊息本身從來沒有被「儲存」過——它只存在於「從 publisher 的 socket 讀進來，立刻寫到 subscriber 的 socket」這個瞬間。這也是為什麼可以用 `redis-cli MONITOR` 或 `SUBSCRIBE` 即時看到 command，但不可能事後用任何指令把已經發過的訊息「查」出來。

之所以 cache 和 pub/sub 能長在同一顆 Redis 裡毫無違和感，是因為它們共用同一個 event loop、同一條 TCP 連線管理機制、同一套 RESP 協定，差別只在於**操作的是哪一份記憶體內的資料結構**（keyspace 的 hash table vs. channel 訂閱清單）。這也解釋了為什麼本專案裡同一個 `RedisConnectionFactory` 就能同時支援兩種用途——底層就是同一個東西。

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

## 用 redis-cli 直接觀察 Pub/Sub

想親眼看 Redis 這邊 pub/sub 的狀態，不透過瀏覽器，可以連進 `docker compose up` 起的 `redis` container：

```bash
docker compose exec redis redis-cli
```

進去後可以查目前有哪些 channel、各自幾個訂閱者（`app-1`、`app-2` 各自的 `RedisMessageListenerContainer` 都會佔一個訂閱數）：

```
127.0.0.1:6379> PUBSUB CHANNELS
1) "chat-broadcast"
127.0.0.1:6379> PUBSUB NUMSUB chat-broadcast
1) "chat-broadcast"
2) (integer) 2
```

想即時看訊息本身（`ChatWebSocketHandler` publish 出去的原始 JSON），另開一個 terminal 直接訂閱該 channel，讓它卡在那裡監看，再回瀏覽器發訊息：

```bash
docker compose exec redis redis-cli SUBSCRIBE chat-broadcast
```

會看到類似這樣的輸出（一則訊息只會被 publish 一次，但因為 app-1 和 app-2 都訂閱了同一個 channel，兩邊各自的 `ChatRedisSubscriber` 都會收到）：

```
1) "message"
2) "chat-broadcast"
3) "{\"username\":\"alice\",\"content\":\"hello\",\"timestamp\":\"2026-08-22T14:39:26.68Z\",\"sourceInstanceId\":\"app-1\"}"
```

想看更底層、包含 `PUBLISH` 指令本身是誰下的（可以確認 app-1／app-2 是不是都真的有連上、有在發指令），改用：

```bash
docker compose exec redis redis-cli MONITOR
```

`MONITOR` 會即時印出 Redis 收到的每一條指令，量大時會很吵，用完記得 `Ctrl+C` 離開，不要長時間掛著（正式環境更是完全不該用，會拖累 Redis 效能）。

## 環境變數

| 變數 | 預設值 | 說明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 應用監聽的 port |
| `REDIS_HOST` | `localhost` | Redis 主機 |
| `REDIS_PORT` | `6379` | Redis port |
| `INSTANCE_ID` | `instance-${server.port}` | 用來標示訊息是哪個 instance 廣播的，docker-compose 中設為 `app-1`/`app-2` |
