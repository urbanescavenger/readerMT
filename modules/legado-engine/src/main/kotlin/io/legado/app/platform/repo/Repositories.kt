package io.legado.app.platform.repo

/**
 * 引擎持久化 SPI 持有者(计划 §3.2)。
 *
 * 引擎内原 `appDb.*Dao.*` 调用(Android 活,服务端已注释)全改为 `Repositories.xxx.yyy(...)`。
 * 各平台注入实现:
 * - Android `:app`:`RoomRepositories`(DAO↔接口 + Entity↔DTO 转换);
 * - `:server`:`VertxRepositories`(JDBC/Vert.x SQL)。
 *
 * 本对象只持有已搬入引擎的实体对应的 Repository;后续实体(BookSource/Book/...)
 * 落地后在此追加对应字段。引擎独立编译时由 [NoOpRepositories] 兜底。
 */
object Repositories {
    lateinit var cookie: CookieRepository
    lateinit var cache: CacheRepository
}