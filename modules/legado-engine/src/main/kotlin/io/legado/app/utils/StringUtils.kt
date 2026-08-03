package io.legado.app.utils

/**
 * `android.text.TextUtils` 的平台无关替代(计划 §3.8)。
 *
 * 引擎源码把 `TextUtils.isEmpty(...)` / `TextUtils.join(...)` 改调 [StringUtils]。
 */
object StringUtils {

    /** 等价 `android.text.TextUtils.isEmpty`:null 或长度为 0 返回 true(不把纯空白当空)。 */
    fun isEmpty(s: CharSequence?): Boolean = s.isNullOrEmpty()

    /** 等价 `android.text.TextUtils.join(delimiter, tokens)`。 */
    fun join(delimiter: CharSequence, tokens: Iterable<*>?): String =
        tokens?.joinToString(delimiter.toString()) ?: ""

    /** 等价 `android.text.TextUtils.join` 的数组重载。 */
    fun join(delimiter: CharSequence, tokens: Array<*>?): String =
        tokens?.joinToString(delimiter.toString()) ?: ""
}