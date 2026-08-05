package io.legado.app.help

import com.htmake.reader.config.AppConfig
import com.htmake.reader.utils.SpringContextUtils
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.StrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 服务端无头浏览器渲染助手:用于 startBrowserAwait 过 Cloudflare 等人机验证。
 *
 * 依赖外部 browserless(chromium)服务(reader.app.remoteWebviewApi 指向,需与 reader 同网络同出口 IP),
 * 调其 /function 端点跑一段 Puppeteer:加载 URL、等 CF 的 JS 挑战自动通过、回传页面 HTML + cookies + 最终 URL。
 * cookies 存入 CookieStore(sourceKey=书源URL),供 reader 后续请求复用(同 IP 同 UA,cf_clearance 可用)。
 *
 * 移动端 legado 的 startBrowserAwait 用 WebView Activity 交互式验证,服务端无 GUI 无法移植(用户浏览器≠服务器 IP,
 * cookie 跨域拿不到);本实现是其服务端无头版:不弹窗、自动过 JS 类挑战。交互式 Turnstile 仍可能失败。
 */
object WebViewRenderHelp {

    // 在 browserless 的浏览器 JS 运行时里执行:等 CF "Just a moment" 类挑战消失后回传 html/cookies/url。
    // 注意:不要用带 ${} 的 JS 模板字符串(Kotlin raw string 会插值)。
    // 格式:官方 browserless(/function)用 `import('./browserless-function-<id>.js')` 把 code 当 ES module
    // 加载,取 default 导出作为函数 → 必须写 `export default async ({ page, context }) => {...}`。
    // 不能写 `module.exports = ...`(ES module 里 module 未定义 → "module is not defined"),也不能写裸函数
    // 表达式(无 default 导出 → code 为 undefined → "code is not a function")。
    private const val RENDER_CODE = """export default async ({ page, context }) => {
  try { if (context.ua) { await page.setUserAgent(context.ua); } } catch (e) {}
  try {
    await page.goto(context.url, { waitUntil: 'networkidle0', timeout: 60000 });
  } catch (e) {}
  try {
    await page.waitForFunction(() => {
      var t = (document.title || '');
      if (/Just a moment|Checking your browser|cf-challenge|Attention Required|请稍候|正在检查/i.test(t)) return false;
      if (document.querySelector('#challenge-form,#cf-turnstile-container,#cf-challenge-running,#cf-spinner-please-wait,#cf-please-wait')) return false;
      return true;
    }, { timeout: 30000 });
  } catch (e) {}
  try { await new Promise(function (r) { setTimeout(r, 1500); }); } catch (e) {}
  var html = await page.content();
  var cookies = await page.cookies();
  var finalUrl = page.url();
  return { html: html, cookies: cookies, url: finalUrl };
}"""

