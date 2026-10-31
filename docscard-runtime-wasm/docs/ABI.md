# ABI v1（冻结）

本文是 `docscard-runtime-wasm` 的 **ABI v1 契约**，与实现逐条对齐：

* 常量与编解码：[src/abi.rs](../src/abi.rs)
* import 注册与调用语义：[src/engine.rs](../src/engine.rs)（`link_abi` / `CardRuntime`）
* manifest 与限额：[src/manifest.rs](../src/manifest.rs)
* 可执行样例：[fixtures/](fixtures)、[tests/integration.rs](../tests/integration.rs)

对应上游规格：`docs/card-runtime-wasm.md` §6（WASM ABI v1）、§9（安全与资源限额）。

**冻结规则**：`abi_version` 必须等于 `1`（[src/abi.rs:22](../src/abi.rs#L22)）。任何破坏性改动都必须升
`abi_version` 并同步 Kotlin 侧宿主；本文件与 `src/abi.rs` 是同一份契约的两种表达。

---

## 1. 模块形状

* import 模块名固定为 **`kk`**（[src/abi.rs:25](../src/abi.rs#L25)），只有下面 5 个 import；
  卡片若 import 其它东西（含 WASI），**实例化直接失败**（`RuntimeError::Instantiate`）。
* 线性内存必须导出为 **`memory`**（[src/abi.rs:34](../src/abi.rs#L34)）。
* 必须导出：`memory`、`kk_alloc`、`kk_free`、`kk_handle`
  （缺任意一个 → `RuntimeError::MissingExport { name }`，报明确名字，不 panic）。
* 可选导出：`kk_init`、`kk_shutdown`、`kk_last_error`
  （缺 `kk_init`/`kk_shutdown` 视为无操作，缺 `kk_last_error` 视为"无错误详情"）。
* 引擎**不预先校验函数签名**；签名与下表不符时，调用会以 `RuntimeError::Trap`
  结束（例如 "encountered an incorrect number of parameters"），不会 panic。
  `kk_free` 的调用失败被忽略（释放失败不影响主流程）。

---

## 2. imports（宿主提供）

| import | 签名 | 说明 |
|---|---|---|
| `kk.host_call` | `(param i32 i32 i32) (result i64)` | `(cap, req_ptr, req_len)` → `(ptr << 32) \| len`（应答 JSON）；失败时为负值 `-(code)` |
| `kk.log` | `(param i32 i32 i32)` | `(level, ptr, len)`，无返回值 |
| `kk.rand` | `(param i32 i32) (result i32)` | `(ptr, len)` → `0` 成功 / 非 0 = `HOST_ERR_*` |
| `kk.now_ms` | `(result i64)` | 当前 Unix 毫秒 |
| `kk.abort` | `(param i32)` | 立刻中断执行（不返回），引擎映射成 `RuntimeError::Aborted { code }` |

### 2.1 `kk.host_call`

`cap` 是 **`manifest.capabilities` 去重后列表的下标**（0 起，见 §5），不是能力名、不是能力码。

宿主侧处理顺序（[src/engine.rs:211](../src/engine.rs#L211)）：

1. 从 guest 内存读 `[req_ptr, req_ptr + req_len)`；越界/负值 → `-(HOST_ERR_POINTER_OOB)`；
   非 UTF-8 → `-(HOST_ERR_INVALID_REQUEST)`。
2. 请求长度必须 ≤ `limits.output_bytes`，否则 `-(HOST_ERR_INVALID_REQUEST)`。
3. `cap` 越界（即能力未在 manifest 声明）→ `-(HOST_ERR_CAPABILITY_DENIED)`，
   **宿主实现根本不会被调用**。
4. 调 `CardHost::host_call(cap_name, request_json)`；`Err(HostError)` → `-(HostError::code())`。
5. 应答 JSON 长度必须 ≤ `limits.output_bytes`，否则 `-(HOST_ERR_OUTPUT_TOO_LARGE)`。
6. 用 **guest 的 `kk_alloc`** 分配应答缓冲区并写入；`kk_alloc` 返回 0 →
   `-(HOST_ERR_HOST_FAILURE)`；写入越界 → `-(HOST_ERR_POINTER_OOB)`。
7. 成功返回 `(ptr << 32) | len`。**guest 读完应答后必须自己 `kk_free(ptr, len)`。**

失败时宿主**不分配**任何内存，也**不写** guest 内存；guest 必须先判 `i64 < 0` 再解包。
引擎不解析应答 JSON（内容归卡片和宿主约定的能力协议）。

### 2.2 `kk.log`

* 消息按 UTF-8 解码（非法字节用替换字符），单条截断到 **4096** 字节（按字符边界，
  [src/engine.rs:38](../src/engine.rs#L38)）。
* 引擎最多保留 **1024** 条，超出的丢弃（[src/engine.rs:40](../src/engine.rs#L40)）；
  同时会转发给注入的 `CardHost::log(level, message)`。
* 指针越界不报错、不 trap，记录为 `"<invalid kk.log buffer>"`。

### 2.3 `kk.rand`

* 用确定性 PRNG（xorshift64\*）填满 `[ptr, ptr + len)`；`len <= 0` 直接返回 0。
* 成功返回 0；指针越界返回 `HOST_ERR_POINTER_OOB`（3）且不写内存。
* "确定性"指同一宿主实例内可复现，**不是**密码学随机，不能用于密钥。

### 2.4 `kk.now_ms` / `kk.abort`

* `now_ms`：Unix 毫秒；系统时钟异常时返回 0。
* `abort(code)`：立即中断 guest 执行，引擎返回 `RuntimeError::Aborted { code }`（`code` 原样保留，
  包括 0）。

---

## 3. exports（卡片导出）

| export | 签名 | 被调用时机 | 返回值语义 |
|---|---|---|---|
| `memory` | 线性内存 | 每次数据交换 | 宿主唯一的数据通道 |
| `kk_alloc` | `(param i32) (result i32)` | 宿主写数据前 | `>0` = 指针；**`0` = 分配失败**（→ `RuntimeError::AllocFailed`） |
| `kk_free` | `(param i32 i32)` | 宿主读完数据后 | 无返回值，失败被忽略 |
| `kk_init` | `(param i32 i32) (result i32)` | 加载后一次 | `0` 成功；非 0 → `CardError { code, export: "kk_init", detail: kk_last_error() }` |
| `kk_handle` | `(param i32 i32) (result i64)` | 每个事件一次 | `(ptr << 32) \| len` 结果 JSON；见下 |
| `kk_shutdown` | `(result i32)` | 卸载前一次 | `0` 成功；非 0 → `CardError { export: "kk_shutdown" }` |
| `kk_last_error` | `(result i64)` | 任一入口失败后 | `(ptr << 32) \| len` 错误 JSON；`0` 或未导出 = 无详情 |

### 3.1 `kk_handle` 返回值的三种情况（[src/engine.rs:559](../src/engine.rs#L559)）

| 返回值 | 含义 | 引擎行为 |
|---|---|---|
| `> 0`（即 `(ptr << 32) \| len`） | 成功，结果是 UTF-8 **合法 JSON** | 读内存；超 `output_bytes` → `OutputTooLarge`；越界 → `MemoryOutOfBounds`；非 UTF-8/非 JSON → `InvalidResult` |
| `0` | 失败，无显式错误码 | `CardError { code: 1, export: "kk_handle", detail: kk_last_error() }` |
| `< 0` | 失败，错误码 = `-value` | `CardError { code: -value, export: "kk_handle", detail: kk_last_error() }` |

因为 `limits.memory_mb <= 1024`（指针 < 2³¹），`(ptr << 32) | len` 恒为非负，
"负值 = 错误码"没有歧义（[src/abi.rs:18](../src/abi.rs#L18)）。

### 3.2 `kk_last_error` 的缓冲区归属

`kk_last_error()` 返回的缓冲区**归 guest 所有**：宿主只读、**不 `kk_free`**。
若指针越界、长度 0 或内容非 UTF-8，引擎返回 `None`（`CardRuntime::last_error_json`），不 panic。

---

## 4. 内存交换协议

只通过**线性内存 + UTF-8 JSON 字符串**交换，不传对象、不共享句柄。`ptr` 与 `len` 都是
**guest 线性内存的字节偏移/长度**（无对齐要求）。

```
宿主 -> guest（写输入）
  len  = bytes(json).len()                     # manifest 原文 / 事件 JSON
  ptr  = kk_alloc(len)                         # 0 => AllocFailed
  memory[ptr .. ptr+len] = bytes(json)         # 越界 => MemoryOutOfBounds
  call kk_init(ptr, len) / kk_handle(ptr, len)
  kk_free(ptr, len)                            # 无论调用成败都释放

guest -> 宿主（读结果）
  i64 packed = kk_handle(ptr, len)             # packed = (ptr << 32) | len
  len  = packed & 0xFFFF_FFFF
  ptr  = (packed >> 32) as u32
  json = utf8(memory[ptr .. ptr+len])          # 越界/超长/非 JSON => 错误
  kk_free(ptr, len)                            # 结果缓冲区由 guest 分配，必须由宿主释放
```

* 编解码 API：`pack(ptr, len)` / `unpack(packed)`（[src/abi.rs:77](../src/abi.rs#L77)），
  Kotlin 侧应使用同样的位运算。
* 事件 JSON 与结果 JSON 的长度上限都是 `limits.output_bytes`（输入方向 → `InputTooLarge`，
  输出方向 → `OutputTooLarge`）。
* 一次调用一个事件；事件是卡片自己的业务 JSON（约定 `{"type": "...", "payload": {...}}`，
  引擎不校验结构）。
* `kk_init` 收到的是 **manifest.json 原文**（不是 `kk_handle` 的事件结构）。

---

## 5. 错误码表（`kk.host_call` / `kk.rand` 的负值或非 0 返回值）

| 码 | 常量 | 触发条件 |
|---:|---|---|
| 1 | `HOST_ERR_CAPABILITY_DENIED` | `cap` 索引不在 `manifest.capabilities` 内；或宿主返回 `HostError::Denied` |
| 2 | `HOST_ERR_INVALID_REQUEST` | 请求指针越界以外的非法请求：非 UTF-8、请求超长；或宿主返回 `HostError::InvalidRequest` |
| 3 | `HOST_ERR_POINTER_OOB` | 指针/长度越出 guest 线性内存（含负值） |
| 4 | `HOST_ERR_OUTPUT_TOO_LARGE` | 宿主应答 JSON 超过 `limits.output_bytes` |
| 5 | `HOST_ERR_HOST_FAILURE` | 宿主实现失败（`HostError::Unsupported` / `Failed`）、或写应答时 `kk_alloc` 返回 0 |
| 6 | `HOST_ERR_INVALID_ENCODING` | **保留**：当前实现不产生（`(ptr,len)` 编码错误改由 §3.1 的 `InvalidResult` 报告） |

`kk.host_call` 的失败编码为 `-(code)`（例如 `-1`），成功为 `(ptr << 32) | len`。
`HostError → code` 映射见 [src/host.rs:22](../src/host.rs#L22)：

| `HostError` | code |
|---|---:|
| `Denied(_)` | 1 |
| `Unsupported(_)` / `Failed(_)` | 5 |
| `InvalidRequest(_)` | 2 |

宿主侧错误类型（给 Kotlin/JNI 层用）：`RuntimeError::kind()` 取值为
`manifest` / `module_load` / `instantiate` / `link` / `missing_export` / `signature_mismatch` /
`memory_out_of_bounds` / `alloc_failed` / `input_too_large` / `output_too_large` / `timeout` /
`aborted` / `card_error` / `invalid_result` / `trap`（[src/error.rs:104](../src/error.rs#L104)）。
其中 `signature_mismatch` 为将来预留；当前签名不符走 `trap`。

---

## 6. capability 码表

`manifest.capabilities` 是**白名单**：未声明即拒绝，运行时不得自行扩权。
字符串 ↔ 枚举见 [src/manifest.rs:113](../src/manifest.rs#L113)。

| capability 字符串 | 枚举 | 说明 |
|---|---|---|
| `ai:chat` | `Capability::AiChat` | 调模型对话 |
| `fs:workspace` | `Capability::FsWorkspace` | 限定工作区根目录的文件访问 |
| `ui:render` | `Capability::UiRender` | 渲染 UI 片段 |
| `haptics` | `Capability::Haptics` | 触感反馈 |
| `clipboard` | `Capability::Clipboard` | 读写剪贴板 |
| `net:<domain>` | `Capability::Net(domain)` | 只允许该域名；`domain` 非空，字符集 `[A-Za-z0-9._:-]` |
| `net:*` | `Capability::Net("*")` | 允许任意域名（宿主必须留审计，且只对 `browser`/`net` 生效） |
| `host` | `Capability::Host` | 环境信息/时间/时区/本地化（`host.*`、`i18n.locale`、`time.*`） |
| `tool` | `Capability::Tool` | 文件/OCR/剪贴板工具（L0） |
| `accessibility` | `Capability::Accessibility` | 无障碍服务（L1） |
| `store` | `Capability::Store` | 卡片私有存储/SQLite/向量 |
| `download` | `Capability::Download` | 后台下载 |
| `web` | `Capability::Web` | 网页登录与 Cookie |
| `browser` | `Capability::Browser` | 可编程浏览器（另需 `net:*` 或具体域名） |
| `media:pick` | `Capability::MediaPick` | 媒体选择 |
| `schedule` | `Capability::Schedule` | 运行期定时任务 |
| `system` | `Capability::System` | toast/share/vibrate/通知/launchApp/openUrl |
| `image:toolbox` | `Capability::ImageToolbox` | 依赖包注入的图像工具 |
| `dep:<bridge>` | `Capability::Dep(bridge)` | 动态依赖 bridge（如 `dep:musicKey`）；`bridge` 非空，字符集同上 |
| `shizuku` / `root` | `Capability::Shizuku` / `Root` | 保留，当前卡片不用 |

规则：

* **`cap` 索引 = 去重后 `capabilities` 列表的下标**，保持 manifest 中的顺序。
  例：`["haptics", "clipboard"]` → `cap 0 = haptics`、`cap 1 = clipboard`。
* 重复项折叠（`["ai:chat","ai:chat"]` → 1 项）。
* 未知能力（如 `fs:root`、`ai:image`）在 manifest 校验阶段就被拒绝
  （`ManifestError::UnknownCapability`）；格式错误（如 `net:`）→ `InvalidCapability`。
* 版本升级不得改变已有字符串的含义；新增能力只能追加。
* 2026-11 词表扩容（`host`/`tool`/`accessibility`/… 与 `net:*`/`dep:<bridge>`）是为
  `KhatKitCards/moonbit` 的 MoonBit 卡片迁移补的能力面，只追加、不改既有含义。

---

## 7. limits 语义

`manifest.limits` 全部可省略，缺省值与合法区间（[src/manifest.rs:15](../src/manifest.rs#L15)）：

| 字段 | 缺省 | 合法区间 | 落到实现上的含义 |
|---|---:|---|---|
| `memory_mb` | 64 | 1 – 1024 | wasmi `StoreLimits::memory_size = memory_mb × 1 MiB`；wasm 页 = 64 KiB，即 **`memory_mb × 16` 页** |
| `timeout_ms` | 5000 | 10 – 120000 | fuel 预算 = `timeout_ms × FUEL_PER_MS`，`FUEL_PER_MS = 10_000`（[src/engine.rs:35](../src/engine.rs#L35)） |
| `output_bytes` | 262144（256 KiB） | 256 – 8388608（8 MiB） | 单次事件/结果/宿主应答的字节上限 |

### 7.1 `memory_mb`

* 建 store 时限制：`memories = 1`、`tables = 4`、`table_elements = 4096`、`instances = 1`
  （[src/engine.rs:430](../src/engine.rs#L430)）。
* 卡片声明的**初始内存**超过上限 → **实例化失败**（`RuntimeError::Instantiate`，wasmi 报
  "out of bounds memory allocation"），不会先分配再拦。
* 运行时 `memory.grow` 超过上限 → wasm 语义返回 **`-1`**，不 trap、不 panic
  （样例：[fixtures/memory_hog.wat](../fixtures/memory_hog.wat)、
  [fixtures/memory_bomb.wat](../fixtures/memory_bomb.wat)）。

### 7.2 `timeout_ms`

* 超时是 **fuel（确定性度量）**，不是墙钟线程中断：不依赖系统负载，可复现。
* **每次导出函数调用前都把 fuel 重置**为 `timeout_ms × 10_000`（[src/engine.rs:748](../src/engine.rs#L748)）。
  限额是"单次导出调用"的预算，而不是卡片生命周期的总预算。
* **实例化（含 wasm start 函数）也预置同一份 fuel**：MoonBit 卡片在 start 里 grow 内存并初始化堆，
  0 fuel 会直接报 `Instantiation: all fuel consumed`（2026-11 修复，见 `KhatKitCards/moonbit/REPORT.md`）。
* 引擎已开启 wasm **SIMD**（`config.wasm_simd(true)`，wasmi 0.44 的 `simd` feature）：MoonBit 的
  wasm1 后端在 core 的字符串/JSON 路径里无条件使用 `v128`，不开启则所有 MoonBit 卡片 `ModuleLoad` 失败。
* fuel 耗尽 → `RuntimeError::Timeout { timeout_ms }`（`kind() == "timeout"`）。
* `kk.abort` → `RuntimeError::Aborted { code }`（优先于超时判定）。
* 其它 trap → `RuntimeError::Trap { export, message }`。
---

## 8. `ui.*` 协议（宿主侧 ui bridge）

卡片通过 `kk.host_call(ui_cap, ...)` 发 `{"method":"ui.<方法>","args":[...]}`（4.4），
宿主实现见 [src/ui.rs](../src/ui.rs)（Rust `UiRenderHost`）与 Kotlin `UiBridgeHost`。

| 方法 | args（位置参数） | 应答 | 阻塞 |
|---|---|---|---|
| `ui.form` | `[title, tree, options?]` | `{id: value}` 或 `null`（取消/超时） | 是 |
| `ui.screen` | `[title, tree, options?]` | `{"event":..,"values":{..}}` 或 `null` | 是 |
| `ui.show` | `[tree, options?]` | `null` | 否 |
| `ui.progress` | `[ratio, label]` 或 `[label]` | `null` | 否 |
| `ui.confirm` | `[title, message]` | `bool` | 是 |
| `ui.isCancelled` | `[]` | `bool` | 否 |

### 8.1 节点树

卡片侧写法（4.6，扁平）：`{"kind":"Column","spacing":8,"children":[...]}`。
宿主在呈现前**校验**并**规范化**成内部格式 `{"__ui":"Column","props":{...},"children":[...]}`，
校验规则（与 Kotlin `UiNodeParser` 一致）：组件/属性白名单、深度 <= 32、节点数 <= 512、
字段 `id` 必填且唯一、`Section.title` 与 `Button.action` 必填、叶子不得有 children、
`Text/Icon.size` 6..64、`ProgressBar.ratio` 0..1。

校验失败按 4.4 走错误应答 `{"__error":"<组件>.<属性>: <原因>"}`（Rust `UiRenderHost`），
或走 `HOST_ERR_INVALID_REQUEST`（Kotlin 侧经 JNI，见 `khatkit-core/src/wasm.rs`）；
两种形态卡片都必须能翻译成自己的结果错误。

### 8.2 阻塞语义（R4 的边界）

`ui.form/screen/confirm` 在宿主侧**阻塞到用户提交或取消**：
* CLI：`--ui <replies.json>` 按顺序应答（队列耗尽视为取消）；`--ui null` 一律取消。
* Kotlin：`UiBridgeHost` 用 `CompletableDeferred` 挂起，等待 Compose 层提交。
* 阻塞期间不消耗 wasm fuel（宿主函数不计 fuel），所以 `timeout_ms` 只约束卡片自身的计算。

v1 不做「超时保留弹层 + 之后续跑」：卡片要么拿到提交值，要么拿到 `null` 当取消（见 REPORT R4）。

### 7.3 `output_bytes`

同一上限同时约束四个方向：

| 方向 | 超限结果 |
|---|---|
| 事件/manifest 输入（宿主写 guest） | `RuntimeError::InputTooLarge { len, limit }` |
| `kk_handle` 结果长度 | `RuntimeError::OutputTooLarge { len, limit }` |
| `kk.host_call` 请求长度 | `kk.host_call` 返回 `-(HOST_ERR_INVALID_REQUEST)` |
| `kk.host_call` 应答长度 | `kk.host_call` 返回 `-(HOST_ERR_OUTPUT_TOO_LARGE)` |

样例：[fixtures/oversized_output.wat](../fixtures/oversized_output.wat)（2010 字节结果，
`output_bytes = 256` 时必须被拒绝，放宽到 4096 时正常返回）。

### 7.4 manifest 校验顺序

校验顺序固定，错误可区分（[src/manifest.rs:227](../src/manifest.rs#L227)）：

1. `runtime`（缺省 `"wasm"`；其它值 → `UnsupportedRuntime`，js/lua 走旧引擎）
2. `abi_version` 必须 == 1（→ `AbiVersionMismatch`）
3. 每个 `capabilities` 条目（→ `UnknownCapability` / `InvalidCapability`）
4. `limits` 三项区间（→ `LimitsOutOfRange { field, value, min, max }`）
5. JSON 本身非法 → `InvalidJson`

`entry` 字段只为兼容而解析：**ABI v1 的入口名是冻结的**，引擎永远调 `kk_init` /
`kk_handle` / `kk_shutdown`，不读 `entry`。

---

## 8. 生命周期

```
CardRuntime::load(wasm, manifest)      # 校验 manifest -> 实例化（执行 start 段）-> 取导出
  runtime.init(&manifest_json)         # 可选调用，一次
  runtime.handle(event_json)           # 0..n 次，每次独立 fuel 预算
  runtime.shutdown()                   # 可选调用，一次
```

* 宿主可以在任何时刻读 `runtime.logs()`、`runtime.last_host_error()`、
  `runtime.host_call_failures()`、`runtime.capabilities()` 做审计。
* 卡片不参与内存寻址以外的宿主状态；实例销毁即丢弃全部 guest 内存。
* 本 crate **不做**市场包（`.kkcard` zip）装载、签名校验、资产/本地化加载 —— 这些属于
  Kotlin 宿主层（见 `docs/card-runtime-wasm.md` §4、§9）。