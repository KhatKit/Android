# 实现与验收记录（docscard-runtime-wasm）

本文件是集成验收的落盘证据：命令、退出码、测试数量、产物路径、已知缺口。
生成时间：2026-10-31（由集成验收任务实测后写入）。

* §1–§6：**M0**（wasmi 引擎 + ABI v1 + 限额 + CLI），t4 落盘。
* §7：**M2①**（`.kkcard` zip 装载与安全校验），t9 追加；代码由 t6 实现、t7 补测试/文档、t10 修复、t11 复审 pass。

## 1. 环境

| 项 | 值 |
|---|---|
| 工作目录 | `Android/Project/KhatKit/docscard-runtime-wasm` |
| `rustc` | 1.97.1 (8bab26f4f 2026-07-14) |
| `cargo` | 1.97.1 (c980f4866 2026-06-30) |
| `CARGO_HOME` | `/tmp/cargo-home`（本机约定，换默认 `~/.cargo` 同样成立） |
| 依赖 | `wasmi = "0.36"`、`serde`、`serde_json`；dev-dependency `wat = "1"`（见 `Cargo.toml`、`Cargo.lock`） |

## 2. 全量测试

```bash
cd Android/Project/KhatKit/docscard-runtime-wasm
CARGO_HOME=/tmp/cargo-home cargo test
# exit code: 0
```

实测输出（test result 摘要）：

| 目标 | 结果 |
|---|---|
| `unittests src/lib.rs`（`src/tests.rs`，18 条 `#[test]`） | **18 passed; 0 failed; 0 ignored** |
| `unittests src/bin/docscard.rs` | 0 passed（CLI 无单测） |
| `tests/integration.rs`（10 条 `#[test]`，WAT 样例走真实引擎） | **10 passed; 0 failed; 0 ignored** |
| Doc-tests | 0 tests（见 §6 缺口 6） |
| 合计 | **28 passed / 0 failed / 0 ignored；退出码 0** |

关键场景与用例（全部无 `#[ignore]`，每个场景一条独立测试）：

* `normal_lifecycle_init_handle_shutdown` — init / handle / shutdown 正常路径
* `undeclared_capability_is_denied` + `declared_capability_reaches_host` — 能力白名单拒绝 / 放行
* `infinite_loop_times_out` — `timeout_ms` → fuel 中断
* `memory_limit_caps_memory_grow` + `oversized_initial_memory_is_rejected` — `memory_mb` 页上限（运行时 + 实例化）
* `output_bytes_rejects_oversized_result` — `output_bytes` 上限（结果 + 事件输入）
* `card_error_carries_last_error_json` — 错误码 + `kk_last_error()`
* `invalid_manifests_are_rejected` — 非法 manifest 可区分
* `fixtures_conform_to_abi_v1` — 7 张样例卡片全部可编译、加载、init/shutdown

## 3. 发布构建

```bash
CARGO_HOME=/tmp/cargo-home cargo build --release
# exit code: 0  （Finished `release` profile [optimized] target(s) in 16.73s）
```

产物路径（相对于 crate 根目录）：

| 产物 | 路径 | 大小（字节） |
|---|---|---:|
| CLI 可执行文件 | `target/release/docscard` | 1,220,584 |
| 静态库 | `target/release/libdocscard_runtime_wasm.rlib` | 570,186 |
| `target/release/` 总计 | — | 121 MB（含中间产物，未纳入版本控制） |

`[profile.release]`：`opt-level="z" + lto=true + codegen-units=1 + strip=true`。

## 4. 版本库边界

```bash
cd Android/Project/KhatKit
git status --short
# -> ?? docscard-runtime-wasm/

git status --short   # 全量未跟踪视角同样只有该目录
git diff --stat | wc -l   # -> 0（无任何既有文件改动）
```

结论：**只新增 `docscard-runtime-wasm/` 目录，KhatKit 既有文件零改动**（`git diff` 为空）。

## 5. 交付清单（本 crate）

