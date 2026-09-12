@file:JvmName("KhatKitNative")

package heizige.kk.khatkit.engine

/**
 * Rust 核心（mlua + rquickjs）的 JNI 入口。
 *
 * 与 `khatkit-core` crate 的导出符号一一对应，见 khatkit-core/src/lib.rs。
 */
external fun nativeCreate(kind: Int, dispatcher: Any): Long

external fun nativeDefine(handle: Long, bridge: String, methodsCsv: String)

external fun nativeDefineModule(handle: Long, name: String, source: String)

external fun nativeEval(handle: Long, script: String, argsJson: String): String?

external fun nativeClose(handle: Long)
