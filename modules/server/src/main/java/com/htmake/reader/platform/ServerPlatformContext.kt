package com.htmake.reader.platform

import io.legado.app.platform.PlatformContext
import java.io.File

/**
 * `:server` 的 [PlatformContext] 实现:背靠 `com.htmake.reader.init.appCtx.cacheDir`。
 *
 * reader-mt 服务器无 Android `Context`,缓存/文件目录统一落在工作目录 `storage/cache`。
 * `externalFilesDir`/`externalCache` 服务端无 Android 分区概念,回退到同一 `cacheDir`
 * (引擎 `JsExtensions.getFile`/`downloadFile` 用)。
 */
class ServerPlatformContext : PlatformContext {
    override val cacheDir: File = File(com.htmake.reader.init.appCtx.cacheDir)
    override val filesDir: File = File(com.htmake.reader.init.appCtx.cacheDir)
    override val externalFilesDir: File? = File(com.htmake.reader.init.appCtx.cacheDir)
    override val externalCache: File? = File(com.htmake.reader.init.appCtx.cacheDir)
}
