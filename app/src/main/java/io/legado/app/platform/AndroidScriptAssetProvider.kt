package io.legado.app.platform

import android.content.Context
import io.legado.app.platform.ScriptAssetProvider
import java.io.InputStream

/**
 * Android `:app` 侧对引擎 [ScriptAssetProvider] 的实现(Phase 1c switchover)。
 *
 * 引擎 `JsExtensions.importScript`/`getScriptAsset` 读 JS 库脚本资源经此接口;
 * app 直接读 APK `assets`。
 */
class AndroidScriptAssetProvider(private val ctx: Context) : ScriptAssetProvider {
    override fun readScript(name: String): String =
        ctx.assets.open(name).bufferedReader().use { it.readText() }

    override fun openScript(name: String): InputStream = ctx.assets.open(name)
}
