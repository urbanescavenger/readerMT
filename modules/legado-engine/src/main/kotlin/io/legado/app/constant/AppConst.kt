package io.legado.app.constant

/**
 * 引擎版 AppConst(极简子集,从 readerMT `constant/AppConst` 抽取引擎所需常量)。
 *
 * readerMT 的 `AppConst` 含大量 Android 耦合(`androidId`、`BuildConfig`、channel id、authority 等);
 * 引擎只取 `BaseSource.getHeaderMap` 用的 UA 头键名 `UA_NAME`。其余(androidId、APP_TAG 等)
 * 留 `:app`(androidId 是登录信息 AES 密钥,app-only)。
 */
object AppConst {

    /** User-Agent 请求头键名(`BaseSource.getHeaderMap` 默认 UA 头用)。 */
    const val UA_NAME = "User-Agent"
}