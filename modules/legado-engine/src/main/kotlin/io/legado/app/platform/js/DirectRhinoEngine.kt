@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.legado.app.platform.js

import io.legado.app.help.LruCache
import io.legado.app.utils.isJsonObject
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Script
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.io.StringReader
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

/**
 * 引擎自带 [RhinoEngine] 实现:直连 `org.mozilla.javascript`(rhino),纯 JVM,无 Android。
 *
 * 作为 app `RhinoAndroidEngine`(`com.script.*`,Android 库 `:modules:rhino`)的对等替代,
 * 是 Phase 1c switchover 把 app 默认 JS 引擎切到直连 rhino 的前置;也是服务端
 * `:server` 的 JS 引擎。语义对齐 app 的 `com.script.rhino.RhinoScriptEngine` +
 * app 侧 `io.legado.app.model.SharedJsScope`(共享作用域),parity 验留 §5c。
 *
 * **设计要点**(勘探自 `:modules:rhino` 与 app `SharedJsScope.kt`):
 * - **ContextFactory**:[EngineContextFactory] 设 `VERSION_ES6` + `setInterpretedMode(true)`
 *   (解释器模式,避免 JVM 字节码生成/类加载器问题)。镜像 app:`ContextFactory.initGlobal(factory)`
 *   + `Context.enter()`/`Context.exit()`(此 rhino 版本无 `Context.enter(ContextFactory)` 重载)。
 *   `initGlobal` 是 JVM 全局 —— 引擎独立 JVM(test/server)下无冲突;app 共存场景(Phase 1c)
 *  届时 app 弃用 rhino-android,亦无冲突。`runCatching` 兜底:全局已被设则沿用(不致命)。
 * - **不**设 ClassShutter/WrapFactory(加固,deferred;书源 JS parity 不依赖)。
 * - **共享作用域**:`getOrCreateSharedScope(srcKey, initJs)` 按 srcKey 缓存一个 `NativeObject`
 *   scope(`prototype = cx.initStandardObjects()`),把 `initJs`(裸 JS 源)`eval` 注入后
 *   `preventExtensions()`,对应 app `SharedJsScope.getScope`。子 eval 不调 `getRuntimeScope`,
 *   而是 `bindings.prototypeScope = sharedScope`(链:bindings→sharedScope→standardGlobal)。
 * - **JSON-map jsLib**(`initJs` 是 name→URL JSON):app 下载+ACache 磁盘缓存;本批**暂不实现**,
 *   抛 [UnsupportedOperationException] 作清晰失败信号(真实源需要时再补 OkHttp+`Platform.context.cacheDir`)。
 * - **cancellation**:本批只接 [ctxHolder] ThreadLocal([currentCoroutineContext] 供 JsExtensions
 *   HTTP/WebView 做 `ensureActive`/`runBlocking`);mid-eval 指令级 cancel
 *   (`instructionObserverThreshold`+`observeInstructionCount`+`ensureActive`)留后续(配合 §5c 压测)。
 * - **结果 unwrap**:[unwrap] 镜像 app `unwrapReturnValue`(Wrapper→unwrap、ConsString→toString、Undefined→null)。
 */
object DirectRhinoEngine : RhinoEngine {

    /** 私有 ContextFactory(ES6 + 解释器模式)。 */
    private class EngineContextFactory : ContextFactory() {
        override fun makeContext(): Context {
            val cx = super.makeContext()
            cx.languageVersion = Context.VERSION_ES6
            cx.setInterpretedMode(true)
            // 加固(ClassShutter/WrapFactory)与 instructionObserverThreshold/maxInterpreterStackDepth
            // deferred —— 书源 JS parity 不依赖;服务器对不可信源前需补回。
            return cx
        }
    }

    private val factory = EngineContextFactory()

    init {
        // 镜像 app RhinoScriptEngine:initGlobal 设全局 factory。已设则沿用(runCatching 兜底)。
        runCatching { ContextFactory.initGlobal(factory) }
    }

    /** 共享 standardGlobal(懒建,供 DirectScriptBindings 默认原型)。 */
    private val standardGlobal: Scriptable by lazy {
        val cx = Context.enter()
        try {
            cx.initStandardObjects()
        } finally {
            Context.exit()
        }
    }

    /** 当前 JS 执行的协程上下文(ThreadLocal;[currentCoroutineContext] 读;eval/+ctx 期间 set)。 */
    private val ctxHolder = ThreadLocal<CoroutineContext?>()

    /** 共享作用域缓存:srcKey → WeakReference<scope>。对应 app `SharedJsScope.scopeMap`(LruCache 16)。 */
    private val scopeMap = LruCache<String, WeakReference<Scriptable>>(16)

    // ---------- RhinoEngine SPI ----------

    override fun newBindings(): ScriptBindings = DirectScriptBindings()

    override fun getRuntimeScope(bindings: ScriptBindings): Any {
        val cx = Context.enter()
        try {
            bindings.prototypeScope = cx.initStandardObjects()
            return bindings
        } finally {
            Context.exit()
        }
    }

    override fun eval(js: String, bindings: ScriptBindings): Any? =
        eval(js, getRuntimeScope(bindings))

