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

/** 不存在则创建(reader-mt `FileExtensions.createFileIfNotExist`;BookHelp 写图链用)。 */
fun File.createFileIfNotExist(): File {
    if (!exists()) {
        parent?.let { File(it).mkdirs() }
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

/** 删除已存在目录并重建(reader-mt `FileExtensions.createFolderReplace`)。 */
fun File.createFolderReplace(): File {
    if (exists()) {
        FileUtils.delete(this, true)
    }
    mkdirs()
    return this
}

/**
 * 在 [this] 目录下按子路径拼出 File(reader-mt `FileExtensions.getFile`)。
 * `:server` 的 `BookHelp` 用它定位书籍缓存目录。
 */
fun File.getFile(vararg subDirFiles: String): File {
    val path = FileUtils.getPath(this, *subDirFiles)
    return File(path)
}

/** 按子路径拼出 File(reader-mt `FileExtensions.exists`;命名沿用原版)。 */
fun File.exists(vararg subDirFiles: String): File {
    val path = FileUtils.getPath(this, *subDirFiles)
    return File(path)
}

/** 写测试(reader-mt `FileExtensions.checkWrite`;FileDocExtensions 359 用)。 */
fun File.checkWrite(): Boolean {
    var file: File? = null
    return try {
        val filename = System.currentTimeMillis().toString()
        file = FileUtils.createFileIfNotExist(this, filename)
        file.outputStream().bufferedWriter().use { it.write(filename) }
        file.inputStream().bufferedReader().use { it.readText() == filename }
    } catch (e: Exception) {
        false
    } finally {
        file?.delete()
    }
}