package io.legado.app.data.entities

import io.legado.app.model.analyzeRule.RuleDataInterface
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/**
 * 引擎版 RssArticle(data class 全集,从 readerMT `data/entities/RssArticle.kt` 移植为纯 JVM)。
 *
 * M1 起 `:server` 用此 data class(原 server 同名 data class 已删,避免同包冲突);`:app` 的 Room
 * `@Entity` RssArticle 保留,Phase 1c switchover 时改 composition/DTO 转换(同 [BookSource] 先例)。
 *
 * `AnalyzeRule` 仅做 `ruleData as? RssArticle` 判断后绑定 JS,data class 亦满足;字段全集供
 * `:server` 的 RssParser 构造/读写。
 */
data class RssArticle(
    var origin: String = "",
    var sort: String = "",
    var title: String = "",
    var order: Long = 0,
    var link: String = "",
    var pubDate: String? = null,
    var description: String? = null,
    var content: String? = null,
    var image: String? = null,
    var read: Boolean = false,
    var variable: String? = null
) : RuleDataInterface {

    override fun hashCode() = link.hashCode()

    override fun equals(other: Any?): Boolean {
        other ?: return false
        return if (other is RssArticle) origin == other.origin && link == other.link else false
    }

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
}
