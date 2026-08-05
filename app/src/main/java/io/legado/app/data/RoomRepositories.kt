package io.legado.app.data

import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.CacheEntity
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.CookieEntity
import io.legado.app.platform.repo.CacheRepository
import io.legado.app.platform.repo.CookieRepository

/**
 * Android `:app` 侧对引擎 [Repositories] 的 Room-backed 实现(Phase 1c switchover)。
 *
 * 引擎 [CacheManager]/[io.legado.app.help.http.CookieStore] 改调 `Repositories.cache/cookie`,
 * 而 app 的持久化在 Room(appDb.cacheDao/cookieDao)。此两个类把引擎 Cache/Cookie DTO 映射到
 * Room `CacheEntity`/`CookieEntity`(字段一一对应),在 [io.legado.app.App.onCreate] 注入
 * `Repositories.cache/cookie`。若不注入,引擎任何一次读缓存/Cookie 都会炸
 * `lateinit property cache/cookie has not been initialized`。
 */
class RoomCacheRepository(private val dao: CacheDao) : CacheRepository {
    override fun get(key: String): Cache? = dao.get(key)?.toCache()
    override fun get(key: String, now: Long): String? = dao.get(key, now)
    override fun insert(cache: Cache) {
        dao.insert(cache.toEntity())
    }
    override fun delete(key: String) {
        dao.delete(key)
    }
    override fun deleteSourceVariables(key: String) {
        dao.deleteSourceVariables(key)
    }
    override fun clearDeadline(now: Long) {
        dao.clearDeadline(now)
    }
}

class RoomCookieRepository(private val dao: CookieDao) : CookieRepository {
    override fun get(url: String): Cookie? = dao.get(url)?.toCookie()
    override fun getOkHttpCookies(): List<Cookie> = dao.getOkHttpCookies().map { it.toCookie() }
    override fun insert(cookie: Cookie) {
        dao.insert(cookie.toEntity())
    }
    override fun insertAll(cookies: List<Cookie>) {
        dao.insert(*cookies.map { it.toEntity() }.toTypedArray())
    }
    override fun update(cookie: Cookie) {
        dao.update(cookie.toEntity())
    }
    override fun delete(url: String) {
        dao.delete(url)
    }
    override fun deleteOkHttp() {
        dao.deleteOkHttp()
    }
}

private fun CacheEntity.toCache() = Cache(key = key, value = value, deadline = deadline)
private fun Cache.toEntity() = CacheEntity(key = key, value = value, deadline = deadline)

private fun CookieEntity.toCookie() = Cookie(url = url, cookie = cookie)
private fun Cookie.toEntity() = CookieEntity(url = url, cookie = cookie)
