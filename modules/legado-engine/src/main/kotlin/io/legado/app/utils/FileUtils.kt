package io.legado.app.utils

import io.legado.app.platform.Platform
import java.io.File

/**
 * 引擎版 FileUtils(从 readerMT `utils/FileUtils.kt` 抽取引擎所需子集,纯 JVM)。
 *
 * `appCtx.externalCache` → `Platform.context.externalCache`(回退 `cacheDir`)。
 * `JsExtensions.getFile`/`deleteFile` 与 `downloadFile`(闭包)用。其余 Android 专属
 * 方法(`getSdCardPath`/`Environment` 等)不进引擎。
 */
object FileUtils {

    fun createFolderIfNotExist(root: File, vararg subDirs: String): File {
        val filePath = getPath(root, *subDirs)
        return createFolderIfNotExist(filePath)
    }

    fun createFolderIfNotExist(filePath: String): File {
        val file = File(filePath)
        //如果文件夹不存在，就创建它
        if (!file.exists()) {
            file.mkdirs()
        }
        return file
    }

    fun getPath(rootPath: String, vararg subDirFiles: String): String {
        val path = StringBuilder(rootPath)
        subDirFiles.forEach {
            if (it.isNotEmpty()) {
                if (!path.endsWith(File.separator)) {
                    path.append(File.separator)
                }
                path.append(it)
            }
        }
        return path.toString()
    }

    fun getPath(root: File, vararg subDirFiles: String): String {
        val path = StringBuilder(root.absolutePath)
        subDirFiles.forEach {
            if (it.isNotEmpty()) {
                path.append(File.separator).append(it)
            }
        }
        return path.toString()
    }

    /** 缓存根路径(app `appCtx.externalCache.absolutePath`;引擎回退 `cacheDir`)。 */
    fun getCachePath(): String {
        return Platform.context.externalCache?.absolutePath ?: Platform.context.cacheDir.absolutePath
    }

    fun delete(file: File, deleteRootDir: Boolean = false): Boolean {
        var result = false
        if (file.isFile) {
            //是文件
            result = deleteResolveEBUSY(file)
        } else {
            //是目录
            val files = file.listFiles() ?: return false
            if (files.isEmpty()) {
                result = deleteRootDir && deleteResolveEBUSY(file)
            } else {
                for (f in files) {
                    delete(f, deleteRootDir)
                    result = deleteResolveEBUSY(f)
                }
            }
            if (deleteRootDir) {
                result = deleteResolveEBUSY(file)
            }
        }
        return result
    }

    /**
     * bug: open failed: EBUSY (Device or resource busy)
     * fix: http://stackoverflow.com/questions/11539657/open-failed-ebusy-device-or-resource-busy
     */
    private fun deleteResolveEBUSY(file: File): Boolean {
        // Before you delete a Directory or File: rename it!
        val to = File(file.absolutePath + System.currentTimeMillis())
        file.renameTo(to)
        return to.delete()
    }

    fun delete(path: String, deleteRootDir: Boolean = true): Boolean {
        val file = File(path)
        return delete(file, deleteRootDir)
    }
}