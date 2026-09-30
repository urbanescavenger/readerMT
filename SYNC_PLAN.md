# 双端同步 + 远程书籍 实施计划

> 调研日期 2026-09-30；分支 `monorepo/unify`。
> 本文档是调研结论 + 实施方案，配套 `MERGE_PLAN.md`（引擎合并）/ `UPGRADE_PLAN.md`（工具链）。

## 0. 已定决策（用户拍板）

| 项 | 决策 |
|---|---|
| 「远程书籍」层次 | **两层都要**：A) 载入服务器书架 B) 远端直读（复用服务端章节缓存） |
| 改动范围 | **两端都改**（服务端 + app），以换取删除可传播与增量同步 |
| 首版同步范围 | **书源 + 在线书架元数据**；**不含阅读进度**（进度仍走现有 WebDAV） |
| 触发方式 | **先只做手动**（设置页「立即同步」），不引入 WorkManager |

---

## 1. 现状结论（调研摘要）

### 1.1 服务端已是成品 reader3 服务器，不是待开发模块

`modules/server` **没有任何 Spring MVC**：`api/controller/` 只是命名习惯，全是普通 Kotlin 类，无 `@RestController`/`@Component`，由 `api/YueduApi.kt` 的 `initRouter()` 手工 `new` 出来并用 Vert.x `Router` 注册。规模：**112 条 route / 91 个唯一 `/reader3/*` 路径**。

| 能力 | 现状 |
|---|---|
| 书源 CRUD | 单条/批量 upsert、按 url 查、列表（分组过滤）、单删/批删/清空、文件导入、远程 URL 导入(SSE)、共享书源池 `sharedBookSource` |
| 书架 CRUD | 列表（`refresh` 触发实时更新最新章节）、按 `name+author` upsert、批删、分组、换源、TXT/EPUB 导出、失效源检测 |
| 正文 | `getBookInfo` / `getChapterList` / `getBookContent` / `cover` + 章节列表与正文**缓存** |
| 多用户鉴权 | session cookie（Vert.x `LocalSessionStore`，**内存态、重启失效**）+ **`?accessToken=<user>:<token>`（7 天，存 `users.json` 的 `token_map`）** |
| 同步基础设施 | 完整 WebDAV 服务端 + zip 备份/恢复（6 类数据）+ `saveUserConfig`/`getUserConfig` |
| 持久化 | **纯 JSON 文件、零数据库**：`storage/data/<ns>/{bookSource,bookshelf,bookGroup,rssSources,replaceRule,bookmark}.json` |
| 定时任务 | `shelfUpdateJob` 每 10 分钟刷新书架；`clearUser` 每日清理不活跃用户 |

**对 Android 客户端最有利的一点**：`POST /reader3/login`（body `{username, password, isLogin, code?}`）的返回体直接带 `accessToken = "<username>:<token>"`（`api/controller/BaseController.kt:278` `formatUser`）。之后所有请求挂 `?accessToken=...`（`BaseController.checkAuth` 只读 query param，`BaseController.kt:128`）。
→ **app 侧无需 cookie jar、无需会话管理**。

端口：默认 8080（`verticle/RestVerticle.kt:28`，`reader.server.port` 可覆盖）；Docker 映射到宿主 4396。

### 1.2 app 侧零服务端客户端

`grep -rn "reader3" app/src/main/java` → **0 命中**。现有的 `Server` 实体、`ServersDialog`、`RemoteBookManager` 全部只服务 WebDAV。

**可复用存量资产**：

