# 卡片运行时：默认 WASM，Lua/JS 改为市场模块

> 状态：设计稿（2026-10-31）。目标读者：卡片作者、运行时维护者、市场运营。
> 相关现状：`khatkit/src/main/java/heizige/kk/khatkit/engine/ScriptEngine.kt`（引擎 SPI）、
> `RustScriptEngine.kt`（mlua + rquickjs 的同一份 native 核心，`libkhatkit_core.so` ≈2.0MB）、
> `app/core/data/ocr/`（原生插件安装器/加载器，本方案复用）、
> `KodeHeapServer/docs/extension-market.md`（市场 kind 与发布协议）。

## 1. 目标

1. **卡片默认跑 WASM**：新卡片以 WebAssembly 作为标准目标，沙箱更强、可跨语言、体积可控。
2. **Lua / JS 改为可选运行时模块**：不再打进基础包，用户需要时从市场安装（与 OCR 插件同一套下载/校验/加载链路）。
3. **基础包不再包含脚本解释器**：`libkhatkit_core.so` 去掉 mlua/rquickjs（预期 -1.5~2.0MB），WASM 执行器按需内置（见 §5 选型）。
4. **不破坏存量卡片**：旧 JS/Lua 卡片在安装对应运行时模块后照常运行，引擎选择对卡片透明。

## 2. 现状（改造起点）

- `ScriptEngine` 已是抽象：`define/defineModule/eval/close` + `EngineResult`，设计注释明确「卡片脚本不得依赖引擎独有特性，保证 Lua <-> JS 可平滑切换」。
- 当前唯一实现 `RustScriptEngine` 在**同一份 native 核心**里同时承载 Lua（mlua）与 JS（rquickjs），通过 `KhatKitNative` JNI 调用。
- 卡片通过 `app/core/data/ai/tools/KhatKitTools.kt` 与 `bridge/` 暴露宿主能力（ai/fs/net/ui/haptics 等）。
- 已有原生模块加载基础设施：`OcrPluginInstaller`（zip ≤8MiB、sha256 校验、路径穿越拒绝、原子替换）、`OcrPluginLoader`（`DexClassLoader` + `nativeLibraryDir`）、`OcrPluginStore`（独立 DataStore 记录版本/来源）。
- 市场已支持 `kind=dependency` 条目（OCR 插件即 `id=1`，approved + preferred）。

**结论**：不需要重建抽象，只需新增 `WasmScriptEngine` 实现，并把 Lua/JS 实现从基础包迁到「市场模块 + 动态加载」。

## 3. 目标架构

```
             ┌─────────────────────────────────────────────┐
卡片(manifest)│ runtime: "wasm" | "lua" | "js"              │
             └───────────────┬─────────────────────────────┘
                             │ RuntimeResolver（按 manifest.runtime 选择）
      ┌──────────────────────┼───────────────────────────┐
      ▼                      ▼                           ▼
 WasmScriptEngine       LuaScriptEngine            JsScriptEngine
 （基础包内置）          （市场模块 lua-runtime）    （市场模块 js-runtime）
 wasm3/WAMR/wasmi       mlua（.so + dex 壳）        rquickjs（.so + dex 壳）
```

- `RuntimeResolver`：检查 `manifest.runtime`；模块缺失时抛 `RuntimeModuleMissing(runtime)`，UI 引导到市场安装（文案与 OCR 一致）。
- **WASM 内置**：WASM 是默认路径，必须开箱可用（不依赖市场），执行器选型见 §5。
- **Lua/JS 外置**：市场条目 `kind=dependency`、`plugin_id=lua-runtime` / `js-runtime`，包内含 `classes.dex` + `lib/<abi>/*.so`，由与 OCR 相同的安装器落地；运行时模块提供实现 `ScriptEngine` 的入口类（反射实例化）。

## 4. 卡片包格式

`.kkcard`（zip，≤8MiB 压缩 / ≤32MiB 解压，与市场限制一致）：

```
manifest.json      # 必填
card.wasm          # runtime=wasm 时必填
main.js / main.lua # runtime=js/lua 时必填（旧格式兼容）
assets/**          # 可选：图片、字体、模板
locales/**         # 可选：i18n
```

`manifest.json` 关键字段：

