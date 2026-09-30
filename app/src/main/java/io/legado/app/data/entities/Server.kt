package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

/**
 * 服务器
 */
@Parcelize
@Entity(tableName = "servers")
data class Server(
    @PrimaryKey
    var id: Long = System.currentTimeMillis(),
    var name: String = "",
    var type: TYPE = TYPE.WEBDAV,
    var config: String? = null,
    var sortNumber: Int = 0
) : Parcelable {

    enum class TYPE {
        WEBDAV,

        /**
         * 阅读3 服务器(reader-mt / hectorqin reader)。
         * 用于双端同步(书源/书架)与远程书籍(SYNC_PLAN.md)。
         */
        READER
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is Server) {
            return id == other.id
        }
        return false
    }

    fun getConfigJsonObject(): JSONObject? {
        val json = config
        json ?: return null
        return JSONObject(json)
    }

    fun getWebDavConfig(): WebDavConfig? {
        return if (type == TYPE.WEBDAV) GSON.fromJsonObject<WebDavConfig>(config).getOrNull() else null
    }

    fun getReaderServerConfig(): ReaderServerConfig? {
        return if (type == TYPE.READER) GSON.fromJsonObject<ReaderServerConfig>(config).getOrNull() else null
    }

    @Parcelize
    data class WebDavConfig(
        var url: String,
        var username: String,
        var password: String
    ) : Parcelable

    /**
     * 阅读3 服务器配置。
     *
     * [accessToken] 与 [lastSyncAt] 是**运行时同步状态**,和 credentials 存在一起是为了
     * 免去单独一张表;保存配置时必须原样保留它们(见 ServerConfigDialog.getServer),
     * 否则每次改地址都会把登录态和增量基准清掉。
     */
    @Parcelize
    data class ReaderServerConfig(
        // 刻意给全部字段默认值:Kotlin 只在"所有构造参数都有默认值"时才生成无参构造,
        // GSON 才能走无参构造 + 应用默认值;否则 GSON 走 Unsafe 分配,缺字段的非空
        // String 会留下 null,后续 isEmpty() 直接 NPE。
        var url: String = "",
        var username: String = "",
        var password: String = "",
        /** 服务端 `/reader3/login` 返回的 accessToken,形如 `<username>:<token>` */
        var accessToken: String = "",
        /** 上次同步成功的时间戳(保留作增量基准;首版全量同步未使用) */
        var lastSyncAt: Long = 0L
    ) : Parcelable

}