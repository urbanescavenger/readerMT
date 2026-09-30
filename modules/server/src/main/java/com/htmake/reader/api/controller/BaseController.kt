package com.htmake.reader.api.controller

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssArticle
import io.legado.app.model.webBook.WebBook
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler;
import mu.KotlinLogging
import com.htmake.reader.config.AppConfig
import com.htmake.reader.config.BookConfig
import io.legado.app.constant.DeepinkBookSource
import com.htmake.reader.utils.error
import com.htmake.reader.utils.success
import com.htmake.reader.utils.getStorage
import com.htmake.reader.utils.saveStorage
import com.htmake.reader.utils.asJsonArray
import com.htmake.reader.utils.asJsonObject
import com.htmake.reader.utils.toDataClass
import com.htmake.reader.utils.toMap
import com.htmake.reader.utils.fillData
import com.htmake.reader.utils.getWorkDir
import com.htmake.reader.utils.getRandomString
import com.htmake.reader.utils.genEncryptedPassword
import com.htmake.reader.entity.User
import com.htmake.reader.utils.SpringContextUtils
import com.htmake.reader.utils.deleteRecursively
import com.htmake.reader.utils.unzip
import com.htmake.reader.utils.zip
import com.htmake.reader.utils.jsonEncode
import com.htmake.reader.utils.getRelativePath
import com.htmake.reader.utils.getFileExtetion
import com.htmake.reader.verticle.RestVerticle
import com.htmake.reader.SpringEvent
import org.springframework.stereotype.Component
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
import io.vertx.core.http.HttpMethod
import com.htmake.reader.api.ReturnData
import io.legado.app.utils.MD5Utils
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.URL;
import java.util.UUID;
import io.vertx.ext.web.client.WebClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import java.io.File
import java.lang.Runtime
import kotlin.collections.mutableMapOf
import kotlin.system.measureTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat;
import io.legado.app.utils.EncoderUtils
import io.legado.app.model.rss.Rss
import org.springframework.scheduling.annotation.Scheduled
import io.legado.app.model.localBook.LocalBook
import java.nio.file.Paths
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import io.legado.app.help.coroutine.Coroutine

private val logger = KotlinLogging.logger {}

open class BaseController(override val coroutineContext: CoroutineContext): CoroutineScope {
    var loginExpireDays = 7

    val appConfig: AppConfig
    val env: Environment

    init {
        appConfig = SpringContextUtils.getBean("appConfig", AppConfig::class.java)
        env = SpringContextUtils.getBean(Environment::class.java)
    }

    suspend fun saveUserSession(context: RoutingContext, userMap: MutableMap<String, Map<String, Any>>, user: User, regenerateToken: Boolean = true): Map<String, Any> {
        user.last_login_at = System.currentTimeMillis()
        if (regenerateToken) {
            user.token = genEncryptedPassword(user.username, System.currentTimeMillis().toString())
            var tokenMap: MutableMap<String, Long>? = null
            var expire = System.currentTimeMillis() + loginExpireDays * 86400 * 1000
            if (user.token_map != null) {
                tokenMap = user.token_map as? MutableMap<String, Long>
            }
            if (tokenMap == null) {
                tokenMap = mutableMapOf(user.token to expire)
            } else {
                tokenMap.put(user.token, expire)
            }
            // 删除已过期token
            tokenMap.values.removeAll { it < user.last_login_at }
            user.token_map = tokenMap
        }
        userMap.put(user.username, user.toMap())
        saveStorage("data", "users", value = userMap)

        val loginData = formatUser(user)

        context.session().put("username", user.username)
        context.put("username", user.username)

        return loginData
    }

