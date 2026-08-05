package io.legado.app.platform

import io.legado.app.data.entities.BaseBook
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSourceEntity
import io.legado.app.model.webBook.WebBook
import io.legado.app.platform.webbook.WebBookProvider

/**
 * Android `:app` 侧对引擎 [WebBookProvider] 的实现(Phase 1c switchover)。
 *
 * 引擎 `AnalyzeRule.reGetBook`/`refreshTocUrl`(preUpdate JS 重抓)经 `Platform.webBook`
 * 反调 app 真 `WebBook`(model/webBook)——即原 master 的实现,非 NoOp。
 *
 * app `WebBook` 参数用 Room 实体 `BookSourceEntity`/`Book`,而引擎 SPI 用引擎接口
 * `BaseSource`/`BaseBook`;因 `BookSourceEntity : BaseSource`、`Book : BaseBook` 已在
 * app 成立,用 `as` 向下转型即可。
 *
 * 注意:SPI 第 3 参 [skipToc] 与 app [io.legado.app.model.webBook.WebBook.getBookInfoAwait]
 * 的 [canReName] 语义不同,但引擎当前恒传 `false`,按位置透传为 `canReName=false`(preUpdate
 * 重抓时不重命名,等价)。`Result<Book>` 因 `Result` 协变(`out T`)可直接作为 `Result<BaseBook>`。
 */
class AndroidWebBookProvider : WebBookProvider {

    override suspend fun preciseSearchAwait(source: BaseSource, name: String, author: String): Result<BaseBook> =
        WebBook.preciseSearchAwait(source as BookSourceEntity, name, author)

    override suspend fun getBookInfoAwait(source: BaseSource, book: BaseBook, skipToc: Boolean): Result<BaseBook> =
        runCatching { WebBook.getBookInfoAwait(source as BookSourceEntity, book as Book, canReName = skipToc) }
}
