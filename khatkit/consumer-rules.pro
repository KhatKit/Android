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
