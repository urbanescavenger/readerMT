package io.legado.app.platform

import io.legado.app.help.config.AppConfig
import io.legado.app.platform.AppConfigProvider
import splitties.init.appCtx

/**
 * Android `:app` 侧对引擎 [AppConfigProvider] 的实现(Phase 1c switchover)。
 *
 * 背靠 app `AppConfig`(help/config)。引擎 `okHttpClient` 取 UA、`JsExtensions.ajaxAll`
 * 取并发度、服务端 browserless 地址等运行期配置都经此接口。
 *
 * app 无 browserless(remoteWebView)配置,返回空串(不影响纯 HTTP 书源)。
 */
class AndroidAppConfigProvider : AppConfigProvider {
    override val remoteWebViewApi: String get() = ""
    override val remoteWebViewToken: String get() = ""
    override val cachePath: String get() = appCtx.cacheDir.path
    override val userAgent: String get() = AppConfig.userAgent
    override val threadCount: Int get() = AppConfig.threadCount
}
