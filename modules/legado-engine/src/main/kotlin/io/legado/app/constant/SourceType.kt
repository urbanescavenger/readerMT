package io.legado.app.constant

@Suppress("ConstPropertyName")
object SourceType {

    const val book = 0
    const val rss = 1

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

}