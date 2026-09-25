package heizige.kk.khatkit.dependency

/**
 * 原生依赖包入口约定（见 docs/dependency-system.md）。
 *
 * 依赖产物是与应用分离的 `jar`（内含 `classes.dex`），随云端卡片一起从 Hub 下载，
 * 校验 sha256 后由 [DependencyManager] 用 DexClassLoader 加载。入口类不强制实现本接口：
 * 实现 [ImageToolboxBridge] 等宿主接口即可获得类型化方法；只实现本接口时脚本用
 * `依赖名.call("方法名", "[参数JSON数组]")` 通用派发。
 *
 * 返回值约定：成功返回 JSON（字符串路径 / 路径数组 / 数字等），失败返回
 * `{"__error":"中文说明"}`；引擎会把它转成脚本可见的错误。
 */
interface KhatKitDependency {
    /** 通用派发：method 为依赖包方法名，argsJson 为参数 JSON 数组（如 `["/sdcard/a.jpg",800]`）。 */
    fun call(method: String, argsJson: String): String

    /** 依赖包暴露的方法名，JSON 字符串数组（如 `["resize","crop"]`）。 */
    fun availableMethods(): String
}
