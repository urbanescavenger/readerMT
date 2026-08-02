package io.legado.app.platform

/**
 * 应用配置 SPI(计划 §3.5):提供无头浏览器 API、缓存路径等运行期配置。
 *
 * - Android `:app`:`SharedPreferencesAppConfigProvider`(背靠 `lib/prefs`);
 * - `:server`:`SpringAppConfigProvider`(背靠 reader-mt `com.htmake.reader.config.AppConfig`)。
 *
 * 字段为 Phase 1a 占位,Phase 1c 按引擎真实读取项校准增补。
 */
interface AppConfigProvider {
    /** browserless 服务地址(服务端无头渲染用);空表示未配置。 */
    val remoteWebViewApi: String

    /** browserless 访问令牌(可空)。 */
    val remoteWebViewToken: String

    /** 缓存目录路径。 */
    val cachePath: String
}