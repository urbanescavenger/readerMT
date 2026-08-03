@file:Suppress("unused")

package io.legado.app.utils

import java.util.Base64

/**
 * 引擎版 EncoderUtils(从 readerMT `utils/EncoderUtils` 移植,去 `android.util.Base64`)。
 *
 * readerMT 用 `android.util.Base64` + flags;引擎用 `java.util.Base64`,按 **android flag 值**
 * (book source JS 传入的 int)映射:DEFAULT=0 / NO_PADDING=1 / NO_WRAP=2 / CRLF=4 / URL_SAFE=8。
 * 常见用法(base64Encode NO_WRAP、base64Decode DEFAULT)与 android 等价;CRLF/NO_CLOSE 等
 * 边缘 flag 近似(getMimeEncoder 用 CRLF,android DEFAULT 用 LF,差异极小)。
 * parity 风险留 §5c fixture 校验。
 */
object EncoderUtils {

    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    fun base64Decode(str: String, flags: Int = DEFAULT): String = String(base64DecodeToByteArray(str, flags))

    fun base64DecodeToByteArray(str: String, flags: Int = DEFAULT): ByteArray {
        val dec = if ((flags and URL_SAFE) != 0) Base64.getUrlDecoder() else Base64.getMimeDecoder()
        return dec.decode(str)
    }

    fun base64Encode(str: String, flags: Int = NO_WRAP): String? = base64Encode(str.toByteArray(), flags)

    fun base64Encode(bytes: ByteArray, flags: Int = NO_WRAP): String {
        val enc = when {
            (flags and URL_SAFE) != 0 -> Base64.getUrlEncoder()
            (flags and NO_WRAP) != 0 -> Base64.getEncoder()
            else -> Base64.getMimeEncoder()
        }
        val e = if ((flags and NO_PADDING) != 0) enc.withoutPadding() else enc
        return e.encodeToString(bytes)
    }
}