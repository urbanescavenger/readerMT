@file:Suppress("unused")

package io.legado.app.help

/**
 * 并发速率记录(从 readerMT `model/analyzeRule/AnalyzeUrl` 的内嵌 `data class ConcurrentRecord`
 * **抽离为顶层类**,以解开 AnalyzeUrl ↔ ConcurrentRateLimiter ↔ ConcurrentRecord 的互递归环;
 * 计划 §9.4 解环点)。
 */
data class ConcurrentRecord(
    /** 开始访问时间 */
    var time: Long,
    /** 限制次数 */
    var accessLimit: Int,
    /** 间隔时间 */
    var interval: Int,
    /** 正在访问的个数 */
    var frequency: Int
)