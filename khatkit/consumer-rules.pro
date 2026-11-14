# KhatKit 原生核心：Rust 导出的 JNI 符号按「类名 + 方法名」解析，
# R8 改名或移除会导致 UnsatisfiedLinkError。
-keep class heizige.kk.khatkit.engine.KhatKitNative { *; }
-keepclasseswithmembernames,includedescriptorclasses class heizige.kk.khatkit.engine.KhatKitNative { native <methods>; }

# Shizuku UserService / AIDL：由系统与 Shizuku 按类名绑定。
-keep class heizige.kk.khatkit.bridge.impl.KhatKitUserService { *; }
-keep interface heizige.kk.khatkit.bridge.impl.IKhatKitUserService { *; }
-keep class heizige.kk.khatkit.bridge.impl.IKhatKitUserService$Stub { *; }
-keep class heizige.kk.khatkit.bridge.impl.IKhatKitUserService$Stub$Proxy { *; }

# 脚本通过方法名反射调用 bridge 实现，保留其公开方法名。
-keepclassmembers class heizige.kk.khatkit.bridge.impl.* {
    public <methods>;
}

# 卡片运行时：khatkit-core(Rust) 通过 JNI 按「方法名 + 签名」回调分派器，
# 见 khatkit-core/src/host.rs（dispatch / interruptReason / wasmAllowed）。
# R8 改名后 GetMethodID 失败，正式包（release）里所有 Lua/JS/WASM 卡片都跑不起来，
# debug 包不受影响——这类问题只在 release 包暴露，务必保留类名和方法名。
-keep class heizige.kk.khatkit.engine.RustBridgeDispatcher { *; }
