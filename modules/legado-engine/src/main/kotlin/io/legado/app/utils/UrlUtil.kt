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
}