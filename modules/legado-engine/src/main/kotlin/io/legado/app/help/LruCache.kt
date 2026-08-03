package io.legado.app.help

import java.util.LinkedHashMap

/**
 * 纯 JVM LruCache(替 `androidx.collection.LruCache`)。
 *
 * readerMT 用 `androidx.collection.LruCache` 按 `memorySize()`(字节)容量淘汰(50M);
 * 引擎平台无关,改用 **按条目数** 淘汰(LinkedHashMap access-order + removeEldestEntry)。
 * 淘汰策略由字节计数改为条目计数——缓存为瞬态,影响可忽略;switchover 时若需严格字节
 * 容量可在平台实现里替换。线程安全:所有访问 `synchronized`。
 */
class LruCache<K, V>(private val maxSize: Int) {

    private val map: LinkedHashMap<K, V> = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    fun put(key: K, value: V): V? = synchronized(this) { map.put(key, value) }

    operator fun get(key: K): V? = synchronized(this) { map[key] }

    fun remove(key: K): V? = synchronized(this) { map.remove(key) }
}