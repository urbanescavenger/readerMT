package com.htmake.reader.platform

import com.htmake.reader.utils.getStorage
import com.htmake.reader.utils.saveStorage
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.Cookie
import io.legado.app.platform.repo.CacheRepository
import io.legado.app.platform.repo.CookieRepository
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import jakarta.annotation.PreDestroy
import kotlin.concurrent.thread
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * `:server` 的 [CookieRepository] 实现(M3 起文件持久化)。
 *
 * 背靠 `ConcurrentHashMap` + `storage/cookies.json`(复用 [saveStorage]/[getStorage])。
 * 懒加载:首次访问(请求期,Spring/AppConfig 已就绪)从文件读入;每次变更同步落盘。
 * 价值:书源登录态(Cookie)跨重启保留,不必重启后重新登录。
 *
 * 注意:不能与 [VertxCacheRepository] 合并成一个实现类——`CookieRepository.get(url)`
 * 与 `CacheRepository.get(key)` 同签名 `get(String)` 但返回不同(Cookie?/Cache?),
 * 单类实现两者会 JVM 签名冲突(Conflicting overloads)。
 */
@Component
class VertxCookieRepository : CookieRepository {

    private val store = ConcurrentHashMap<String, Cookie>()

    @Volatile
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            getStorage("cookies")?.let { json ->
                val arr = JsonArray(json)
                for (i in 0 until arr.size()) {
                    val c = arr.getJsonObject(i).mapTo(Cookie::class.java)
                    store[c.url] = c
                }
            }
            loaded = true
        }
    }

    private fun persist() {
        ensureLoaded()
        val arr = JsonArray()
        store.values.forEach { arr.add(JsonObject.mapFrom(it)) }
        saveStorage("cookies", value = arr)
    }

    override fun get(url: String): Cookie? {
        ensureLoaded()
        return store[url]
    }

    override fun getOkHttpCookies(): List<Cookie> {
        ensureLoaded()
        return store.values.toList()
    }

    override fun insert(cookie: Cookie) {
        ensureLoaded()
        store[cookie.url] = cookie
        persist()
    }

    override fun insertAll(cookies: List<Cookie>) {
        ensureLoaded()
        cookies.forEach { store[it.url] = it }
        persist()
    }

    override fun update(cookie: Cookie) {
        ensureLoaded()
        store[cookie.url] = cookie
        persist()
    }

    override fun delete(url: String) {
        ensureLoaded()
        store.remove(url)
        persist()
    }

    override fun deleteOkHttp() {
        ensureLoaded()
        store.clear()
        persist()
    }
}

/**
 * `:server` 的 [CacheRepository] 实现(M3 起文件持久化)。
 *
 * 背靠 `ConcurrentHashMap` + `storage/cache.json`。缓存是**热写路径**([CacheManager.put]
 * 每次 `Repositories.cache.insert`),若每次变更把整表落盘会有写放大,故用**后台合并 flush**:
 * 变更仅置脏标记,后台线程(≤5s)把整表写一次,[@PreDestroy] 关闭时兜底 flush。
 * 懒加载:首次访问(请求期)从文件读入。
 */
@Component
class VertxCacheRepository : CacheRepository {

    private val store = ConcurrentHashMap<String, Cache>()

    @Volatile
    private var loaded = false

    @Volatile
    private var dirty = false

    @Volatile
    private var running = true

    private val flusher: Thread = thread(name = "vertx-cache-flusher", isDaemon = true) {
        while (running) {
            try {
                Thread.sleep(5000)
            } catch (_: InterruptedException) {
                break
            }
            flush()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            getStorage("cache")?.let { json ->
                val arr = JsonArray(json)
                for (i in 0 until arr.size()) {
                    val c = arr.getJsonObject(i).mapTo(Cache::class.java)
                    store[c.key] = c
                }
            }
            loaded = true
        }
    }

    private fun markDirty() {
        ensureLoaded()
        dirty = true
    }

    private fun flush() {
        if (!dirty) return
        ensureLoaded()
        dirty = false
        val arr = JsonArray()
        store.values.forEach { arr.add(JsonObject.mapFrom(it)) }
        saveStorage("cache", value = arr)
    }

    @PreDestroy
    fun stop() {
        running = false
        flusher.interrupt()
        flush()
    }

    override fun get(key: String): Cache? {
        ensureLoaded()
        return store[key]
    }

    override fun get(key: String, now: Long): String? {
        val cache = get(key) ?: return null
        // 过期检查:deadline <= now 视为过期
        if (cache.deadline > 0 && cache.deadline <= now) {
            return null
        }
        return cache.value
    }

    override fun insert(cache: Cache) {
        markDirty()
        store[cache.key] = cache
    }

    override fun delete(key: String) {
        markDirty()
        store.remove(key)
    }

    override fun deleteSourceVariables(key: String) {
        markDirty()
        store.keys.filter { it.startsWith("sourceVariable_$key") }.forEach { store.remove(it) }
    }

    override fun clearDeadline(now: Long) {
        markDirty()
        store.entries.removeIf { it.value.deadline > 0 && it.value.deadline <= now }
    }
}
