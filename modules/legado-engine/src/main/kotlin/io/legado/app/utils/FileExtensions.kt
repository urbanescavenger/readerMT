package io.legado.app.utils

import java.io.File

/**
 * 引擎版 File 扩展(从 readerMT `utils/FileExtensions.kt` 抽取引擎所需子集,纯 JVM)。
 * `JsExtensions.downloadFile`(闭包)用 `File.createFileReplace`/`File.createFolderIfNotExist`。
 */
fun File.createFileReplace(): File {
    if (!exists()) {
        parent?.let {
            File(it).mkdirs()
        }
        createNewFile()
    } else {
        delete()
        createNewFile()
    }
    return this
}

fun File.createFolderIfNotExist(): File {
    if (!exists()) {
        mkdirs()
    }
    return this
}