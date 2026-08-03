@file:Suppress("unused")

package io.legado.app.help

import cn.hutool.core.codec.Base64
import cn.hutool.core.util.HexUtil
import io.legado.app.utils.EncoderUtils
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone

/**
 * 引擎版 JsExtensions(书源 JS 的 API 表面)。
 *
 * readerMT 的 `help/JsExtensions` 是 102 方法的单体对象,深度耦合 android(appCtx/
 * android.webkit.JavascriptInterface/@JavascriptInterface)、com.script、hutool、UI、
 * model.AnalyzeUrl 等。本引擎对象**逐簇往里加方法**:app 的全量 JsExtensions 不动
 * (两端同名不同模块,switchover 前不在同一 classpath,无冲突),引擎版逐步长齐后再让
 * AnalyzeRule 用它。
 *
 * 与 readerMT 的差异:
 * - **去 `@JavascriptInterface`**:那是 android.webkit 的 JS 桥注解;引擎用 RhinoEngine
 *   绑定,不需要。方法仍是 public,可被 JS 调用。
 * - android 专属方法(openVideoPlayer 等)不进引擎。
 *
 * 本批(簇①纯子集):strToBytes/bytesToStr、hex 编解码、timeFormatUTC、encodeURI。
 * 缓:base64 flags 变体(走 android.util.Base64,需 java.util.Base64 重写 + flag 映射)、
 * timeFormat(走 AppConst.dateFormat)、htmlFormat(HtmlFormatter→AnalyzeUrl)。
 */
object JsExtensions {

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
}