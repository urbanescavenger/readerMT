@file:Suppress("unused")

package io.legado.app.utils

import java.io.IOException

/**
 * 引擎纯 JVM Throwable 扩展(从 readerMT `utils/ThrowableExtensions.kt` 移植)。
 */
val Throwable.stackTraceStr: String
    get() {
        val stackTrace = stackTraceToString()
        val lMsg = this.localizedMessage ?: "noErrorMsg"
        return when {
            stackTrace.isNotEmpty() -> stackTrace
            else -> lMsg
        }
    }

fun Throwable.asIOException(): IOException {
    val newException = IOException(this.message)
    newException.initCause(this)
    return newException
}