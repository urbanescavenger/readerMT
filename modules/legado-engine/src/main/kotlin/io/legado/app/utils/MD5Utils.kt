package io.legado.app.utils

import cn.hutool.crypto.digest.DigestUtil
import cn.hutool.crypto.digest.Digester
import java.io.InputStream
import kotlin.concurrent.getOrSet

/**
 * 将字符串转化为 MD5(从 readerMT `utils/MD5Utils.kt` 移植,纯 JVM,无 Android 依赖)。
 * ThreadLocal 缓存 Digester 复用。
 */
@Suppress("unused")
object MD5Utils {

    private val threadLocal = ThreadLocal<Digester>()

    private val MD5Digester
        get() = threadLocal.getOrSet {
            DigestUtil.digester("MD5")
        }

    fun md5Encode(str: String?): String {
        return MD5Digester.digestHex(str)
    }

    fun md5Encode(inputStream: InputStream): String {
        return MD5Digester.digestHex(inputStream)
    }

    fun md5Encode16(str: String): String {
        var reStr = md5Encode(str)
        reStr = reStr.substring(8, 24)
        return reStr
    }
}