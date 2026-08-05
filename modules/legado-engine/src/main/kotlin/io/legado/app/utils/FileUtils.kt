package io.legado.app.utils

import io.legado.app.platform.Platform
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 引擎版 FileUtils(从 readerMT `utils/FileUtils.kt` 抽取引擎所需子集,纯 JVM)。
 *
 * `appCtx.externalCache` → `Platform.context.externalCache`(回退 `cacheDir`)。
 * `JsExtensions.getFile`/`deleteFile` 与 `downloadFile`(闭包)用。其余 Android 专属
 * 方法(`getSdCardPath`/`Environment`/`getMimeType`(MimeTypeMap) 等)不进引擎,app 侧 shim。
 */
object FileUtils {

    fun createFileIfNotExist(root: File, vararg subDirFiles: String): File {
        val filePath = getPath(root, *subDirFiles)
        return createFileIfNotExist(filePath)
    }

    @Synchronized
    fun createFileIfNotExist(filePath: String): File {
        val file = File(filePath)
        try {
            if (!file.exists()) {
                //创建父类文件夹
                file.parent?.let {
                    createFolderIfNotExist(it)
                }
                //创建文件
                file.createNewFile()
            }
        } catch (e: Exception) {
            throw RuntimeException("无法创建文件: $filePath", e)
        }
        return file
    }

    fun createFileWithReplace(filePath: String): File {
        val file = File(filePath)
        if (!file.exists()) {
            //创建父类文件夹
            file.parent?.let {
                createFolderIfNotExist(it)
            }
            //创建文件
            file.createNewFile()
        } else {
            file.delete()
            file.createNewFile()
        }
        return file
    }

    fun exist(path: String): Boolean {
        val file = File(path)
        return file.exists()
    }

    /** 获取文件后缀,不包括 ".". */
    fun getExtension(pathOrUrl: String): String {
        val dotPos = pathOrUrl.lastIndexOf('.')
        return if (0 <= dotPos) {
            pathOrUrl.substring(dotPos + 1)
        } else {
            "ext"
        }
    }

    fun getName(path: String?): String {
        if (path == null) {
            return ""
        }
        val pos = path.lastIndexOf(File.separator)
        return if (0 <= pos) {
            path.substring(pos + 1)
        } else {
            path
        }
    }

    /** 获取文件名(不包括扩展名). */
    fun getNameExcludeExtension(path: String): String {
        return try {
            var fileName = File(path).name
            val lastIndexOf = fileName.lastIndexOf(".")
            if (lastIndexOf != -1) {
                fileName = fileName.substring(0, lastIndexOf)
            }
            fileName
        } catch (e: Exception) {
            ""
        }
    }

    fun separator(path: String): String {
        var path1 = path
        val separator = File.separator
        path1 = path1.replace("\\", separator)
        if (!path1.endsWith(separator)) {
            path1 += separator
        }
        return path1
    }

    fun move(src: String, tar: String): Boolean {
        return move(File(src), File(tar))
    }

    fun move(src: File, tar: File): Boolean {
        return rename(src, tar)
    }

    fun rename(oldPath: String, newPath: String): Boolean {
        return rename(File(oldPath), File(newPath))
    }

    fun rename(src: File, tar: File): Boolean {
        return src.renameTo(tar)
    }

    /** 获取格式化后的文件/目录创建或最后修改时间. */
    @JvmOverloads
    fun getDateTime(path: String, format: String = "yyyy年MM月dd日HH:mm"): String {
        val file = File(path)
        val time = if (file.exists()) file.lastModified() else 0L
        return if (time == 0L) "" else SimpleDateFormat(format).format(Date(time))
    }

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

    /** 写字节到文件(reader-mt `FileUtils.writeBytes`)。 */
    fun writeBytes(filepath: String, data: ByteArray): Boolean {
        val file = File(filepath)
        var fos: FileOutputStream? = null
        return try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            fos = FileOutputStream(filepath)
            fos.write(data)
            true
        } catch (e: IOException) {
            false
        } finally {
            fos?.close()
        }
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