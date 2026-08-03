@file:Suppress("unused")

package io.legado.app.utils

import org.jsoup.Jsoup
import java.io.File

/**
 * 引擎版 EncodingDetect(从 readerMT `utils/EncodingDetect.kt` 移植)。
 *
 * readerMT 用 `android.text.TextUtils` + 自带 `lib/icu4j/CharsetDetector`(统计编码检测,
 * 含 android.os.ParcelFileDescriptor/android.system.OsConstants 耦合)。引擎版:
 * - `TextUtils.isEmpty` → `StringUtils.isEmpty`(纯 JVM)。
 * - `getEncode(bytes)` 的 icu4j 统计检测**暂缓**(那批 Java 文件含 android 耦合,需单独剥离子批);
 *   无 meta charset 时回退 "UTF-8"。多数书源页在 meta 声明 charset,统计检测是兜底;
 *   parity 差异留 §5c。后续可移植纯 JVM 的 `CharsetRecog_*` 子集或换 Maven icu4j。
 */
object EncodingDetect {

    private val headTagRegex = "(?i)<head>[\\s\\S]*?</head>".toRegex()
    private val headOpenBytes = "<head>".toByteArray()
    private val headCloseBytes = "</head>".toByteArray()

    fun getHtmlEncode(bytes: ByteArray): String {
        try {
            var head: String? = null
            val startIndex = bytes.indexOf(headOpenBytes)
            if (startIndex > -1) {
                val endIndex = bytes.indexOf(headCloseBytes, startIndex)
                if (endIndex > -1) {
                    head = String(bytes.copyOfRange(startIndex, endIndex + headCloseBytes.size))
                }
            }
            val doc = Jsoup.parseBodyFragment(head ?: headTagRegex.find(String(bytes))!!.value)
            val metaTags = doc.getElementsByTag("meta")
            var charsetStr: String
            for (metaTag in metaTags) {
                charsetStr = metaTag.attr("charset")
                if (!StringUtils.isEmpty(charsetStr)) {
                    return charsetStr
                }
                val httpEquiv = metaTag.attr("http-equiv")
                if (httpEquiv.equals("content-type", true)) {
                    val content = metaTag.attr("content")
                    val idx = content.indexOf("charset=", ignoreCase = true)
                    charsetStr = if (idx > -1) {
                        content.substring(idx + "charset=".length)
                    } else {
                        content.substringAfter(";")
                    }
                    if (!StringUtils.isEmpty(charsetStr)) {
                        return charsetStr
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return getEncode(bytes)
    }

    fun getEncode(bytes: ByteArray): String {
        // TODO(batch 后续): 移植 lib/icu4j 统计编码检测(剥离 android.os/android.system 耦合),
        //   或换 com.ibm.icu:icu4j Maven 依赖。暂回退 UTF-8。
        return "UTF-8"
    }

    fun getEncode(filePath: String): String {
        return getEncode(File(filePath))
    }

    fun getEncode(file: File): String {
        val tempByte = getFileBytes(file)
        if (tempByte.isEmpty()) {
            return "UTF-8"
        }
        return getEncode(tempByte)
    }

    private fun getFileBytes(file: File): ByteArray {
        val byteArray = ByteArray(8000)
        var pos = 0
        try {
            file.inputStream().buffered().use {
                while (pos < byteArray.size) {
                    val n = it.read(byteArray, pos, 1)
                    if (n == -1) {
                        break
                    }
                    if (byteArray[pos] < 0) {
                        pos++
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Error: $e")
        }
        return byteArray.copyOf(pos)
    }
}

private fun ByteArray.indexOf(pattern: ByteArray, start: Int = 0): Int {
    if (pattern.isEmpty()) return 0
    outer@ for (i in start..size - pattern.size) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        return i
    }
    return -1
}