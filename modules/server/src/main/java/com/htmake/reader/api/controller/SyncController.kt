package com.htmake.reader.api.controller

import com.htmake.reader.api.ReturnData
import com.htmake.reader.utils.asJsonArray
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import kotlin.coroutines.CoroutineContext

/**
 * 双端同步控制器(SYNC_PLAN.md 阶段 1)。
 *
 * 与既有 `/reader3/saveBookSources` 等端点**刻意分开**:那些端点代表"服务端本地编辑",
 * 写入时把 `lastModifiedAt` 刷成 now;本控制器的推送则**保留客户端自带的版本号**,
 * 否则服务端时间总是最新,LWW 会退化成"服务端永远赢",另一端的编辑再也无法生效。
 *
 * 分工:
 * - 拉:`GET /reader3/getBookSources?since=`、`GET /reader3/getBookshelf?since=`、
 *   `GET /reader3/getTombstones?since=`
 * - 推:`POST /reader3/syncPush`(一次往返推书源+书架+墓碑)
 *
 * 合并规则一律 **LWW + 墓碑优先**,且服务端做仲裁:推送条目的 `lastModifiedAt` 若不比
 * 服务端副本新,则拒绝本次写入并计入 skipped,避免滞后的客户端覆盖较新内容。
 */
class SyncController(coroutineContext: CoroutineContext) : BaseController(coroutineContext) {

    /**
     * 拉取删除墓碑。
     *
     * 墓碑存放在"实体所属"的命名空间:书源墓碑在书源命名空间(共享书源模式下为 default),
     * 书籍墓碑在用户命名空间。非共享模式下两者可能相同,故按 (type, key) 去重取较新时间。
     */
    suspend fun getTombstones(context: RoutingContext): ReturnData {
        val returnData = ReturnData()
        if (!checkAuth(context)) {
            return returnData.setData("NEED_LOGIN").setErrorMsg("请登录后使用")
        }
        val since = getSinceParam(context)
        // 按 (type, key) 去重:非共享模式下书源与书籍命名空间相同,同一份墓碑会被读两次
        val merged = linkedMapOf<String, JsonObject>()
        for (ns in setOf(getBookSourceNameSpace(context), getUserNameSpace(context))) {
            val list = getUserTombstones(ns)
            for (i in 0 until list.size()) {
                val item = list.getJsonObject(i)
                val deletedAt = jsonToLong(item.getValue("deletedAt"))
                if (deletedAt <= since) {
                    continue
                }
                val type = item.getString("type", "")
                val key = item.getString("key", "")
                if (type.isEmpty() || key.isEmpty()) {
                    continue
                }
                val id = "$type\u0000$key"
                val old = merged[id]
                if (old == null || jsonToLong(old.getValue("deletedAt")) < deletedAt) {
                    merged[id] = item
                }
            }
        }
        val result = JsonArray()
        merged.values.forEach { result.add(it) }
        return returnData.setData(result.getList())
    }

    /**
     * 推送本地变更。请求体:
     * ```json
     * { "bookSources": [BookSource...], "books": [Book...],
     *   "tombstones": [{"type":"bookSource|book","key":"...","deletedAt":123}] }
     * ```
     * 三个字段都可省略。返回各类的 applied / skipped 计数,便于客户端诊断。
     */
    suspend fun syncPush(context: RoutingContext): ReturnData {
        val returnData = ReturnData()
        if (!checkAuth(context)) {
            return returnData.setData("NEED_LOGIN").setErrorMsg("请登录后使用")
        }
        val body = context.bodyAsJson ?: return returnData.setErrorMsg("参数错误")
        val userNS = getUserNameSpace(context)
        val sourceNS = getBookSourceNameSpace(context)

        val (sourceApplied, sourceSkipped) = applyBookSources(body.getJsonArray("bookSources"), sourceNS)
        val (bookApplied, bookSkipped) = applyBooks(body.getJsonArray("books"), userNS)
        // 墓碑最后应用:实体先落地,再按墓碑删除,保证"墓碑更新则实体一定被删"
        val tombApplied = applyTombstones(body.getJsonArray("tombstones"), userNS, sourceNS)

        val result = JsonObject()
            .put("bookSources", JsonObject().put("applied", sourceApplied).put("skipped", sourceSkipped))
            .put("books", JsonObject().put("applied", bookApplied).put("skipped", bookSkipped))
            .put("tombstones", tombApplied)
        return returnData.setData(result.map)
    }