    suspend fun checkAuth(context: RoutingContext): Boolean {
        if (!appConfig.secure) {
            return true
        }
        var username = context.session().get("username") as String? ?: ""
        var userInfo = getUserInfoClass(username)
        if (userInfo != null) {
            context.put("username", userInfo.username)
            context.put("userInfo", userInfo)
            return true
        }
        // 自动登录
        var accessToken = context.queryParam("accessToken").firstOrNull() ?: ""
        if (accessToken.isNotEmpty()) {
            var userMap = mutableMapOf<String, Map<String, Any>>()
            var userMapJson: JsonObject? = asJsonObject(getStorage("data", "users"))
            if (userMapJson != null) {
                userMap = userMapJson.map as? MutableMap<String, Map<String, Any>> ?: mutableMapOf<String, Map<String, Any>>()
            }
            var tmp = accessToken.split(":", limit=2)
            if (tmp.size >= 2) {
                var _username = tmp[0]
                var token = tmp[1]
                var existedUser: User? = userMap.getOrDefault(_username, null)?.toDataClass()
                if (existedUser != null && token.isNotEmpty()) {
                    var isLogin = false
                    if (existedUser.token.isNotEmpty() && existedUser.token.equals(token)) {
                        isLogin = true
                    }
                    // 查找历史有效会话
                    if (!isLogin && existedUser.token_map != null) {
                        var tokenMap = existedUser.token_map as? MutableMap<String, Long>
                        if (tokenMap != null &&
                            tokenMap.containsKey(token)) {
                            if (tokenMap.getOrDefault(token, 0L) > System.currentTimeMillis()) {
                                isLogin = true
                                // 延长有效期
                                tokenMap.put(token, System.currentTimeMillis() + loginExpireDays * 86400 * 1000)
                            } else {
                                // 删除过期token
                                tokenMap.remove(token)
                            }
                            existedUser.token_map = tokenMap
                        }
                    }
                    if (isLogin) {
                        // 保存用户session
                        saveUserSession(context, userMap, existedUser, false)
                        context.put("username", existedUser.username)
                        context.put("userInfo", existedUser)
                    }
                    return isLogin
                }
            }
        }

        return false
    }

    fun checkManagerAuth(context: RoutingContext): Boolean {
        if (!appConfig.secure) {
            return true
        }
        if (appConfig.secureKey.isEmpty()) {
            return true
        }
        var secureKey = context.queryParam("secureKey").firstOrNull() ?: ""
        if (secureKey.equals(appConfig.secureKey)) {
            // 判断是否需要修改 userNameSpace
            var userNS = context.queryParam("userNS").firstOrNull()
            if (userNS != null && userNS.isNotEmpty()) {
                context.put("userNameSpace", userNS)
            }
            return true
        }
        return false
    }

    fun getUserNameSpace(context: RoutingContext): String {
        if (!appConfig.secure) {
            return "default"
        }
        // 管理权限，可以修改 userNameSpace 来获取任意用户信息
        checkManagerAuth(context)
        var userNS = context.get("userNameSpace") as String?
        if (userNS != null && userNS.isNotEmpty()) {
            return userNS
        }
        var username = context.get("username") as String?
        if (username != null) {
            return username;
        }
        return "default"
    }

    /**
     * 书源命名空间。
     * 共享书源模式(secure && sharedBookSource)下,书源全局使用 default 命名空间,
     * 所有用户读写同一个书源池;书架等其它数据仍按用户隔离(getUserNameSpace)。
     * 非 secure 模式下 getUserNameSpace 本就返回 default,行为不变。
     */
    fun getBookSourceNameSpace(context: RoutingContext): String {
        return if (appConfig.secure && appConfig.sharedBookSource) "default" else getUserNameSpace(context)
    }

    fun getUserStorage(context: Any, vararg path: String): String? {
        var userNameSpace = ""
        when(context) {
            is RoutingContext -> userNameSpace = getUserNameSpace(context)
            is String -> userNameSpace = context
        }
        if (userNameSpace.isEmpty()) {
            return getStorage("data", *path)
        }
        return getStorage("data", userNameSpace, *path)
    }