```
docscard-runtime-wasm/
├── Cargo.toml / Cargo.lock
├── src/{lib,abi,engine,error,host,manifest,tests}.rs   ABI v1 常量、引擎、错误、宿主 trait、manifest 校验、单测
├── src/bin/docscard.rs                                 CLI（run --card/--manifest/--event/--verbose）
├── fixtures/*.wat                                      7 张样例卡片（echo / spin / memory_hog / memory_bomb / oversized_output / undeclared_capability / error）
├── tests/integration.rs                                10 条集成测试
├── docs/ABI.md                                         ABI v1 契约（imports/exports/内存协议/错误码/cap 码/limits）
├── README.md                                           构建、测试、CLI、与规格 §5/§6 的对应
└── IMPLEMENTATION.md                                   本文件（验收证据）
```

ABI v1 要点（细节见 `docs/ABI.md`）：5 个 imports（`kk.host_call`/`log`/`rand`/`now_ms`/`abort`）、
6 个 exports（`memory`+`kk_alloc`/`kk_free`/`kk_init`/`kk_handle`/`kk_shutdown`/`kk_last_error`）、
`(ptr << 32) | len` 编解码、失败读 `kk_last_error()`。限额：能力白名单 + `memory_mb`（wasmi StoreLimits）
+ `timeout_ms`（fuel，每次调用前重置）+ `output_bytes`（事件/结果/宿主应答）。

## 6. 已知缺口（如实列出）

1. **未接 Gradle / JNI**：本 crate 是纯 Rust 库（`#![forbid(unsafe_code)]`），没有 `build.gradle.kts`、
   没有 `libkhatkit_core.so` 打包、没有 Kotlin `external fun` 绑定；JNI 桥接属后续任务。
2. **未做 `.kkcard` zip 装载**：调用方负责解包，本 crate 只接收 `manifest.json` 文本 + `card.wasm` 字节；
   `assets/`、`locales/` 未加载。
3. **无 WASI**：没有 `wasi_snapshot_preview1`；时钟/随机只走 `kk.now_ms` / `kk.rand`，文件/网络只经
   `kk.host_call` 白名单。
4. **未做签名/市场校验**：`sha256` / 发布者签名 / `min_app_version` 判定均不在本 crate（属安装链路）。
5. **默认宿主无任何能力**：`DefaultHost` 对 `host_call` 一律返回 `Denied`；真实能力需注入 `CardHost` 实现。
6. **Doc-tests 为 0 条**：`src/lib.rs` 顶部示例用的是单反引号包裹（而非三反引号 + `no_run` 的代码围栏），
   因此 `cargo test` 的 `Doc-tests` 段实际 0 条；示例未被编译验证，README 中"含 doc-tests"的说法偏乐观。
7. **无 `.gitignore`**：crate 内未忽略 `target/`，构建后 `git status -uall` 会列出大量缓存文件；
   提交前需加 `target/` 忽略项（本次未改，避免越界）。
8. **`HOST_ERR_INVALID_ENCODING`（=6）为保留码**：`src/abi.rs` 定义但当前实现不产生
   （编码错误改由 `RuntimeError::InvalidResult` 报告），`docs/ABI.md` 已标注"保留"。
9. **单线程同步调用**：`init`/`handle`/`shutdown` 都是阻塞调用；每次调用有独立 fuel 预算
   （超时不挂死线程），但没有跨调用的累计 CPU 预算，并发与取消由上层调度负责。
10. **Windows/macOS 交叉产物未构建**：只验证了本机（Linux）`cargo build --release`；Android 三 ABI
    （arm64-v8a / armeabi-v7a / x86_64）未交叉编译，属后续打包任务。

---

## 7. M2① 追加：`.kkcard` 装载与安全校验

### 7.1 新增/变更文件

