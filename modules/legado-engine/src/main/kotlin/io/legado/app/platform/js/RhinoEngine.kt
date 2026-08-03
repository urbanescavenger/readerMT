package io.legado.app.platform.js

/**
 * JS 引擎 SPI(计划 §3.3,最高风险 seam)。
 *
 * 统一用 `org.mozilla.javascript:rhino` 直连,两端共用,弃 rhino-android(`com.script.*`)。
 * 本接口镜像原 `com.script.*` 的执行模型,便于 Phase 1c 把 `import com.script.*` 站点逐个替换:
 * - **共享作用域**:`getOrCreateSharedScope(srcKey, initJs)` 按源键缓存一个共享 scope(编译
 *   该源的 init JS 一次),后续 eval 经原型链复用——对应 rhino-android 的 SharedJsScope。
 * - **原型绑定**:[ScriptBindings.prototype] 可指向共享 scope,形成原型链(`bindings.prototype = sharedScope`)。
 * - **运行作用域**:`getRuntimeScope(bindings)` 由 bindings 构造 eval 用的 scope(无共享作用域时)。
 * - **eval**:`eval(js, scope)` 在给定 scope 执行;`eval(js, bindings)` 便捷重载。
 *
 * 实现端:
 * - Android `:app` 的 `RhinoAndroidEngine`:委托 rhino-android(过渡期默认,可回滚);
 * - 服务端 `:server` 的 `DirectRhinoEngine`:直连 `org.mozilla.javascript`。
 *
 * 仅当 §5c parity 测试在两端 50+ 真实书源通过后才切 `DirectRhinoEngine` 为 Android 默认。
 */
interface RhinoEngine {
    fun newBindings(): ScriptBindings
    fun compile(js: String): CompiledScript
    fun eval(js: String, bindings: ScriptBindings): Any?
    fun eval(js: String, scope: Any): Any?
    fun getRuntimeScope(bindings: ScriptBindings): Any
    fun getOrCreateSharedScope(srcKey: String, initJs: String?): Any
}

interface ScriptBindings {
    operator fun set(key: String, value: Any?)
    operator fun get(key: String): Any?
    fun putAll(map: Map<String, Any?>)
    /** 原型链:可指向共享作用域(`bindings.prototype = sharedScope`)。 */
    var prototype: Any?
}

interface CompiledScript {
    fun eval(bindings: ScriptBindings): Any?
    fun eval(scope: Any): Any?
}