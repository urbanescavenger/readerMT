package io.legado.app.data.entities

/**
 * 引擎版 RssArticle(标记接口,继承引擎 [BaseRssArticle])。
 *
 * `AnalyzeRule.rssArticle = ruleData as? RssArticle` 后绑定到 JS `bindings["rssArticle"]`;
 * 引擎只需该类型存在。RSS 文章字段留 `:app`。
 *
 * Phase 1c switchover 时 app 的 `RssArticle`(data class `: BaseRssArticle`)extend 本接口。
 */
interface RssArticle : BaseRssArticle