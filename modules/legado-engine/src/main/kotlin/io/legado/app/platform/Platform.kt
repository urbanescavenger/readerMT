package io.legado.app.platform

import io.legado.app.platform.js.RhinoEngine

/**
 * 平台无关 legado 引擎的全局 SPI 持有者。
 *
 * 各平台在启动时注入实现:
 * - Android `:app` 在 Application.onCreate 注入 Android 实现;
 * - `:server` 在 Spring/Vert.x 启动时注入服务端实现。
 *
 * 引擎源码仅通过 [Platform] 访问平台能力,禁止 import
 * `android.*`/`androidx.*`/`com.script.*`/`com.htmake.*`
 * (由 CI grep 强制,见 `.github/workflows/engine-cross-check.yml`)。
 *
 * 注:[repositories] 暂未在 Phase 1a 声明——待 Phase 1b 搬入实体 DTO 后,
 * 连同 Repository SPI 一并加入。
 */
object Platform {
    lateinit var context: PlatformContext
    lateinit var webView: WebViewRenderer
    lateinit var appConfig: AppConfigProvider
    lateinit var scriptAssets: ScriptAssetProvider
    lateinit var rhino: RhinoEngine

    /** 是否主线程。Android 注入 `Looper` 判定;服务端恒 false。 */
    var isMainThread: () -> Boolean = { false }
}