| 资产 | 位置 | 用途 |
|---|---|---|
| `Server` 实体（单值枚举 + config blob） | `app/src/main/java/io/legado/app/data/entities/Server.kt:16-27` | 加 `TYPE.READER` + 新 config 类 |
| 服务器管理 UI | `app/src/main/java/io/legado/app/ui/book/import/remote/{ServersDialog,ServerConfigDialog,ServerConfigViewModel}.kt` | 直接复用，扩展类型分支 |
| `RemoteBookManager` 抽象基类 | `app/src/main/java/io/legado/app/model/remote/RemoteBookManager.kt` | 新增 reader-server 实现 |
| WebDAV 实现（模板） | `app/src/main/java/io/legado/app/model/remote/RemoteBookWebDav.kt` | 照抄结构 |
| `origin` 打标先例 | `modules/legado-engine/src/main/kotlin/io/legado/app/constant/BookType.kt:73` `webDavTag = "webDav::"` | 加 `readerServerTag` |
| 「拉远程 JSON 数组按主键 merge」骨架 | `app/src/main/java/io/legado/app/model/RuleUpdate.kt:24-110` `cacheSource` | 同步 client 骨架 |
| 进度同步雏形（比较逻辑） | `app/src/main/java/io/legado/app/help/AppWebDav.kt:307-337` `downloadAllBookProgress` | 书架合并策略参照 |
| 引擎网络层 | `modules/legado-engine/.../help/http/OkHttpUtils.kt`（`newCallResponseBody`/`postJson`/`addHeaders`） | 新 client 直接复用 |
| 共享引擎 DTO | `modules/legado-engine/.../data/entities/{BookSource,BookChapter,RssArticle}.kt` | 两端 JSON 线格式已同源 |

### 1.3 三个真实缺口（决定工作量）

1. **无增量协议、无墓碑**：删除是直接从 JSON 数组移除，无 `since`/version/etag/tombstone。
   → 多端场景**删除无法传播**，且无法区分"被删了"与"本地还没同步到"。这是双端同步最硬的一块。
2. **进度只写不读**：只有 `POST /reader3/saveBookProgress`，**无 GET**；进度寄生在书架 `Book` 记录的 `durChapterIndex/Pos/Time/Title`，只能靠拉整个书架列表获取。（首版不做进度，此缺口仅记录）
3. **双端 Book 模型不对齐**：服务端 `Book` 无 `syncTime`；`group` 是 `Int`（app 是 `Long`）；服务端无 `BookProgress` 实体。

### 1.4 顺带发现的服务端既有问题（非本计划目标，但影响联调）

- **路由表与实现不一致**：`getBookshelf`/`getShelfBook` 只注册 GET（`YueduApi.kt:165-166`），handler 里的 POST 分支（`BookController.kt:1234`/`1251`）不可达会 404；`saveBookProgress` 只注册 POST，其 GET 分支（:478）不可达。
- **鉴权缺口**：至少 6 个读接口调了 `checkAuth(context)` 却**丢弃返回值**（`getBookSource`/`getBookSources`/`exploreBook`/`searchBook`/`getBookGroups`/`getUserInfo`/`getDefaultReplaceRules`）→ 实际匿名可读 `default` 命名空间。**`getChapterList`/`getBookContent` 鉴权是正常的**（远端直读必须带 token）。
- `?accessToken=` 走 query string 而非 header，且 `RestVerticle.kt:76-85` 会把 body < 1000 字符的请求打进日志 → token 有进日志风险。
- 死配置：`conf/application.properties:25-27` 的 `mongoUri`/`mongoDbName`、`server/bin/startup.sh:128` 的 `-Dreader.app.workDir` 均无代码引用。

---

## 2. 总体架构

```
                    ┌──────────────────────────────────────────┐
                    │  :modules:server  (reader3 服务器)        │
                    │  storage/data/<ns>/                       │
                    │    bookSource.json   bookshelf.json       │
                    │    tombstone.json    (本计划新增)          │
                    │  /reader3/*  + accessToken 鉴权            │
                    └────────────┬─────────────────────────────┘
                                 │  HTTP (accessToken)
                    ┌────────────┴─────────────────────────────┐
                    │  :app                                     │
                    │  ReaderServerClient  (新增)               │
                    │   ├── SyncManager        → 功能一          │
                    │   └── RemoteBookServer   → 功能二 A        │
                    │  Server.TYPE.READER (新增)                │
                    │  内置书源 readerServer:// → 功能二 B       │
                    └──────────────────────────────────────────┘
```

