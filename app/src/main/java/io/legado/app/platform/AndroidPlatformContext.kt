package io.legado.app.platform

import android.content.Context
import io.legado.app.platform.PlatformContext
import java.io.File

/**
 * Android `:app` 侧对引擎 [PlatformContext] 的实现(Phase 1c switchover)。
 *
 * 引擎 `ACache`/`JsExtensions` 等经 `Platform.context.cacheDir` 访问文件目录;
 * app 直接背靠 `applicationContext` 的标准目录。
 */
class AndroidPlatformContext(private val ctx: Context) : PlatformContext {
    override val cacheDir: File get() = ctx.cacheDir
    override val filesDir: File get() = ctx.filesDir
    override val externalFilesDir: File? get() = ctx.externalFilesDir
    override val externalCache: File? get() = ctx.externalCacheDir
}
