@file:Suppress("unused")

package io.legado.app.help

import cn.hutool.core.codec.Base64
import cn.hutool.core.util.HexUtil
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.http.CookieStore
import io.legado.app.platform.Platform
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.JsURL
import io.legado.app.utils.StringUtils
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 引擎版 JsExtensions(书源 JS 的 API 表面,计划 §3.3 / §9.4)。
 *
 * 对应 readerMT `app/.../help/JsExtensions.kt` 的 `interface JsExtensions : JsEncodeUtils`
 * (1199 行,102 方法)。本接口**逐簇往里加默认方法**:app 全量 JsExtensions 不动
 * (两端同名不同模块,switchover 前不在同一 classpath,无冲突),引擎版逐步长齐后再让
 * AnalyzeRule 用它。
 *
 * 继承:`interface JsExtensions : JsEncodeUtils`(JsEncodeUtils 加密簇已落地,§9.5 batch 1)。
 * 抽象成员 `getSource(): BaseSource?`/`getTag(): String?` 已加(batch 2b,由 BaseSource/AnalyzeRule 实现)。
 *
 * 与 readerMT 的差异:
 * - **去 `@JavascriptInterface`**:那是 android.webkit 的 JS 桥注解;引擎用 RhinoEngine
 *   绑定,不需要。方法仍是 public,可被 JS 调用。
 * - android 专属方法(openVideoPlayer/openUrl/toast/getReadBookConfig/getThemeConfig 等)不进引擎。
 *
 * 已搬:
 * - 簇①(strToBytes/bytesToStr、hex 编解码、timeFormatUTC、encodeURI、base64 全套、timeFormat);
 * - 簇②非 AnalyzeUrl 阻塞(getCookie/randomUUID/getWebViewUA/t2s/s2t/toURL/toNumChapter/
 *   htmlFormat/log/logType/getFile/readFile/readTxtFile/deleteFile)。
 * 待 2c-3 闭包(阻塞 AnalyalyzeUrl):HTTP(ajax/ajaxAll/ajaxTestAll/connect/get/post/head)、
 * downloadFile、getZip/Rar/7zByteArrayContent、queryTTF、importScript/cacheFile、
 * webView*/webViewGetSource/webViewGetOverrideUrl/startBrowser*。
 */
interface JsExtensions : JsEncodeUtils {

    /** 书源对象(JS 中 `source`/`java` 可调 `source.getTag()`);由 BaseSource/AnalyzeRule/AnalyzeUrl 实现。 */
    fun getSource(): BaseSource?

    /** 源标签(日志/缓存键);由 BaseSource/AnalyzeRule 实现。 */
    fun getTag(): String?

    fun strToBytes(str: String): ByteArray {
        return str.toByteArray(charset("UTF-8"))
    }

    fun strToBytes(str: String, charset: String): ByteArray {
        return str.toByteArray(charset(charset))
    }

    /* ByteArray转Str */
    fun bytesToStr(bytes: ByteArray): String {
        return String(bytes, charset("UTF-8"))
    }

    fun bytesToStr(bytes: ByteArray, charset: String): String {
        return String(bytes, charset(charset))
    }

    /* HexString 解码为字节数组 */
    fun hexDecodeToByteArray(hex: String): ByteArray? {
        return HexUtil.decodeHex(hex)
    }

    /* hexString 解码为utf8String */
    fun hexDecodeToString(hex: String): String? {
        return HexUtil.decodeHexStr(hex)
    }

    /* utf8 编码为hexString */
    fun hexEncodeToString(utf8: String): String? {
        return HexUtil.encodeHexStr(utf8)
    }

    /**
     * 格式化时间
     */
    fun timeFormatUTC(time: Long, format: String, sh: Int): String? {
        val utc = SimpleTimeZone(sh, "UTC")
        return SimpleDateFormat(format, Locale.getDefault()).run {
            timeZone = utc
            format(Date(time))
        }
    }

    fun encodeURI(str: String): String {
        return try {
            URLEncoder.encode(str, "UTF-8")
        } catch (e: Exception) {
            ""
        }
    }

    fun encodeURI(str: String, enc: String): String {
        return try {
            URLEncoder.encode(str, enc)
        } catch (e: Exception) {
            ""
        }
    }

    // ---- 簇①b:base64(+flags 经 EncoderUtils java.util.Base64 重写)+ timeFormat ----

    fun base64Decode(str: String?): String {
        return Base64.decodeStr(str)
    }

    fun base64Decode(str: String?, charset: String): String {
        return Base64.decodeStr(str, charset(charset))
    }

    fun base64Decode(str: String, flags: Int): String {
        return EncoderUtils.base64Decode(str, flags)
    }

    fun base64DecodeToByteArray(str: String?): ByteArray? {
        if (str.isNullOrBlank()) {
            return null
        }
        return EncoderUtils.base64DecodeToByteArray(str, 0)
    }

