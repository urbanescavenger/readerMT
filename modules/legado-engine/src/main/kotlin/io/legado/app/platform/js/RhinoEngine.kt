package io.legado.app.platform.js

/**
 * JS 引擎 SPI(计划 §3.3,最高风险 seam)。
 *
 * 统一用 `org.mozilla.javascript:rhino` 直连,两端共用,弃 rhino-android(`com.script.*`)。
 * 本接口及其伴生 [ScriptBindings]/[CompiledScript] 镜像原 `com.script.*` API 表面,
 * 便于 Phase 1c 把 `import com.script.*` 站点逐个替换为此 SPI:
 * - Android `:app` 的 `RhinoAndroidEngine` 实现:内部委托 rhino-android(过渡期默认,可回滚);
 * - 服务端 `:server` 的 `DirectRhinoEngine` 实现:直连 `org.mozilla.javascript`。
 *
 * 仅当 §5c parity 测试在两端 50+ 真实书源通过后才切 `DirectRhinoEngine` 为 Android 默认。
 *
 * 签名为 Phase 1a 占位,Phase 1c 按 `AnalyzeRule` 真实调用点校准。
 */
interface RhinoEngine {
    fun newBindings(): ScriptBindings
    fun compile(js: String): CompiledScript
    fun eval(js: String, bindings: ScriptBindings): Any?
    fun getRuntimeScope(bindings: ScriptBindings): Any
}

interface ScriptBindings {
    operator fun set(key: String, value: Any?)
    operator fun get(key: String): Any?
    fun putAll(map: Map<String, Any?>)
}

interface CompiledScript {
    fun eval(bindings: ScriptBindings): Any?
}