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

    /**
     * 在预加载的 [html](非导航 URL)上执行 [javaScript] 并返回结果(body 文本)。
     * 对应 `AnalyzeRule.getWebJsResult` 调 `BackstageWebView(url,html,javaScript,headerMap,
     * tag,cacheFirst,timeout,result,isRule=true).getStrResponse().body`。
     *
     * - Android `:app`:委托真 `BackstageWebView`;
     * - `:server`:browserless/jsdom 等价。
     *
     * [result] 为上下文 JSON(书源规则中间结果),[tag] 为源键(缓存用),[cacheFirst] 优先缓存。
     */
    fun renderHtmlWithJs(
        url: String?,
        html: String,
        javaScript: String,
        headerMap: Map<String, String>?,
        tag: String?,
        cacheFirst: Boolean,
        timeout: Long,
        result: String?,
    ): String

    /**
     * 带 `sourceRegex`/`overrideUrlRegex`/`delayTime` 的渲染(过 Cloudflare 资源拦截/重定向匹配)。
     *
     * 对应 `AnalyzeUrl.executeStrRequest` 的 `BackstageWebView(url,html,javaScript,sourceRegex,
     * headerMap,delayTime).getStrResponse()`(POST 分支先 HTTP 拿 res 再渲染;
     * 非 POST 分支 html="" 即导航式加载),以及 `JsExtensions.webViewGetSource`(sourceRegex)/
     * `webViewGetOverrideUrl`(overrideUrlRegex)。
     *
     * - [sourceRegex]:命中后返回该资源响应(过 CF 关键),空表示不拦截;
     * - [overrideUrlRegex]:命中重定向 URL 时拦截,空表示不拦截;
     * - [delayTime]:渲染前等待毫秒(让 JS 执行完)。
     *
     * [result] 为上下文 JSON(书源规则中间结果),[tag] 为源键(缓存用),[cacheFirst] 优先缓存。
     */
    fun renderHtmlWithJs(
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
    ): String
}