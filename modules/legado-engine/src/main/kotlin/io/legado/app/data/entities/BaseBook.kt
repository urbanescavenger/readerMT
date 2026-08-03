package io.legado.app.data.entities

import io.legado.app.model.analyzeRule.RuleDataInterface

/**
 * 引擎版 BaseBook(从 readerMT `data/entities/BaseBook.kt` 抽取为平台无关 interface,最小子集)。
 *
 * app 的 `BaseBook` 还含 `kind/wordCount/variable/infoHtml/tocHtml` 及
 * `putVariable`/`putBigVariable`/`getBigVariable` 默认实现(后者依赖 app-only `RuleBigDataHelp`);
 * 引擎只声明 `AnalyzeUrl`/`AnalyzeRule` 实际触及的 `name/author/bookUrl` + [RuleDataInterface]
 * (提供 `variableMap`/`putVariable`/`getVariable`;`putBigVariable`/`getBigVariable` 留抽象,由 app 实现类 override)。
 *
 * 与 app 同名同包不同模块(switchover 前 app 用自家 BaseBook,无 classpath 冲突)。
 * Phase 1c switchover 时 app 的 `BaseBook` extend 本接口、`Book` 实现本接口(已有 `override var name/author/bookUrl`)。
 */
interface BaseBook : RuleDataInterface {
    var name: String
    var author: String
    var bookUrl: String
}