package io.legado.app.data.entities

import io.legado.app.model.analyzeRule.RuleDataInterface

/**
 * 引擎版 BaseRssArticle(从 readerMT `data/entities/BaseRssArticle.kt` 抽取为平台无关 interface,最小子集)。
 *
 * app 的 `BaseRssArticle` 含 `origin/link/variable` 及 `putVariable`/`putBigVariable`/`getBigVariable`
 * 默认实现(后者依赖 app-only `RuleBigDataHelp`);引擎只声明 [RuleDataInterface](AnalyzeRule 仅做
 * `ruleData as? RssArticle` 后绑定到 JS,不触碰 RSS 专属字段)。
 *
 * Phase 1c switchover 时 app 的 `BaseRssArticle` extend 本接口、`RssArticle` 实现本接口。
 */
interface BaseRssArticle : RuleDataInterface