package io.legado.app.utils

import io.legado.app.constant.AppLog
import io.legado.app.model.analyzeRule.CustomUrl

/**
 * 引擎版 UrlUtil(从 readerMT `utils/UrlUtil.kt` 抽取引擎所需 `getSuffix`,纯 JVM)。
 * readerMT UrlUtil 还含 `getAbsoluteUrl`/`toAbsoluteUrl` 等(Android/HttpURLConnection
 * 耦合),不进引擎;`JsExtensions.downloadFile` 只需 `getSuffix`。
 */
object UrlUtil {

    private val fileSuffixRegex = Regex("^[a-z\\d]+$", RegexOption.IGNORE_CASE)

    fun getSuffix(str: String, default: String? = null): String {
        val suffix = CustomUrl(str).getUrl()
            .substringAfterLast("/")
            .substringBefore("?")
            .substringBefore("#")
            .substringAfterLast(".", "")
        //检查截取的后缀字符是否合法 [a-zA-Z0-9]
        return if (suffix.length > 5 || !suffix.matches(fileSuffixRegex)) {
            if (default == null) {
                AppLog.put("Cannot find legal suffix:\n target: $str\n suffix: $suffix")
            }
            default ?: "ext"
        } else {
            suffix
        }
    }

    /** URL 保留字符百分号编码(reader-mt `UrlUtil.replaceReservedChar`)。 */
    fun replaceReservedChar(text: String): String {
        return text.replace("%", "%25")
            .replace(" ", "%20")
            .replace("\"", "%22")
            .replace("#", "%23")
            .replace("&", "%26")
            .replace("(", "%28")
            .replace(")", "%29")
            .replace("+", "%2B")
            .replace(",", "%2C")
            .replace("/", "%2F")
            .replace(":", "%3A")
            .replace(";", "%3B")
            .replace("<", "%3C")
            .replace("=", "%3D")
            .replace(">", "%3E")
            .replace("?", "%3F")
            .replace("@", "%40")
            .replace("\\", "%5C")
            .replace("|", "%7C")
    }
}