| 文件 | 行数 | 说明 |
|---|---:|---|
| `src/package.rs` | 1129 | `.kkcard`（zip）装载：路径安全、符号链接、解压预算、条目数、EOCD 一致性、整包 sha256、`into_runtime` |
| `tests/package.rs` | 546 | 18 条包层集成测试（zip slip ×3、符号链接、重名、缺件 ×2、runtime=js、abi≠1、未知顶层、zip bomb、8 MiB、256 条目、sha256 ×2、目录条目、open_path） |
| `docs/PACKAGE.md` | 234 | 包布局、硬限额、路径规则、校验顺序、错误码表、sha256 用法、§4/§9 对照、边界 |
| `Cargo.toml` | — | 新增依赖 `zip = { version = "2", default-features = false, features = ["deflate"] }`、`sha2 = "0.10"`（dev-dependencies 各一份，供测试独立构造包/算哈希） |
| `src/bin/docscard.rs` | — | CLI 增 `--package <kkcard>` / `--sha256 <hex>`（与 `--card` 互斥，`--package` 下拒绝 `--manifest`） |

### 7.2 全量测试（2026-10-31 复跑）

```bash
cd Android/Project/KhatKit/docscard-runtime-wasm
CARGO_HOME=/tmp/cargo-home cargo test
# exit code: 0  （无 compiler warning）
```

| 目标 | 测试数 | 结果 |
|---|---:|---|
| `unittests src/lib.rs`（`src/tests.rs` + `src/package.rs` 内联单测） | **35** | 35 passed; 0 failed; 0 ignored |
| `tests/integration.rs`（ABI/限额/样例卡片） | **10** | 10 passed; 0 failed; 0 ignored |
| `tests/package.rs`（`.kkcard` 装载与安全） | **18** | 18 passed; 0 failed; 0 ignored |
| `unittests src/bin/docscard.rs` | 0 | CLI 无单测 |
| Doc-tests | 0 | 见 §6 缺口 6 |
| **合计** | **63** | **63 passed / 0 failed / 0 ignored；退出码 0** |

全库无 `#[ignore]`；每个场景一条独立 `#[test]`。M2① 关键场景：

* `zip_slip_parent_traversal_is_rejected` / `zip_slip_absolute_path_is_rejected` / `zip_slip_backslash_is_rejected` — 字节级 patch 构造真实恶意 zip，断言 `UnsafePath{name, reason}` + `kind()`
* `symlink_entry_is_rejected` — `ZipWriter::add_symlink` 构造真实符号链接条目
* `zip_bomb_over_32mib_uncompressed_is_rejected` — 断言 `entry == "assets/e.bin"`（其后的 `z` 一次未读）且 `bytes_read ∈ (32 MiB, 32 MiB+64 KiB]`
* `accepts_exactly_max_entries` / `accepts_exactly_max_uncompressed_and_rejects_one_more_byte` — 256 条目与 32 MiB 是**闭区间**
* `rejects_eocd_comment_with_embedded_fake_signature` — EOCD 注释内伪签名 → `Zip("central directory count mismatch")`（fail-closed）
* `rejects_oversized_archive_on_disk_before_reading` — 稀疏 8 MiB+4096 文件在读之前即被拒（`size` 取自 metadata）

### 7.3 发布构建

```bash
CARGO_HOME=/tmp/cargo-home cargo build --release
# exit code: 0   （本次命中 t10 的产物缓存，0.03s；t10 曾 clean --release -p 后完整重建 14.37s）
```

| 产物 | 路径 | 大小（字节） |
|---|---|---:|
| CLI 可执行文件 | `target/release/docscard` | 1,339,616 |
| 静态库 | `target/release/libdocscard_runtime_wasm.rlib` | 994,740 |

（M0 时为 1,220,584 / 570,186 字节；M2① 引入 `zip` + `sha2` 后增大。两者 mtime 均为 14:31。）

### 7.4 版本库边界

```bash
cd Android/Project/KhatKit
git status --short   # -> ?? docscard-runtime-wasm/
git diff --stat | wc -l   # -> 0
```

