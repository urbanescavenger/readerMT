package io.legado.app.help

import android.webkit.JavascriptInterface
import androidx.annotation.Keep
import androidx.collection.LruCache
import io.legado.app.model.analyzeRule.QueryTTF
import io.legado.app.utils.ACache

private val queryTTFMap = LruCache<String, QueryTTF>(4)
private val memoryLruCache = LruCache<String, Any>(1024 * 1024 * 50)

/**
 * 原 `help/CacheManager.kt` 的 `AppCacheManager`(被删副本),app 端恢复 —— QueryTTF 字体缓存
 * + 源变量内存清理。引擎 `CacheManager` 只含核心缓存,不含此 Android 专属对象。
 */
object AppCacheManager {
    fun put(key: String, queryTTF: QueryTTF) {
        queryTTFMap.put(key, queryTTF)
    }

    fun getQueryTTF(key: String): QueryTTF? {
        return queryTTFMap[key]
    }

    fun clearSourceVariables() {
        memoryLruCache.snapshot().keys.forEach {
            if (it.startsWith("v_")
                || it.startsWith("userInfo_")
                || it.startsWith("loginHeader_")
                || it.startsWith("sourceVariable_")
            ) {
                memoryLruCache.remove(it)
            }
        }
    }
}

/** 网页缓存(原 `help/CacheManager.kt` 的 WebCacheManager;包装引擎 CacheManager + ACache)。 */
@Keep
@Suppress("unused")
object WebCacheManager {

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
