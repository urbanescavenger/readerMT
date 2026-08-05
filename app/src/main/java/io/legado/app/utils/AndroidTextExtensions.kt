package io.legado.app.utils

import android.text.Editable

/** Android 专属文本扩展 —— 原 `StringExtensions.toEditable`(被删副本),留 `:app`。 */
fun String.toEditable(): Editable = Editable.Factory.getInstance().newEditable(this)