**核心取向**：服务端为同步中枢（always-on，天然有网络与存储），app 主动 pull/push。
**不做**对 `AppWebDav` 的重构——WebDAV 继续负责阅读进度与整包备份，新链路平行存在。对称重构收益低、回归风险高。

---

## 3. 数据模型改动

### 3.1 版本号字段（LWW 的基准）

| 端 | 实体 | 改动 | 迁移 |
|---|---|---|---|
| 引擎 | `BookSource` | 新增 `lastModifiedAt: Long?`（任一端本地编辑即刷新） | 无（JSON 序列化，向后兼容） |
| server | `io.legado.app.data.entities.Book` | 新增 `lastModifiedAt: Long = 0` | 无（JSON 文件） |
| app | `BookSourceEntity` | 新增 `lastModifiedAt: Long` | Room **v89 → v90** |
| app | `Book` | 新增 `lastModifiedAt: Long` | 同上 |

**为什么不复用现有字段**：
- `BookSource.lastUpdateTime` 语义是"**订阅**更新时间"，被 `RuleUpdate.cacheSource` 用来决定是否从订阅 URL 更新。污染它会破坏现有书源订阅功能。
- `Book.syncTime` 语义是"**WebDAV 进度**同步时间"（`AppWebDav` 写入并用于 `webDavFile.lastModify <= book.syncTime` 判断）。同样不复用。

> 引擎 `BookSource` 是两端共享的 DTO（server 经 `SourceAnalyzer.jsonToBookSource` 解析，见 `io/legado/app/utils/SourceAnalyzer.kt`），改一处两端受益；app 侧仍需同步改 Room 实体。

### 3.2 墓碑（删除传播）

**服务端**：新增 `storage/data/<ns>/tombstone.json`，结构
```json
[{ "type": "bookSource" | "book", "key": "<bookSourceUrl|bookUrl>", "deletedAt": 1730000000000 }]
```
写入点：`BookSourceController.deleteBookSource(s)` / `deleteAllBookSources`、`BookController.deleteBook(s)`。

**app 端**：新增 Room 表
```kotlin
@Entity(tableName = "sync_tombstones", primaryKeys = ["type", "key"])
data class SyncTombstone(
    val type: String,          // "bookSource" | "book"
    val key: String,
    val deletedAt: Long
)
```
写入点：`help/source/SourceHelp.deleteBookSources`（书源删除的唯一收口）、书架删除的收口处。
迁移：`DatabaseMigrations` 加 `Migration_89_90`（v89 → v90）。

### 3.3 双端 Book 字段映射（**不要试图全等**）

只同步共有且对"书架元数据"有意义的字段，其余显式忽略：

| 概念 | app `Book` | server `Book` | 处理 |
|---|---|---|---|
| 主键 | `bookUrl` | `bookUrl` | 直接对应 |
| 书名/作者 | `name`/`author` | 同名 | 直接对应 |
| 书源 | `origin`/`originName` | 同名 | 直接对应（**但见 §5.3 回环坑**） |
| 封面/简介 | `coverUrl`/`intro` + `custom*` | 同名 | 直接对应 |
| 分组 | `group: Long` | `group: Int` | **位掩码**，转换为 Int（当前值域在 Int 内；转换前断言高位为 0，越界则丢弃并记日志） |
| 进度 | `durChapterIndex/Pos/Time/Title` | 同名 | **首版不同步**（排除） |
| 类型 | `type: Int` 位掩码 (`BookType`) | `type: Int` 枚举(0-4) | **语义不同**，首版不同步 `type`，仅用于判定"是否在线书" |
| 版本 | `lastModifiedAt`（新增） | `lastModifiedAt`（新增） | 同步 |
| 忽略 | `variable`/`readConfig`/`syncTime`/`durVolumeIndex`/`chapterInVolumeIndex`/`totalChapterNum` 等 | `lastCheckTime`/`lastCheckCount`/`order`/`originOrder`/`canUpdate`/`useReplaceRule` | 各自本地 |

