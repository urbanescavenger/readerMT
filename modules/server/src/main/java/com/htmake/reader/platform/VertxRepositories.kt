package com.htmake.reader.platform

import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.Cookie
import io.legado.app.platform.repo.CacheRepository
import io.legado.app.platform.repo.CookieRepository
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * `:server` 的 [CookieRepository] 实现:in-memory stub(M1 起,真实持久化 M3)。
 *
 * 背靠 `ConcurrentHashMap` 提供 Cookie 的进程内存储,保证引擎 `CookieStore` 可运行(不 NPE)。
 * 重启即丢(M3 接 Vert.x SQL/JDBC 持久化)。
 *
 * 注意:不能与 [VertxCacheRepository] 合并成一个实现类——`CookieRepository.get(url)`
 * 与 `CacheRepository.get(key)` 同签名 `get(String)` 但返回不同(Cookie?/Cache?),
 * 单类实现两者会 JVM 签名冲突(Conflicting overloads)。
 */
@Component
class VertxCookieRepository : CookieRepository {

    private val store = ConcurrentHashMap<String, Cookie>()

    override fun get(url: String): Cookie? = store[url]

    override fun getOkHttpCookies(): List<Cookie> = store.values.toList()

    override fun insert(cookie: Cookie) {
        store[cookie.url] = cookie
    }

    override fun insertAll(cookies: List<Cookie>) {
        cookies.forEach { store[it.url] = it }
    }

    override fun update(cookie: Cookie) {
        store[cookie.url] = cookie
    }

    override fun delete(url: String) {
        store.remove(url)
    }

    override fun deleteOkHttp() {
        store.clear()
    }
}

/**
 * `:server` 的 [CacheRepository] 实现:in-memory stub(M1 起,真实持久化 M3)。
 *
 * 背靠 `ConcurrentHashMap` 提供 Cache 的进程内存储,保证引擎 `CacheManager` 可运行(不 NPE)。
 * 重启即丢(M3 接 Vert.x SQL/JDBC 持久化)。
 */
@Component
class VertxCacheRepository : CacheRepository {

    private val store = ConcurrentHashMap<String, Cache>()

    override fun get(key: String): Cache? = store[key]

    override fun get(key: String, now: Long): String? {
        val cache = store[key] ?: return null
        // 过期检查:deadline <= now 视为过期
        if (cache.deadline > 0 && cache.deadline <= now) {
            return null
        }
        return cache.value
    }

    override fun insert(cache: Cache) {
        store[cache.key] = cache
    }

    override fun delete(key: String) {
        store.remove(key)
    }

    override fun deleteSourceVariables(key: String) {
        store.keys.filter { it.startsWith("sourceVariable_$key") }.forEach { store.remove(it) }
    }

    override fun clearDeadline(now: Long) {
        store.entries.removeIf { it.value.deadline > 0 && it.value.deadline <= now }
    }
}
