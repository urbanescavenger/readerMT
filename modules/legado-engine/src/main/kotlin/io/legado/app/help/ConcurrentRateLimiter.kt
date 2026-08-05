package io.legado.app.help

import io.legado.app.exception.ConcurrentException
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * 引擎版 ConcurrentRateLimiter(从 readerMT `help/ConcurrentRateLimiter.kt` 移植)。
 *
 * **解环改动**(计划 §9.4):原版构造取 `BaseSource?`,读 `source.concurrentRate`/`source.getKey()`;
 * 引擎版改取原始参 `(key, concurrentRate)`,不再依赖 `BaseSource`(BaseSource 在 batch 2b 才进引擎,
 * 且解耦后本类可独立编译)。`ConcurrentRecord` 抽成顶层类(见 [ConcurrentRecord])。
 *
 * AnalyzeUrl(batch 2c)构造时改调 `ConcurrentRateLimiter(source?.getKey(), source?.concurrentRate)`。
 */
class ConcurrentRateLimiter(
    private val key: String?,
    private val concurrentRate: String? = null
) {

    companion object {
        val concurrentRecordMap = ConcurrentHashMap<String, ConcurrentRecord>()

        /**
         * 更新并发率
         */
        fun updateConcurrentRate(key: String, concurrentRate: String) {
            concurrentRecordMap.compute(key) { _, record ->
                try {
                    val rateIndex = concurrentRate.indexOf("/")
                    when {
                        rateIndex > 0 -> {
                            val accessLimit = concurrentRate.take(rateIndex).toInt()
                            val interval = concurrentRate.substring(rateIndex + 1).toInt()
                            if (accessLimit <= 0 || interval <= 0) throw NumberFormatException()
                            ConcurrentRecord(
                                record?.time ?: System.currentTimeMillis(),
                                accessLimit,
                                interval,
                                record?.frequency ?: 0
                            )
                        }
                        concurrentRate.toInt() > 0 -> {
                            ConcurrentRecord(
                                record?.time ?: System.currentTimeMillis(),
                                1,
                                concurrentRate.toInt(),
                                record?.frequency ?: 0
                            )
                        }
                        else -> record
                    }
                } catch (_: NumberFormatException) {
                    record
                }
            }
        }
    }

    /**
     * 开始访问,并发判断
     */
    @Throws(ConcurrentException::class)
    private fun fetchStart(): ConcurrentRecord? {
        if (concurrentRate.isNullOrEmpty() || concurrentRate == "0") {
            return null
        }
        val key = key ?: return null
        var isNewRecord = false
        val fetchRecord = concurrentRecordMap.computeIfAbsent(key) {
            isNewRecord = true
            val rateIndex = concurrentRate.indexOf("/")
            if (rateIndex > 0) {
                val accessLimit = concurrentRate.take(rateIndex).toIntOrNull() ?: 1
                val interval = concurrentRate.substring(rateIndex + 1).toIntOrNull() ?: 0
                ConcurrentRecord(System.currentTimeMillis(), accessLimit, interval, 1)
            } else {
                ConcurrentRecord(System.currentTimeMillis(), 1, concurrentRate.toIntOrNull() ?: 0, 1)
            }
        }
        if (isNewRecord) return fetchRecord
        val waitTime: Long = synchronized(fetchRecord) {
            //并发控制为 次数/毫秒 , 非并发实际为1/毫秒
            val nextTime = fetchRecord.time + fetchRecord.interval.toLong()
            val nowTime = System.currentTimeMillis()
            if (nowTime >= nextTime) {
                //已经过了限制时间,重置开始时间
                fetchRecord.time = nowTime
                fetchRecord.frequency = 1
                return@synchronized 0
            }
            if (fetchRecord.frequency < fetchRecord.accessLimit) {
                fetchRecord.frequency++
                return@synchronized 0
            } else {
                return@synchronized nextTime - nowTime
            }
        }
        if (waitTime > 0) {
            throw ConcurrentException(
                "根据并发率还需等待${waitTime}毫秒才可以访问",
                waitTime = waitTime
            )
        }
        return fetchRecord
    }

    /**
     * 获取并发记录，若处于并发限制状态下则会等待
     */
    suspend fun getConcurrentRecord(): ConcurrentRecord? {
        while (true) {
            try {
                return fetchStart()
            } catch (e: ConcurrentException) {
                delay(e.waitTime)
            }
        }
    }

    fun getConcurrentRecordBlocking(): ConcurrentRecord? {
        while (true) {
            try {
                return fetchStart()
            } catch (e: ConcurrentException) {
                Thread.sleep(e.waitTime)
            }
        }
    }

    suspend inline fun <T> withLimit(block: () -> T): T {
        getConcurrentRecord()
        return block()
    }

    inline fun <T> withLimitBlocking(block: () -> T): T {
        getConcurrentRecordBlocking()
        return block()
    }

}