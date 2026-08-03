package io.legado.app.parity

import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule

/**
 * §5c parity 测试用的 BookSource 承载体(测试侧,非引擎 `BookSource` interface)。
 *
 * 引擎 `data/entities/BookSource` 仅是空 interface(Phase 1b 实体迁移尚未推进);
 * 纯 JSoup/JsonPath 规则路径 `AnalyzeRule(ruleData=null, source=null)` 不触碰
 * `BaseSource` 任何成员(见勘探报告)。故测试用一个独立 data class 持有 JSON 反序列化
 * 出来的字段,驱动 `AnalyzeRule` 解析,不实现 `BaseSource`。
 *
 * 字段命名与 app `BookSource` JSON 一致,`GSON`(`utils.GsonExtensions.GSON`,
 * 已注册各规则 `jsonDeserializer`)默认反射反序列化 scalar 字段,`ruleSearch` 等走
 * 已注册 deserializer。后续 Phase 1b 真实 `BookSource` data class 进引擎后,本类可被
 * 其替换(driver 只读 `ruleSearch` 等字段)。
 */
data class BookSourceFixture(
    var bookSourceUrl: String? = null,
    var bookSourceName: String? = null,
    var bookSourceType: Int? = null,
    var bookUrlPattern: String? = null,
    var customOrder: Int? = null,
    var searchUrl: String? = null,
    var header: String? = null,
    var loginUrl: String? = null,
    var loginUi: String? = null,
    var loginCheckJs: String? = null,
    var jsLib: String? = null,
    var concurrentRate: String? = null,
    var enabledCookieJar: Boolean? = null,
    var ruleSearch: SearchRule? = null,
    var ruleBookInfo: BookInfoRule? = null,
    var ruleToc: TocRule? = null,
    var ruleContent: ContentRule? = null,
    var ruleExplore: ExploreRule? = null
) {
    /** 搜索规则(缺省空 SearchRule,避免 NPE)。 */
    fun searchRule(): SearchRule = ruleSearch ?: SearchRule()
}