```json
{
  "name": "example-card",
  "version": "1.0.0",
  "runtime": "wasm",
  "abi_version": 1,
  "entry": { "init": "kk_init", "handle": "kk_handle", "shutdown": "kk_shutdown" },
  "capabilities": ["ai:chat", "fs:workspace", "ui:render", "haptics"],
  "limits": { "memory_mb": 64, "timeout_ms": 5000, "output_bytes": 262144 },
  "assets": ["assets/logo.png"],
  "min_app_version": "2.6.0"
}
```

- `runtime` 缺省 = `"wasm"`（新卡片默认 WASM）；`"js"`/`"lua"` 仅用于存量兼容。
- `abi_version` 不匹配时拒绝执行并提示升级 App/卡片。
- `capabilities` 是白名单：未声明即拒绝，运行时不得自行扩权。

## 5. WASM 执行器选型

| 方案 | 体积（arm64 估算） | 优点 | 风险 |
|---|---:|---|---|
| **wasm3**（C，解释器） | ≈120–200KB | 极小、无 JIT、Android 可直接编译 | 性能一般（解释执行） |
| **WAMR fast-interp** | ≈0.6–1.0MB | 性能好、生态成熟、支持 AOT | 构建链更重 |
| **wasmi**（Rust） | ≈0.8–1.2MB | 与现有 Rust 核心同栈、内存安全 | 需要把 wasmi 编进 `libkhatkit_core.so` |
| wasmtime-android | 数 MB | 特性全 | 体积/启动成本不适合移动端 |

**建议**：先接 **wasm3**（M0，最小可跑通）；若性能不达标再切 WAMR fast-interp，接口不变。执行器必须支持：WASI 子集（clock、random、部分 fs 映射到工作区）、无网络默认、可中断（超时/取消）。

## 6. WASM ABI（v1）

Kotlin ↔ wasm 只通过**线性内存 + JSON 字符串**交换，避免复杂绑定：

**imports（宿主提供，按能力白名单开放）**
```
kk.host_call(cap: i32, req_ptr: i32, req_len: i32) -> i64   // 返回 (ptr<<32 | len)，内容为 JSON
kk.log(level: i32, ptr: i32, len: i32)
kk.rand(ptr: i32, len: i32) -> i32
kk.now_ms() -> i64
kk.abort(code: i32)                                          // 主动取消/报错
```

**exports（卡片导出）**
```
kk_init(manifest_ptr, len) -> i32
kk_handle(event_ptr, len) -> i64
kk_shutdown() -> i32
```

- 一次调用一个事件，宿主负责序列化（`{ "type": "...", "payload": {...} }`）。
- 错误码：`0` 成功；非 0 时宿主读 `kk_last_error()` 的 JSON。
- 不允许卡片直接持有宿主对象；所有副作用经 `kk.host_call` 走白名单。

## 7. 语言支持与官方推荐

WASM 支持多语言：**Rust、C/C++（WASI SDK）、Go（TinyGo）、Zig、AssemblyScript、MoonBit** 等。

- **官方推荐：MoonBit**（用户原话写作 "boonbit"，本文按 **MoonBit** 记录；若指其它语言请更正本文）。
  理由：面向 WASM 优先设计、产物体积小、无 GC/运行时重依赖、官方工具链直接用；适合卡片这种「小、快、可沙箱」场景。
- 备选：Rust（`wasm32-wasip1` + `wasm-opt -Oz`）、TinyGo（注意 GC 运行时代价）。
- SDK 需提供：`khatkit-card-sdk`（Rust crate / MoonBit 包 / TS types）+ 本地 `kk card test` 调试器 + 示例卡片（天气、记账、RSS 摘要）。

## 8. Lua / JS 模块化（市场下发）

**包结构**（与 OCR 插件一致，复用 `OcrPluginInstaller`，重命名为通用 `RuntimeModuleInstaller`）：

```
manifest.json   # { kind: "dependency", plugin_id: "js-runtime", entry_class, abi:["arm64-v8a"] }
classes.dex     # Kotlin 壳：实现 ScriptEngine 的入口 + 桥接
lib/arm64-v8a/libjs_runtime.so   # rquickjs / mlua 编出来的 native
```

- 市场条目：`kind=dependency`、`name=js-runtime` / `lua-runtime`、`version`、`min_app_version`、`sha256`。
- 安装后 `RuntimeModuleStore` 记录版本；`RuntimeResolver` 优先用市场模块，缺失时提示安装。
- **降级策略**：卡片声明 `runtime: "js"` 但模块未安装 → 不执行，弹「需要安装 JS 运行时」并提供市场入口（与 OCR 文案一致）；不静默回退到其它引擎，避免语义差异。
- **卸载保护**：若有已安装卡片依赖该运行时，卸载前提示「N 张卡片将无法运行」。

