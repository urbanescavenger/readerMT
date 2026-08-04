package io.legado.app.parity

import io.legado.app.platform.Platform
import io.legado.app.platform.js.CompiledScript
import io.legado.app.platform.js.DirectRhinoEngine
import io.legado.app.platform.js.RhinoEngine
import io.legado.app.platform.js.ScriptBindings
import io.legado.app.platform.repo.NoOpCacheRepository
import io.legado.app.platform.repo.NoOpCookieRepository
import io.legado.app.platform.repo.Repositories
import io.legado.app.platform.webbook.NoOpWebBookProvider
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * 直接单元测 [DirectRhinoEngine] —— 引擎 §3.3 JS 引擎 seam 的 SPI 实现。
 *
 * 不经 AnalyzeRule,直接驱动 `Platform.rhino` 的 eval/compile/bindings/getRuntimeScope/
 * getOrCreateSharedScope/currentCoroutineContext,验证直连 rhino 的语义对齐 app
 * `com.script.rhino.RhinoScriptEngine` + `SharedJsScope`。纯 JVM,无 Android。
 *
 * 共享作用域测试用 `eval(js, scope as Any)` 强制走 `eval(js, scope: Any)` 重载
 * (不经 `getRuntimeScope` 覆盖 prototypeScope),镜像 AnalyzeRule 共享作用域分支
 * `scope = bindings.apply{prototypeScope = sharedScope}; script.eval(scope, ctx)`。
 */
class DirectRhinoEngineTest {

    private val engine: RhinoEngine get() = DirectRhinoEngine

    @Before
    fun setUp() {
        Platform.rhino = DirectRhinoEngine
        Repositories.cookie = NoOpCookieRepository
        Repositories.cache = NoOpCacheRepository
        Platform.repositories = Repositories
        Platform.webBook = NoOpWebBookProvider
        Platform.isMainThread = { false }
        engine.removeSharedScope("k")
        engine.removeSharedScope("k2")
        engine.removeSharedScope("k3")
        engine.removeSharedScope("kp")
    }

    @Test
    fun evalArithmetic() {
        val result = engine.eval("1 + 2", engine.newBindings())
        assertNotNull(result)
        assertEquals(3, (result as Number).toInt())
    }

    @Test
    fun bindingsAccess() {
        val bindings = engine.newBindings()
        bindings["a"] = 5
        val result = engine.eval("a * 2", bindings)
        assertEquals(10, (result as Number).toInt())
    }

    @Test
    fun compileAndEval() {
        val compiled: CompiledScript = engine.compile("a * 2")
        val bindings = engine.newBindings()
        bindings["a"] = 5
        val scope = engine.getRuntimeScope(bindings)
        val result = compiled.eval(scope)
        assertEquals(10, (result as Number).toInt())
    }

    @Test
    fun javaObjectDispatch() {
        // Java String 经 javaToJS 包装后参与 JS 字符串拼接(验证 Java 对象绑定 + 方法/操作符分发)
        val bindings = engine.newBindings()
        bindings["s"] = "书名"
        val result = engine.eval("s + '!'", bindings)
        assertEquals("书名!", result)
    }

    @Test
    fun sharedScopeRawJs() {
        val src = "function f(x){return 'F:' + x}"
        val scope1 = engine.getOrCreateSharedScope("k", src)
        assertNotNull(scope1)
        // 子 eval:bindings.prototypeScope = sharedScope → 原型链找到 f;走 scope 重载(不经 getRuntimeScope)
        val bindings = engine.newBindings()
        bindings.prototypeScope = scope1
        assertEquals("F:hi", engine.eval("f('hi')", bindings as Any))

        // 缓存命中:同 srcKey 返回同一 scope
        val scope2 = engine.getOrCreateSharedScope("k", src)
        assertSame(scope1, scope2)

        // removeSharedScope 后重建 → 新 scope
        engine.removeSharedScope("k")
        val scope3 = engine.getOrCreateSharedScope("k", src)
        assertNotSame(scope1, scope3)
    }

    @Test
    fun sharedScopeBlankReturnsNull() {
        assertNull(engine.getOrCreateSharedScope("k2", null))
        assertNull(engine.getOrCreateSharedScope("k2", ""))
        assertNull(engine.getOrCreateSharedScope("k2", "   "))
    }

    @Test(expected = UnsupportedOperationException::class)
    fun sharedScopeJsonMapThrows() {
        // JSON-map jsLib(name→URL,需下载)本批未实现 → 明确异常
        engine.getOrCreateSharedScope("k3", "{\"a\":\"http://x\"}")
    }

    @Test
    fun currentCoroutineContextDuringEval() {
        // probe 是普通 Java 对象,其 record() 读 Platform.rhino.currentCoroutineContext()(ThreadLocal)
        val probe = ParityProbe()
        val bindings = engine.newBindings()
        bindings["probe"] = probe
        val ctx: CoroutineContext = EmptyCoroutineContext
        // eval(js, scope, ctx) 期间 ThreadLocal=ctx;JS 内 probe.record() 读到它
        engine.eval("probe.record()", bindings as Any, ctx)
        assertEquals(ctx, probe.capturedCtx)
        // eval 外 ThreadLocal 已清
        assertNull(engine.currentCoroutineContext())
    }
}

/** 顶层探针(供 rhino 反射可靠访问;私有嵌套类可能被 reflection 拒)。 */
class ParityProbe {
    @Volatile
    var capturedCtx: CoroutineContext? = null
    @Suppress("unused") // 由 JS 反射调用
    fun record() {
        capturedCtx = Platform.rhino.currentCoroutineContext()
    }
}