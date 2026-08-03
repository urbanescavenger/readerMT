package io.legado.app.constant

/**
 * 引擎日志占位(临时)。
 *
 * readerMT `constant/AppLog` 用 `android.util.Log`;引擎平台无关,不能引 Android。
 * 此处先放一个最小 shim(写到 stderr),**Phase 1c 起按计划 §3.7 统一改为
 * `io.github.microutils:kotlin-logging` + `slf4j-api`**:app 加 `slf4j-android` binding,
 * 服务端留 logback。届时把本对象替换为基于 mu.KotlinLogging 的实现,或直接在各文件内联 logger。
 */
object AppLog {

    fun put(msg: String, e: Throwable? = null) {
        System.err.println(msg)
        e?.printStackTrace()
    }
}