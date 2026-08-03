package io.legado.app.data.entities

import io.legado.app.model.analyzeRule.RuleDataInterface

/**
 * 引擎版 BookChapter(从 readerMT `data/entities/BookChapter.kt` 抽取为平台无关 interface,最小子集)。
 *
 * app 的 `BookChapter` 是 Room data class(`@Entity`)+ Parcelable,含 `title/baseUrl/bookUrl/index/
 * titleMD5/...`;引擎只声明 `AnalyzeUrl`/`AnalyzeRule` 实际触及的 `title` + [RuleDataInterface]
 * (`putVariable`/`getVariable`/`variableMap`)。其余字段留 `:app`(Room schema 不变)。
 *
 * Phase 1c switchover 时 app 的 `BookChapter` 实现本接口(已有 `var title` 与 `variableMap`)。
 */
interface BookChapter : RuleDataInterface {
    var title: String
}