    // renderHtmlWithJs 的 Puppeteer code:在预加载 html(或导航 url)上执行 JS 规则并回传结果。
    // 镜像 app BackstageWebView 语义:html 非空 → setContent(注入 <base> 使相对资源/相对URL可用,
    // 等价 loadDataWithBaseURL);否则导航 url。sourceRegex/overrideUrlRegex 命中首个请求URL即返回该 URL
    // (等价 onLoadResource/shouldOverrideUrlLoading 拦截);result 参数注入 window.result(等价
    // "window.result = WebCacheManager.getFromMemory('webview_result')")。JS 空则取 outerHTML。
    // 注意:不要用带 ${} 的 JS 模板字符串(Kotlin raw string 会插值)。
    // 格式:ES module default 导出(官方 browserless 用 import() 加载 code 取 default,见 RENDER_CODE 注释)。
    private const val RENDER_HTML_WITH_JS_CODE = """export default async ({ page, context }) => {
  const { url, html, javaScript, ua, sourceRegex, overrideUrlRegex, delayTime, result, headers } = context;
  const requestedUrls = [];
  page.on('request', req => { try { requestedUrls.push(req.url()); } catch (e) {} });
  try { if (ua) { await page.setUserAgent(ua); } } catch (e) {}
  try { if (headers && Object.keys(headers).length) { await page.setExtraHTTPHeaders(headers); } } catch (e) {}
  try {
    if (html && html.length > 0) {
      let content = html;
      if (url && url.length > 0 && !/<base[\s>]/i.test(content)) {
        const b = '<base href="' + url + '">';
        content = /<head[^>]*>/i.test(content) ? content.replace(/<head[^>]*>/i, m => m + b) : b + content;
      }
      await page.setContent(content, { waitUntil: 'networkidle0', timeout: 60000 });
    } else if (url && url.length > 0) {
      await page.goto(url, { waitUntil: 'networkidle0', timeout: 60000 });
    }
  } catch (e) {}
  try {
    await page.waitForFunction(() => {
      var t = (document.title || '');
      if (/Just a moment|Checking your browser|cf-challenge|Attention Required|请稍候|正在检查/i.test(t)) return false;
      if (document.querySelector('#challenge-form,#cf-turnstile-container,#cf-challenge-running,#cf-spinner-please-wait,#cf-please-wait')) return false;
      return true;
    }, { timeout: 30000 });
  } catch (e) {}
  try { if (delayTime > 0) { await new Promise(r => setTimeout(r, delayTime + 100)); } } catch (e) {}
  try {
    if (sourceRegex) { const re = new RegExp(sourceRegex); for (const u of requestedUrls) { if (re.test(u)) return { result: u }; } }
    if (overrideUrlRegex) { const re = new RegExp(overrideUrlRegex); for (const u of requestedUrls) { if (re.test(u)) return { result: u }; } }
  } catch (e) {}
  let js = (javaScript && javaScript.length > 0) ? javaScript : 'document.documentElement.outerHTML';
  // page.evaluate 把字符串包成 return (js),要求单表达式;result 注入用 IIFE 保持单表达式(否则多语句 → 语法错误)。
  // result 可能已被 browserless JSON.parse 成 object,须先 JSON.stringify,否则 [object Object] 语法错误
  if (result != null) { const resultJs = (typeof result === 'object') ? JSON.stringify(result) : result; js = '(function(){ window.result = ' + resultJs + '; return (' + js + '); })()'; }
  let out = '';
  // page.evaluate 失败时降级返回页面 HTML(graceful,镜像 app 语义);需调试可临时改回返回错误信息
  try { out = await page.evaluate(js); } catch (e) { try { out = await page.content(); } catch (e2) {} }
  return { result: String(out == null ? '' : out) };
}"""

    // evalJS 的 Puppeteer code:在空白页上执行 JS 并回传结果(服务端无持久 page,每次新建)。
    // 格式:ES module default 导出(官方 browserless 用 import() 加载 code 取 default,见 RENDER_CODE 注释)。
    private const val EVAL_JS_CODE = """export default async ({ page, context }) => {
  let out = '';
  try { out = await page.evaluate(context.js); } catch (e) { try { out = await page.content(); } catch (e2) {} }
  return { result: String(out == null ? '' : out) };
}"""

    fun renderUrl(url: String, sourceKey: String, ua: String): StrResponse {
        val data = browserlessCall(RENDER_CODE, mapOf("url" to url, "ua" to ua), 0L)
        val html = (data["html"] as? String) ?: ""
        val finalUrl = (data["url"] as? String)?.takeIf { it.isNotEmpty() } ?: url
        // 把 cookies 存入 CookieStore,供 reader 后续请求复用(同 IP 同 UA,cf_clearance 有效)
        val cookies = data["cookies"] as? List<*>
        if (cookies != null) {
            val sb = StringBuilder()
            for (c in cookies) {
                val m = c as? Map<*, *> ?: continue
                val name = m["name"] as? String ?: continue
                val value = m["value"] as? String ?: continue
                if (name.isEmpty()) continue
                if (sb.isNotEmpty()) sb.append("; ")
                sb.append(name).append("=").append(value)
            }
            if (sb.isNotEmpty()) {
                CookieStore.setCookie(sourceKey, sb.toString())
            }
        }
        return StrResponse(finalUrl, html)
    }

