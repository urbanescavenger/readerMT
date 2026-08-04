package io.legado.app.utils

import java.text.DecimalFormat

/**
 * `android.text.TextUtils` 的平台无关替代(计划 §3.8)。
 *
 * 引擎源码把 `TextUtils.isEmpty(...)` / `TextUtils.join(...)` 改调 [StringUtils]。
 */
object StringUtils {

    /** 等价 `android.text.TextUtils.isEmpty`:null 或长度为 0 返回 true(不把纯空白当空)。 */
    fun isEmpty(s: CharSequence?): Boolean = s.isNullOrEmpty()

    /** 等价 `android.text.TextUtils.join(delimiter, tokens)`。 */
    fun join(delimiter: CharSequence, tokens: Iterable<*>?): String =
        tokens?.joinToString(delimiter.toString()) ?: ""

    /** 等价 `android.text.TextUtils.join` 的数组重载。 */
    fun join(delimiter: CharSequence, tokens: Array<*>?): String =
        tokens?.joinToString(delimiter.toString()) ?: ""

    private val chnMap: HashMap<Char, Int>
        get() {
            val map = HashMap<Char, Int>()
            var cnStr = "零一二三四五六七八九十"
            var c = cnStr.toCharArray()
            for (i in 0..10) {
                map[c[i]] = i
            }
            cnStr = "〇壹贰叁肆伍陆柒捌玖拾"
            c = cnStr.toCharArray()
            for (i in 0..10) {
                map[c[i]] = i
            }
            map['两'] = 2
            map['百'] = 100
            map['佰'] = 100
            map['千'] = 1000
            map['仟'] = 1000
            map['万'] = 10000
            map['亿'] = 100000000
            return map
        }

    /** 全角转半角(readerMT `StringUtils.fullToHalf`)。 */
    fun fullToHalf(input: String): String {
        val c = input.toCharArray()
        for (i in c.indices) {
            if (c[i].code == 12288) {
                //全角空格
                c[i] = 32.toChar()
                continue
            }
            if (c[i].code in 65281..65374)
                c[i] = (c[i].code - 65248).toChar()
        }
        return String(c)
    }

    /** 中文数字转整数(readerMT `StringUtils.chineseNumToInt`)。 */
    fun chineseNumToInt(chNum: String): Int {
        var result = 0
        var tmp = 0
        var billion = 0
        val cn = chNum.toCharArray()

        // "一零二五" 形式
        if (cn.size > 1 && chNum.matches("^[〇零一二三四五六七八九壹贰叁肆伍陆柒捌玖]$".toRegex())) {
            for (i in cn.indices) {
                cn[i] = (48 + chnMap[cn[i]]!!).toChar()
            }
            return Integer.parseInt(String(cn))
        }

        // "一千零二十五", "一千二" 形式
        return kotlin.runCatching {
            for (i in cn.indices) {
                val tmpNum = chnMap[cn[i]]!!
                when {
                    tmpNum == 100000000 -> {
                        result += tmp
                        result *= tmpNum
                        billion = billion * 100000000 + result
                        result = 0
                        tmp = 0
                    }

                    tmpNum == 10000 -> {
                        result += tmp
                        result *= tmpNum
                        tmp = 0
                    }

                    tmpNum >= 10 -> {
                        if (tmp == 0)
                            tmp = 1
                        result += tmpNum * tmp
                        tmp = 0
                    }

                    else -> {
                        tmp = if (i >= 2 && i == cn.size - 1 && chnMap[cn[i - 1]]!! > 10)
                            tmpNum * chnMap[cn[i - 1]]!! / 10
                        else
                            tmp * 10 + tmpNum
                    }
                }
            }
            result += tmp + billion
            result
        }.getOrDefault(-1)
    }

    /** 字符串转数字:先 parseInt,失败转中文数字(readerMT `StringUtils.stringToInt`)。 */
    fun stringToInt(str: String?): Int {
        if (str != null) {
            val num = fullToHalf(str).replace("\\s+".toRegex(), "")
            return kotlin.runCatching {
                Integer.parseInt(num)
            }.getOrElse {
                chineseNumToInt(num)
            }
        }
        return -1
    }

    /** 是否为纯数字字符串(reader-mt `StringUtils.isNumeric`)。 */
    fun isNumeric(str: String): Boolean {
        val pattern = java.util.regex.Pattern.compile("-?[0-9]+")
        val isNum = pattern.matcher(str)
        return isNum.matches()
    }

    /** 字数格式化:纯数字转 "N字"/"N万字",否则原样(reader-mt `StringUtils.wordCountFormat`)。 */
    fun wordCountFormat(wc: String?): String {
        if (wc == null) return ""
        var wordsS = ""
        if (isNumeric(wc)) {
            val words: Int = wc.toInt()
            if (words > 0) {
                wordsS = words.toString() + "字"
                if (words > 10000) {
                    val df = DecimalFormat("#.#")
                    wordsS = df.format(words * 1.0f / 10000f.toDouble()) + "万字"
                }
            }
        } else {
            wordsS = wc
        }
        return wordsS
    }

    /**
     * 移除字符串首尾空字符的高效方法(利用ASCII值判断,包括全角空格)。
     * reader-mt `StringUtils.trim`。
     */
    fun trim(s: String): String {
        if (s.isEmpty()) return ""
        var start = 0
        val len = s.length
        var end = len - 1
        while (start < end && (s[start].code <= 0x20 || s[start] == '　')) {
            ++start
        }
        while (start < end && (s[end].code <= 0x20 || s[end] == '　')) {
            --end
        }
        return s.substring(start, end + 1)
    }
}