    private suspend fun applyBookSources(jsonArray: JsonArray?, userNameSpace: String): Pair<Int, Int> {
        if (jsonArray == null || jsonArray.size() == 0) {
            return 0 to 0
        }
        var bookSourceList = asJsonArray(getUserStorage(userNameSpace, "bookSource")) ?: JsonArray()
        val tombstones = tombstoneMap(userNameSpace, BaseController.TOMBSTONE_TYPE_BOOK_SOURCE)
        var applied = 0
        var skipped = 0
        for (k in 0 until jsonArray.size()) {
            // 用 GSON 直接映射到引擎 BookSource,而**不用** SourceAnalyzer.jsonToBookSource:
            // 后者是为"导入第三方 legacy 书源"写的逐字段容错解析器,它不认 lastModifiedAt,
            // 还会丢掉 jsLib / enabledCookieJar / loginUi / coverDecodeJs / variableComment /
            // exploreScreen / ruleReview / eventListener / customButton —— 同步过去再被
            // 第三台设备拉下来就是坏源(例如带 JS 库的书源直接失效)。
            // 同步收发的是我们自己 app 的格式,忠实 round-trip 才是正确语义。
            val pushed = GSON.fromJsonObject<BookSource>(jsonArray.getJsonObject(k).toString()).getOrNull()
            if (pushed == null || pushed.bookSourceUrl.isEmpty()) {
                skipped++
                continue
            }
            // 回环防护(纵深):远端直读的内置书源带的是**推送方本机**的 accessToken 与服务器
            // 地址,一旦进入服务端书源池就会被其它设备拉走(凭证泄漏),且会造成同步自激。
            // app 侧已在两个方向过滤,服务端再拦一道,不依赖客户端守规矩。
            if (pushed.bookSourceUrl.startsWith(READER_SERVER_SOURCE_PREFIX)) {
                skipped++
                continue
            }
            if (pushed.lastModifiedAt == null || pushed.lastModifiedAt!! <= 0) {
                pushed.lastModifiedAt = System.currentTimeMillis()
            }
            val pushedAt = pushed.lastModifiedAt!!
            // 纵深校验:本地墓碑比实体新 → 不落库,否则一个滞后的客户端能把已删的源复活
            val tombstoneAt = tombstones[pushed.bookSourceUrl]
            if (tombstoneAt != null && tombstoneAt >= pushedAt) {
                skipped++
                continue
            }
            var existIndex = -1
            var existingAt = 0L
            for (i in 0 until bookSourceList.size()) {
                val obj = bookSourceList.getJsonObject(i)
                if (obj.getString("bookSourceUrl", "") == pushed.bookSourceUrl) {
                    existIndex = i
                    existingAt = jsonToLong(obj.getValue("lastModifiedAt"))
                    break
                }
            }
            // LWW 仲裁:服务端副本更新则拒绝本次推送
            if (existIndex >= 0 && existingAt > pushedAt) {
                skipped++
                continue
            }
            if (existIndex >= 0) {
                val list = bookSourceList.getList()
                list.set(existIndex, JsonObject.mapFrom(pushed))
                bookSourceList = JsonArray(list)
            } else {
                bookSourceList.add(JsonObject.mapFrom(pushed))
            }
            applied++
        }
        saveUserStorage(userNameSpace, "bookSource", bookSourceList)
        return applied to skipped
    }

