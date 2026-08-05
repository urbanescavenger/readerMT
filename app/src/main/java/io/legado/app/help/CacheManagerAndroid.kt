package io.legado.app.help

import androidx.collection.LruCache
import io.legado.app.model.analyzeRule.QueryTTF

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
