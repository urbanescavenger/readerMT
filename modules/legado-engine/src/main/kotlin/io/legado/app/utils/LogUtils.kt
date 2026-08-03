package io.legado.app.utils

/**
 * 引擎纯 JVM 日志助手子集(从 readerMT `utils/LogUtils` 抽取)。
 *
 * readerMT `LogUtils` 含大量 Android 依赖(BuildConfig/WebSettings/appCtx/AppConfig/
 * globalExecutor);引擎只抽 `Throwable.printOnDebug`。原版仅在 BuildConfig.DEBUG 时打印;
 * 引擎无 BuildConfig,直接打印栈(平台可按 §3.7 接 kotlin-logging 后细化)。
 */
fun Throwable.printOnDebug() {
    printStackTrace()
}
