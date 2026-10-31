# docscard-runtime-wasm

KhatKit 卡片的 **WASM 运行时**（ABI v1），基于 [wasmi 0.36](https://crates.io/crates/wasmi)。
纯 Rust、无 JNI、无网络、可 `no_std` 之外的零 Unsafe（`#![forbid(unsafe_code)]`）。

* 规格来源：`docs/card-runtime-wasm.md` §5（WASM 执行器选型）、§6（WASM ABI v1）、§9（安全与资源限额）
* **ABI 契约（冻结）**：[docs/ABI.md](docs/ABI.md)
* 样例卡片源：[fixtures/](fixtures)（WAT）；集成测试：[tests/integration.rs](tests/integration.rs)

## 一句话流程

```rust
use docscard_runtime_wasm::{CardRuntime, DefaultHost};

let wasm = std::fs::read("card.wasm")?;
let manifest_json = std::fs::read_to_string("manifest.json")?;
let mut runtime = CardRuntime::from_manifest_json(&wasm, &manifest_json, DefaultHost::new())?;
runtime.init(&manifest_json)?;                                  // kk_init(manifest_ptr, len)
let result = runtime.handle(r#"{"type":"tick","payload":{}}"#)?; // kk_handle(event_ptr, len)
runtime.shutdown()?;                                           // kk_shutdown()
println!("{result}");
```

卡片与宿主只经 **线性内存 + UTF-8 JSON** 交换；所有副作用走 `kk.host_call`，受
`manifest.capabilities` 白名单约束。完整细节见 [docs/ABI.md](docs/ABI.md)。

## 目录

```
docscard-runtime-wasm/
├── Cargo.toml            wasmi / zip / sha2；dev-dependencies: wat / zip / sha2
├── src/                  引擎实现（abi / engine / error / host / manifest / package + CLI）
├── fixtures/             WAT 样例卡片（测试与手工实验用）
├── tests/integration.rs  集成测试：wat 编译样例 -> 引擎执行
├── tests/package.rs      .kkcard 装载集成测试：zip 现场构造 -> 校验/拒绝
├── docs/ABI.md           ABI v1 契约（imports/exports/内存协议/错误码/能力码/limits）
├── docs/PACKAGE.md       .kkcard 布局/限额/路径安全/错误码表/sha256
└── README.md
```

## 构建与测试

本仓库约定 Rust 构建缓存放 `/tmp/cargo-home`（换成默认 `~/.cargo` 也成立）：

```bash
cd Android/Project/KhatKit/docscard-runtime-wasm

# 全量测试：59 项 = lib 单测 31 条 + integration 10 条 + package 18 条
# （doc-tests 0 条：src/lib.rs 的「快速上手」是行内示例，不是可编译的 no_run 代码围栏）
CARGO_HOME=/tmp/cargo-home cargo test

# 只跑集成测试（样例卡片 + 引擎行为）
CARGO_HOME=/tmp/cargo-home cargo test --test integration

# 只跑 .kkcard 装载测试（zip 现场构造 + 安全拒绝）
CARGO_HOME=/tmp/cargo-home cargo test --test package

# 只跑某个验收场景（每个场景一条独立 #[test]，没有 #[ignore]）
CARGO_HOME=/tmp/cargo-home cargo test --test integration undeclared_capability_is_denied
CARGO_HOME=/tmp/cargo-home cargo test --test integration infinite_loop_times_out
CARGO_HOME=/tmp/cargo-home cargo test --test integration memory_limit_caps_memory_grow

# 发布构建（opt-level="z" + LTO + strip）
CARGO_HOME=/tmp/cargo-home cargo build --release
```

## .kkcard 包装载（`package` 模块）

`.kkcard` 是 zip：`manifest.json` + `card.wasm` + 可选 `assets/**` / `locales/**`。
`CardPackage` 负责解析与安全校验（8 MiB 包 / 32 MiB 解压预算边读边扣 / 256 条目 / zip slip /
符号链接 / 顶层白名单 / 整包 sha256），`into_runtime` 直接给出可执行运行时：

```rust
use docscard_runtime_wasm::{CardPackage, DefaultHost};

let package = CardPackage::open_path("card.kkcard")?;   // 结构 + 路径 + 预算校验
package.verify_sha256(market_sha256_hex)?;              // 可选：整包 sha256
println!("assets: {:?}", package.assets.keys());         // assets/** + locales/**（包内全路径）

let mut runtime = package.into_runtime(DefaultHost::new())?;
runtime.init(&package.manifest_json)?;                   // manifest 原文来自包内
let result = runtime.handle(r#"{"type":"tick"}"#)?;
runtime.shutdown()?;
```

拒绝的包返回 `PackageError`，用 `err.kind()` 分流：`archive-too-large` / `uncompressed-budget-exceeded` /
`too-many-entries` / `path-traversal` / `symlink-entry` / `unknown-entry` / `missing-manifest` /
`missing-card-wasm` / `manifest-unsupported-runtime` / `sha256-mismatch` …。
规则、校验顺序与全量错误码表见 [docs/PACKAGE.md](docs/PACKAGE.md)。

## CLI：本地跑一张卡片

```bash
cd Android/Project/KhatKit/docscard-runtime-wasm

# 1) 先生成一份可用的样例卡片（card.wasm + manifest.json + event.json）
DOCSCARD_EMIT_FIXTURE=1 CARGO_HOME=/tmp/cargo-home cargo test --lib emit_cli_fixture
#    -> target/cli-fixture/{card.wasm,manifest.json,event.json}

# 2) 跑卡片：依次 kk_init -> kk_handle -> kk_shutdown，结果 JSON 打到 stdout
./target/release/docscard run \
  --card target/cli-fixture/card.wasm \
  --manifest target/cli-fixture/manifest.json \
  --event '{"type":"tick","payload":{"n":1}}'
# -> {"type":"tick","payload":{"n":1}}   (exit 0)

# 3) 事件也可以来自文件；--verbose 把卡片 kk.log 与能力拒绝/失败打到 stderr
./target/release/docscard run \
  --card target/cli-fixture/card.wasm \
  --manifest target/cli-fixture/manifest.json \
  --event target/cli-fixture/event.json --verbose

# 4) 失败路径：任何错误都以 exit 1 + 明确 stderr 结束（不 stack trace）
./target/release/docscard run --card target/cli-fixture/nope.wasm \
  --manifest target/cli-fixture/manifest.json
# -> docscard: cannot read card target/cli-fixture/nope.wasm: ...   (exit 1)

./target/release/docscard --help
```


`--event` 接受内联 JSON（以 `{` / `[` 开头）或 `.json` 文件路径。

### mock 宿主：验证能力行为（`--mock-host`）

CLI 默认的 `DefaultHost` **拒绝所有 `host_call`**（返回 `-1`），所以裸跑只能验证模块形状、JSON 往返、
限额与超时。要验证 `ui.*` / `accessibility.*` / `imageToolbox.*` 等能力路径，用 `--mock-host`：

```bash
# mock.json：键 = host_call 请求里的 method，值 = 应答 JSON
# {"host.locale": "zh-CN",
#  "ui.form": {"text": "确定"},
#  "browser.act": {"__raw": "{\"ok\":true}"},   // 方法返回值本身是 JSON 字符串
#  "__default": {"__error": "未实现"}}             // 未知方法的兜底（缺省 -> HOST_ERR_HOST_FAILURE）
./target/release/docscard run --card card.wasm --manifest manifest.json \
  --event '{"payload":{}}' --mock-host mock.json --verbose

# 同一事件连跑 N 次（验证事件间复用不泄漏/不越界）
./target/release/docscard run --card card.wasm --manifest manifest.json \
  --event '{"a":1}' --repeat 100
```


### UI 宿主：验证 ui.form/screen（`--ui`）

`--ui <file|null>` 启用 Rust 侧 UI 宿主（`src/ui.rs`）：`ui.*` 请求在这里做节点树校验 +
规范化，然后交给应答脚本；其余能力仍走 `--mock-host`。

```bash
# ui-replies.json：按顺序给 form/screen/confirm 的应答（show/progress 不占位）
# [{"text": "确定", "exact": true}]
./target/release/docscard run --card card.wasm --manifest manifest.json \
  --event '{"payload":{}}' --ui ui-replies.json --mock-host mock.json --verbose
# -> [ui] ui.form title="无障碍点击" nodes=5 fields=["text", "exact"]

# 一律取消（form/screen 返回 null）；DOCSCARD_UI_DEBUG=1 打印节点树被拒的原因
./target/release/docscard run --card card.wasm --manifest manifest.json \
  --event '{"payload":{}}' --ui null --verbose
```

节点树非法时宿主按 4.4 返回 `{"__error":...}`，卡片把它翻译成结果错误（不会 panic）。

`MockHost` 在库侧（`src/mock_host.rs`）也导出为 `docscard_runtime_wasm::MockHost`，集成测试可直接用，
并通过 `calls()` 断言卡片实际调用了哪些 `(capability, method, args)`。

### 跑 .kkcard 包

```bash
# 1) 生成样例包：合法包 + runtime=js 的坏包
DOCSCARD_EMIT_FIXTURE=1 CARGO_HOME=/tmp/cargo-home cargo test --lib emit_
#    -> target/cli-fixture/{good.kkcard,bad.kkcard}

# 2) 直接跑包：manifest.json / card.wasm 从包内读，不接受 --manifest
./target/release/docscard run --package target/cli-fixture/good.kkcard --event '{"type":"tick"}'
# -> {"type":"tick"}   (exit 0)

# 3) 带整包 sha256 校验（sha256sum 输出可直接给）
./target/release/docscard run --package target/cli-fixture/good.kkcard   --sha256 "$(sha256sum target/cli-fixture/good.kkcard | cut -d' ' -f1)" --event '{}'
# -> {}   (exit 0)；哈希不对 -> exit 1 + docscard: [package:sha256-mismatch] ...

# 4) 坏包：runtime=js -> exit 1 + [package:manifest-unsupported-runtime]
./target/release/docscard run --package target/cli-fixture/bad.kkcard --event '{}'

# 5) --card 与 --package 互斥；--package 模式下 --manifest 被拒绝（都 exit 1）
./target/release/docscard run --card target/cli-fixture/card.wasm   --package target/cli-fixture/good.kkcard --manifest target/cli-fixture/manifest.json
```

## 样例卡片（fixtures/）

每个文件都是**可直接编译的 WAT**（`wat2wasm fixtures/echo_card.wat -o card.wasm`），
集成测试用 `wat` crate 在进程内编译，不依赖外部工具链。

| 文件 | 用途 | 对应集成测试 |
|---|---|---|
| [fixtures/echo_card.wat](fixtures/echo_card.wat) | 最小 ABI v1 卡片：六个 export 齐全，原样回显事件；`kk_init` 把 manifest 原文写进 `kk.log` | `normal_lifecycle_init_handle_shutdown` |
| [fixtures/undeclared_capability.wat](fixtures/undeclared_capability.wat) | 调 `kk.host_call(cap=1)`：按 manifest 声明 1 个还是 2 个能力，分别走"拒绝"和"放行" | `undeclared_capability_is_denied` / `declared_capability_reaches_host` |
| [fixtures/spin_forever.wat](fixtures/spin_forever.wat) | `kk_handle` 死循环：验证 `timeout_ms` -> fuel 中断 | `infinite_loop_times_out` |
| [fixtures/memory_hog.wat](fixtures/memory_hog.wat) | 反复 `memory.grow(1)` 直到 `-1`：验证 `memory_mb` 页上限（停在 16 页 vs 1024 页） | `memory_limit_caps_memory_grow` |
| [fixtures/memory_bomb.wat](fixtures/memory_bomb.wat) | 声明 64 页初始内存：验证 `memory_mb` 在**实例化**阶段就生效 | `oversized_initial_memory_is_rejected` |
| [fixtures/oversized_output.wat](fixtures/oversized_output.wat) | 回传 2010 字节 JSON：验证 `output_bytes`（256 拒绝 / 4096 通过） | `output_bytes_rejects_oversized_result` |
| [fixtures/error_card.wat](fixtures/error_card.wat) | `kk_handle` 返回 `-7` + `kk_last_error()` 返回错误 JSON | `card_error_carries_last_error_json` |

## 集成测试覆盖

### tests/integration.rs（引擎行为，10 条）

| 场景 | 断言要点 |
|---|---|
| 正常流程 | init/handle/shutdown 全通；事件原样回显（含中文）；多次 handle 复用同一实例；`kk.log` 日志可见 |
| 能力白名单拒绝 | 返回 `-1`；卡片走到 denied 分支；**宿主实现零调用**；`last_host_error().code == 1`；失败计数 = 1 |
| 白名单命中 | 声明 2 个能力时 cap 1 = `clipboard` 真的到达宿主，请求 JSON 逐字节一致 |
| 超时 | `RuntimeError::Timeout { timeout_ms: 20 }`；`kind() == "timeout"`；耗时 < 5s（是 fuel 中断不是挂死）；第二次调用仍是 timeout |
| 内存上限（grow） | `memory_mb=1` 时页数恰好停在 16；放宽到 64 MiB 同一张卡能长到 1024 页 |
| 内存上限（实例化） | 64 页初始内存 + `memory_mb=1` -> `kind() == "instantiate"`；`memory_mb=8` 可正常加载 |
| output_bytes | 2010 字节结果 + `output_bytes=256` -> `OutputTooLarge { len: 2010, limit: 256 }`；300 字节事件 -> `InputTooLarge`；放宽到 4096 后正常返回 |
| 错误码 / last_error | `CardError { code: 7, export: "kk_handle", detail: Some({\"error\":\"bad_event\"}) }`；`last_error_json()` 内容一致 |
| 非法 manifest | runtime=js / abi=2 / 未知能力 / `net:` / timeout 越界 / 非 JSON 各自可区分；最小 manifest `{"abi_version":1}` 可加载 |
| 样例卡片全集 | 每个 `fixtures/*.wat` 都能编译、加载并调用 `kk_init`/`kk_shutdown`（证明六个 export 齐全） |

### tests/package.rs（.kkcard 装载，18 条）

| 场景 | 断言要点 |
|---|---|
| 合法包 + 端到端 | assets/locales 逐字节一致；`archive_sha256` 与独立 sha2 实现一致；`into_runtime` 后 init/handle/shutdown 全通 |
| 压缩包 > 8 MiB | `ArchiveTooLarge { size: 8 MiB + 1, limit: 8 MiB }`；正好 8 MiB 不因大小被拒（改以 `zip` 类别失败） |
| 解压炸弹 > 32 MiB | 5×8 MiB 全 0（压缩后 < 1 MiB）；`bytes_read <= 32 MiB` 证明**边读边扣**、没有整体解压 |
| 条目数 > 256 | `TooManyEntries { count > 256, limit: 256 }` |
| zip slip ×3 | `../evil` / `/abs` / 反斜杠各自 `UnsafePath { reason }` + `kind()`；安全路径 `assets/img/a.png` 仍放行 |
| 缺件 ×2 | `MissingManifest` / `MissingCardWasm`（`missing-manifest` / `missing-card-wasm`） |
| runtime = js | `Manifest(UnsupportedRuntime)`，`kind() == "manifest-unsupported-runtime"` |
| abi_version = 2 | `Manifest(AbiVersionMismatch { expected: 1, found: Some(2) })` |
| 未知顶层 | 文件条目 `extra.txt` / `src/main.rs` / `assets` / `locales` → `UnknownEntry` |
| 目录条目 | `assets/` 这类以 `/` 结尾的目录条目只做路径校验后跳过，不变成 assets、也不报未知条目 |
| 重名 | 字节层改名造出重复条目 → `DuplicateEntry` |
| 符号链接 | `add_symlink` → `SymlinkEntry` |
| sha256 ×2 | 正确（含 `sha256:` 前缀 / 大写 / 空白）通过；错一位 → `Sha256Mismatch { expected, actual }`；格式非法 → `InvalidSha256` |
| 磁盘装载 | `open_path` 读盘成功；文件不存在 → `PackageError::Io`（`kind() == "io"`） |

## 默认限额

`manifest.limits` 可整段省略（缺省值 + 区间见 [docs/ABI.md §7](docs/ABI.md#7-limits-语义)）：

| 字段 | 缺省 | 区间 | 实现 |
|---|---:|---|---|
| `memory_mb` | 64 | 1 – 1024 | wasmi `StoreLimits`，wasm 页 = `memory_mb × 16` |
| `timeout_ms` | 5000 | 10 – 120000 | fuel = `timeout_ms × 10_000`，每次导出调用前重置 |
| `output_bytes` | 262144 | 256 – 8388608 | 事件输入 / 结果 / 宿主应答四个方向的字节上限 |

能力白名单：`ai:chat` / `fs:workspace` / `ui:render` / `haptics` / `clipboard` / `net:<domain>`。
未声明即拒绝；`kk.host_call` 的 `cap` 是**声明列表去重后的下标**。

## 与 docs/card-runtime-wasm.md 的对应关系

| 规格 | 本 crate 的落点 | 状态 |
|---|---|---|
| §4 卡片包格式（`.kkcard` zip、assets/locales） | [src/package.rs](src/package.rs) + [docs/PACKAGE.md](docs/PACKAGE.md) | 已实现：8 MiB 包 / 32 MiB 解压边读边扣 / 256 条目 / zip slip / 符号链接 / 顶层白名单 / 整包 sha256 |
| §4 `≤8 MiB 压缩 / ≤32 MiB 解压` | `MAX_ARCHIVE_BYTES` / `MAX_UNCOMPRESSED_BYTES` | 已实现（另加 256 条目上限；不支持 zip64） |
| §4 manifest 字段 `runtime`/`abi_version`/`capabilities`/`limits` | [src/manifest.rs](src/manifest.rs) | 已实现（`entry` 只解析不生效，ABI v1 入口名冻结） |
| §5 WASM 执行器选型 | `Cargo.toml`（`wasmi = "0.36"`） | 采用 **wasmi** 而非 §5 建议的 wasm3：与 Rust 核心同栈、内存安全、可确定性中断（fuel）；ABI 已冻结，后续换引擎不影响卡片 |
| §6 WASM ABI（v1） | [docs/ABI.md](docs/ABI.md)、[src/abi.rs](src/abi.rs)、[src/engine.rs](src/engine.rs) | 已冻结：5 imports / 6 exports / `(ptr<<32)\|len` / `kk_last_error` |
| §9 能力白名单 | `HostState::capabilities` + `link_abi` | 已实现：未声明能力返回 `-1`，不触达宿主 |
| §9 限额（内存/CPU/输出） | `LimitsSpec` + wasmi `StoreLimits` + fuel | 已实现：实例化与 `memory.grow` 双路径拦内存，fuel 拦 CPU，`output_bytes` 拦四个方向 |
| §9 签名校验（sha256/发布者签名） | `CardPackage::verify_sha256`（整包 sha256） | 只做哈希；发布者签名 / 证书链属市场下载链路（Kotlin） |
| §9 WASI 子集（clock/random/fs 映射） | 只有 `kk.rand` / `kk.now_ms` | **不含 WASI**：卡片只能 import `kk.*`，其它 import 实例化即失败 |

## 当前边界（明确不包含）

* **不含发布者签名 / 证书链校验**：`.kkcard` 只做结构 + 路径 + 预算 + 整包 sha256；
  签名验证与市场 `sha256` 字段来源属下载链路。
* **`.kkcard` 不解压到磁盘**：包内容全在内存（`CardPackage::assets`）；宿主若要写 assets，自己决定路径与覆盖策略。
* **不含 Kotlin / JNI 绑定**：这是纯 Rust crate，供 `libkhatkit_core.so` 内嵌；JNI 桥接在后续任务。
* **不含 WASI**：没有 `wasi_snapshot_preview1`；时钟/随机走 `kk.now_ms` / `kk.rand`，文件/网络只经
  `kk.host_call` 的能力白名单。
* **默认无宿主能力**：`DefaultHost`（CLI 默认）对任何 `host_call` 都返回 `Denied`；真实能力需注入
  自己的 `CardHost` 实现。
* **审批 / 网络 / UI 渲染**：本 crate 只负责"把事件喂给卡片并收 JSON"，不含任何聊天/网络语义。
* **单线程阻塞调用**：`init`/`handle`/`shutdown` 是同步调用；并发与取消由上层调度（每次调用有独立
  fuel 预算，超时不会挂死线程）。
* **未做发布者签名校验、assets 落盘、`min_app_version` 判定**（manifest 里只解析并忽略 `min_app_version`）。

## 构建产物

`target/` 是本机构建缓存（`cargo test` / `cargo build --release` 生成，与源码无关）。
