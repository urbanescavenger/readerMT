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
    private const val RENDER_CODE = """module.exports = async ({ page, context }) => {
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
};"""

    fun renderUrl(url: String, sourceKey: String, ua: String): StrResponse {
        val appConfig = appConfig()
        val apiBase = appConfig.remoteWebviewApi.trim().trimEnd('/')
        if (apiBase.isBlank()) {
            throw NoStackTraceException(
                "未配置 remoteWebviewApi(无头浏览器服务),无法执行 startBrowserAwait,请在配置中设置或改用手机版阅读APP"
            )
        }
        val token = appConfig.remoteWebviewToken.trim()
        val query = if (token.isNotEmpty()) {
            "token=" + URLEncoder.encode(token, "UTF-8") + "&timeout=70000"
        } else {
            "timeout=70000"
        }
        val endpoint = "$apiBase/function?$query"
        val bodyJson = GSON.toJson(
            mapOf(
                "code" to RENDER_CODE,
                "context" to mapOf("url" to url, "ua" to ua)
            )
        )
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
                    val data = GSON.fromJsonObject<Map<String, Any?>>(respBody).getOrNull()
                        ?: throw IOException("browserless 响应解析失败(确认是 browserless 服务,非旧 remote-webview 8050)")
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
                    StrResponse(finalUrl, html)
                }
            }
        } catch (e: Exception) {
            // 连接失败/超时/非 browserless 服务等,统一转成清晰中文错误,而非 Rhino 栈
            throw NoStackTraceException(
                "无头浏览器服务调用失败,请确认 reader.app.remoteWebviewApi 指向运行中的 browserless 服务" +
                "(当前配置: " + appConfig.remoteWebviewApi + ";docker 内用容器名 http://browserless:3000 或 host.docker.internal:3000,勿用 localhost/旧 remote-webview 8050)。" +
                "原因: " + (e.message ?: e.toString())
            )
        }
    }

    private fun appConfig(): AppConfig =
        SpringContextUtils.getBean("appConfig", AppConfig::class.java)
}