**「在线书架」的界定**：只同步 `BookType.local` 未置位、且 `origin` 非 `loc_book`/`webDav::`/`readerServer::` 的书。本地书籍（txt/epub 文件）与服务端本地书仓的书**跳过**（服务端文档亦注明"本地书源的书籍同步后无法打开"）。

---

## 4. 服务端改动清单

| # | 改动 | 位置 | 说明 |
|---|---|---|---|
| S1 | `Book` DTO 加 `lastModifiedAt: Long = 0` | `io/legado/app/data/entities/Book.kt` | 兼容旧 JSON（缺字段默认 0） |
| S2 | 书源写入刷新 `lastModifiedAt` | `BookSourceController.saveBookSource(s)` | 服务端本地编辑时置 `now` |
| S3 | 书架写入刷新 `lastModifiedAt` | `BookController.saveBook` / `saveBookProgress` / 分组变更 | 同上 |
| S4 | 墓碑写入 | delete 路径（§3.2） | 新增 `tombstone.json` 读写工具 |
| S5 | `GET /reader3/getBookSources?since=<ts>` | `BookSourceController.getBookSources` | 缺省全量；带 since 只返回 `lastModifiedAt > since` |
| S6 | `GET /reader3/getBookshelf?since=<ts>` | `BookController.getBookshelf` | **不要传 `refresh`**（会触发并发 16 实时重取，代价高） |
| S7 | `GET /reader3/getTombstones?since=<ts>` | 新增 handler（可放 `BookController` 或新 `SyncController`） | 返回墓碑数组 |
| S8 | `POST /reader3/saveBooks`（批量） | 新增 | 避免逐条 POST 导致 `saveStorage` 全量重写 JSON 的写放大 |
| S9 | 修 `getBookshelf`/`getShelfBook` 的 POST 注册缺失 | `YueduApi.kt:165-166` | 顺手修（§1.4） |
| S10 | （可选）`accessToken` 支持 header 传入 | `BaseController.checkAuth` | 降低 token 进日志风险 |

**注意**：服务端 `saveStorage`（`utils/VertExt.kt:128-156`）每次**整体重写 JSON 文件**，无索引、无按 id 查询（都是遍历数组）。批量端点 S8 是缓解写放大的必要手段，但不是根本解；书架规模上限由 `userBookLimit`（默认 200）兜住，可接受。

---

## 5. 功能一：双端同步（书源 + 在线书架元数据）

### 5.1 app 侧新增组件

```
app/src/main/java/io/legado/app/
├── data/entities/Server.kt              (改) TYPE.READER + ReaderServerConfig
├── data/entities/SyncTombstone.kt       (新)
├── model/sync/
│   ├── ReaderServerClient.kt            (新) HTTP 客户端
│   ├── SyncTarget.kt                    (新) 同步后端接口（留扩展位）
│   ├── ReaderServerSyncTarget.kt        (新) 唯一实现
│   └── SyncManager.kt                   (新) 编排 + 合并规则
└── ui/config/ServerSyncFragment.kt      (新) 手动同步入口
```

`ReaderServerConfig`：`{ url, username, password, accessToken }`——`accessToken` 登录后缓存，避免每次同步都登录。与 `WebDavConfig` 一样明文存 DB（`servers.json` 在备份时经 `BackupAES` 加密，与现状一致）。

### 5.2 同步协议（一轮 = pull → merge → push）

