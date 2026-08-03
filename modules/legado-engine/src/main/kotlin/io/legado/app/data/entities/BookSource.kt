package io.legado.app.data.entities

/**
 * 引擎版 BookSource(从 readerMT `data/entities/BookSource.kt` 抽取为平台无关 interface,标记接口)。
 *
 * `AnalyzeRule.reGetBook`/`refreshTocUrl` 做 `source as? BookSource` 守卫后传给 [io.legado.app.platform.webbook.WebBookProvider]。
 * 引擎只需该类型存在(空 interface,继承引擎 [BaseSource]);BookSource 专属字段(`bookUrlPattern`/
 * `ruleSearch`/`ruleBookInfo`/...) 留 `:app`(Room `@Entity`)。
 *
 * Phase 1c switchover 时 app 的 `BookSource`(data class `: BaseSource`)extend 本接口。
 */
interface BookSource : BaseSource