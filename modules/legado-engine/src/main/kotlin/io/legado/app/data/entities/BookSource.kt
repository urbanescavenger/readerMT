package io.legado.app.data.entities

import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule

/**
 * 引擎版 BookSource(从 readerMT `data/entities/BookSource.kt` 抽取为平台无关完整 DTO)。
 *
 * `AnalyzeRule.reGetBook`/`refreshTocUrl` 做 `source as? BookSource` 守卫后传给
 * [io.legado.app.platform.webbook.WebBookProvider](仅类型判断,不读专属字段)。
 * 真实书源 JSON 经引擎 [io.legado.app.utils.GSON](已注册 7 个规则 jsonDeserializer)
 * 反序列化进本 data class,驱动 `AnalyzeRule` 跑 §5c parity。
 *
 * **注意(Phase 1c switchover 语义)**:Kotlin `data class` 是 `final`,**不能做 supertype**。
 * Phase 1c 不能"app 的 `BookSource` extend 本类",须改为 **composition/DTO 转换**:
 * 引擎反序列化真实 JSON → 本 DTO;`:app` 保留 Room `@Entity BookSource` 存储,在
 * WebBook/repository 边界做字段拷贝或 GSON round-trip 映射。
 *
 * GSON 对未声明 JSON 字段默认忽略,多余字段不报错;完整字段集使 1c 映射变纯字段拷贝。
 */
data class BookSource(
    // identity — 非 null,使 getTag()/getKey() 可返回 String
    var bookSourceUrl: String = "",
    var bookSourceName: String = "",
    // BaseSource 抽象 override(6 个 nullable var)
    override var concurrentRate: String? = null,
    override var loginUrl: String? = null,
    override var loginUi: String? = null,
    override var header: String? = null,
    override var enabledCookieJar: Boolean? = null,
    override var jsLib: String? = null,
    // 真实书源 JSON 里的元数据字段(app 命名,nullable)
    var bookSourceGroup: String? = null,
    var bookSourceType: Int? = null,
    var bookUrlPattern: String? = null,
    var customOrder: Int? = null,
    var enabled: Boolean? = null,
    var enabledExplore: Boolean? = null,
    var loginCheckJs: String? = null,
    var coverDecodeJs: String? = null,
    var bookSourceComment: String? = null,
    var variableComment: String? = null,
    var lastUpdateTime: Long? = null,
    var respondTime: Long? = null,
    var weight: Int? = null,
    var exploreUrl: String? = null,
    var exploreScreen: String? = null,
    var searchUrl: String? = null,
    // 嵌套规则 DTO(经已注册 deserializer 反序列化)
    var ruleExplore: ExploreRule? = null,
    var ruleSearch: SearchRule? = null,
    var ruleBookInfo: BookInfoRule? = null,
    var ruleToc: TocRule? = null,
    var ruleContent: ContentRule? = null,
    var ruleReview: ReviewRule? = null,
    var eventListener: Boolean? = null,
    var customButton: Boolean? = null
) : BaseSource {

    override fun getTag(): String = bookSourceName

    override fun getKey(): String = bookSourceUrl

    /** 搜索规则(缺省空 SearchRule,避免 NPE;镜像 app `getSearchRule`)。 */
    fun getSearchRule(): SearchRule = ruleSearch ?: SearchRule().also { ruleSearch = it }

    /** 发现规则(缺省空 ExploreRule;镜像 app `getExploreRule`)。 */
    fun getExploreRule(): ExploreRule = ruleExplore ?: ExploreRule().also { ruleExplore = it }

    /** 书籍信息规则(缺省空 BookInfoRule;镜像 app `getBookInfoRule`)。 */
    fun getBookInfoRule(): BookInfoRule = ruleBookInfo ?: BookInfoRule().also { ruleBookInfo = it }

    /** 目录规则(缺省空 TocRule;镜像 app `getTocRule`)。 */
    fun getTocRule(): TocRule = ruleToc ?: TocRule().also { ruleToc = it }

    /** 正文规则(缺省空 ContentRule;镜像 app `getContentRule`)。 */
    fun getContentRule(): ContentRule = ruleContent ?: ContentRule().also { ruleContent = it }
}
