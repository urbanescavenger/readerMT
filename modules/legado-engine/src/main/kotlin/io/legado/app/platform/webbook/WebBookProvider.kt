package io.legado.app.platform.webbook

import io.legado.app.data.entities.BaseBook
import io.legado.app.data.entities.BaseSource

/**
 * WebBook 编排 SPI(计划 §3.x,`AnalyzeRule.reGetBook`/`refreshTocUrl` 用)。
 *
 * `AnalyzeRule` 在 preUpdate JS 中可被书源 JS 反射调用 `java.reGetBook()`/`java.refreshTocUrl()`,
 * 原实现调 app 层 `WebBook`(orchestrator,依赖 `BookInfoRule`/`BookListRule`/`AnalyzeUrl`+
 * 源规则解析,属 app 层,不进引擎)。引擎经此 SPI 反调平台实现:
 * - Android `:app`:注入 `WebBook` 真实现(`WebBook.preciseSearchAwait`/`getBookInfoAwait`);
 * - `:server`:NoOp(抛 `UnsupportedOperationException`,服务端引擎不支持 preUpdate 重抓)。
 *
 * 方法为 `suspend`(`AnalyzeRule` 在 `runBlocking(coroutineContext) { withTimeout(1800000) { ... } }` 内调用)。
 * 返回 `Result<BaseBook>`(对应 app `WebBook.*Await` 的 `Result<Book>`);`AnalyzeRule.reGetBook` 用
 * `.getOrThrow().let { book.bookUrl = it.bookUrl; it.variableMap.forEach { book.putVariable(...) } }`。
 */
interface WebBookProvider {

    /** 精确搜索书籍(对应 `WebBook.preciseSearchAwait(source, name, author)`)。 */
    suspend fun preciseSearchAwait(source: BaseSource, name: String, author: String): Result<BaseBook>

    /** 获取书籍详情(对应 `WebBook.getBookInfoAwait(source, book, skipToc)`);[skipToc] 跳过目录加载。 */
    suspend fun getBookInfoAwait(source: BaseSource, book: BaseBook, skipToc: Boolean): Result<BaseBook>
}

/** 服务端/未注入平台的默认实现:抛 `UnsupportedOperationException`(preUpdate 重抓 app-only)。 */
object NoOpWebBookProvider : WebBookProvider {
    override suspend fun preciseSearchAwait(source: BaseSource, name: String, author: String): Result<BaseBook> =
        Result.failure(UnsupportedOperationException("WebBook.preciseSearchAwait not available in platform-agnostic engine"))

    override suspend fun getBookInfoAwait(source: BaseSource, book: BaseBook, skipToc: Boolean): Result<BaseBook> =
        Result.failure(UnsupportedOperationException("WebBook.getBookInfoAwait not available in platform-agnostic engine"))
}