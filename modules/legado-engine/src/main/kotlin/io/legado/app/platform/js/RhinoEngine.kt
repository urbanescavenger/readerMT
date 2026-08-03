package io.legado.app.platform.js

import kotlin.coroutines.CoroutineContext

/**
 * JS 引擎 SPI(计划 §3.3,最高风险 seam)。
 *
 * 统一用 `org.mozilla.javascript:rhino` 直连,两端共用,弃 rhino-android(`com.script.*`)。
 * 本接口镜像原 `com.script.*` 的执行模型,便于 Phase 1c 把 `import com.script.*` 站点逐个替换:
 * - **共享作用域**:`getOrCreateSharedScope(srcKey, initJs)` 按源键缓存一个共享 scope(编译
 *   该源的 init JS 一次),后续 eval 经原型链复用——对应 rhino-android 的 SharedJsScope。
 *   `initJs` 为空时返回 null(无共享作用域)。完整语义(JSON-URL jsLib 下载+磁盘缓存+
 *   preventExtensions)由实现端提供,parity 验留 §5c。
 * - **原型绑定**:[ScriptBindings.prototype] 可指向共享 scope,形成原型链(`bindings.prototype = sharedScope`)。
 * - **运行作用域**:`getRuntimeScope(bindings)` 由 bindings 构造 eval 用的 scope(无共享作用域时)。
 * - **eval**:`eval(js, scope)` 在给定 scope 执行;`eval(js, bindings)` 便捷重载;
 *   `eval(js, scope, coroutineContext)` 带 cancellation hook(AnalyzeRule/AnalyzeUrl evalJS 传 ctx)。
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

    /** 带 [coroutineContext] 的 eval(供 `ensureActive` cancellation;实现端按需接入)。 */
    fun eval(js: String, scope: Any, coroutineContext: CoroutineContext): Any?

    fun getRuntimeScope(bindings: ScriptBindings): Any

    /**
     * 按源键 [srcKey] 缓存一个共享 scope,编译 [initJs](源的 init JS)一次,后续经原型链复用。
     * 对应 rhino-android 的 `SharedJsScope.getScope`。[initJs] 为空/blank 时返回 null。
     * 完整语义(JSON-URL jsLib 下载+磁盘缓存+preventExtensions)由实现端提供,parity 验留 §5c。
     */
    fun getOrCreateSharedScope(srcKey: String, initJs: String?): Any?

    /** 驱逐 [srcKey] 的共享 scope 缓存(对应 `SharedJsScope.remove`;`BaseSource.refreshJSLib` 用)。 */
    fun removeSharedScope(srcKey: String?)
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

    /** 带 [coroutineContext] 的 eval(cancellation hook;AnalyzeRule scriptCache 用)。 */
    fun eval(scope: Any, coroutineContext: CoroutineContext): Any?
}