package io.legado.app.model.sync

import com.google.gson.JsonObject
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSourceEntity
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.SyncTombstone
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppCacheManager
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.upType
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 双端同步编排(SYNC_PLAN.md 阶段 2 书源 + 阶段 3 书架元数据)。
 *
 * 以服务端为中枢,一轮 = 登录 → 拉(书源+书架+墓碑) → 合并 → 推(一次往返)。
 *
 * **推送为什么是全量**:服务端 `SyncController` 会做 LWW 仲裁(推送版本不比服务端副本新
 * 则拒绝),所以全量推送不会用旧内容覆盖服务端较新的副本;而全量省掉了"哪些条目改过"的
 * 增量簿记——那正是最容易漏推的地方(例如启用开关、排序这类不走统一写入路径的改动)。
 */
object SyncManager {

    /** 一次同步的结果,用于 UI 汇报 */
    data class Result(
        /** 服务端接受写入的条数(书源 + 书架) */
        val pushed: Int,
        /** 本地被远端覆盖/新增的条数(书源 + 书架) */
        val merged: Int,
        /** 本地被远端墓碑删除的条数(书源 + 书架) */
        val deleted: Int
    )

    /**
     * 是否参与同步的书籍(即"在线书架"的界定)。
     *
     * 排除:
     * - `notShelf`:试读临时书,根本没进书架(书架列表也是这么过滤的)
     * - `isLocal`:本地文件书、WebDAV 文件书 —— 文件无法跨端,同步过去也打不开
     * - `readerServer://`:远端直读的内置书源(阶段 5),带本机凭证,绝不能过同步
     */
    fun isSyncableBook(book: Book): Boolean {
        if (book.isNotShelf || book.isLocal) {
            return false
        }
        val origin = book.origin
        return !origin.startsWith(BookType.webDavTag) &&
            !origin.startsWith(READER_SERVER_SOURCE_PREFIX)
    }

    suspend fun sync(
        server: Server,
        onProgress: (String) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        val config = server.getReaderServerConfig()
            ?: throw NoStackTraceException("该服务器不是阅读服务器")

        val client = ReaderServerClient(
            rootUrl = config.url,
            accessToken = config.accessToken,
            username = config.username,
            password = config.password
        )
        if (client.accessToken.isEmpty()) {
            onProgress("登录服务器…")
            config.accessToken = client.login()
            persistConfig(server, config)
        }

        onProgress("拉取服务端数据…")
        val remoteSources = client.getBookSources()
        val remoteBooks = client.getBookshelf()
        // 墓碑一次拉全,按 type 分给书源与书架
        val remoteTombstones = client.getTombstones()

        onProgress("合并书源…")
        val (sourceMerged, sourceDeleted) = mergeBookSources(
            remoteSources,
            remoteTombstones.filter { it.type == SyncTombstone.TYPE_BOOK_SOURCE }
        )

        onProgress("合并书架…")
        val (bookMerged, bookDeleted) = mergeBooks(
            remoteBooks,
            remoteTombstones.filter { it.type == SyncTombstone.TYPE_BOOK }
        )

        onProgress("推送本地数据…")
        // 内置书源(远端直读)必须排除:它带本机 accessToken 与服务器地址,
        // 推到服务端的书源池会造成回环(见 SYNC_PLAN.md §5.4)
        val localSources = appDb.bookSourceDao.all.filterNot { isBuiltInSource(it.bookSourceUrl) }
        val localBooks = appDb.bookDao.webBooks.filter { isSyncableBook(it) }
        val localTombstones = appDb.syncTombstoneDao.all
        val pushResult = client.syncPush(
            bookSources = localSources,
            books = localBooks.map { it.toSyncPayload() },
            tombstones = localTombstones
        )

        config.lastSyncAt = System.currentTimeMillis()
        persistConfig(server, config)

        Result(
            pushed = appliedCount(pushResult, "bookSources") + appliedCount(pushResult, "books"),
            merged = sourceMerged + bookMerged,
            deleted = sourceDeleted + bookDeleted
        )
    }

    private fun appliedCount(result: JsonObject?, key: String): Int =
        result?.getAsJsonObject(key)?.get("applied")?.asInt ?: 0

