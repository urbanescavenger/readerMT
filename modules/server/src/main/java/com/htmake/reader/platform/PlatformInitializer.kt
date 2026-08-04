package com.htmake.reader.platform

import io.legado.app.platform.Platform
import io.legado.app.platform.js.DirectRhinoEngine
import io.legado.app.platform.repo.Repositories
import io.legado.app.platform.webbook.NoOpWebBookProvider
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * M1:启动时把 `:server` 的 5 个平台实现注入引擎 [Platform]。
 *
 * 引擎源码(`AnalyzeRule`/`AnalyzeUrl`/`BaseSource`/`CookieStore`/`CacheManager`/
 * `okHttpClient` 等)只经 [Platform] 访问平台能力,未注入即 `UninitializedPropertyAccessException`。
 *
 * 注入面:
 * - [Platform.context] = [ServerPlatformContext](缓存/文件目录);
 * - [Platform.appConfig] = [SpringAppConfigProvider](browserless 地址/UA/缓存路径);
 * - [Platform.scriptAssets] = [ServerScriptAssetProvider](classpath 脚本);
 * - [Platform.webView] = [BrowserlessWebViewRenderer](browserless 过 CF);
 * - [Platform.repositories] = [Repositories](in-memory Cookie/Cache stub,M3 换持久化);
 * - [Platform.rhino] = 引擎自带 [DirectRhinoEngine](直连 org.mozilla.javascript,零实现);
 * - [Platform.webBook] = 引擎自带 [NoOpWebBookProvider](preUpdate 重抓 app-only);
 * - [Platform.isMainThread] = 服务端恒 false(默认已合适,不改)。
 *
 * 用 Spring 构造函数注入拿到 4 个 @Component 实现,避免在 object 里手工 `getBean`。
 */
@Component
class PlatformInitializer(
    private val context: ServerPlatformContext,
    private val appConfig: SpringAppConfigProvider,
    private val scriptAssets: ServerScriptAssetProvider,
    private val webView: BrowserlessWebViewRenderer,
    private val cookieRepository: VertxCookieRepository,
    private val cacheRepository: VertxCacheRepository,
) {

    @PostConstruct
    fun init() {
        Platform.context = context
        Platform.appConfig = appConfig
        Platform.scriptAssets = scriptAssets
        Platform.webView = webView
        Repositories.cookie = cookieRepository
        Repositories.cache = cacheRepository
        Platform.repositories = Repositories
        Platform.rhino = DirectRhinoEngine
        Platform.webBook = NoOpWebBookProvider
    }
}
