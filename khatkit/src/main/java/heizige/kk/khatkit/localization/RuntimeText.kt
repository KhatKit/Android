package heizige.kk.khatkit.localization

/** Pure host-copy token. Inject a resolver for UI; default diagnostics remain SDK-compatible. */
data class RuntimeText(val code: Code, val arguments: List<Any?> = emptyList()) {
    enum class Code(val diagnosticTemplate: String) {
        DEPENDENCY_HOST_UNSUPPORTED("卡片 %1\$s 依赖原生依赖包（%2\$s），但当前宿主不支持依赖包运行时；请升级 KhatKit 后重试。"),
        DEPENDENCY_PREPARING("正在准备依赖包：%1\$s %2\$s"),
        DEPENDENCY_FALLBACK("原依赖版本不可用，正在尝试备用版本：%1\$s %2\$s"),
        DEPENDENCY_UNAVAILABLE("依赖包 %1\$s %2\$s 不可用：%3\$s"),
        DEPENDENCY_UPDATE_CARD("请更新卡片或从卡片市场安装最新版本"),
        DEPENDENCY_ALL_UNAVAILABLE("依赖包 %1\$s 全部候选版本均不可用"),
        RUNTIME_MISSING_CAPABILITIES("设备缺少卡片所需能力：%1\$s。请在聊天输入框「+」面板中开启对应权限（root / Shizuku / 无障碍 / 所有文件访问）后重试。"),
        RUNTIME_BRIDGE_INJECTION("bridge 注入失败"),
        RUNTIME_COMMAND_MISSING("command 字段为空"),
        RUNTIME_COMMAND_UNAVAILABLE("宿主未提供命令执行能力"),
        RUNTIME_COMMAND_FAILED("命令执行失败"),
        HUB_CATALOG_UNAVAILABLE("工具目录暂时无法连接"),
        HUB_TOKEN_EXPIRED("令牌无效或已过期，请重新激活套餐"),
        HUB_RATE_LIMITED("请求过于频繁，请稍后再试"),
        HUB_QUOTA_INSUFFICIENT("套餐工具次数不足，请充值或续费"),
        HUB_QUOTA_DENIED("套餐工具次数不足，本次调用被拒绝"),
        HUB_CALL_DENIED("调用被拒绝"),
        HUB_RESPONSE_INVALID("市场响应格式无效"),
        HUB_ITEMS_MISSING("市场响应缺少 items"),
        HUB_NETWORK_ERROR("网络错误：%1\$s（请检查网络或 Hub 地址）"),
        HUB_BAD_REQUEST("请求参数有误（400）"),
        HUB_ENTITLEMENT_UNAVAILABLE("套餐工具次数不足或权益不可用"),
        HUB_ENDPOINT_MISSING("服务端未提供该接口（Hub 可能尚未部署）"),
        HUB_REQUEST_TIMEOUT("请求超时"),
        HUB_SERVER_ERROR("服务器错误（%1\$d），请稍后重试"),
        HUB_REQUEST_FAILED("请求失败（%1\$d）"),
        DEPENDENCY_TRIED_VERSIONS("已尝试清单中的 %1\$d 个版本 %2\$s"),
    }

    fun diagnosticMessage(): String = String.format(java.util.Locale.ROOT, code.diagnosticTemplate, *arguments.toTypedArray())
}
