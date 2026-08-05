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
 * `startBrowserAwait`(过 CF 关键路径)与 `renderHtmlWithJs`(8/11 参,解锁 `@JS:` 规则书源)
 * 均经 browserless `/function` 跑 Puppeteer 实现;`evalJS` 在空白页上执行(服务端无持久 page,
 * 每次新建)。已知近似:不实现 `cacheFirst`(每次重取)、不注入 app WebView 的 `source.*`/`java.*`/
 * `cache.*` JS 绑定(纯 DOM 提取规则可跑,引用这些变量的规则 fallback 到 `page.content()`)。
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

    /** 在空白页上执行 [js] 并返回结果(服务端无持久 page,每次新建,经 browserless)。 */
    override fun evalJS(js: String): String =
        WebViewRenderHelp.evalJS(js)

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
        WebViewRenderHelp.renderHtmlWithJs(
            url, html, javaScript, headerMap, tag, null, null, cacheFirst, timeout, 0, result
        ).body ?: ""

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
        WebViewRenderHelp.renderHtmlWithJs(
            url, html, javaScript, headerMap, tag, sourceRegex, overrideUrlRegex, cacheFirst, timeout, delayTime, result
        ).body ?: ""

    private fun userAgent(): String =
        io.legado.app.platform.Platform.appConfig.userAgent
}
