package io.legado.app.help

/**
 * 引擎版 JsEncodeUtils(书源 JS 加解密 API 表面,计划 §3.3 / §9.4)。
 *
 * 对应 readerMT `app/.../help/JsEncodeUtils.kt` 的 `interface JsEncodeUtils`:
 * 纯加密方法(md5/AES/DES/3DES/digest/HMac/createSymmetricCrypto/
 * createAsymmetricCrypto/createSign),均为 `@JavascriptInterface` 默认方法。
 *
 * **本接口当前为空**——加密簇(~500 行)依赖 android crypto 助手
 * (`help.crypto.{AsymmetricCrypto,Sign,SymmetricCryptoAndroid}`、`MD5Utils`、
 * android.util.Base64),需在 §9.5 big-bang 时连同 hutool-crypto 移植,故暂留空壳。
 * 引擎内 `interface JsExtensions : JsEncodeUtils` 已可据此编译,继承骨架就位。
 *
 * 与 app 的差异(待 big-bang 落地时遵守):
 * - 去 `@JavascriptInterface`(android.webkit JS 桥注解;引擎用 RhinoEngine 绑定,不需要)。
 * - android crypto 助手 → 引擎纯 JVM 实现(经 Platform SPI 或 hutool-crypto 直连,parity 敏感)。
 */
interface JsEncodeUtils