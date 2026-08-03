@file:Suppress("unused")

package io.legado.app.utils

import io.legado.app.constant.AppPattern.dataUriRegex
import java.net.InetAddress

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