    fun saveUserStorage(context: Any, path: String, value: Any) {
        var userNameSpace = ""
        when(context) {
            is RoutingContext -> userNameSpace = getUserNameSpace(context)
            is String -> userNameSpace = context
        }
        if (userNameSpace.isEmpty()) {
            return saveStorage("data", path, value = value)
        }
        return saveStorage("data", userNameSpace, path, value = value)
    }

    fun getUserInfoClass(username: String): User? {
        var user: User? = getUserInfoMap(username)?.toDataClass()
        return user
    }

    /**
     * 双端同步:读取某命名空间的删除墓碑(storage/data/<ns>/tombstone.json)。
     *
     * 墓碑放在**它所标记实体所在的命名空间**:书源墓碑进书源命名空间
     * ([getBookSourceNameSpace],共享书源模式下恒为 default),书籍墓碑进用户命名空间
     * ([getUserNameSpace])。这样共享书源模式下多人删同一个源也能收敛。
     */
    suspend fun getUserTombstones(userNameSpace: String): JsonArray {
        return asJsonArray(getUserStorage(userNameSpace, TOMBSTONE_STORAGE_KEY)) ?: JsonArray()
    }

    /**
     * 记录一条删除墓碑。[deletedAt] 参与双端同步的 LWW 比较,同 (type, key) 只保留较新时间
     * (并发删除不把时间改小,否则会让较新的一端"复活"该实体)。
     *
     * **刻意不做按时间淘汰**:墓碑一旦过期被删,超过保留期未同步的客户端就会把已删除实体
     * 复活。而墓碑量级由 bookSourceLimit(默认100)/userBookLimit(默认200)兜住,不会无限增长。
     */
    suspend fun addTombstone(
        userNameSpace: String,
        type: String,
        key: String,
        deletedAt: Long = System.currentTimeMillis()
    ) {
        if (key.isEmpty()) {
            return
        }
        var list = getUserTombstones(userNameSpace)
        var existIndex = -1
        for (i in 0 until list.size()) {
            val item = list.getJsonObject(i)
            if (item.getString("type", "") == type && item.getString("key", "") == key) {
                existIndex = i
                break
            }
        }
        if (existIndex >= 0) {
            val old = jsonToLong(list.getJsonObject(existIndex).getValue("deletedAt"))
            if (old >= deletedAt) {
                return
            }
            val itemList = list.getList()
            itemList.set(existIndex, tombstoneJson(type, key, deletedAt))
            list = JsonArray(itemList)
        } else {
            list.add(tombstoneJson(type, key, deletedAt))
        }
        saveUserStorage(userNameSpace, TOMBSTONE_STORAGE_KEY, list)
    }

    /**
     * 双端同步:读取 `since` 增量参数。**缺省或 <= 0 表示全量**——
     * 首次同步的客户端不传该参数;实体上 `lastModifiedAt = 0`(旧数据未打过版本号)
     * 的条目在全量模式下必须能被取到,故不能用 `lastModifiedAt > 0` 过滤。
     */
    fun getSinceParam(context: RoutingContext): Long {
        if (context.request().method() == HttpMethod.POST) {
            // 空 body 时 bodyAsJson 会抛 DecodeException,增量参数缺失按全量处理即可
            val body = try {
                context.bodyAsJson
            } catch (e: Exception) {
                null
            }
            if (body != null) {
                return jsonToLong(body.getValue("since"))
            }
            return 0L
        }
        return context.queryParam("since").firstOrNull()?.toLongOrNull() ?: 0L
    }

