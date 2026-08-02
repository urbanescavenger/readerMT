package io.legado.app.platform.web

/**
 * WebView 渲染 SPI(计划 §3.4)。
 *
 * - Android `:app`:真 `BackstageWebView`/`AjaxWebView`(交互式过 Cloudflare);
 * - `:server`:browserless HTTP(移植 reader-mt `WebViewRenderHelp`)。
 *
 * 引擎 `AnalyzeUrl` 等改调 `Platform.webView.*`,平台无关。
 *
 * 签名为 Phase 1a 占位,Phase 1c 搬入 `AnalyzeUrl` 时按真实调用点校准
 * (例如返回值可能改为带 finalUrl 的结果类型)。
 */
interface WebViewRenderer {
    /**
     * 加载 [url](可带 method/headers/body),执行 [js] 后返回渲染页面 HTML。
     * 用于 `startBrowserAwait` 过 Cloudflare 等人机验证。
     */
    fun startBrowserAwait(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        charset: String,
        js: String?,
    ): String

    /** 在已加载页面执行 [js] 并返回结果字符串。 */
    fun evalJS(js: String): String
}