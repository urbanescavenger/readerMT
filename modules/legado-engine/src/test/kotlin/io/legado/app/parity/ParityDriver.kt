package io.legado.app.parity

import io.legado.app.model.analyzeRule.AnalyzeRule
import java.security.MessageDigest

/**
 * §5c parity 测试驱动:复刻 app `BookList.analyzeBookList`
 * (`app/.../webBook/BookList.kt:55-289`)的**引擎相关子集**。
 *
 * 去掉 app 专属的 `SearchBook`/`BookHelp.formatBookName`/`appDb`/`wordCountFormat`
 * /`HtmlFormatter.format`/`getBookType`/dedup 等展示与持久化层;只保留 `AnalyzeRule`
 * 实际执行的解析调用:`setContent` → `getElements(bookList)` → 逐项
 * `setContent(item)` + `getString`/`getStringList`。由此产出的是引擎解析的**原始**
 * 结构化结果 —— §5c "规范化结果 hash" 的输入。
 *
 * 纯 JSoup/XPath/JsonPath/Regex 规则(无 `@js:`/`<js>`/`@webJs:`/`{{}}`)全程不
 * 触碰 `Platform` SPI(勘探已核实),故 `AnalyzeRule(ruleData=null, source=null)`
 * 即可。含 JS 的 fixture 需后续真 `RhinoEngine` 实现(本批不涉及)。
 */
object ParityDriver {

    /** 固定字段序列 → 序列化稳定(规范化 hash 用)。 */
    private val FIELD_ORDER = listOf(
        "name", "author", "bookUrl", "coverUrl", "intro", "kind", "lastChapter", "wordCount"
    )

    /**
     * 解析搜索结果页,返回每本书的原始字段 map(保持页面顺序)。
     *
     * @param source 书源 fixture(读 `ruleSearch`)
     * @param body 搜索结果页 body(HTML 或 JSON 文本)
     * @param baseUrl 站点根 URL(相对链接解析用)
     */
    fun parseSearch(
        source: BookSourceFixture,
        body: String,
        baseUrl: String
    ): List<Map<String, String?>> {
        val rule = source.searchRule()
        // 对应 BookList.kt:55-58
        val analyzeRule = AnalyzeRule(ruleData = null, source = null)
        analyzeRule.setContent(body, baseUrl)
        analyzeRule.setBaseUrl(baseUrl)
        // 对应 BookList.kt:57;getString(isUrl=true) 用 redirectUrl 解析相对链接
        analyzeRule.setRedirectUrl(baseUrl)
        // 对应 BookList.kt:89-98(bookList 规则,去前导 +/-)
        val bookListRule = rule.bookList?.trimStart('-', '+', ' ') ?: ""
        val items = analyzeRule.getElements(bookListRule)

        val books = ArrayList<Map<String, String?>>()
        for (item in items) {
            // 对应 BookList.kt:217(逐项重设 content;stringRuleCache 复用)
            analyzeRule.setContent(item)
            val book = LinkedHashMap<String, String?>()
            book["name"] = analyzeRule.getString(rule.name)
            book["author"] = analyzeRule.getString(rule.author)
            // isUrl=true → NetworkUtils.getAbsoluteURL 解析相对链接(对应 BookList.kt:281/269)
            book["bookUrl"] = analyzeRule.getString(rule.bookUrl, isUrl = true)
            book["coverUrl"] = analyzeRule.getString(rule.coverUrl, isUrl = true)
            book["intro"] = analyzeRule.getString(rule.intro)
            // kind 是列表规则(对应 BookList.kt:230,joinToString ",")
            book["kind"] = analyzeRule.getStringList(rule.kind)?.joinToString(",")
            book["lastChapter"] = analyzeRule.getString(rule.lastChapter)
            book["wordCount"] = analyzeRule.getString(rule.wordCount)
            books.add(book)
        }
        return books
    }

    /**
     * §5c 规范化结果 hash(SHA-256 hex):按 bookUrl 排序去页面顺序抖动,每项按
     * [FIELD_ORDER] 取值(null→""),字段以 `` 分隔、条目以 `\n` 分隔。
     *
     * 此 hash 即 parity 契约常量:引擎解析逻辑若意外变动 → hash 变 → 测试红,
     * 需人工核对输出后更新常量(回归闸门)。三端 parity 时 app/server 用同一 fixture
     * 复跑须产出同一 hash。
     */
    fun normalizedHash(books: List<Map<String, String?>>): String {
        val sb = normalizedString(books)
        val digest = MessageDigest.getInstance("SHA-256").digest(sb.toByteArray(Charsets.UTF_8))
        // Byte 有符号,>= 0x80 字节被 %x 符号扩展成 8 位 hex;先 and 0xFF 转无符号,
        // 确保每字节恰 2 位 hex(与 python hexdigest 一致)。
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** 规范化字符串(hash 前明文),诊断用。 */
    fun normalizedString(books: List<Map<String, String?>>): String {
        val sorted = books.sortedBy { it["bookUrl"].orEmpty() }
        val sb = StringBuilder()
        for ((i, book) in sorted.withIndex()) {
            if (i > 0) sb.append('\n')
            sb.append(FIELD_ORDER.joinToString("") { book[it].orEmpty() })
        }
        return sb.toString()
    }
}