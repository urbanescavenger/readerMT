package io.legado.app.platform

import io.legado.app.help.http.BackstageWebView
import io.legado.app.platform.web.WebViewRenderer
import kotlinx.coroutines.runBlocking

/**
 * Android `:app` 侧对引擎 [WebViewRenderer] 的实现(Phase 1c switchover)。
 *
 * 引擎 `AnalyzeUrl`/`AnalyzeRule`/`JsExtensions` 的 WebJs 渲染经 `Platform.webView`
 * 反调 app 真 `BackstageWebView`(help/http)——即原 master 的实现,非 NoOp。
 * `BackstageWebView` 是单次请求模型(构造一次渲染一次),与引擎 SPI 两个
 * `renderHtmlWithJs` 重载一一对应。
 *
 * `startBrowserAwait`/`evalJS`:引擎当前无调用点,先抛 `UnsupportedOperationException`;
 * 真实书源(过 CF 交互式加载)需要时再补 `WebViewPool`/`WebJsExtensions` 支持。
 */
class AndroidWebViewRenderer : WebViewRenderer {

    override fun startBrowserAwait(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        charset: String,
        js: String?,
    ): String = throw UnsupportedOperationException(
        "AndroidWebViewRenderer.startBrowserAwait not implemented yet (no engine call site)"
    )

    override fun evalJS(js: String): String = throw UnsupportedOperationException(
        "AndroidWebViewRenderer.evalJS not implemented yet (no engine call site)"
    )

    override fun renderHtmlWithJs(
        url: String?,
        html: String,
        javaScript: String,
        headerMap: Map<String, String>?,
        tag: String?,
        cacheFirst: Boolean,
        timeout: Long,
        result: String?,
    ): String = runBlocking {
        BackstageWebView(
            url = url,
            html = html,
            encode = null,
            tag = tag,
            headerMap = headerMap,
            javaScript = javaScript,
            cacheFirst = cacheFirst,
            timeout = timeout,
            result = result,
            isRule = true
        ).getStrResponse().body
    }

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
    ): String = runBlocking {
        BackstageWebView(
            url = url,
            html = html,
            encode = null,
            tag = tag,
            headerMap = headerMap,
            sourceRegex = sourceRegex,
            overrideUrlRegex = overrideUrlRegex,
            javaScript = javaScript,
            delayTime = delayTime,
            cacheFirst = cacheFirst,
            timeout = timeout,
            result = result,
            isRule = true
        ).getStrResponse().body
    }
}