    /**
     * 把远端书源与墓碑合并进本地库,返回 (覆盖或新增数, 删除数)。
     *
     * 规则见 SYNC_PLAN.md §5.3:
     * - **墓碑优先于实体比较,且两个方向都要**——只做"远端墓碑 vs 本地实体"会漏掉镜像
     *   情形:本地删了、远端还留着 → 把远端旧副本插回本地,**删除被自己撤销**。
     * - 实体比墓碑更新 = 墓碑之后重建,实体保留并把过期墓碑清掉。
     * - 无墓碑时 `lastModifiedAt` 大者胜;**相等保留本地**——服务端仲裁写的是
     *   `existingAt > pushedAt` 才拒绝,即相等时接受推送,所以"相等本地胜"两端才会收敛。
     */
    private suspend fun mergeBookSources(
        remoteSources: List<BookSourceEntity>,
        remoteTombstones: List<SyncTombstone>
    ): Pair<Int, Int> {
        val localSources = appDb.bookSourceDao.all.associateBy { it.bookSourceUrl }
        val localTombstones = appDb.syncTombstoneDao
            .getByType(SyncTombstone.TYPE_BOOK_SOURCE).associateBy { it.key }
        val remoteTombMap = remoteTombstones.associateBy { it.key }

        // 过期墓碑:实体在墓碑之后又被改过/重建过,墓碑不能再压制它
        val expiredTombstones = arrayListOf<String>()

        // 1. 远端墓碑优先于实体比较
        val toDelete = arrayListOf<String>()
        for ((key, remoteTomb) in remoteTombMap) {
            val localSourceAt = localSources[key]?.lastModifiedAt ?: 0L
            val localTombAt = localTombstones[key]?.deletedAt ?: 0L
            val hasLocalEntity = localSources[key] != null
            if (hasLocalEntity && localSourceAt > remoteTomb.deletedAt) {
                // 本地实体更新 → 保留实体,清掉本地可能残留的旧墓碑
                if (localTombAt > 0L) {
                    expiredTombstones.add(key)
                }
            } else if (!hasLocalEntity && localTombAt == 0L) {
                // 本地既无实体也无墓碑 → 无事可做
            } else if (localTombAt > remoteTomb.deletedAt) {
                // 本地墓碑更新 → 保留本地删除,交给推送阶段让服务端也删
            } else {
                toDelete.add(key)
            }
        }

        // 2. 本地墓碑同样优先于实体比较(镜像方向)
        var merged = 0
        for (remote in remoteSources) {
            val key = remote.bookSourceUrl
            if (key.isEmpty() || remoteTombMap.containsKey(key) || isBuiltInSource(key)) {
                continue
            }
            val localTomb = localTombstones[key]
            if (localTomb != null) {
                if (localTomb.deletedAt >= remote.lastModifiedAt) {
                    // 本地删除不早于服务端实体 → 保留删除
                    continue
                }
                // 服务端在本地删除之后重新创建 → 复活,旧墓碑失效
                expiredTombstones.add(key)
            }
            val local = localSources[key]
            if (local == null || remote.lastModifiedAt > local.lastModifiedAt) {
                appDb.bookSourceDao.insert(remote)
                merged++
            }
        }

        if (expiredTombstones.isNotEmpty()) {
            appDb.runInTransaction {
                expiredTombstones.distinct().forEach {
                    appDb.syncTombstoneDao.delete(SyncTombstone.TYPE_BOOK_SOURCE, it)
                }
            }
        }
        if (toDelete.isNotEmpty()) {
            appDb.runInTransaction {
                toDelete.forEach { key ->
                    // 用 deleteBookSourceFromSync:墓碑时间以服务端为准,不能在这里重记成 now
                    SourceHelp.deleteBookSourceFromSync(key)
                    appDb.syncTombstoneDao.put(
                        SyncTombstone.TYPE_BOOK_SOURCE,
                        key,
                        remoteTombMap.getValue(key).deletedAt
                    )
                }
            }
        }

        if (toDelete.isNotEmpty() || expiredTombstones.isNotEmpty() || merged > 0) {
            AppCacheManager.clearSourceVariables()
        }
        return merged to toDelete.size
    }

