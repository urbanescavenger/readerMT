package io.legado.app.platform

import java.io.File

/**
 * 平台文件系统上下文(计划 §3.6)。
 *
 * - Android `:app`:由 `applicationContext` 提供;
 * - `:server`:由 Spring/Vert.x 配置提供(替换 reader-mt 的 `appCtx`)。
 *
 * 引擎内 `ACache`、`JsExtensions` 等现引 `appCtx` → 改 `Platform.context.cacheDir`。
 */
interface PlatformContext {
    val cacheDir: File
    val filesDir: File
    val externalFilesDir: File?

    /** 外部缓存目录(`JsExtensions.getFile` 用;Android `externalCacheDir`,服务端可指向同一 cacheDir)。 */
    val externalCache: File?
}