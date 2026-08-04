package com.htmake.reader.platform

import com.htmake.reader.config.AppConfig
import io.legado.app.platform.AppConfigProvider
import org.springframework.stereotype.Component

/**
 * `:server` 的 [AppConfigProvider] 实现:背靠 `com.htmake.reader.config.AppConfig`
 * (Spring `@ConfigurationProperties(prefix = "reader.app")`)。
 *
 * M1 注入 `Platform.appConfig`。`cachePath` 映射 `storagePath + "/cache"`(与
 * [ServerPlatformContext] 一致);`userAgent` 用服务器桌面 UA;`threadCount` 固定 8
 * (`AppConfig` 未暴露该配置,沿用 reader-mt 默认并发)。
 */
@Component
class SpringAppConfigProvider(private val appConfig: AppConfig) : AppConfigProvider {

    override val remoteWebViewApi: String get() = appConfig.remoteWebviewApi
    override val remoteWebViewToken: String get() = appConfig.remoteWebviewToken

    override val cachePath: String
        get() = com.htmake.reader.init.appCtx.cacheDir

    override val userAgent: String
        get() = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36"

    override val threadCount: Int get() = 8
}
