package io.legado.app.parity

import io.legado.app.data.entities.BaseBook
import io.legado.app.data.entities.BaseSource
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.platform.Platform
import io.legado.app.platform.webbook.WebBookProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 1c composition 映射守卫回归测试(引擎 `BookSource` DTO vs app `BookSourceEntity`)。
 *
 * 引擎 `AnalyzeRule.reGetBook`/`refreshTocUrl` 原守卫 `source as? BookSource`(引擎 DTO)。
 * app 传给 AnalyzeRule 的 source 是 Room 实体 `BookSourceEntity`(实现引擎 `BaseSource`),
 * 不是引擎 `BookSource`(final data class 不能做 supertype,app 实体永远不满足 `as? BookSource`)
 * → 守卫失败 → preUpdate JS 重抓(`java.reGetBook()`/`java.refreshTocUrl()`)静默失效。
 * 守卫放宽为 `as? BaseSource`(source 已声明 `BaseSource?`,故直接取 source)后,任何 BaseSource
 * 实现(含 app BookSourceEntity/RssSource)都能触发重抓,对齐 main(那里 source 是 app BookSource)。
 *
 * 本测试验证:非 `BookSource` DTO 的 BaseSource 实现(preUpdateJs=true)调 reGetBook 时守卫通过,
 * 能走到 `Platform.webBook.preciseSearchAwait`(注入捕获 provider,返回失败 Result →
 * `.getOrThrow()` 抛 IllegalStateException → 证明调用点到达)。若守卫退回具体 DTO 类型,reGetBook
 * 会静默 return 不抛异常 → 测试 fail。
 */
class BookSourcePreUpdateTest {

    /** 非引擎 `BookSource` DTO 的 BaseSource 实现 —— 等价于 app 的 BookSourceEntity/RssSource。 */
    private class FakeSource : BaseSource {
        override var concurrentRate: String? = null
        override var loginUrl: String? = null
        override var loginUi: String? = null
        override var header: String? = null
        override var enabledCookieJar: Boolean? = null
        override var jsLib: String? = null
        override fun getTag(): String = "fake"
        override fun getKey(): String = "https://fake"
    }

    private class FakeBook : BaseBook {
        override var name: String = "书名"
        override var author: String = "作者"
        override var bookUrl: String = "https://fake/book"
        override var kind: String? = null
        override var wordCount: String? = null
        override var infoHtml: String? = null
        override var tocHtml: String? = null
        override val variableMap: HashMap<String, String> = HashMap()
    }

    @Test
    fun reGetBookFiresForNonBookSourceDtoBaseSource() {
        val source = FakeSource()
        val book = FakeBook()
        val seen = mutableListOf<BaseSource>()
        val prevWebBook = Platform.webBook
        Platform.webBook = object : WebBookProvider {
            override suspend fun preciseSearchAwait(source: BaseSource, name: String, author: String): Result<BaseBook> {
                seen += source
                return Result.failure(IllegalStateException("reached preciseSearchAwait"))
            }

            override suspend fun getBookInfoAwait(source: BaseSource, book: BaseBook, skipToc: Boolean): Result<BaseBook> =
                Result.failure(IllegalStateException("reached getBookInfoAwait"))
        }
        try {
            val rule = AnalyzeRule(ruleData = book, source = source, preUpdateJs = true, isFromBookInfo = false)
            rule.reGetBook()
            // 守卫失败(静默 return)会走到这 → 测试 fail
            org.junit.Assert.fail("reGetBook 应触发 webBook.preciseSearchAwait(守卫对非 BookSource DTO 的 BaseSource 应通过)")
        } catch (e: IllegalStateException) {
            assertEquals("reached preciseSearchAwait", e.message)
            assertEquals(listOf(source), seen)
        } finally {
            Platform.webBook = prevWebBook
        }
    }
}
