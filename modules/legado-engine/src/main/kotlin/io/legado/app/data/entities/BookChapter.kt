package io.legado.app.data.entities

import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.RuleDataInterface
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonObject

/**
 * 引擎版 BookChapter(data class 全集,从 readerMT `data/entities/BookChapter.kt` 移植为纯 JVM)。
 *
 * M1 起 `:server` 用它(原 server 同名 data class 已删,避免同包冲突);`:app` 的 Room `@Entity`
 * BookChapter 保留,Phase 1c switchover 时改 composition/DTO 转换(同 [BookSource] 先例)。
 *
 * 字段为 readerMT 全集(章节地址/卷标志/起止位置/EPUB fragment 等),无 Android/Jackson 依赖。
 */
data class BookChapter(
    var url: String = "",               // 章节地址
    var title: String = "",              // 章节标题
    var isVolume: Boolean = false,      // 是否是卷名
    var baseUrl: String = "",           // 用来拼接相对url
    var bookUrl: String = "",           // 书籍地址
    var index: Int = 0,                 // 章节序号
    var resourceUrl: String? = null,    // 音频真实URL
    var tag: String? = null,            //
    var start: Long? = null,            // 章节起始位置
    var end: Long? = null,               // 章节终止位置
    var startFragmentId: String? = null,  //EPUB书籍当前章节的fragmentId
    var endFragmentId: String? = null,    //EPUB书籍下一章节的fragmentId
    var variable: String? = null        //变量
) : RuleDataInterface {

    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    override fun putVariable(key: String, value: String?): Boolean {
        val keyExist = variableMap.contains(key)
        when {
            value == null -> variableMap.remove(key)
            value.length < 10000 -> variableMap[key] = value
            else -> variableMap[key] = value
        }
        variable = GSON.toJson(variableMap)
        return keyExist
    }

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    fun getAbsoluteURL(): String {
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(url)
        val urlBefore = if (urlMatcher.find()) url.substring(0, urlMatcher.start()) else url
        val urlAbsoluteBefore = NetworkUtils.getAbsoluteURL(baseUrl, urlBefore)
        return if (urlBefore.length == url.length) urlAbsoluteBefore else urlAbsoluteBefore + ',' + url.substring(urlMatcher.end())
    }

    fun getFileName(): String = String.format("%05d-%s.nb", index, MD5Utils.md5Encode16(title))
}
