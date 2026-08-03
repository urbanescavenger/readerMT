package io.legado.app.platform.repo

import io.legado.app.data.entities.Cookie

/**
 * Cookie 持久化 SPI(计划 §3.2)。
 *
 * 镜像 readerMT `CookieDao` 的方法表面(返回引擎 Cookie DTO,非 Room Entity):
 * 引擎 `CookieStore` 把 `appDb.cookieDao.*` 改调 `Repositories.cookie.*`。
 * - Android:`RoomCookieRepository` 转 DAO + `CookieEntity` 映射;
 * - 服务端:`VertxCookieRepository` 经 JDBC。
 */
interface CookieRepository {
    fun get(url: String): Cookie?
    fun getOkHttpCookies(): List<Cookie>
    fun insert(cookie: Cookie)
    fun insertAll(cookies: List<Cookie>)
    fun update(cookie: Cookie)
    fun delete(url: String)
    fun deleteOkHttp()
}

/** 引擎独立编译/单测时的 NoOp 默认实现(返回空、不持久化)。 */
object NoOpCookieRepository : CookieRepository {
    override fun get(url: String): Cookie? = null
    override fun getOkHttpCookies(): List<Cookie> = emptyList()
    override fun insert(cookie: Cookie) {}
    override fun insertAll(cookies: List<Cookie>) {}
    override fun update(cookie: Cookie) {}
    override fun delete(url: String) {}
    override fun deleteOkHttp() {}
}