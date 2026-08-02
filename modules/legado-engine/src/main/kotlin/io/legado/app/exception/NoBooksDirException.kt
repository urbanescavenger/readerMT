package io.legado.app.exception

/**
 * 书籍目录不存在/不可用。
 *
 * 引擎版剥离了 Android R 资源字符串(原 `appCtx.getString(R.string.no_books_dir)`),
 * 改用默认消息;Android `:app` 仍保留本地化版本直至 Phase 1c switchover。
 * 调用点 `NoBooksDirException()` 因默认参数仍可编译。
 */
class NoBooksDirException(msg: String = "no books dir") : NoStackTraceException(msg)