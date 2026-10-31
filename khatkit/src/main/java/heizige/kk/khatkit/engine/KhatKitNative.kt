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

// ---- WASM 卡片（docscard-runtime-wasm ABI v1，见 khatkit-core/src/wasm.rs） ----

/** 加载 card.wasm；manifest 原文用于 ABI 校验；返回 0 表示失败。 */
external fun nativeCardLoad(wasm: ByteArray, manifest: String, dispatcher: Any): Long

/** kk_init：0 = 成功，非 0 = 错误码。 */
external fun nativeCardInit(handle: Long, manifest: String): Int

/** kk_handle：事件 JSON 进，结果 JSON 出（失败时是 {"__error":...}）。 */
external fun nativeCardHandle(handle: Long, event: String): String?

/** kk_shutdown。 */
external fun nativeCardShutdown(handle: Long): Int

/** 释放卡片实例。 */
external fun nativeCardClose(handle: Long)