    /**
     * 读取 JSON 里的 Long。不用 `JsonObject.getLong(key, def)`:Vert.x 4.5 该两参重载
     * 可用性不明确,而 JSON 数字经 Jackson/Vert.x 往返可能是 Int/Long/Double,统一按 Number 收。
     */
    fun jsonToLong(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun tombstoneJson(type: String, key: String, deletedAt: Long): JsonObject {
        return JsonObject().put("type", type).put("key", key).put("deletedAt", deletedAt)
    }

    /**
     * 把 Vert.x `JsonObject`/`JsonArray` 深度转成朴素 Map/List。
     *
     * **为什么必须转**:响应体是 `gson.toJson(ReturnData)` **反射**序列化的(见 `VertExt.success`),
     * 而 Vert.x `JsonObject` 的内部字段名就叫 `map` —— 直接塞进 `ReturnData.data` 会被序列化成
     * `{"map":{...}}`,客户端按字段名取值全部取不到。
     * (实测:`syncPush` 曾返回 `data.bookSources.map.applied` 而非 `data.bookSources.applied`,
     *  被 docker 冒烟断言第 1 条抓住。)
     *
     * **存储侧没有这个问题**:`saveStorage` 对 `JsonObject`/`JsonArray` 走 `value.toString()`
     * (Vert.x 原生编码);**app 端也没有**:它的 `getBookSources` 返回
     * `List<BookSourceEntity>` 朴素对象。所以只有"服务端返回 JsonArray-derived 数据"的端点
     * 有这个包装问题 —— 这是本 fork 的既有缺陷,不是同步功能引入的。
     *
     * 参数与返回值都非空:`ReturnData.setData(data: Any)` 不接受 null。嵌套里的 null 值由
     * [plainOrNull] 原样保留(JSON 字段为 null 是合法的)。
     */
    fun plain(value: Any): Any = plainOrNull(value) ?: value

    /** [plain] 的递归核心:允许输入/输出为 null,以便原样保留嵌套的 null 值。 */
    private fun plainOrNull(value: Any?): Any? = when (value) {
        null -> null
        is JsonObject -> value.getMap().mapValues { plainOrNull(it.value) }
        is JsonArray -> value.getList().map { plainOrNull(it) }
        is Map<*, *> -> value.mapValues { plainOrNull(it.value) }
        is List<*> -> value.map { plainOrNull(it) }
        else -> value
    }

    /** 墓碑类型:书源(主键 bookSourceUrl) */
    companion object {
        const val TOMBSTONE_TYPE_BOOK_SOURCE = "bookSource"

        /** 墓碑类型:书籍(主键 bookUrl) */
        const val TOMBSTONE_TYPE_BOOK = "book"

        /** 墓碑文件在命名空间目录下的 storage key(对应 storage/data/<ns>/tombstone.json) */
        const val TOMBSTONE_STORAGE_KEY = "tombstone"
    }

    fun getUserInfoMap(username: String): Map<String, Any>? {
        if (username.isEmpty()) {
            return null
        }
        var userMap = mutableMapOf<String, Map<String, Any>>()
        var userMapJson: JsonObject? = asJsonObject(getStorage("data", "users"))
        if (userMapJson != null) {
            userMap = userMapJson.map as MutableMap<String, Map<String, Any>>
        }
        return userMap.getOrDefault(username, null)
    }

    fun formatUser(userInfo: Any): MutableMap<String, Any> {
        var user: User? = null
        if (userInfo !is User) {
            var userMap = userInfo as? Map<String, Any>
            if (userMap != null) {
                user = userMap.toDataClass()
            }
        } else {
            user = userInfo
        }
        if (user == null) {
            return mutableMapOf()
        }
        return mutableMapOf(
            "username" to user.username,
            "lastLoginAt" to user.last_login_at,
            "accessToken" to user.username + ":" + user.token,
            "enableWebdav" to user.enable_webdav,
            "enableLocalStore" to user.enable_local_store,
            "createdAt" to user.created_at
        )
    }

    fun getUserWebdavHome(context: Any): String {
        var prefix = getWorkDir("storage", "data")
        var userNameSpace = ""
        when(context) {
            is RoutingContext -> userNameSpace = getUserNameSpace(context)
            is String -> userNameSpace = context
        }
        if (userNameSpace.isNotEmpty()) {
            prefix = prefix + File.separator + userNameSpace
        }
        prefix = prefix + File.separator + "webdav"
        var file = File(prefix)
        if (!file.exists()) {
            file.mkdirs()
        }
        return prefix
    }

    fun getFileExt(url: String, defaultExt: String=""): String {
        return getFileExtetion(url, defaultExt)
    }

    suspend fun limitConcurrent(concurrentCount: Int, startIndex: Int, endIndex: Int, handler: suspend CoroutineScope.(Int) -> Any) {
        limitConcurrent(concurrentCount, startIndex, endIndex, handler) {_, _ ->
            true
        }
    }

    suspend fun limitConcurrent(concurrentCount: Int, startIndex: Int, endIndex: Int, handler: suspend CoroutineScope.(Int) -> Any, needContinue: (ArrayList<Any>, Int) -> Boolean) {
        var lastIndex = startIndex
        var loopCount = 0
        var resultCount = 0
        var loopStart = System.currentTimeMillis()
        var costTime = 0L
        var deferredList = arrayListOf<Deferred<Any>>()
        while(true) {
            var croutineCount = deferredList.size;
            if (croutineCount < concurrentCount) {
                for(i in lastIndex until endIndex) {
                    croutineCount += 1;
                    deferredList.add(async {
                        handler(i)
                    })

                    lastIndex = i
                    if (croutineCount >= concurrentCount) {
                        break;
                    }
                }
            }
            var resultList = arrayListOf<Any>()

            // 等待任何一个完成
            while (resultList.size <= 0) {
                delay(10)
                var stillDeferredList = arrayListOf<Deferred<Any>>()
                for (i in 0 until deferredList.size) {
                    try {
                        var deferred = deferredList.get(i)
                        if (deferred.isCompleted) {
                            resultCount++
                            resultList.add(deferred.getCompleted())
                        } else if (!deferred.isCancelled) {
                            stillDeferredList.add(deferred)
                        } else {
                            resultCount++
                        }
                    } catch(e: Exception) {

                    }
                }
                deferredList.clear()
                deferredList.addAll(stillDeferredList)
            }

            if (resultCount / concurrentCount > loopCount) {
                loopCount = resultCount / concurrentCount
                costTime = System.currentTimeMillis() - loopStart
                logger.info("Loop: {} concurrentCount: {} lastIndex: {} endIndex: {} costTime: {} ms deferredList size: {}", loopCount, croutineCount, lastIndex, endIndex, costTime, deferredList.size)
            }

            if (lastIndex >= endIndex - 1) {
                // 搞完了，等待所有结束
                for (i in 0 until deferredList.size) {
                    try {
                        resultList.add(deferredList.get(i).await())
                    } catch(e: Exception) {

                    }
                }
                deferredList.clear()
                needContinue(resultList, loopCount)
                break;
            }
            if (resultList.size > 0) {
                if (!needContinue(resultList, loopCount)) {
                    break;
                }
            }
            lastIndex = lastIndex + 1
        }

        // for (i in 0 until concurrentCount) {
        //     runBlocking(concurrentCount, startIndex + i , endIndex, handler, needContinue)
        // }
    }

    suspend fun runBlocking(concurrentCount: Int, startIndex: Int, endIndex: Int, handler: suspend CoroutineScope.(Int) -> Any, needContinue: (ArrayList<Any>, Int) -> Boolean) {
        var lastIndex = startIndex

        Coroutine.async(this, coroutineContext) {
            handler(lastIndex)
        }.timeout(30000L)
        .onSuccess(Dispatchers.IO) {
            if (lastIndex < endIndex - concurrentCount && needContinue(arrayListOf(it), 0)) {
                lastIndex += concurrentCount
                runBlocking(concurrentCount, lastIndex, endIndex, handler, needContinue)
            }
        }
        .onError(Dispatchers.IO) {
            if (lastIndex < endIndex - concurrentCount) {
                lastIndex += concurrentCount
                runBlocking(concurrentCount, lastIndex, endIndex, handler, needContinue)
            } else {
                needContinue(arrayListOf(), 0)
            }
        }
    }
}