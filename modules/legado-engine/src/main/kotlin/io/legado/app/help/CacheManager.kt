package io.legado.app.help

import io.legado.app.data.entities.Cache
import io.legado.app.platform.repo.Repositories

/**
 * 引擎版 `CacheManager`(从 readerMT `help/CacheManager` 拆出的平台无关字符串缓存)。
 *
 * 与 readerMT 的差异(架构切分):
 * - **`appDb.cacheDao` → `Repositories.cache`**(SPI)。
 * - **`androidx.collection.LruCache` → 引擎 [LruCache]**(按条目数淘汰,见 LruCache 说明)。
 * - **ACache 磁盘缓存路径移除**:`ByteArray` / `putFile` / `getFile` / `getByteArray` /
 *   `delete` 里的 ACache 调用——磁盘缓存属 app 端,留 `:app`(将来可另立 `FileCache` SPI)。
 * - **`@JavascriptInterface` 移除**:引擎不参与 WebView JS 桥。
 * - `AppCacheManager`(缓存 `QueryTTF`,属 model)与 `WebCacheManager`(WebView JS 桥包装)
 *   **留 app/**,不进引擎。
 */
@Suppress("unused")
object CacheManager {

    /** 引擎内存 LRU(原 readerMT 与 AppCacheManager/WebCacheManager 共享一个 androidx LruCache;引擎独立持有)。 */
    private val memoryLruCache = LruCache<String, Any>(4096)

    /** saveTime 单位为秒 */
    fun put(key: String, value: Any, saveTime: Int = 0) {
        val deadline = if (saveTime == 0) 0L else System.currentTimeMillis() + saveTime * 1000
        // 原 ByteArray -> ACache 磁盘分支移除(app 端磁盘缓存);引擎只缓存可 toString 的值。
        val valueStr = value.toString()
        putMemory(key, valueStr)
        Repositories.cache.insert(Cache(key, valueStr, deadline))
    }

    fun putMemory(key: String, value: Any) {
        memoryLruCache.put(key, value)
    }

    fun getFromMemory(key: String): Any? {
        return memoryLruCache[key]
    }

    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }

    fun get(key: String): String? {
        getFromMemory(key)?.let {
            if (it is String) return it
        }
        val cache = Repositories.cache.get(key)
        if (cache != null && (cache.deadline == 0L || cache.deadline > System.currentTimeMillis())) {
            return cache.value?.also { putMemory(key, it) }
        }
        return null
    }

    fun get(key: String, onlyDisk: Boolean): String? {
        if (!onlyDisk) {
            return get(key)
        }
        val cache = Repositories.cache.get(key)
        if (cache != null && (cache.deadline == 0L || cache.deadline > System.currentTimeMillis())) {
            return cache.value
        }
        return null
    }

    fun getInt(key: String): Int? {
        getFromMemory(key)?.let {
            if (it is Int) return it
        }
        return get(key, true)?.toIntOrNull()
    }

    fun getLong(key: String): Long? {
        getFromMemory(key)?.let {
            if (it is Long) return it
        }
        return get(key, true)?.toLongOrNull()
    }

    fun getDouble(key: String): Double? {
        getFromMemory(key)?.let {
            if (it is Double) return it
        }
        return get(key, true)?.toDoubleOrNull()
    }

    fun getFloat(key: String): Float? {
        getFromMemory(key)?.let {
            if (it is Float) return it
        }
        return get(key, true)?.toFloatOrNull()
    }

    fun delete(key: String) {
        Repositories.cache.delete(key)
        deleteMemory(key)
        // ACache.get().remove(key) -> app 端磁盘缓存
    }
}