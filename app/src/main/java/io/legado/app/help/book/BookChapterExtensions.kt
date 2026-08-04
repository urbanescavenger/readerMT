@file:Suppress("unused")

package io.legado.app.help.book

import io.legado.app.data.entities.BookChapterEntity
import io.legado.app.help.RuleBigDataHelp.getDanmakuFile

fun BookChapterEntity.getDanmaku(): Any? { //读取弹幕数据
    return variableMap["danmaku"] ?: getDanmakuFile(bookUrl, url)
}