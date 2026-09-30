package io.legado.app.help.sync

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SyncTombstone
import io.legado.app.model.sync.SyncManager

/**
 * app 侧删除墓碑的写入收口(双端同步,SYNC_PLAN.md §3.2)。
 *
 * 为什么需要墓碑:同步合并是 LWW,而"删除"在数据层表现为**记录消失**——消失既可能是
 * "被删了",也可能是"本地/远端还没同步到"。没有墓碑就无法区分,结果是"一端删除后
 * 被另一端的旧副本复活"。
 *
 * **刻意不做"是否配置了服务器"的门控**:门控需要在删除路径上查库判断,而这个路径是
 * 用户手动触发的低频操作;墓碑量的上限由用户实际删除次数决定(书源/书籍量本身由
 * bookSourceLimit、userBookLimit 约束),不会因"从没用过同步"而无界增长。
 */
object SyncTombstoneHelp {

    fun recordBookSource(key: String) {
        record(SyncTombstone.TYPE_BOOK_SOURCE, key)
    }

    /**
     * 记录书籍删除。只对[参与同步的书][SyncManager.isSyncableBook]记墓碑——
     * 本地书、WebDAV 文件书、试读临时书服务端从来就没有过,记了纯是噪声。
     */
    fun recordBook(book: Book) {
        if (!SyncManager.isSyncableBook(book)) {
            return
        }
        record(SyncTombstone.TYPE_BOOK, book.bookUrl)
    }

    /**
     * 记录一条删除。[deletedAt] 取当前时间;已存在同 (type, key) 墓碑时由 DAO 保留较新值,
     * 避免并发删除把时间改小、反而让另一端"复活"该实体。
     */
    fun record(type: String, key: String, deletedAt: Long = System.currentTimeMillis()) {
        if (key.isEmpty()) {
            return
        }
        appDb.syncTombstoneDao.put(type, key, deletedAt)
    }

}
