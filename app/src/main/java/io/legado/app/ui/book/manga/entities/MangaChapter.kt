package io.legado.app.ui.book.manga.entities

import io.legado.app.data.entities.BookChapterEntity

data class MangaChapter(
    val chapter: BookChapterEntity,
    val pages: List<BaseMangaPage>,
    val imageCount: Int
)
