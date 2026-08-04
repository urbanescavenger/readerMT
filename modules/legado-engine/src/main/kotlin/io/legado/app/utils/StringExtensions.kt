@file:Suppress("unused")

package io.legado.app.utils

import io.legado.app.constant.AppPattern.dataUriRegex
import io.legado.app.constant.AppPattern.fileNameRegex2
import io.legado.app.constant.AppPattern.regexCharRegex
import java.net.InetAddress
import java.text.Collator
import java.util.Locale

/**
 * 从 readerMT `utils/StringExtensions.kt` 抽出的纯 JVM 扩展(原文件含 android.icu/Uri 等
 * Android 依赖,整文件不可直接搬;按需逐个抽取)。
 */

fun String.splitNotBlank(vararg delimiter: String, limit: Int = 0): Array<String> = run {
    this.split(*delimiter, limit = limit).map { it.trim() }.filterNot { it.isBlank() }
        .toTypedArray()
}

fun String.splitNotBlank(regex: Regex, limit: Int = 0): Array<String> = run {
    this.split(regex, limit).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()
}

/* String 是否为十六进制串(SymmetricCryptoAndroid.decrypt 用以区分 hex / base64)。 */
fun String.isHex(): Boolean {
    return all { c ->
        c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
    }
}

fun String?.isAbsUrl(): Boolean =
    this?.let {
        it.startsWith("http://", true) || it.startsWith("https://", true)
    } ?: false

fun String?.isDataUrl(): Boolean =
    this?.let { dataUriRegex.matches(it) } ?: false

fun String?.isJson(): Boolean =
    this?.run {
        val str = this.trim()
        when {
            str.startsWith("{") && str.endsWith("}") -> true
            str.startsWith("[") && str.endsWith("]") -> true
            else -> false
        }
    } ?: false

fun String?.isJsonObject(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("{") && str.endsWith("}")
    } ?: false

fun String?.isJsonArray(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("[") && str.endsWith("]")
    } ?: false

fun String?.isXml(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("<") && str.endsWith(">")
    } ?: false

/**
 * 将 ip 字符串转为 InetAddress
 */
fun String.parseIpsFromString(): List<InetAddress>? =
    split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { it.runCatching { InetAddress.getByName(this) }.getOrNull() }
        .takeIf { it.isNotEmpty() }
/**
 * 等价 reader-mt `StringExtensions.isTrue`(bookSourceType 等 "0/false/no" 字符串判否)。
 * `:server` 的 `BookChapterList` 用它判断 `isVolume.isTrue()`。
 */
fun String?.isTrue(nullIsTrue: Boolean = false): Boolean {
    if (this.isNullOrBlank() || this == "null") {
        return nullIsTrue
    }
    return !this.matches("\\s*(?i)(false|no|not|0)\\s*".toRegex())
}

/**
 * 等价 reader-mt `StringExtensions.htmlFormat`:把富文本 HTML 转纯文本(换行/去 script/加缩进)。
 * `:server` 的 `BookList`/`BookInfo` 用。
 */
fun String?.htmlFormat(): String = if (this.isNullOrBlank()) "" else
    this.replace("(?i)<(br[\\s/]*|/*p\\b.*?|/*div\\b.*?)>".toRegex(), "\n")
        .replace("<[script>]*.*?>|&nbsp;".toRegex(), "")
        .replace("\\s*\\n+\\s*".toRegex(), "\n　　")
        .replace("^[\\n\\s]+".toRegex(), "　　")
        .replace("[\\n\\s]+$".toRegex(), "")

/** 中文拼音/笔画排序(app 原版用 android.os.Build 判断,引擎纯 JDK Collator). */
fun String.cnCompare(other: String): Int {
    return Collator.getInstance(Locale.SIMPLIFIED_CHINESE).compare(this, other)
}

fun String.normalizeFileName(): String {
    return replace(fileNameRegex2, "_")
}

fun String.escapeRegex(): String {
    return replace(regexCharRegex, "\\\\$0")
}

fun String.quoteReplacementJs(): String {
    if (!this.contains('\\')) {
        return this
    }
    val sb = StringBuilder()
    for (c in this) {
        if (c == '\\') {
            sb.append("\\\\")
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}

fun CharSequence.toStringArray(): Array<String> {
    var codePointIndex = 0
    return try {
        Array(Character.codePointCount(this, 0, length)) {
            val start = codePointIndex
            codePointIndex = Character.offsetByCodePoints(this, start, 1)
            substring(start, codePointIndex)
        }
    } catch (e: Exception) {
        split("").toTypedArray()
    }
}
