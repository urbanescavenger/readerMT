package io.legado.app.help.crypto

import cn.hutool.core.codec.Base64
import cn.hutool.core.util.HexUtil
import cn.hutool.crypto.symmetric.SymmetricCrypto
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.isHex
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 引擎版 SymmetricCryptoAndroid(从 readerMT `help/crypto/SymmetricCryptoAndroid.kt` 移植)。
 *
 * 继承 hutool `SymmetricCrypto`,重写 `encryptBase64` 走引擎 `EncoderUtils`
 * (java.util.Base64,替 android.util.Base64),`decrypt(String)` 按 hex/base64 自动识别。
 * 原 `@Keep`(androidx)已去;引擎无需 android 注解。
 */
@Suppress("unused")
class SymmetricCryptoAndroid(
    algorithm: String,
    key: ByteArray?,
) : SymmetricCrypto(algorithm, key) {

    override fun encryptBase64(data: ByteArray): String {
        return EncoderUtils.base64Encode(encrypt(data))
    }

    override fun encryptBase64(data: String, charset: String?): String {
        return EncoderUtils.base64Encode(encrypt(data, charset))
    }

    override fun encryptBase64(data: String, charset: Charset?): String {
        return EncoderUtils.base64Encode(encrypt(data, charset))
    }

    override fun encryptBase64(data: String): String {
        return EncoderUtils.base64Encode(encrypt(data))
    }

    override fun encryptBase64(data: InputStream): String {
        return EncoderUtils.base64Encode(encrypt(data))
    }

    override fun decrypt(data: String): ByteArray {
        val bytes = if (data.isHex()) {
            HexUtil.decodeHex(data)
        } else {
            Base64.decode(data)
        }
        return decrypt(bytes)
    }

}