package io.legado.app.model.analyzeRule

interface RuleDataInterface {

    val variableMap: HashMap<String, String>

    fun putVariable(key: String, value: String?): Boolean {
        val keyExist = variableMap.contains(key)
        return when {
            value == null -> {
                variableMap.remove(key)
                putBigVariable(key, null)
                keyExist
            }

            value.length < 10000 -> {
                putBigVariable(key, null)
                variableMap[key] = value
                true
            }

            else -> {
                variableMap.remove(key)
                putBigVariable(key, value)
                keyExist
            }
        }
    }

    /**
     * 存大变量(≥10000 字符)。默认无持久化(内存 `variableMap` 已够,大变量留给实现端
     * 用 `:app` 的 `RuleBigDataHelp` 落盘);`:server` 的实体不实现此方法,走默认空操作。
     */
    fun putBigVariable(key: String, value: String?) {}

    fun getVariable(key: String): String {
        return variableMap[key] ?: getBigVariable(key) ?: ""
    }

    /** 取大变量。默认无持久化返回 null;实现端 override(如 `:app` RuleBigDataHelp)。 */
    fun getBigVariable(key: String): String? = null

}