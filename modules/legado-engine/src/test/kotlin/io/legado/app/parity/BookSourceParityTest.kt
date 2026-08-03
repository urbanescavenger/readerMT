package io.legado.app.parity

import io.legado.app.platform.Platform
import io.legado.app.platform.repo.NoOpCacheRepository
import io.legado.app.platform.repo.NoOpCookieRepository
import io.legado.app.platform.repo.Repositories
import io.legado.app.platform.webbook.NoOpWebBookProvider
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * §5c parity 测试 — 引擎侧基线(MERGE_PLAN §5c)。
 *
 * 用合成 BookSource JSON + 合成搜索结果 fixture(JSoup / JsonPath 各一,覆盖两种
 * analyzer 路径)驱动引擎 [io.legado.app.model.analyzeRule.AnalyzeRule],经
 * [ParityDriver] 复刻 app `BookList.analyzeBookList` 的引擎相关子集,断言解析出的
 * 字段值正确,且规范化结果 hash 等于固化常量。
 *
 * **纯 JSoup/JsonPath 规则路径不触碰任何 `Platform` lateinit SPI**(勘探核实:
 * getString/getStringList/getElements/splitSourceRule 全程不读 Platform.rhino/
 * webView/webBook/Repositories)。故 [setUp] 仅赋值廉价的现有 NoOp(webBook/
 * Repositories)以防御性兜底;context/appConfig/scriptAssets/webView/rhino 不赋值
 * —— 若某条 fixture 意外走到 JS/WebView 路径会以 `UninitializedPropertyAccessException`
 * 明确失败,这正是"本批 fixture 不支持 JS"的预期信号。含 `@js:` 的 fixture 需后续
 * 真 RhinoEngine 实现落地后再加。
 *
 * hash 常量即 §5c 契约:引擎解析逻辑若意外变动 → hash 变 → 测试红,需人工核对输出
 * 后更新常量(回归闸门)。三端 parity 时 app/server 用同一 fixture 复跑须产出同一 hash。
 */
class BookSourceParityTest {

    private val baseUrl = "https://synthetic.test"

    /** 防御性赋值:纯规则路径不触达,但兜底避免后续 fixture 误触时裸 NPE。 */
    @Before
    fun setUp() {
        Repositories.cookie = NoOpCookieRepository
        Repositories.cache = NoOpCacheRepository
        Platform.repositories = Repositories
        Platform.webBook = NoOpWebBookProvider
        Platform.isMainThread = { false }
    }

    // ---------- JSoup fixture ----------

    @Test
    fun jsoupSearchParsesExpectedBooks() {
        val source = loadSource("/parity/synthetic_jsoup/source.json")
        val html = resourceText("/parity/synthetic_jsoup/search.html")
        val books = ParityDriver.parseSearch(source, html, baseUrl)

        assertEquals("应解析出 3 本书", 3, books.size)
        assertBook(books[0], "书名A", "作者A", "https://synthetic.test/book/1", "https://synthetic.test/cover/1.jpg", "简介A", "玄幻", "第10章", "10.0万字")
        assertBook(books[1], "书名B", "作者B", "https://synthetic.test/book/2", "https://synthetic.test/cover/2.jpg", "简介B", "都市", "第20章", "20.0万字")
        assertBook(books[2], "书名C", "作者C", "https://synthetic.test/book/3", "https://synthetic.test/cover/3.jpg", "简介C", "科幻", "第30章", "30.0万字")
    }

    @Test
    fun jsoupSearchHashIsStable() {
        val source = loadSource("/parity/synthetic_jsoup/source.json")
        val html = resourceText("/parity/synthetic_jsoup/search.html")
        val books = ParityDriver.parseSearch(source, html, baseUrl)
        val actual = ParityDriver.normalizedHash(books)
        assertEquals(
            "JSoup fixture hash 变更:引擎解析逻辑可能回归(核对输出后更新 JSOUP_HASH)。",
            JSOUP_HASH, actual
        )
    }

    // ---------- JsonPath fixture ----------

    @Test
    fun jsonPathSearchParsesExpectedBooks() {
        val source = loadSource("/parity/synthetic_jsonpath/source.json")
        val json = resourceText("/parity/synthetic_jsonpath/search.json")
        val books = ParityDriver.parseSearch(source, json, baseUrl)

        assertEquals("应解析出 3 本书", 3, books.size)
        assertBook(books[0], "书名D", "作者D", "https://synthetic.test/book/4", "https://synthetic.test/cover/4.jpg", "简介D", "历史", "第40章", "40.0万字")
        assertBook(books[1], "书名E", "作者E", "https://synthetic.test/book/5", "https://synthetic.test/cover/5.jpg", "简介E", "悬疑", "第50章", "50.0万字")
        assertBook(books[2], "书名F", "作者F", "https://synthetic.test/book/6", "https://synthetic.test/cover/6.jpg", "简介F", "言情", "第60章", "60.0万字")
    }

    @Test
    fun jsonPathSearchHashIsStable() {
        val source = loadSource("/parity/synthetic_jsonpath/source.json")
        val json = resourceText("/parity/synthetic_jsonpath/search.json")
        val books = ParityDriver.parseSearch(source, json, baseUrl)
        val actual = ParityDriver.normalizedHash(books)
        assertEquals(
            "JsonPath fixture hash 变更:引擎解析逻辑可能回归(核对输出后更新 JSONPATH_HASH)。",
            JSONPATH_HASH, actual
        )
    }

    // ---------- helpers ----------

    private fun loadSource(path: String): BookSourceFixture =
        GSON.fromJson(resourceText(path), BookSourceFixture::class.java)

    private fun assertBook(
        book: Map<String, String?>,
        name: String, author: String, bookUrl: String, coverUrl: String,
        intro: String, kind: String, lastChapter: String, wordCount: String
    ) {
        assertEquals("name", name, book["name"])
        assertEquals("author", author, book["author"])
        assertEquals("bookUrl", bookUrl, book["bookUrl"])
        assertEquals("coverUrl", coverUrl, book["coverUrl"])
        assertEquals("intro", intro, book["intro"])
        assertEquals("kind", kind, book["kind"])
        assertEquals("lastChapter", lastChapter, book["lastChapter"])
        assertEquals("wordCount", wordCount, book["wordCount"])
    }

    private fun resourceText(path: String): String =
        javaClass.getResourceAsStream(path).use { it!!.bufferedReader().readText() }

    private companion object {
        /** §5c 契约常量:JSoup fixture 规范化结果 SHA-256。变更需人工核对输出。 */
        private const val JSOUP_HASH = "210c6e1b824feebb920318fad717c0f100c0e0fadb04b156a9daf9078b88da2a"
        /** §5c 契约常量:JsonPath fixture 规范化结果 SHA-256。变更需人工核对输出。 */
        private const val JSONPATH_HASH = "40ce6aa1fdc843ea6e2e915e48bb90af0a7d3292c18dacd9e31de09f5ccc314a"
    }
}