    fun base64DecodeToByteArray(str: String?, flags: Int): ByteArray? {
        if (str.isNullOrBlank()) {
            return null
        }
        return EncoderUtils.base64DecodeToByteArray(str, flags)
    }

    fun base64Encode(str: String): String? {
        return EncoderUtils.base64Encode(str, 2)
    }

    fun base64Encode(str: String, flags: Int): String? {
        return EncoderUtils.base64Encode(str, flags)
    }

    fun timeFormat(time: Long): String {
        // readerMT 用 AppConst.dateFormat(FastDateFormat "yyyy/MM/dd HH:mm",线程安全共享);
        // 引擎无 AppConst,用每次新建 SimpleDateFormat(线程安全)同格式。
        return SimpleDateFormat("yyyy/MM/dd HH:mm").format(Date(time))
    }

    // ---- 簇②:非 AnalyzeUrl 阻塞的独立方法(Cookie/源/UUID/UA/繁简/URL/章节号/html/文件/日志) ----

    /**
     * 当前 JS 执行的协程上下文(对应 rhino-android `rhinoContextOrNull`)。
     * HTTP/WebView/文件簇经它做 `ensureActive` cancellation 与 `runBlocking` 上下文。
     */
    private val context: CoroutineContext
        get() = Platform.rhino.currentCoroutineContext() ?: EmptyCoroutineContext

    /** js 实现读取 cookie */
    fun getCookie(tag: String): String {
        return getCookie(tag, null)
    }

    fun getCookie(tag: String, key: String?): String {
        return if (key != null) {
            CookieStore.getKey(tag, key)
        } else {
            CookieStore.getCookie(tag)
        }
    }

    /** 生成 UUID */
    fun randomUUID(): String {
        return UUID.randomUUID().toString()
    }

    /** 获取 WebView UA(app 用 `WebSettings.getDefaultUserAgent`;引擎走 `Platform.appConfig.userAgent`)。 */
    fun getWebViewUA(): String {
        return Platform.appConfig.userAgent
    }

    fun t2s(text: String): String {
        return ChineseUtils.t2s(text)
    }

    fun s2t(text: String): String {
        return ChineseUtils.s2t(text)
    }

    fun htmlFormat(str: String): String {
        return HtmlFormatter.formatKeepImg(str)
    }

    /** 章节数转数字 */
    fun toNumChapter(s: String?): String? {
        s ?: return null
        val matcher = AppPattern.titleNumPattern.matcher(s)
        if (matcher.find()) {
            val intStr = StringUtils.stringToInt(matcher.group(2))
            return "${matcher.group(1)}${intStr}${matcher.group(3)}"
        }
        return s
    }

    fun toURL(urlStr: String): JsURL {
        return JsURL(urlStr)
    }

    fun toURL(url: String, baseUrl: String? = null): JsURL {
        return JsURL(url, baseUrl)
    }

    /** 输出调试日志(app 用 `Debug.log` + `AppLog.putDebug`;引擎无 Debug UI,仅 `AppLog.put`)。 */
    fun log(msg: Any?): Any? {
        Platform.rhino.currentCoroutineContext()?.ensureActive()
        AppLog.put("${getTag() ?: "源"}调试输出: $msg")
        return msg
    }

    /** 输出对象类型 */
    fun logType(any: Any?) {
        if (any == null) {
            log("null")
        } else {
            log(any.javaClass.name)
        }
    }

    //****************文件操作******************//

    /**
     * 获取本地文件(相对路径,基于 `Platform.context.externalCache`)。
     * @param path 相对路径
     */
    fun getFile(path: String): File {
        val cachePath = (Platform.context.externalCache ?: Platform.context.cacheDir).absolutePath
        val aPath = if (path.startsWith(File.separator)) {
            cachePath + path
        } else {
            cachePath + File.separator + path
        }
        val file = File(aPath)
        val safePath = (Platform.context.externalCache ?: Platform.context.cacheDir).parent
        if (safePath != null && !file.canonicalPath.startsWith(safePath)) {
            throw SecurityException("非法路径")
        }
        return file
    }

    fun readFile(path: String): ByteArray? {
        val file = getFile(path)
        if (file.exists()) {
            return file.readBytes()
        }
        return null
    }

    fun readTxtFile(path: String): String {
        val file = getFile(path)
        if (file.exists()) {
            val charsetName = EncodingDetect.getEncode(file)
            return String(file.readBytes(), charset(charsetName))
        }
        return ""
    }

    fun readTxtFile(path: String, charsetName: String): String {
        val file = getFile(path)
        if (file.exists()) {
            return String(file.readBytes(), charset(charsetName))
        }
        return ""
    }

    /** 删除本地文件 */
    fun deleteFile(path: String): Boolean {
        val file = getFile(path)
        return FileUtils.delete(file, true)
    }
}