    private suspend fun applyBooks(jsonArray: JsonArray?, userNameSpace: String): Pair<Int, Int> {
        if (jsonArray == null || jsonArray.size() == 0) {
            return 0 to 0
        }
        var bookshelf = asJsonArray(getUserStorage(userNameSpace, "bookshelf")) ?: JsonArray()
        val tombstones = tombstoneMap(userNameSpace, BaseController.TOMBSTONE_TYPE_BOOK)
        var applied = 0
        var skipped = 0
        for (k in 0 until jsonArray.size()) {
            val pushed = jsonArray.getJsonObject(k).mapTo(Book::class.java)
            if (pushed.bookUrl.isEmpty() || isNotSyncable(pushed)) {
                skipped++
                continue
            }
            if (pushed.lastModifiedAt <= 0) {
                pushed.lastModifiedAt = System.currentTimeMillis()
            }
            // 纵深校验:本地墓碑比实体新 → 不落库(同 applyBookSources)
            val tombstoneAt = tombstones[pushed.bookUrl]
            if (tombstoneAt != null && tombstoneAt >= pushed.lastModifiedAt) {
                skipped++
                continue
            }
            // 先按 bookUrl 认身份;退回 name+author 以兼容服务端既有的(name,author)唯一约束,
            // 否则同一本书会因链接不同而重复入库
            var existIndex = -1
            var existing: Book? = null
            for (i in 0 until bookshelf.size()) {
                val _book = bookshelf.getJsonObject(i).mapTo(Book::class.java)
                if (_book.bookUrl == pushed.bookUrl) {
                    existIndex = i
                    existing = _book
                    break
                }
                if (_book.name == pushed.name && _book.author == pushed.author) {
                    existIndex = i
                    existing = _book
                    break
                }
            }
            // LWW 仲裁
            if (existing != null && existing.lastModifiedAt > pushed.lastModifiedAt) {
                skipped++
                continue
            }
            if (existing == null && bookshelf.size() >= appConfig.userBookLimit) {
                skipped++
                continue
            }
            if (existing != null) {
                // 只覆盖同步白名单字段,保留服务端的阅读进度与章节状态
                mergeSyncedBook(existing, pushed)
                val list = bookshelf.getList()
                list.set(existIndex, JsonObject.mapFrom(existing))
                bookshelf = JsonArray(list)
            } else {
                bookshelf.add(JsonObject.mapFrom(pushed))
            }
            applied++
        }
        saveUserStorage(userNameSpace, "bookshelf", bookshelf)
        return applied to skipped
    }

    private suspend fun applyTombstones(jsonArray: JsonArray?, userNS: String, sourceNS: String): Int {
        if (jsonArray == null || jsonArray.size() == 0) {
            return 0
        }
        var count = 0
        for (k in 0 until jsonArray.size()) {
            val item = jsonArray.getJsonObject(k)
            val type = item.getString("type", "")
            val key = item.getString("key", "")
            if (type.isEmpty() || key.isEmpty()) {
                continue
            }
            val deletedAt = jsonToLong(item.getValue("deletedAt"))
            val ns = if (type == BaseController.TOMBSTONE_TYPE_BOOK_SOURCE) sourceNS else userNS
            // 记墓碑:让其它客户端也能拉到这次删除
            addTombstone(ns, type, key, deletedAt)
            // 应用墓碑:仅当实体不比墓碑新时才删,否则视为"另一端已重新创建该书/源"
            if (type == BaseController.TOMBSTONE_TYPE_BOOK_SOURCE) {
                removeBookSourceIfOlder(ns, key, deletedAt)
            } else {
                removeBookIfOlder(ns, key, deletedAt)
            }
            count++
        }
        return count
    }

