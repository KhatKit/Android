package heizige.kk.khatkit.ui

/** Pure validation result: no Android resources or translated text is persisted. */
data class UiFieldIssue(val code: Code, val step: Double? = null) {
    enum class Code { REQUIRED, BOOLEAN_REQUIRED, COUNTER_RANGE, TEXT_REQUIRED, PATH_REQUIRED, NUMBER_REQUIRED, OUT_OF_RANGE, STEP_MISMATCH, OPTION_REQUIRED }

    /** Compatibility diagnostics for scripts and existing non-UI clients. */
    fun diagnosticMessage(): String = when (code) {
        Code.REQUIRED -> "请填写此项"
        Code.BOOLEAN_REQUIRED -> "请输入布尔值"
        Code.COUNTER_RANGE -> "计数须为 0..10 的整数"
        Code.TEXT_REQUIRED -> "请输入文本"
        Code.PATH_REQUIRED -> "请输入文件路径"
        Code.NUMBER_REQUIRED -> "请输入有效数字"
        Code.OUT_OF_RANGE -> "数值超出范围"
        Code.STEP_MISMATCH -> "数值不符合步长 $step"
        Code.OPTION_REQUIRED -> "请选择列表中的值"
    }
}