```
1. 确保 accessToken（缓存有效则跳过；否则 POST /reader3/login，失败则提示）
2. 记 t0 = max(本地 max(lastModifiedAt), 上次 lastSyncAt)
3. PULL:
     GET /reader3/getBookSources?since=0      → 远端书源全量
     GET /reader3/getBookshelf?since=0        → 远端书架全量
     GET /reader3/getTombstones?since=<lastSyncAt>  → 远端墓碑增量
   （首版 since 传 0 走全量：数据量小、逻辑简单、正确性优先；
     实体数量上万后再启用增量参数）
4. MERGE（见 5.3）
5. PUSH:
     POST /reader3/saveBookSources  ← 本地 lastModifiedAt > lastSyncAt 的书源
     POST /reader3/saveBooks        ← 本地新增/变更的书架条目
     POST 墓碑（本地删除 → 远端）
6. lastSyncAt = t0（持久化）
```

### 5.3 合并规则（LWW）

对每个实体（key = `bookSourceUrl` / `bookUrl`）：

```
远端有墓碑 + 墓碑.deletedAt >= max(本地.lastModifiedAt, 本地墓碑.deletedAt)
    → 删除本地（书源/书）并把墓碑落地本地
本地有墓碑 + 本地墓碑.deletedAt > 远端.lastModifiedAt
    → 推墓碑到远端（保留删除）
两边都有且都无墓碑
    → lastModifiedAt 大者胜；相等则远端胜（保证收敛）
只有一边有
    → 补到另一边
```

**关键：墓碑必须先于实体比较**，否则"删除后被另一端的旧副本复活"（经典 bug）。

### 5.4 ⚠️ 回环坑（必须处理）

内置书源（§6.2）的 `bookSourceUrl` 以 `readerServer://` 开头，**绝不能被同步到服务端**，否则：
- 污染服务端书源池
- 多设备间互相覆盖 `accessToken`
- 可能造成同步自激

处理：`SyncManager` 在 bookSource 的 pull/push 两端都**无条件过滤 `origin`/`bookSourceUrl` 以 `readerServer://` 开头的条目**；`Book` 同步同理过滤 `origin` 为内置书源的条目（这些书由功能二管理，不参与书架同步）。

---

## 6. 功能二：远程书籍（两层）

### 6.1 层 A —— 载入服务器书架

**不硬套 `RemoteBookManager`**。该抽象是**文件语义**（`RemoteBook{filename, path, size, lastModify, contentType, isDir}` + `downloadRemoteBook(): Uri`），服务端书架是**在线书籍语义**，硬套会让 WebDAV 实现被迫扭曲。

**做法：加一个 open 方法，WebDAV 走默认实现保持原行为。**

```kotlin
// RemoteBookManager.kt 新增
open suspend fun addToBookshelf(remoteBook: RemoteBook): Book? {
    // 默认实现 = 现有 WebDAV 行为：下载文件 → LocalBook.importFiles
}

// RemoteBookServer.kt 新增，override 为直接在本地建 Book 记录
class RemoteBookServer(
    val rootUrl: String,          // 服务器根，如 http://192.168.1.10:8080
    val client: ReaderServerClient,
    val serverID: Long? = null
) : RemoteBookManager() {
    override suspend fun getRemoteBookList(path: String): MutableList<RemoteBook>
        // → GET /reader3/getBookshelf，映射：
        //    filename   = book.name
        //    path       = book.bookUrl        （后续定位用）
        //    lastModify = book.lastCheckTime
        //    contentType= "book"（非目录，区别于 WebDAV 的 "folder"）
        //    isOnBookShelf = appDb.bookDao.getBook(bookUrl) != null
    override suspend fun downloadRemoteBook(remoteBook: RemoteBook): Uri = throw ...
        // 不适用；UI 走 addToBookshelf 分支
    override suspend fun delete(remoteBookUrl: String)  // → 远端 deleteBook（可选）
    // upload / getRemoteBook 按需最小实现
}
```

`RemoteBookViewModel.addToBookshelf` 改调新的 `addToBookshelf(remoteBook)`，WebDAV 行为不变。

**UI**：`RemoteBookActivity` 复用，按 `Server.type` 分派到 `RemoteBookServer` 或 `RemoteBookWebDav`；`ServersDialog` 的 TYPE 分支加 READER。服务端书架没有目录层级，UI 需在 `isDir == false` 时正常展示（现有排序逻辑 `RemoteBookSort` 已按 `isDir` 分组，天然兼容）。

