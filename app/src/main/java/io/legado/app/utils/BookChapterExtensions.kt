package io.legado.app.utils

import io.legado.app.data.entities.BookChapterEntity

fun BookChapterEntity.internString() {
    title = title.intern()
    bookUrl = bookUrl.intern()
}
