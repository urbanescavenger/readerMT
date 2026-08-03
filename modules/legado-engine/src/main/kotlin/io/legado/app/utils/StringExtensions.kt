@file:Suppress("unused")

package io.legado.app.utils

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