### 6.2 层 B —— 远端直读（服务器注册为内置书源）

**这是本计划里改动最小、收益最大的一招。**

关键事实：`app/src/main/java/io/legado/app/model/ReadBook.kt:167` 按 `book.origin` 反查书源
```kotlin
appDb.bookSourceDao.getBookSource(book.origin)?.let { ... }
```
→ 只要把一个合成书源写进 `book_sources` 表，书的 `origin` 指向它，**阅读管线零改动**（目录走 `WebBook.getChapterList`、正文走 `WebBook.getContent`），全部规则驱动。

内置书源模板：

```
bookSourceUrl  = "readerServer://<host:port>/<username>"
bookSourceName = "服务器：<Server.name>"
bookSourceType = BookSourceType.default
bookSourceGroup= "服务器"
enabled        = true

# 注意：服务端 checkAuth 只读 query param（BaseController.kt:128），
# 所以 accessToken 必须编进 URL，不能放 header
exploreUrl     = "<root>/reader3/getBookshelf?accessToken=<token>"
                 # 规则 JSONPath: $.data[*]
                 #   name → $.name,  bookUrl → $.bookUrl,  author → $.author,
                 #   coverUrl → $.coverUrl,  intro → $.intro,  kind → $.latestChapterTitle

ruleToc.chapterList = "<root>/reader3/getChapterList?url={{bookUrl}}&accessToken=<token>"
                      # JSONPath: $.data[*]
                      #   text → $.title,  href → $.url
ruleContent.content = "<root>/reader3/getBookContent?url={{bookUrl}}&index={{chapterIndex}}&accessToken=<token>"
                      # JSONPath: $.data
```

**载入策略（层 A 与层 B 的衔接）**：层 A 载入时**默认把 `origin` 设为内置书源**（即"载入即直读"），原书源名记入 `originName` 以便回退；用户若不想要直读可换源（服务端本身支持 `setBookSource`）。服务端返回的 Book 自带原始 `origin`，回退路径始终可用。

**必须验证的 5 个技术点**（见 §8 阶段划分，逐项在 CI/真机确认）：

1. legado 的 JSONPath 规则能否直接吃 `ReturnData` 包装 → `$.data[*].title`。`ReturnData{isSuccess, errorMsg, data}` 与 app 侧 `api/ReturnData.kt` 同构，预期可行。
2. `getBookContent` 的 `data` 是**HTML 字符串**（服务端章节内容含标签），取到后需要清洗。可能需要 `@js:result` 或 `##` 正则，取决于服务端是否已做 `delTag`。
3. `getChapterList` 返回的 `BookChapter` 是否提供 `{{chapterIndex}}` 所需字段（`url`/`title`/`index`）。
4. `exploreUrl` 的 JSONPath 与 `BookList` 解析规则（`app/.../model/webBook/BookList.kt`）字段名对齐。
5. 内置书源**不进同步**（§5.4）。

---

## 7. 风险与缓解

| 风险 | 严重度 | 缓解 |
|---|---|---|
| **回环**：内置书源被同步到服务端 | 高 | §5.4 双向过滤 `readerServer://`；加单元测试断言 |
| 墓碑缺失导致删除复活 | 高 | 墓碑先于实体比较（§5.3）；专门测试用例：A 删 → 同步 → B 拉，断言不复活 |
| 双端 Book 模型不等导致字段丢失 | 中 | §3.3 显式映射表 + 只同步白名单字段；不做全量 round-trip |
| `group` Long→Int 截断 | 中 | 转换前断言高位为 0，越界丢弃并记日志 |
| 服务端 `saveStorage` 全量重写 JSON | 中 | 批量端点 S8；书架规模由 `userBookLimit` 兜住 |
| 内置书源规则依赖服务端响应格式 | 中 | §6.2 五个技术点分阶段验证，任一不成立则回退到"层 A + 原书源" |
| `accessToken` 进服务端日志 | 低 | 服务端 S10 支持 header（可选）；或用短有效期 token |
| Room 迁移 v89→v90 出错 | 中 | `exportSchema = true`（schema JSON 已入库 `app/schemas/`），迁移加测试 |

