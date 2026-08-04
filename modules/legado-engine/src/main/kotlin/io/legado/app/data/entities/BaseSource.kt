@file:Suppress("unused")

package io.legado.app.data.entities

import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.help.CacheManager
import io.legado.app.help.ConcurrentRateLimiter.Companion.updateConcurrentRate
import io.legado.app.help.JsExtensions
import io.legado.app.help.http.CookieStore
import io.legado.app.platform.Platform
import io.legado.app.platform.js.ScriptBindings
import io.legado.app.utils.GSON
import io.legado.app.utils.GSONStrict
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.has
import kotlinx.coroutines.runBlocking

/**
 * 引擎版 BaseSource(从 readerMT `data/entities/BaseSource.kt` 移植为平台无关 interface)。
 *
 * `interface BaseSource : JsExtensions`(引擎),与 app 同名同包不同模块(switchover 前 app 用自家
 * BaseSource,无 classpath 冲突)。实现端:`:app` 的 `BookSource`/`RssSource`/`HttpTTS`、
 * `:server` 的对应类,实现本接口。
 *
 * **可移植主体**(默认方法,去 `@JavascriptInterface`):`getKey`/`getTag`(抽象)、`getSource`、
 * `getLoginJs`、`login`、`getHeaderMap`、`getLoginHeader`/`getLoginHeaderMap`/`putLoginHeader`/
 * `removeLoginHeader`、`setVariable`/`putVariable`/`getVariable`、`put`/`get`、`removeLoginInfo`、
 * `refreshJSLib`(`Platform.rhino.removeSharedScope`)、`putConcurrent`、`evalJS`(`Platform.rhino`)。
 * UA 头走 `Platform.appConfig.userAgent`(替 `AppConfig.userAgent`);UA 头键名 `AppConst.UA_NAME`。
 *
 * **app-only(留 `open fun` 引擎默认,由 :app 实现类 override)**:
 * - `getLoginInfo`/`putLoginInfo`:用 `AppConst.androidId`(Android 设备 id)作 AES 密钥,app-only;
 *   引擎默认返回 null/false。`:app` 的 `BookSource` override 用 androidId+`SymmetricCryptoAndroid`。
 * - `getLoginInfoMap`:解析 `loginUi` 为 `RowUi`(app-only UI 描述);引擎默认返回空 map。
 * - `refreshExplore`:`is BookSource` + `clearExploreKindsCache`(app-only);引擎默认仅线程检查。
 *
 * **不强制实体层**:`BookSource`/`RssSource`/`HttpTTS` 留 `:app` 实现本接口;引擎 BaseSource 仅
 * `refreshExplore` 内对 `BookSource` 的引用已去掉(改为 open fun 让 app override)。
 *
 * com.script.* → `Platform.rhino.*`(`newBindings`/`getRuntimeScope`/`eval`/`getOrCreateSharedScope`/
 * `removeSharedScope`)。`getShareScope()` → `Platform.rhino.getOrCreateSharedScope(getKey(), jsLib)`。
 */
interface BaseSource : JsExtensions {

    /** 并发率 */
    var concurrentRate: String?

    /** 登录地址 */
    var loginUrl: String?

    /** 登录UI */
    var loginUi: String?

    /** 请求头 */
    var header: String?

    /** 启用cookieJar */
    var enabledCookieJar: Boolean?

    /** js库 */
    var jsLib: String?

    override fun getTag(): String

    fun getKey(): String

    override fun getSource(): BaseSource? {
        return this
    }

    fun getLoginJs(): String? {
        val loginJs = loginUrl
        return when {
            loginJs == null -> null
            loginJs.startsWith("@js:") -> loginJs.substring(4)
            loginJs.startsWith("<js>") -> loginJs.substring(4, loginJs.lastIndexOf("<"))
            else -> loginJs
        }
    }

    /**
     * 调用login函数 实现登录请求(逻辑可移植;登录 UI 驱动由 :app 调用本方法)。
     */
    fun login() {
        val loginJs = getLoginJs()
        if (!loginJs.isNullOrBlank()) {
            val js = """$loginJs
                if(typeof login=='function'){
                    login.apply(this);
                } else {
                    throw('Function login not implements!!!')
                }
            """.trimIndent()
            evalJS(js)
        }
    }

    /**
     * 解析header规则
     */
    fun getHeaderMap(hasLoginHeader: Boolean = false) = HashMap<String, String>().apply {
        header?.let {
            try {
                val json = when {
                    it.startsWith("@js:", true) -> evalJS(it.substring(4)).toString()
                    it.startsWith("<js>", true) -> evalJS(
                        it.substring(4, it.lastIndexOf("<"))
                    ).toString()

                    else -> it
                }
                GSONStrict.fromJsonObject<Map<String, String>>(json).getOrNull()?.let { map ->
                    putAll(map)
                } ?: GSON.fromJsonObject<Map<String, String>>(json).getOrNull()?.let { map ->
                    AppLog.put("请求头规则 JSON 格式不规范，请改为规范格式")
                    putAll(map)
                }
            } catch (e: Exception) {
                AppLog.put("执行请求头规则出错\n$e", e)
            }
        }
        if (!has(AppConst.UA_NAME, true)) {
            put(AppConst.UA_NAME, Platform.appConfig.userAgent)
        }
        if (hasLoginHeader) {
            getLoginHeaderMap()?.let {
                putAll(it)
            }
        }
    }

