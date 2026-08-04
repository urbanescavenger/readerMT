package com.htmake.reader.platform

import io.legado.app.platform.ScriptAssetProvider
import org.springframework.stereotype.Component
import java.io.InputStream

/**
 * `:server` 的 [ScriptAssetProvider] 实现:读 classpath 资源。
 *
 * Android `:app` 读 `assets/`;服务端无 assets,脚本资源(jslib 等)放 classpath
 * (`src/main/resources/`)。M1 阶段书源 jsLib 多为 URL 下载(引擎 `getOrCreateSharedScope`
 * 对 JSON-map jsLib 暂抛 UnsupportedOperationException),此实现仅兜底 classpath 资源。
 */
@Component
class ServerScriptAssetProvider : ScriptAssetProvider {
    override fun readScript(name: String): String {
        return javaClass.classLoader.getResource(name)
            ?.readText(Charsets.UTF_8)
            ?: throw IllegalArgumentException("script asset not found on classpath: $name")
    }

    override fun openScript(name: String): InputStream {
        return javaClass.classLoader.getResourceAsStream(name)
            ?: throw IllegalArgumentException("script asset not found on classpath: $name")
    }
}
