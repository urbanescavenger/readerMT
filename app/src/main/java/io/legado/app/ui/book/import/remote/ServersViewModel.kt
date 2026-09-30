package io.legado.app.ui.book.import.remote

import android.app.Application
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Server
import io.legado.app.model.sync.SyncManager
import io.legado.app.utils.toastOnUi

class ServersViewModel(application: Application): BaseViewModel(application) {


    fun delete(server: Server) {
        execute {
            appDb.serverDao.delete(server)
        }
    }

    /**
     * 手动同步(书源 + 书架元数据,SYNC_PLAN.md 阶段 2/3)。
     *
     * 不做类型预检:非阅读服务器时 [SyncManager] 会抛"该服务器不是阅读服务器",
     * 统一走 onError 提示即可,免得两处判断口径不一致。
     */
    fun sync(server: Server) {
        execute {
            SyncManager.sync(server)
        }.onSuccess { result ->
            context.toastOnUi(
                context.getString(
                    R.string.sync_success,
                    result.pushed,
                    result.merged,
                    result.deleted
                )
            )
        }.onError {
            context.toastOnUi(context.getString(R.string.sync_failed, it.localizedMessage ?: ""))
        }
    }

}