    /**
     * 调 browserless `/function` 端点跑一段 Puppeteer [code],返回解析后的响应 map。
     * 连接失败/超时/非 browserless 服务等统一转成清晰中文错误,而非 Rhino 栈。
     */
    private fun browserlessCall(code: String, context: Map<String, Any?>, timeout: Long): Map<String, Any?> {
        val appConfig = appConfig()
        val apiBase = appConfig.remoteWebviewApi.trim().trimEnd('/')
        if (apiBase.isBlank()) {
            throw NoStackTraceException(
                "未配置 remoteWebviewApi(无头浏览器服务),无法执行 webView 渲染,请在配置中设置或改用手机版阅读APP"
            )
        }
        val token = appConfig.remoteWebviewToken.trim()
        val t = if (timeout > 0) timeout else 70000L
        val query = if (token.isNotEmpty()) {
            "token=" + URLEncoder.encode(token, "UTF-8") + "&timeout=$t"
        } else {
            "timeout=$t"
        }
        val endpoint = "$apiBase/function?$query"
        val bodyJson = GSON.toJson(mapOf("code" to code, "context" to context))
        return try {
            runBlocking {
                val client = okHttpClient.newBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .callTimeout(90, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(endpoint)
                    .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("HTTP " + resp.code + " " + resp.message)
                    }
                    val respBody = resp.body?.string()
                        ?: throw IOException("browserless 返回空响应")
                    GSON.fromJsonObject<Map<String, Any?>>(respBody).getOrNull()
                        ?: throw IOException("browserless 响应解析失败(确认是 browserless 服务,非旧 remote-webview 8050)")
                }
            }
        } catch (e: Exception) {
            throw NoStackTraceException(
                "无头浏览器服务调用失败,请确认 reader.app.remoteWebviewApi 指向运行中的 browserless 服务" +
                "(当前配置: " + appConfig.remoteWebviewApi + ";docker 内用容器名 http://browserless:3000 或 host.docker.internal:3000,勿用 localhost/旧 remote-webview 8050)。" +
                "原因: " + (e.message ?: e.toString())
            )
        }
    }

    /**
     * 在预加载 [html](或导航 [url])上执行 [javaScript] 规则并返回结果字符串。
     * 对应引擎 `Platform.webView.renderHtmlWithJs`(AnalyzeRule.getWebJsResult / AnalyzeUrl.executeStrRequest /
     * JsExtensions.webView*)。[sourceRegex]/[overrideUrlRegex] 命中首个请求 URL 即返回该 URL;
     * [result] 注入 `window.result`;[cacheFirst] 为已知近似(不实现,browserless 每次重取)。
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
        result: String?
    ): StrResponse {
        val data = browserlessCall(
            RENDER_HTML_WITH_JS_CODE,
            mapOf(
                "url" to url,
                "html" to html,
                "javaScript" to javaScript,
                "ua" to userAgent(),
                "sourceRegex" to sourceRegex,
                "overrideUrlRegex" to overrideUrlRegex,
                "delayTime" to delayTime,
                "result" to result,
                "headers" to headerMap
            ),
            timeout
        )
        val out = (data["result"] as? String) ?: ""
        return StrResponse(url ?: "", out)
    }

    /** 在空白页上执行 [js] 并返回结果(服务端无持久 page,每次新建)。 */
    fun evalJS(js: String): String {
        val data = browserlessCall(EVAL_JS_CODE, mapOf("js" to js), 0L)
        return (data["result"] as? String) ?: ""
    }

    private fun userAgent(): String =
        io.legado.app.platform.Platform.appConfig.userAgent

    private fun appConfig(): AppConfig =
        SpringContextUtils.getBean("appConfig", AppConfig::class.java)
}