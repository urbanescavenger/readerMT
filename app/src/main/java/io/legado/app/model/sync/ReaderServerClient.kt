package io.legado.app.model.sync

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSourceEntity
import io.legado.app.data.entities.SyncTombstone
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.net.URLEncoder

/**
 * 阅读3 服务器(reader-mt)的 HTTP 客户端,只覆盖双端同步需要的端点。
 *
 * **鉴权**:服务端 `checkAuth` 只读 **query param** `accessToken`(见 server
 * `BaseController.kt:128`),不认 header。`POST /reader3/login` 的返回体里直接带
 * `accessToken = "<username>:<token>"`,所以客户端不需要维护 cookie jar —— 登录一次后
 * 把 token 挂到后续每个请求的 URL 上即可。
 *
 * 未登录时服务端会把 `data` 置为字符串 `NEED_LOGIN`,据此提示重新登录。
 */
class ReaderServerClient(
    rootUrl: String,
    var accessToken: String = "",
    private val username: String = "",
    private val password: String = ""
) {

    private val root = rootUrl.trim().trimEnd('/')

    /**
     * 登录并返回 `accessToken`(形如 `<username>:<token>`)。
     *
     * 服务端同时支持注册与登录,这里固定 `isLogin = true`:同步场景下用户名不存在应当报错,
     * 而不是悄悄注册一个新用户。
     */
    suspend fun login(): String {
        if (username.isEmpty() || password.isEmpty()) {
            throw NoStackTraceException("请先填写服务器用户名和密码")
        }
        val body = GSON.toJson(
            mapOf(
                "username" to username,
                "password" to password,
                "isLogin" to true
            )
        )
        val text = request("login", post = body)
        val data = parseData(text) ?: throw NoStackTraceException("登录失败:服务端未返回数据")
        val token = data.takeIf { it.isJsonObject }
            ?.asJsonObject?.get("accessToken")?.asString
        if (token.isNullOrEmpty()) {
            throw NoStackTraceException("登录失败:未拿到 accessToken")
        }
        accessToken = token
        return token
    }

    /** 拉取服务端全部书源。 */
    suspend fun getBookSources(): List<BookSourceEntity> {
        val data = parseData(request("getBookSources")) ?: return emptyList()
        if (!data.isJsonArray) {
            return emptyList()
        }
        return data.asJsonArray.mapNotNull { element ->
            runCatching { GSON.fromJson(element, BookSourceEntity::class.java) }.getOrNull()
        }
    }

    /** 拉取服务端书架。 */
    suspend fun getBookshelf(): List<Book> {
        val data = parseData(request("getBookshelf")) ?: return emptyList()
        if (!data.isJsonArray) {
            return emptyList()
        }
        return data.asJsonArray.mapNotNull { element ->
            runCatching { GSON.fromJson(element, Book::class.java) }.getOrNull()
        }
    }

    /**
     * 拉取全部删除墓碑(不传 since:墓碑量小,且传 since 需要维护基准,
     * 首版按 SYNC_PLAN.md §5.2 走全量换取正确性)。
     */
    suspend fun getTombstones(): List<SyncTombstone> {
        val data = parseData(request("getTombstones")) ?: return emptyList()
        if (!data.isJsonArray) {
            return emptyList()
        }
        return data.asJsonArray.mapNotNull { element ->
            runCatching { GSON.fromJson(element, SyncTombstone::class.java) }.getOrNull()
        }
    }

    /**
     * 推送本地变更(书源 + 书架 + 墓碑,一次往返)。服务端会做 LWW 仲裁
     * (本地版本不比服务端新则拒绝),因此**每次同步全量推送是安全的**:
     * 不会用旧内容覆盖服务端较新的副本。返回服务端各类的 applied/skipped 计数。
     */
    suspend fun syncPush(
        bookSources: List<BookSourceEntity> = emptyList(),
        books: List<Book> = emptyList(),
        tombstones: List<SyncTombstone> = emptyList()
    ): JsonObject? {
        val body = GSON.toJson(
            mapOf(
                "bookSources" to bookSources,
                "books" to books,
                "tombstones" to tombstones
            )
        )
        val data = parseData(request("syncPush", post = body))
        return data?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /**
     * 发一次请求并返回 body 文本。[post] 非空时用 POST 并带该 JSON body,否则 GET。
     */
    private suspend fun request(path: String, post: String? = null): String {
        return okHttpClient.newCallStrResponse {
            url(api(path))
            post?.let { postJson(it) }
        }.body ?: ""
    }

    /** 拼 `/reader3/<path>` 并把 accessToken 挂到 query(服务端只认 query param)。 */
    private fun api(path: String): String {
        val base = "$root/reader3/$path"
        if (accessToken.isEmpty()) {
            return base
        }
        return "$base?accessToken=${URLEncoder.encode(accessToken, "UTF-8")}"
    }

    /**
     * 解包服务端统一的 `ReturnData{isSuccess, errorMsg, data}`。
     * 未登录时服务端返回 `data == "NEED_LOGIN"`,单独给出可操作的提示。
     */
    private fun parseData(text: String): JsonElement? {
        val response = GSON.fromJsonObject<ApiResponse>(text).getOrNull()
            ?: throw NoStackTraceException("服务端返回无法解析:$text")
        val data = response.data
        // 未登录时服务端返回 data == "NEED_LOGIN",单独给出可操作的提示
        if (data != null && data.isJsonPrimitive && data.asJsonPrimitive.isString &&
            data.asString == "NEED_LOGIN"
        ) {
            throw NoStackTraceException("登录已过期,请重新同步以重新登录")
        }
        if (!response.isSuccess) {
            throw NoStackTraceException(response.errorMsg.ifEmpty { "服务端返回失败" })
        }
        return data
    }

    /** 服务端 `ReturnData` 的映射。`data` 保持 JsonElement 以便按端点各自解析。 */
    private data class ApiResponse(
        val isSuccess: Boolean = false,
        val errorMsg: String = "",
        val data: JsonElement? = null
    )

}
