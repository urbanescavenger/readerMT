package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 双端同步的删除墓碑(tombstone)。
 *
 * 为什么需要:同步的合并是 LWW(按 `lastModifiedAt` 比大小),而"删除"在数据层表现为
 * **记录消失**——消失既可能是"被删了",也可能是"本地/远端还没同步到"。没有墓碑就无法
 * 区分二者,结果是"一端删除后被另一端的旧副本复活"。
 *
 * 规则:**墓碑必须先于实体参与比较**(见 `SYNC_PLAN.md` §5.3)。即
 * `墓碑.deletedAt >= max(对方.lastModifiedAt, 对方墓碑.deletedAt)` 时判定为删除。
 *
 * 写入点(app 侧):`SourceHelp.deleteBookSources` 等删除收口处。
 * 服务端对称实现见 `storage/data/<ns>/tombstone.json`。
 */
@Entity(
    tableName = "sync_tombstones",
    primaryKeys = ["type", "key"]
)
data class SyncTombstone(
    // 实体类型,见 companion 的 TYPE_* 常量
    val type: String,
    // 被删除实体的主键:书源为 bookSourceUrl,书籍为 bookUrl
    val key: String,
    // 删除时间,参与 LWW 比较
    @ColumnInfo(defaultValue = "0")
    val deletedAt: Long = 0L
) {

    companion object {
        /** 书源(主键 bookSourceUrl) */
        const val TYPE_BOOK_SOURCE = "bookSource"

        /** 书籍(主键 bookUrl) */
        const val TYPE_BOOK = "book"
    }

}
