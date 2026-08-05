package io.legado.app.model.analyzeRule

import androidx.media3.common.MediaItem
import com.bumptech.glide.load.model.GlideUrl
import io.legado.app.help.exoplayer.ExoPlayerHelper
import io.legado.app.help.glide.GlideHeaders

/**
 * Android 增强 —— 原 `AnalyzeUrl.getGlideUrl`/`getMediaItem`(引擎 AnalyzeUrl 纯 JVM,
 * Glide/Media3 留 `:app`)。`setCookie` 已在引擎改 public 供此调用。
 */
fun AnalyzeUrl.getGlideUrl(): GlideUrl {
    setCookie()
    return GlideUrl(url, GlideHeaders(headerMap))
}

fun AnalyzeUrl.getMediaItem(): MediaItem {
    setCookie()
    return ExoPlayerHelper.createMediaItem(url, headerMap)
}