---

## 8. 分阶段执行

> **验证约定**：本地无 Android SDK/Gradle，**所有编译验证走云端 CI**。每阶段推送后必须监控本次 CI 运行至拿到最终结果（`gh run list --repo urbanescavenger/BiliMT --json databaseId,status,conclusion,headSha` 匹配完整 SHA 或 databaseId），**红了先修再继续**，不在红 CI 上叠功能。

### 阶段 0：数据模型（两端，先做不可逆的部分）
- 引擎 `BookSource` 加 `lastModifiedAt`
- app `BookSourceEntity`/`Book` 加 `lastModifiedAt` + `SyncTombstone` 表 + `Migration_89_90`
- server `Book` 加 `lastModifiedAt`
- **CI 验证**：三端编译绿 + Room schema 导出更新

### 阶段 1：服务端同步端点（S1–S9）
- `since` 参数、墓碑读写、批量 `saveBooks`、修 POST 注册
- **CI 验证**：server 编译绿 + 冒烟脚本（沿用现有 docker 冒烟方式，`curl` 断言 `getTombstones`/`saveBooks`）

### 阶段 2：书源同步（功能一 · 前半）
- app `ReaderServerClient` + `SyncTarget` + `SyncManager`（只做 bookSource）
- `Server.TYPE.READER` + `ReaderServerConfig` + UI 类型分支
- 设置页手动「立即同步」入口
- **CI 验证**：app 编译绿
- **真机验证**：两端各建/改/删若干书源 → 同步 → 双向一致；删除可传播

### 阶段 3：书架元数据同步（功能一 · 后半）
- `SyncManager` 加 Book 的 pull/merge/push + 在线书判定 + 字段映射
- **真机验证**：加书/改分组/删书双向一致，本地书被正确跳过

### 阶段 4：远程书籍 层 A（载入服务器书架）
- `RemoteBookManager.addToBookshelf` open 方法 + `RemoteBookServer`
- `RemoteBookViewModel` / `RemoteBookActivity` / `ServersDialog` 分派
- **真机验证**：浏览器服务器书架 → 载入 → 出现在本地书架

### 阶段 5：远程书籍 层 B（远端直读 · 内置书源）
- 内置书源模板生成 + 写入 `book_sources` + `origin` 打标
- 逐项验证 §6.2 的 5 个技术点（先在服务端用 `curl` 看响应，再在 app 里调规则）
- **真机验证**：载入的书能正常出目录与正文；断开原书源站点仍可读（证明确实走服务端缓存）

### 阶段 6（后续）
- 阅读进度走服务端（需新增 GET 进度端点，补 §1.3 缺口 2）
- 自动后台同步（WorkManager）
- 服务端鉴权缺口修复（§1.4）

---

## 9. 验收清单

- [ ] 书源：app 增/删/改 → 服务端一致；服务端增/删/改 → app 一致
- [ ] 书源：一端删除 → 同步后另一端**确实删除**（不被复活）
- [ ] 书源：内置 `readerServer://` 源**未**出现在服务端书源池
- [ ] 书架：在线书籍增/删/改（含分组）双端一致
- [ ] 书架：本地书籍（txt/epub）**未**被同步
- [ ] 远程书籍 A：能列出服务端书架并载入本地
- [ ] 远程书籍 B：载入的书目录/正文来自服务端（原站点不可达时仍可读）
- [ ] 冲突：两端同时改同一条 → 收敛到同值，无反复震荡
- [ ] 无 token 时同步给出明确提示而非静默失败
- [ ] CI 全绿（android-build / test / docker / engine-cross-check）