    /**
     * 获取用于登录的头部信息
     */
    fun getLoginHeader(): String? {
        return CacheManager.get("loginHeader_${getKey()}")
    }

    fun getLoginHeaderMap(): Map<String, String>? {
        val cache = getLoginHeader() ?: return null
        return GSON.fromJsonObject<Map<String, String>>(cache).getOrNull()
    }

    /**
     * 保存登录头部信息,map格式,访问时自动添加
     */
    fun putLoginHeader(header: String) {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(header).getOrNull()
        val cookie = headerMap?.get("Cookie") ?: headerMap?.get("cookie")
        cookie?.let {
            CookieStore.replaceCookie(getKey(), it)
        }
        CacheManager.put("loginHeader_${getKey()}", header)
    }

    fun removeLoginHeader() {
        CacheManager.delete("loginHeader_${getKey()}")
        CookieStore.removeCookie(getKey())
    }

    /**
     * 获取用户信息,可以用来登录(aes 加密存储,**app-only**:AES 密钥用 `AppConst.androidId`)。
     * 引擎默认返回 null;`:app` 实现类 override 用 androidId + AES 解密。
     */
    open fun getLoginInfo(): String? = null

    /**
     * 解析 loginUi 为登录字段 map(**app-only**:依赖 `RowUi` UI 描述 + getLoginInfo/putLoginInfo)。
     * 引擎默认返回空 map;`:app` 实现类 override 复刻原逻辑。
     */
    open fun getLoginInfoMap(): MutableMap<String, String> = mutableMapOf()

    /**
     * 保存用户信息(aes 加密,**app-only**:AES 密钥用 `AppConst.androidId`)。
     * 引擎默认返回 false;`:app` 实现类 override 用 androidId + `SymmetricCryptoAndroid`。
     */
    open fun putLoginInfo(info: String): Boolean = false

    fun removeLoginInfo() {
        CacheManager.delete("userInfo_${getKey()}")
    }

    /**
     * 设置自定义变量
     */
    fun setVariable(variable: String?) {
        if (variable != null) {
            CacheManager.put("sourceVariable_${getKey()}", variable)
        } else {
            CacheManager.delete("sourceVariable_${getKey()}")
        }
    }

    /**
     * 设置自定义变量(新,统一为put名称存变量)
     */
    fun putVariable(variable: String?) {
        if (variable != null) {
            CacheManager.put("sourceVariable_${getKey()}", variable)
        } else {
            CacheManager.delete("sourceVariable_${getKey()}")
        }
    }

    /**
     * 获取自定义变量
     */
    fun getVariable(): String {
        return CacheManager.get("sourceVariable_${getKey()}") ?: ""
    }

    /**
     * 保存数据
     */
    fun put(key: String, value: String): String {
        CacheManager.put("v_${getKey()}_${key}", value)
        return value
    }

    /**
     * 获取保存的数据
     */
    fun get(key: String): String {
        return CacheManager.get("v_${getKey()}_${key}") ?: ""
    }

    /**
     * 刷新发现(**app-only**:`is BookSource` + `clearExploreKindsCache`)。
     * 引擎默认仅做线程检查(可移植);`:app` 实现类 override 追加 `clearExploreKindsCache`。
     */
    open fun refreshExplore() {
        if (Platform.isMainThread()) {
            error("refreshExplore must be called on a background thread")
        }
    }

    /**
     * 刷新JSLib(可移植:`Platform.rhino.removeSharedScope` 替 `SharedJsScope.remove`)。
     */
    fun refreshJSLib() {
        if (Platform.isMainThread()) {
            error("refreshJSLib must be called on a background thread")
        }
        runBlocking {
            Platform.rhino.removeSharedScope(jsLib)
        }
    }

    /**
     * 设置并发率
     */
    fun putConcurrent(value: String) {
        updateConcurrentRate(getKey(), value)
    }

    /**
     * 执行JS(`com.script.*` → `Platform.rhino.*`;`getShareScope` →
     * `Platform.rhino.getOrCreateSharedScope(getKey(), jsLib)`)。
     */
    @Throws(Exception::class)
    fun evalJS(jsStr: String, bindingsConfig: ScriptBindings.() -> Unit = {}): Any? {
        val bindings = Platform.rhino.newBindings()
        bindings["java"] = this
        bindings["source"] = this
        bindings["baseUrl"] = getKey()
        bindings["cookie"] = CookieStore
        bindings["cache"] = CacheManager
        bindingsConfig.invoke(bindings)
        val sharedScope = Platform.rhino.getOrCreateSharedScope(getKey(), jsLib)
        val scope = if (sharedScope == null) {
            Platform.rhino.getRuntimeScope(bindings)
        } else {
            bindings.prototypeScope = sharedScope
            bindings
        }
        return Platform.rhino.eval(jsStr, scope)
    }
}