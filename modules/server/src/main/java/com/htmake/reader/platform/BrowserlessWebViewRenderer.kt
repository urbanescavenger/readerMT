package com.htmake.reader.platform

import io.legado.app.help.WebViewRenderHelp
import io.legado.app.platform.web.WebViewRenderer
import org.springframework.stereotype.Component

/**
 * `:server` 的 [WebViewRenderer] 实现:背靠 browserless 无头浏览器。
 *
 * 复用 reader-mt 现有 `WebViewRenderHelp`(调 browserless `/function` 端点跑 Puppeteer
 * 过 Cloudflare 类人机验证)。browserless 服务地址/token 来自 `AppConfig.remoteWebviewApi`/
 * `remoteWebviewToken`(引擎 `Platform.appConfig` 已映射)。
 *
 * M1 阶段仅实现 `startBrowserAwait`(过 CF 关键路径);其余 `renderHtmlWithJs` 重载与
 * `evalJS` 抛 `UnsupportedOperationException`——服务端 browserless 无持久 page,无法
 * 复现 app `BackstageWebView` 的"预加载 html 上执行 JS"语义,M2 冒烟(searchBook 纯 HTTP
 * 书源)不触达;真实书源需要时再补(§9.9 deferred)。
 */
@Component
class BrowserlessWebViewRenderer : WebViewRenderer {

    /**
     * 加载 [url],执行 [js](可为 null)后返回渲染页面 HTML。
     * `:server` 用 `WebViewRenderHelp.renderUrl(url, sourceKey=url, ua)`(browserless)。
     * [method]/[headers]/[body]/[charset]/[js] 参数被忽略(browserless 走 GET 导航 + 页面 JS)。
     */
    override fun startBrowserAwait(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        charset: String,
        js: String?,
    ): String {
        val resp = WebViewRenderHelp.renderUrl(url, url, userAgent())
        return resp.body ?: ""
    }

    /** 服务端 browserless 无持久 page,无法在已加载页面执行 JS。 */
    override fun evalJS(js: String): String =
        throw UnsupportedOperationException("evalJS not supported on :server (no persistent browser page)")

    override fun renderHtmlWithJs(
        url: String?,
        html: String,
        javaScript: String,
        headerMap: Map<String, String>?,
        tag: String?,
        cacheFirst: Boolean,
        timeout: Long,
        result: String?,
    ): String =
        throw UnsupportedOperationException("renderHtmlWithJs(8) not supported on :server (no BackstageWebView)")

    override fun renderHtmlWithJs(
        url: String?,
        html: String,
        javaScript: String,
        headerMap: Map<String, String>?,
        tag: String?,
        sourceRegex: String?,
        overrideUrlRegex: String?,
        cacheFirst: Boolean,
        timeout: Long,
        delayTime: Long,
        result: String?,
    ): String =
        throw UnsupportedOperationException("renderHtmlWithJs(11) not supported on :server (no BackstageWebView)")

    private fun userAgent(): String =
        io.legado.app.platform.Platform.appConfig.userAgent
}