## 9. 安全与资源限额

- 能力白名单（`capabilities`）：`ai:chat`、`fs:workspace`（限定工作区根）、`net:<domain>`（默认无网络）、`ui:render`、`haptics`、`clipboard` 等。
- 限额：内存（wasm 最大页数）、CPU（超时中断）、输出大小（单次 `output_bytes`）、递归/循环保护。
- 签名：市场下发包 sha256 + 发布者签名；本地加载前校验，失败拒绝加载并清缓存。
- 审计：卡片可声明 `audit_report`（沿用市场字段），运行时不做静态检查，但**能力超集**一律拒绝。

## 10. 兼容与迁移

1. **引擎选择**：`manifest.runtime` 优先；缺省视为 `wasm`；存量卡片（无 `runtime` 且存在 `main.js`）自动识别为 `js`。
2. **工具调用 API**：宿主 bridge（`KhatKitTools`）保持不变，WASM 通过 `kk.host_call` 调同一批能力，保证三种运行时行为一致。
3. **内置兜底**：首版 App 可选择性内置 JS 运行时（短期），但目标态是「基础包无解释器」；文档与发布说明需明确。
4. **迁移工具**：`kk card migrate <dir>` 把 JS 卡片外壳替换为 WASM 模板（不做语义翻译，只生成脚手架与 manifest）。

## 11. 体积/性能预算

- 基础包：移除 mlua+rquickjs ≈ **-1.5~2.0MB**；新增 wasm3 ≈ **+0.2MB** ⇒ 净减约 1.3~1.8MB。
- 卡片：wasm 产物通常 20–200KB（MoonBit/Rust 小型卡片），远小于同等 JS 包。
- 启动：wasm 模块实例化目标 < 20ms；单次事件处理 < 100ms（超时中断保护）。

## 12. 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| **M0** 执行器 | `WasmScriptEngine`（wasm3 接入）+ `ScriptEngine` 单测（init/handle/超时/错误码） | App 可跑内置 wasm 示例卡片 |
| **M1 ABI 与 SDK** | ABI v1 冻结；Rust/MoonBit SDK；`kk card test`；3 个示例卡片 | 示例卡片在真机跑通 |
| **M2 模块化** | Lua/JS 运行时装成市场模块（复用 OCR 安装器）；`RuntimeResolver`；卸载保护 | 旧 JS 卡片安装模块后可运行 |
| **M3 默认切换** | 新卡片默认 wasm；市场新增「WASM 卡片」分区；文档/模板更新 | 新提交卡片 100% wasm |
| **M4 瘦身** | 基础包移除 mlua/rquickjs；体积验收 | arm64 release −1.5MB 以上 |

## 13. 风险与待决

1. **"boonbit" 名称**：确认是否指 **MoonBit**；若指其它语言/工具链，需替换 §7 的推荐与 SDK 计划。
2. **WASM 能力边界**：`fs` 是否允许写工作区之外；网络是否默认全关（建议全关 + 白名单域名）。
3. **旧卡片存量**：需要统计 JS/Lua 卡片数量，决定 M2 是否内置临时 JS 运行时。
4. **调试体验**：wasm 调试（DWARF）在移动端支持有限，需提供「事件回放 + 宿主日志」替代。
5. **多线程/异步**：v1 不做 wasm 线程；异步通过事件回调与 `kk.host_call` 的挂起语义实现，需明确取消模型。
6. **桌面/iOS**：若未来复用，wasm3/WAMR 均可移植，需在 SDK 层隔离平台差异。

## 14. 市场字段对照（发布用）

`kind=dependency`（与 OCR 插件同构）：

```json
{
  "kind": "dependency",
  "plugin_id": "js-runtime",
  "version": "1.0.0",
  "entry_class": "heizige.kk.khatkit.runtime.js.JsScriptEngine",
  "abi": ["arm64-v8a"],
  "min_app_version": "2.6.0"
}
```

发布走 `KodeHeapServer` 的 `POST /api/items/publish`（multipart/JSON 二选一按现有脚本），审核通过后 `GET /api/items/{id}/download` 下发；安装器复用 `app/core/data/ocr` 的校验与原子落盘逻辑。
