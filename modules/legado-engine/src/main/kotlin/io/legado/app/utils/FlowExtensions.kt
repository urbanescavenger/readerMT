package io.legado.app.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.buffer
import kotlinx.coroutines.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore

/**
 * 引擎版 Flow 扩展(从 readerMT `utils/FlowExtensions.kt` 抽取 `mapAsync`,纯 JVM)。
 * `JsExtensions.ajaxAll`/`ajaxTestAll` 并发访问用。
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T, R> Flow<T>.mapAsync(
    concurrency: Int,
    crossinline transform: suspend (T) -> R
): Flow<R> = if (concurrency == 1) {
    map { transform(it) }
} else {
    Semaphore(concurrency).let { semaphore ->
        channelFlow {
            collect {
                semaphore.acquire()
                send(async { transform(it) })
            }
        }.map {
            it.await()
        }.onEach { semaphore.release() }
    }.buffer(0)
}