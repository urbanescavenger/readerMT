package io.legado.app.help.source

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.legado.app.constant.SourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSourceEntity
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.RssSource
import io.legado.app.help.AppCacheManager
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.sync.SyncTombstoneHelp
import io.legado.app.model.AudioPlay
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.VideoPlay
import io.legado.app.service.VideoPlayService
import io.legado.app.ui.video.VideoPlayerActivity
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

object SourceHelp {

    private val list18Plus by lazy {
        try {
            return@lazy String(appCtx.assets.open("18PlusList.txt").readBytes())
                .splitNotBlank("\n").map {
                    EncoderUtils.base64Decode(it)
                }.toHashSet()
        } catch (_: Exception) {
            return@lazy emptySet()
        }
    }

    fun getSource(key: String?): BaseSource? {
        key ?: return null
        if (ReadBook.bookSource?.bookSourceUrl == key) {
            return ReadBook.bookSource
        } else if (AudioPlay.bookSource?.bookSourceUrl == key) {
            return AudioPlay.bookSource
        } else if (ReadManga.bookSource?.bookSourceUrl == key) {
            return ReadManga.bookSource
        } else if (VideoPlay.source?.getKey() == key) {
            return VideoPlay.source
        }
        return appDb.bookSourceDao.getBookSource(key)
            ?: appDb.rssSourceDao.getByKey(key)
    }

    fun getSource(key: String?, @SourceType.Type type: Int): BaseSource? {
        key ?: return null
        return when (type) {
            SourceType.book -> appDb.bookSourceDao.getBookSource(key)
            SourceType.rss -> appDb.rssSourceDao.getByKey(key)
            else -> null
        }
    }

    fun deleteSource(key: String, @SourceType.Type type: Int) {
        when (type) {
            SourceType.book -> deleteBookSource(key)
            SourceType.rss -> deleteRssSource(key)
        }
    }

    fun deleteBookSourceParts(sources: List<BookSourcePart>) {
        appDb.runInTransaction {
            sources.forEach {
                deleteBookSourceInternal(it.bookSourceUrl)
            }
        }
        AppCacheManager.clearSourceVariables()
    }

    fun deleteBookSources(sources: List<BookSourceEntity>) {
        appDb.runInTransaction {
            sources.forEach {
                deleteBookSourceInternal(it.bookSourceUrl)
            }
        }
        AppCacheManager.clearSourceVariables()
    }

    fun deleteBookSourceKeys(keys: Collection<String>) {
        appDb.runInTransaction {
            keys.forEach {
                deleteBookSourceInternal(it)
            }
        }
        AppCacheManager.clearSourceVariables()
    }

    /**
     * 同步合并专用:删除本地书源及其缓存,但**不**记墓碑。
     *
     * 墓碑由服务端同步过来,它的 `deletedAt` 才是权威时间;若在这里重记会被刷成 now,
     * 让"本地删除"显得比实际更新,进而可能删掉另一端更晚的重新创建。
     */
    fun deleteBookSourceFromSync(key: String) {
        deleteBookSourceInternal(key, recordTombstone = false)
    }

    private fun deleteBookSourceInternal(key: String, recordTombstone: Boolean = true) {
        if (recordTombstone) {
            // 双端同步:删除必须留墓碑,否则下次同步会被另一端的旧副本复活
            SyncTombstoneHelp.recordBookSource(key)
        }
        appDb.bookSourceDao.delete(key)
        appDb.cacheDao.deleteSourceVariables(key)
        SourceConfig.removeSource(key)
    }

    fun deleteBookSource(key: String) {
        deleteBookSourceInternal(key)
        AppCacheManager.clearSourceVariables()
    }

    fun deleteRssSources(sources: List<RssSource>) {
        appDb.runInTransaction {
            sources.forEach {
                deleteRssSourceInternal(it.sourceUrl)
            }
        }
        AppCacheManager.clearSourceVariables()
    }

    private fun deleteRssSourceInternal(key: String) {
        appDb.rssSourceDao.delete(key)
        appDb.rssArticleDao.delete(key)
        appDb.cacheDao.deleteSourceVariables(key)
    }

    fun deleteRssSource(key: String) {
        deleteRssSourceInternal(key)
        AppCacheManager.clearSourceVariables()
    }

    fun enableSource(key: String, @SourceType.Type type: Int, enable: Boolean) {
        when (type) {
            SourceType.book -> appDb.bookSourceDao.enable(key, enable)
            SourceType.rss -> appDb.rssSourceDao.enable(key, enable)
        }
    }

    fun insertRssSource(vararg rssSources: RssSource) {
        val rssSourcesGroup = rssSources.groupBy {
            is18Plus(it.sourceUrl)
        }
        rssSourcesGroup[true]?.forEach {
            appCtx.toastOnUi("${it.sourceName}是18+网址,禁止导入.")
        }
        rssSourcesGroup[false]?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }
    }

    fun insertBookSource(vararg bookSources: BookSourceEntity) {
        val bookSourcesGroup = bookSources.groupBy {
            is18Plus(it.bookSourceUrl)
        }
        bookSourcesGroup[true]?.forEach {
            appCtx.toastOnUi("${it.bookSourceName}是18+网址,禁止导入.")
        }
        bookSourcesGroup[false]?.let {
            // 双端同步:导入/订阅更新都算"内容被修改",刷新 LWW 版本号
            val now = System.currentTimeMillis()
            it.forEach { source -> source.lastModifiedAt = now }
            appDb.bookSourceDao.insert(*it.toTypedArray())
        }
        Coroutine.async {
            adjustSortNumber()
        }
    }

    private fun is18Plus(url: String?): Boolean {
        if (list18Plus.isEmpty()) {
            return false
        }
        url ?: return false
        val baseUrl = NetworkUtils.getBaseUrl(url) ?: return false
        kotlin.runCatching {
            val host = baseUrl.split("//", ".").let {
                if (it.size > 2) "${it[it.lastIndex - 1]}.${it.last()}" else return false
            }
            return list18Plus.contains(host)
        }
        return false
    }

    /**
     * 调整排序序号
     */
    fun adjustSortNumber() {
        if (
            appDb.bookSourceDao.maxOrder > 99999
            || appDb.bookSourceDao.minOrder < -99999
            || appDb.bookSourceDao.hasDuplicateOrder
        ) {
            val sources = appDb.bookSourceDao.allPart
            sources.forEachIndexed { index, bookSource ->
                bookSource.customOrder = index
            }
            appDb.bookSourceDao.upOrder(sources)
        }
    }

    fun openVideoPlayer(source: BaseSource?, url: String, title: String, isFloat: Boolean) {
        if (isFloat) {
            val intent = Intent(appCtx, VideoPlayService::class.java).apply {
                putExtra("videoUrl", url)
                putExtra("videoTitle", title)
                putExtra("sourceKey", source?.getKey())
                putExtra("sourceType", source?.getSourceType())
            }
            ContextCompat.startForegroundService(appCtx, intent)
        } else {
            appCtx.startActivity<VideoPlayerActivity> {
                putExtra("videoUrl", url)
                putExtra("videoTitle", title)
                putExtra("sourceKey", source?.getKey())
                putExtra("sourceType", source?.getSourceType())
            }
        }
    }

}