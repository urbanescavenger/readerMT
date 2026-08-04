package com.htmake.reader.platform

import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.Cookie
import io.legado.app.platform.repo.CacheRepository
import io.legado.app.platform.repo.CookieRepository
import io.legado.app.platform.repo.Repositories
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * `:server` 的 [Repositories] 实现:in-memory stub(M1 起,真实持久化 M3)。
 *
 * 背靠 `ConcurrentHashMap` 提供 Cookie/Cache 的进程内存储,保证引擎 `CookieStore`/
 * `CacheManager` 可运行(不 NPE)。重启即丢(M3 接 Vert.x SQL/JDBC 持久化)。
 *
 * 注入方式:M1 在 `ReaderApplication.deployVerticle()` 调 `init()` 或经 Spring 注入
 * [Repositories.cookie] = this。[init] 是幂等的。
 */
@Component
class VertxRepositories : CookieRepository, CacheRepository {

    private val cookies = ConcurrentHashMap<String, Cookie>()
    private val caches = ConcurrentHashMap<String, Cache>()

    // ---- CookieRepository ----

    override fun get(url: String): Cookie? = cookies[url]

    override fun getOkHttpCookies(): List<Cookie> = cookies.values.toList()

    override fun insert(cookie: Cookie) {
        cookies[cookie.url] = cookie
    }

    override fun insertAll(cookies: List<Cookie>) {
        cookies.forEach { this.cookies[it.url] = it }
    }

    override fun update(cookie: Cookie) {
        cookies[cookie.url] = cookie
    }

    override fun delete(url: String) {
        cookies.remove(url)
    }

    override fun deleteOkHttp() {
        cookies.clear()
    }

    // ---- CacheRepository ----

    override fun get(key: String): Cache? = caches[key]

    override fun get(key: String, now: Long): String? {
        val cache = caches[key] ?: return null
        // 过期检查:deadline <= now 视为过期
        if (cache.deadline > 0 && cache.deadline <= now) {
            return null
        }
        return cache.value
    }

    override fun insert(cache: Cache) {
        caches[cache.key] = cache
    }

    override fun delete(key: String) {
        caches.remove(key)
    }

    override fun deleteSourceVariables(key: String) {
        caches.keys.filter { it.startsWith("sourceVariable_$key") }.forEach { caches.remove(it) }
    }

    override fun clearDeadline(now: Long) {
        caches.entries.removeIf { it.value.deadline > 0 && it.value.deadline <= now }
    }
}
