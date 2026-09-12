package heizige.kk.khatkit.engine

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua

/**
 * Kotlin <-> Lua 值转换。
 *
 * 基础类型/集合转成原生 Lua 值；其余对象交给 CoerceJavaToLua 包成 userdata，
 * 这样脚本可以直接 `tool.compressImage(...)` 调用宿主方法。
 */
object LuaValues {

    fun toLua(value: Any?): LuaValue = when (value) {
        null -> LuaValue.NIL
        is LuaValue -> value
        is String -> LuaValue.valueOf(value)
        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Short -> LuaValue.valueOf(value.toInt())
        is Byte -> LuaValue.valueOf(value.toInt())
        is Double -> LuaValue.valueOf(value)
        is Float -> LuaValue.valueOf(value.toDouble())
        is Map<*, *> -> LuaTable().apply {
            value.forEach { (k, v) -> set(k?.toString() ?: "null", toLua(v)) }
        }
        is Iterable<*> -> LuaTable().apply {
            var i = 1
            value.forEach { set(i++, toLua(it)) }
        }
        is Array<*> -> LuaTable().apply {
            value.forEachIndexed { index, v -> set(index + 1, toLua(v)) }
        }
        is BooleanArray -> LuaTable().apply {
            value.forEachIndexed { index, v -> set(index + 1, LuaValue.valueOf(v)) }
        }
        is IntArray -> LuaTable().apply {
            value.forEachIndexed { index, v -> set(index + 1, LuaValue.valueOf(v)) }
        }
        else -> CoerceJavaToLua.coerce(value)
    }

    fun fromLua(value: LuaValue): Any? = when {
        value.isnil() -> null
        value.isboolean() -> value.toboolean()
        value.isint() -> value.toint()
        value.isnumber() -> value.todouble()
        value.isstring() -> value.tojstring()
        value.istable() -> fromTable(value.checktable())
        else -> value.tojstring()
    }

    private fun fromTable(table: LuaTable): Any {
        val length = table.length()
        if (length > 0) {
            val list = ArrayList<Any?>(length)
            for (i in 1..length) list.add(fromLua(table.get(i)))
            return list
        }
        val map = LinkedHashMap<String, Any?>()
        for (key in table.keys()) {
            map[key.tojstring()] = fromLua(table.get(key))
        }
        return map
    }

    /** 脚本返回值统一收敛成 Map，便于回灌聊天上下文。 */
    fun asResultMap(value: LuaValue): Map<String, Any?> = when {
        value.istable() -> {
            @Suppress("UNCHECKED_CAST")
            (fromLua(value) as? Map<String, Any?>) ?: mapOf("result" to fromLua(value))
        }
        value.isnil() -> emptyMap()
        else -> mapOf("result" to fromLua(value))
    }
}