    /**
     * 把远端书架与墓碑合并进本地库,返回 (覆盖或新增数, 删除数)。墓碑规则同 [mergeBookSources]。
     */
    private suspend fun mergeBooks(
        remoteBooks: List<Book>,
        remoteTombstones: List<SyncTombstone>
    ): Pair<Int, Int> {
        val localBooks = appDb.bookDao.all.associateBy { it.bookUrl }
        val localTombstones = appDb.syncTombstoneDao
            .getByType(SyncTombstone.TYPE_BOOK).associateBy { it.key }
        val remoteTombMap = remoteTombstones.associateBy { it.key }

        val expiredTombstones = arrayListOf<String>()

        // 1. 远端墓碑优先
        val toDelete = arrayListOf<String>()
        for ((key, remoteTomb) in remoteTombMap) {
            val localBookAt = localBooks[key]?.lastModifiedAt ?: 0L
            val localTombAt = localTombstones[key]?.deletedAt ?: 0L
            val hasLocalEntity = localBooks[key] != null
            if (hasLocalEntity && localBookAt > remoteTomb.deletedAt) {
                if (localTombAt > 0L) {
                    expiredTombstones.add(key)
                }
            } else if (!hasLocalEntity && localTombAt == 0L) {
                // 无事可做
            } else if (localTombAt > remoteTomb.deletedAt) {
                // 本地墓碑更新 → 保留本地删除
            } else {
                toDelete.add(key)
            }
        }

        // 2. 本地墓碑优先 + 实体合并
        var merged = 0
        for (remote in remoteBooks) {
            val key = remote.bookUrl
            if (key.isEmpty() || remoteTombMap.containsKey(key) || !isSyncableBook(remote)) {
                continue
            }
            val localTomb = localTombstones[key]
            if (localTomb != null) {
                if (localTomb.deletedAt >= remote.lastModifiedAt) {
                    continue
                }
                expiredTombstones.add(key)
            }
            // 先按 bookUrl 认身份;认不到再退回 (name, author) —— books 表有 (name, author)
            // 唯一索引,不认领这条就会在 insert 时 REPLACE 掉另一本同名同作者的书(静默丢书)。
            // 按 (name, author) 认领时**不动 bookUrl**:改主键会波及目录缓存等以 bookUrl 为键的数据
            var local = localBooks[key]
            if (local == null) {
                local = localBooks.values.firstOrNull {
                    it.name == remote.name && it.author == remote.author
                }
            }
            if (local == null) {
                // 新书:upType 把服务端的 0-4 枚举(或另一端推来的位掩码)统一成 app 位掩码。
                // 不做这一步,type 为 0 的书会被书架的类型查询过滤掉、在书架上直接隐身。
                remote.upType()
                appDb.bookDao.insert(remote)
                merged++
            } else if (remote.lastModifiedAt > local.lastModifiedAt) {
                mergeSyncedBook(local, remote)
                appDb.bookDao.update(local)
                merged++
            }
        }

        if (expiredTombstones.isNotEmpty()) {
            appDb.runInTransaction {
                expiredTombstones.distinct().forEach {
                    appDb.syncTombstoneDao.delete(SyncTombstone.TYPE_BOOK, it)
                }
            }
        }
        if (toDelete.isNotEmpty()) {
            appDb.runInTransaction {
                toDelete.forEach { key ->
                    appDb.bookDao.getBook(key)?.let { appDb.bookDao.delete(it) }
                    // 一并清掉章节行,否则 chapters 表里留下孤儿数据
                    appDb.bookChapterDao.delByBook(key)
                    appDb.syncTombstoneDao.put(
                        SyncTombstone.TYPE_BOOK,
                        key,
                        remoteTombMap.getValue(key).deletedAt
                    )
                }
            }
        }
        return merged to toDelete.size
    }

    /**
     * 把远端书籍的**同步白名单字段**覆盖到本地已有记录上。
     *
     * 必须逐字段合并而不是整对象替换:远端记录里的 `durChapterIndex` / `latestChapterTitle` /
     * `totalChapterNum` / `readConfig` 等要么是阅读进度(本阶段不同步),要么是本端状态,
     * 整对象替换会把本机阅读进度清掉。
     */
    private fun mergeSyncedBook(target: Book, source: Book) {
        target.name = source.name
        target.author = source.author
        target.tocUrl = source.tocUrl
        target.origin = source.origin
        target.originName = source.originName
        target.kind = source.kind
        target.customTag = source.customTag
        target.coverUrl = source.coverUrl
        target.customCoverUrl = source.customCoverUrl
        target.intro = source.intro
        target.customIntro = source.customIntro
        target.wordCount = source.wordCount
        target.group = source.group
        target.lastModifiedAt = source.lastModifiedAt
    }

    /**
     * 构造推送用的精简书:只带同步白名单字段。
     *
     * 不这么做的话,app 专有状态(阅读进度、章节数、`readConfig`、书源变量)会写到服务端
     * 书架上;服务端对**已存在**的书只合并它的白名单,但对**新书**是整对象入库,
     * 那些字段就会真的落盘。
     */
    private fun Book.toSyncPayload(): Book = Book(
        bookUrl = bookUrl,
        name = name,
        author = author,
        origin = origin,
        originName = originName,
        tocUrl = tocUrl,
        kind = kind,
        customTag = customTag,
        coverUrl = coverUrl,
        customCoverUrl = customCoverUrl,
        intro = intro,
        customIntro = customIntro,
        wordCount = wordCount,
        group = group,
        // app 的位掩码直接带过去:另一端拉下来后 upType() 对 >=4 的值是空操作,
        // 位掩码正好正确;不给的话音频/视频书跨端会退化成文本
        type = type,
        lastModifiedAt = lastModifiedAt,
        // 显式归零,免得新书在服务端书架上显示成"刚读过/刚检查过"
        latestChapterTime = 0L,
        lastCheckTime = 0L,
        durChapterTime = 0L
    )

    private fun persistConfig(server: Server, config: Server.ReaderServerConfig) {
        appDb.serverDao.insert(server.copy(config = GSON.toJson(config)))
    }

    /**
     * 是否是远端直读的内置书源。这类源由 app 本地生成(内含本机 accessToken 与服务器地址),
     * **两个方向都不能过同步**:推上去会污染服务端书源池、多设备互相覆盖 token,
     * 拉下来则会带着别的设备的凭证(见 SYNC_PLAN.md §5.4)。
     */
    private fun isBuiltInSource(bookSourceUrl: String) =
        bookSourceUrl.startsWith(READER_SERVER_SOURCE_PREFIX)

    /** 远端直读内置书源的 URL 前缀(阶段 5 创建时须与此一致) */
    const val READER_SERVER_SOURCE_PREFIX = "readerServer://"

}
