package io.legado.app.platform

import java.io.InputStream

/**
 * JS 脚本资源解析 SPI(计划 §3.3)。
 *
 * rhino-android 多出的 AssetManager 资源解析抽到引擎:
 * - Android `:app`:读 `AssetManager`(assets);
 * - `:server`:读 classpath。
 */
interface ScriptAssetProvider {
    /** 读取脚本资源为文本。 */
    fun readScript(name: String): String

    /** 读取脚本资源为流。 */
    fun openScript(name: String): InputStream
}