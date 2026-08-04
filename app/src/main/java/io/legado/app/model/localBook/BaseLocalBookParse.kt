package io.legado.app.model.localBook

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapterEntity
import java.io.InputStream

/**
 *companion object interface
 *see EpubFile.kt
 */
interface BaseLocalBookParse {

    fun upBookInfo(book: Book)

    fun getChapterList(book: Book): ArrayList<BookChapterEntity>

    fun getContent(book: Book, chapter: BookChapterEntity): String?

    fun getImage(book: Book, href: String): InputStream?

}
