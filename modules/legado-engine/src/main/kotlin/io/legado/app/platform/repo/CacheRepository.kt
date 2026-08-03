package io.legado.app.platform.repo

import io.legado.app.data.entities.Cache

/**
 * 缓存持久化 SPI(计划 §3.2)。
 *
 * 镜像 readerMT `CacheDao` 的方法表面(返回引擎 Cache DTO)。
 * 引擎 `CacheManager` 把 `appDb.cacheDao.*` 改调 `Repositories.cache.*`。
 */
interface CacheRepository {
    fun get(key: String): Cache?
    fun get(key: String, now: Long): String?
    fun insert(cache: Cache)
    fun delete(key: String)
    fun deleteSourceVariables(key: String)
    fun clearDeadline(now: Long)
}

/** 引擎独立编译/单测时的 NoOp 默认实现。 */
object NoOpCacheRepository : CacheRepository {
    override fun get(key: String): Cache? = null
    override fun get(key: String, now: Long): String? = null
    override fun insert(cache: Cache) {}
    override fun delete(key: String) {}
    override fun deleteSourceVariables(key: String) {}
    override fun clearDeadline(now: Long) {}
}