    private suspend fun removeBookSourceIfOlder(userNameSpace: String, bookSourceUrl: String, deletedAt: Long) {
        val list = asJsonArray(getUserStorage(userNameSpace, "bookSource")) ?: return
        var removed = false
        val out = JsonArray()
        for (i in 0 until list.size()) {
            val obj = list.getJsonObject(i)
            if (obj.getString("bookSourceUrl", "") == bookSourceUrl &&
                jsonToLong(obj.getValue("lastModifiedAt")) <= deletedAt
            ) {
                removed = true
                continue
            }
            out.add(obj)
        }
        if (removed) {
            saveUserStorage(userNameSpace, "bookSource", out)
        }
    }

    private suspend fun removeBookIfOlder(userNameSpace: String, bookUrl: String, deletedAt: Long) {
        val list = asJsonArray(getUserStorage(userNameSpace, "bookshelf")) ?: return
        var removed = false
        val out = JsonArray()
        for (i in 0 until list.size()) {
            val obj = list.getJsonObject(i)
            if (obj.mapTo(Book::class.java).bookUrl == bookUrl &&
                jsonToLong(obj.getValue("lastModifiedAt")) <= deletedAt
            ) {
                removed = true
                continue
            }
            out.add(obj)
        }
        if (removed) {
            saveUserStorage(userNameSpace, "bookshelf", out)
        }
    }

    /**
     * 读取某命名空间内指定类型的墓碑,返回 `key → deletedAt`(同 key 取较新值)。
     *
     * 一次读盘供整轮写入使用,而不是每个实体查一次 —— `getStorage` 每次都整文件读+解析,
     * 逐实体调用会退化成 O(n²)。
     */
    private suspend fun tombstoneMap(userNameSpace: String, type: String): Map<String, Long> {
        val map = HashMap<String, Long>()
        val list = getUserTombstones(userNameSpace)
        for (i in 0 until list.size()) {
            val item = list.getJsonObject(i)
            if (item.getString("type", "") != type) {
                continue
            }
            val key = item.getString("key", "")
            if (key.isEmpty()) {
                continue
            }
            val at = jsonToLong(item.getValue("deletedAt"))
            val old = map[key]
            if (old == null || old < at) {
                map[key] = at
            }
        }
        return map
    }

    /**
     * 不该参与同步的书籍:本地书籍(含服务端本地书仓)文件无法跨端,同步过去也打不开;
     * `webDav::` 是 app 侧远程文件书;`readerServer://` 是远端直读的内置书源(见 SYNC_PLAN.md §5.4)。
     */
    private fun isNotSyncable(book: Book): Boolean {
        return book.isLocalBook() ||
            book.origin.startsWith("webDav::") ||
            book.origin.startsWith("readerServer://")
    }

    /**
     * 把推送来的书籍**仅白名单字段**覆盖到服务端已有记录上。
     *
     * 必须逐字段合并而不是整对象替换:客户端(按 SYNC_PLAN.md §3.3)只推送同步白名单,
     * 若整对象覆盖,推送 JSON 里缺失的 `durChapterIndex` / `latestChapterTitle` /
     * `totalChapterNum` / `lastCheckTime` / `readConfig` 等会取默认值,把服务端上的
     * 阅读进度与章节信息直接抹掉。
     */
    private fun mergeSyncedBook(target: Book, source: Book) {
        target.name = source.name
        target.author = source.author
        target.bookUrl = source.bookUrl
        target.tocUrl = source.tocUrl
        target.origin = source.origin
        target.originName = source.originName
        target.kind = source.kind
        target.customTag = source.customTag
        target.coverUrl = source.coverUrl
        target.customCoverUrl = source.customCoverUrl
        target.intro = source.intro
        target.customIntro = source.customIntro
        target.wordCount = source.wordCount
        target.group = source.group
        target.lastModifiedAt = source.lastModifiedAt
    }

    companion object {
        /**
         * 远端直读内置书源的 URL 前缀。app 端在 `SyncManager.READER_SERVER_SOURCE_PREFIX`
         * 有同值常量(阶段 5 创建该源时两边必须一致);服务端这里再拦一道做纵深防护。
         */
        const val READER_SERVER_SOURCE_PREFIX = "readerServer://"
    }

}