本 crate 是唯一由本工作流新增的目录，既有文件零改动（`git diff` 为空）。
**注意**：同一工作树里还有一个与本 crate 无关的未跟踪文件
`khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/KkGroupedList.kt`（mtime 14:31，属 Mochi UI 工作流，
该目录最近提交 `d30691ab3`），不是 t6/t7/t10 的产物；提交本 crate 时**不要**把它一起 `git add`。

### 7.5 M2① 安全边界（t11 复审结论：pass）

| 防护 | 实现 | 证据 |
|---|---|---|
| zip slip ×4（`..` / 绝对路径 / 反斜杠 / 符号链接） | `check_entry_name`（`src/package.rs:230-251`，含盘符）、`S_IFLNK` 拒绝（346-350）、路径校验先于任何内容读取 | `tests/package.rs:281-345`、`445-465` |
| 8 MiB 整包 | 两道闸门：`open_path` 先 `metadata().len()`（394-406）再 `fs::read`；`open` 内再兜底 | `src/package.rs:958-985`；实测 200 MiB 包 RSS 峰值 4,644 KB（修复前 208,896 KB） |
| 32 MiB 解压预算 | `read_entry_capped` 64 KiB 分块、全局 `total_read` 先记账再判、超限立即 Err（554-583） | `src/package.rs:737-789`、`tests/package.rs:209-254` |
| 256 条目 + EOCD 一致性 | 自解析 central directory 计条目/查重；`archive.len() != declared_entries` → fail-closed（303-309） | `src/package.rs:989-1007`、`1065-1092` |
| 顶层白名单默认拒绝 | `classify_entry` 只认 `manifest.json` / `card.wasm` / `assets/**` / `locales/**`（440-457） | `tests/package.rs:408-424` |
| sha256 | 整包文件字节的 sha256（`archive_sha256`）+ `verify_sha256`（接受 `sha256:` 前缀/大写/空白） | `tests/package.rs:470-523` |
| 错误可区分 | `PackageError` 20 变体、`kind()` 24 个稳定串，与 `docs/PACKAGE.md` 全一致 | 脚本核对 24/24 命中，缺失 0 |

### 7.6 M2① 剩余缺口（如实列出）

1. **签名 / 市场校验未做**：只有整包 `sha256` 哈希与 `verify_sha256`（是否调用由上层决定）；没有发布者签名、
   证书链、`min_app_version` 判定，也没有从市场元数据读取 expected sha256 的链路。
2. **Android 未交叉编译**：未构建 arm64-v8a / armeabi-v7a / x86_64 产物，未接 Gradle（无 `build.gradle.kts`
   任务、无 ABI 打包、无 `.so` 合并），只在 Linux x86_64 上验证。
3. **Kotlin / JNI 未接**：没有 `external fun` 绑定、没有 `libkhatkit_core.so`，Android 侧还无法调用本 crate。
4. **无 WASI**：卡片只能 import `kk.*`；时钟/随机走 `kk.now_ms` / `kk.rand`，文件/网络只经 `kk.host_call` 白名单。
5. **不解压到磁盘**：`assets/**` 与 `locales/**` 全在内存 `BTreeMap`（键为包内全路径），是否落盘、落哪、覆盖策略由宿主决定。
6. **默认宿主无能力**：`DefaultHost` 对所有 `host_call` 返回 `Denied`；真实能力需注入 `CardHost` 实现。
7. **zip 支持面**：不支持 zip64（按 `PackageError::Zip` 拒绝）；压缩方式只有 `deflate`（无 zstd/lzma/bzip2）。
8. **Doc-tests 仍为 0 条**：`src/lib.rs` 顶部示例用单反引号而非三反引号 + `no_run` 围栏（M0 缺口 6 未变）。
9. **无 `.gitignore`**：`target/` 未忽略，`git status -uall` 会列出缓存文件（M0 缺口 7 未变）；提交前需补。
10. **单线程同步 API**：`open`/`into_runtime`/`init`/`handle` 均阻塞；每次导出调用独立 fuel 预算，无跨调用累计 CPU 预算，
    并发与取消由上层调度。
