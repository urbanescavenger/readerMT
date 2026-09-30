package io.legado.app.model.sync

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourceEntity
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.SyncTombstone
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppCacheManager
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 双端同步编排(SYNC_PLAN.md 阶段 2,只做书源;书架见阶段 3)。
 *
 * 以服务端为中枢,一轮 = 登录 → 拉 → 合并 → 推。
 *
 * **推送为什么是全量**:服务端 `SyncController` 会做 LWW 仲裁(推送版本不比服务端副本新
 * 则拒绝),所以全量推送不会用旧内容覆盖服务端较新的副本;而全量省掉了"哪些条目改过"的
 * 增量簿记——那正是最容易漏推的地方(例如启用开关、排序这类不走统一写入路径的改动)。
 */
object SyncManager {

    /** 一次同步的结果,用于 UI 汇报 */
    data class Result(
        /** 服务端书源总数 */
        val remoteCount: Int,
        /** 服务端接受写入的条数 */
        val pushed: Int,
        /** 服务端因版本更新而拒绝的条数 */
        val skipped: Int,
        /** 本地被远端墓碑删除的条数 */
        val deleted: Int,
        /** 本地被远端覆盖或新增的条数 */
        val merged: Int
    )

    suspend fun syncBookSources(
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
            persistConfig(server, config.apply { accessToken = client.login() })
        }

        onProgress("拉取服务端书源…")
        val remoteSources = client.getBookSources()
        val remoteTombstones = client.getTombstones()

        onProgress("合并…")
        val (merged, deleted) = mergeBookSources(remoteSources, remoteTombstones)

        onProgress("推送本地书源…")
        // 内置书源(远端直读,阶段 5 创建)必须排除:它带有本机的 accessToken 与服务器地址,
        // 推到服务端的书源池会造成回环(见 SYNC_PLAN.md §5.4)
        val localSources = appDb.bookSourceDao.all.filterNot { isBuiltInSource(it.bookSourceUrl) }
        val localTombstones = appDb.syncTombstoneDao.getByType(SyncTombstone.TYPE_BOOK_SOURCE)
        val pushed = client.syncPush(localSources, localTombstones)

        config.lastSyncAt = System.currentTimeMillis()
        persistConfig(server, config)

        Result(
            remoteCount = remoteSources.size,
            pushed = pushed?.getAsJsonObject("bookSources")?.get("applied")?.asInt ?: 0,
            skipped = pushed?.getAsJsonObject("bookSources")?.get("skipped")?.asInt ?: 0,
            deleted = deleted,
            merged = merged
        )
    }

    /**
     * 把远端书源与墓碑合并进本地库,返回 (覆盖或新增数, 删除数)。
     *
     * 规则见 SYNC_PLAN.md §5.3:
     * - **墓碑优先于实体比较**,否则"删除后被另一端旧副本复活"。
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
        val remoteTombMap = remoteTombstones
            .filter { it.type == SyncTombstone.TYPE_BOOK_SOURCE }
            .associateBy { it.key }

        // 过期墓碑:实体在墓碑之后又被改过/重建过,墓碑不能再压制它
        val expiredTombstones = arrayListOf<String>()

        // 1. 远端墓碑优先于实体比较(否则"服务端删除后被本地旧副本复活")
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

        // 2. 本地墓碑**同样**优先于实体比较。
        // 少了这一步就会出现:本地删了某个源、服务端还留着 → 这一步把服务端旧副本插回本地,
        // 删除被自己撤销(和"服务端删除被本地复活"是同一个 bug 的镜像)。
        var merged = 0
        for (remote in remoteSources) {
            val key = remote.bookSourceUrl
            if (key.isEmpty() || remoteTombMap.containsKey(key) || isBuiltInSource(key)) {
                continue
            }
            localTombstones[key]?.let { localTomb ->
                if (localTomb.deletedAt >= remote.lastModifiedAt) {
                    // 本地删除不早于服务端实体 → 保留删除,推送阶段会让服务端也删
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