    override fun eval(js: String, scope: Any): Any? {
        val cx = Context.enter()
        try {
            val r = cx.evaluateReader(scope as Scriptable, StringReader(js), "<eval>", 1, null)
            return unwrap(r)
        } finally {
            Context.exit()
        }
    }

    override fun eval(js: String, scope: Any, coroutineContext: CoroutineContext): Any? {
        ctxHolder.set(coroutineContext)
        try {
            return eval(js, scope)
        } finally {
            ctxHolder.remove()
        }
    }

    override fun compile(js: String): CompiledScript {
        val cx = Context.enter()
        try {
            val scr = cx.compileReader(StringReader(js), "<compile>", 1, null)
            return DirectCompiledScript(scr)
        } finally {
            Context.exit()
        }
    }

    override fun currentCoroutineContext(): CoroutineContext? = ctxHolder.get()

    override fun getOrCreateSharedScope(srcKey: String, initJs: String?): Any? {
        if (initJs.isNullOrBlank()) return null
        // JSON-map jsLib(name→URL,需下载)暂未实现,清晰失败信号。
        if (initJs.isJsonObject()) {
            throw UnsupportedOperationException(
                "JSON-map jsLib 下载未实现:本批仅支持裸 JS jsLib;真实源需要时再补 OkHttp+磁盘缓存"
            )
        }
        scopeMap[srcKey]?.get()?.let { return it }
        val scope = DirectScriptBindings()
        val cx = Context.enter()
        try {
            scope.prototypeScope = cx.initStandardObjects()
            cx.evaluateReader(scope, StringReader(initJs), "<jsLib:$srcKey>", 1, null)
            scope.preventExtensions()
        } finally {
            Context.exit()
        }
        scopeMap.put(srcKey, WeakReference(scope))
        return scope
    }

    override fun getOrCreateSharedScope(
        srcKey: String,
        initJs: String?,
        coroutineContext: CoroutineContext
    ): Any? {
        ctxHolder.set(coroutineContext)
        try {
            return getOrCreateSharedScope(srcKey, initJs)
        } finally {
            ctxHolder.remove()
        }
    }

    override fun removeSharedScope(srcKey: String?) {
        if (srcKey.isNullOrEmpty()) return
        scopeMap.remove(srcKey)
    }

    // ---------- 内部:结果 unwrap ----------

    /** 镜像 app `RhinoScriptEngine.unwrapReturnValue`。 */
    private fun unwrap(result: Any?): Any? {
        var r = result
        if (r is Wrapper) r = r.unwrap()
        if (r is ConsString) r = r.toString()
        return if (r is Undefined) null else r
    }

    // ---------- 内部:ScriptBindings 实现(NativeObject,可直接作 Scriptable scope) ----------

    /**
     * [ScriptBindings] 实现:`class : NativeObject()`,可直接作为 eval 的 scope(对应 app
     * `com.script.ScriptBindings : NativeObject()`)。AnalyzeRule 共享作用域分支 `scope = bindings`
     * 要求 bindings 即 Scriptable,故必须继承 NativeObject 而非组合。
     *
     * [prototypeScope] 委托 `super.setPrototype/getPrototype`(命名避开 NativeObject 的
     * `prototype: Scriptable` 返回类型 widening override 冲突)。声明于 init 之前以避免
     * "Variable cannot be initialized before declaration"。
     */
    private class DirectScriptBindings : NativeObject(), ScriptBindings {
        override var prototypeScope: Any?
            get() = super.getPrototype()
            set(value) { super.setPrototype(value as? Scriptable) }

        init {
            // 默认原型 = 懒建共享 standardGlobal(对应 app ScriptBindings 的 topLevelScope);
            // getRuntimeScope/getOrCreateSharedScope 会覆盖为各自的 standardGlobal/sharedScope。
            prototypeScope = standardGlobal
        }

        override operator fun set(key: String, value: Any?) {
            val cx = Context.enter()
            try {
                put(key, this, Context.javaToJS(value, this))
            } finally {
                Context.exit()
            }
        }

        // ScriptableObject 无单参 get(String);显式实现接口,委托 2 参 get(key, start)。
        override operator fun get(key: String): Any? = get(key, this)

        // putAll 不在 SPI(NativeObject 继承的 putAll(Map) 与 SPI 的 Map<String,Any?> 同 JVM
        // 签名冲突,且引擎调用方只用 set 运算符,故 SPI 不声明 putAll)。
    }

    // ---------- 内部:CompiledScript 实现 ----------

    private class DirectCompiledScript(private val script: Script) : CompiledScript {
        override fun eval(bindings: ScriptBindings): Any? =
            eval(getRuntimeScope(bindings))

        override fun eval(scope: Any): Any? {
            val cx = Context.enter()
            try {
                return unwrap(script.exec(cx, scope as Scriptable))
            } finally {
                Context.exit()
            }
        }

        override fun eval(scope: Any, coroutineContext: CoroutineContext): Any? {
            ctxHolder.set(coroutineContext)
            try {
                return eval(scope)
            } finally {
                ctxHolder.remove()
            }
        }
    }
}