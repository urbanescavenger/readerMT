package io.legado.app.help

import android.webkit.JavascriptInterface
import androidx.annotation.Keep
import androidx.collection.LruCache
import io.legado.app.utils.ACache

/**
 * Android 端网页缓存 —— 原 `help/CacheManager.kt` 的 `object WebCacheManager` 在被删副本里。
 * 引擎 `CacheManager` 已含核心内存/磁盘缓存(经 Repositories),此处包装它;
 * `putFile/getFile` 引擎缺(走 ACache 磁盘缓存),故直接落 ACache。
 */
@Keep
@Suppress("unused")
object WebCacheManager {

    private val memoryLruCache = LruCache<String, Any>(1024 * 1024 * 50)

    @JvmOverloads
    @JavascriptInterface
    fun put(key: String, value: String, saveTime: Int = 0) {
        CacheManager.put(key, value, saveTime)
    }

    @JavascriptInterface
    fun putMemory(key: String, value: String) {
        memoryLruCache.put(key, value)
    }

    @JavascriptInterface
    fun getFromMemory(key: String): String? {
        return memoryLruCache[key]?.toString()
    }

    @JavascriptInterface
    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }

    @JavascriptInterface
    fun get(key: String): String? {
        return CacheManager.get(key)
    }

    @JavascriptInterface
    fun get(key: String, onlyDisk: Boolean): String? {
        return CacheManager.get(key, onlyDisk)
    }

    @JavascriptInterface
    fun putFile(key: String, value: String, saveTime: Int = 0) {
        ACache.get().put(key, value, saveTime)
    }

    @JavascriptInterface
    fun getFile(key: String): String? {
        return ACache.get().getAsString(key)
    }

    @JavascriptInterface
    fun delete(key: String) {
        CacheManager.delete(key)
        ACache.get().remove(key)
    }
}
