package heizige.kk.khatkit.engine

/**
 * 通过反射把 JSON 参数派发到 bridge 对象的方法上。
 * Lua/JS 脚本都用同一套点号 API，这里负责把参数转成方法签名需要的类型。
 */
internal object ReflectiveInvoker {

    fun methodNames(target: Any): List<String> =
        target.javaClass.methods
            .asSequence()
            .filter { it.declaringClass != Any::class.java }
            .filter { !it.name.contains('$') }
            .map { it.name }
            .distinct()
            .toList()

    fun invoke(target: Any, methodName: String, args: List<Any?>): Any? {
        val method = target.javaClass.methods.firstOrNull { it.name == methodName }
            ?: error("unknown method: $methodName")
        val params = method.parameterTypes
        val converted = Array<Any?>(params.size) { index -> convert(args.getOrNull(index), params[index]) }
        return method.invoke(target, *converted)
    }

    private fun convert(value: Any?, type: Class<*>): Any? = when (type) {
        String::class.java -> value?.toString()
        // 原始类型：脚本可以省略参数，缺省补 0/false，避免反射调用直接炸
        Int::class.javaPrimitiveType -> (value as? Number)?.toInt() ?: 0
        Long::class.javaPrimitiveType -> (value as? Number)?.toLong() ?: 0L
        Double::class.javaPrimitiveType -> (value as? Number)?.toDouble() ?: 0.0
        Float::class.javaPrimitiveType -> (value as? Number)?.toFloat() ?: 0f
        Boolean::class.javaPrimitiveType ->
            value as? Boolean ?: value?.toString()?.toBooleanStrictOrNull() ?: false
        Int::class.javaObjectType -> (value as? Number)?.toInt()
        Long::class.javaObjectType -> (value as? Number)?.toLong()
        Double::class.javaObjectType -> (value as? Number)?.toDouble()
        Float::class.javaObjectType -> (value as? Number)?.toFloat()
        Boolean::class.javaObjectType -> value as? Boolean ?: value?.toString()?.toBooleanStrictOrNull()
        else -> value
    }
}
