# .kkcard 包格式（装载与安全校验）

本文是 `docscard-runtime-wasm` 的 **.kkcard 装载契约**，与实现逐一对齐：

* 实现：[src/package.rs](../src/package.rs)（`CardPackage` / `PackageError` / `check_entry_name`）
* 集成测试：[tests/package.rs](../tests/package.rs)（17 条，覆盖本文每一条规则）
* 上游规格：`docs/card-runtime-wasm.md` §4（卡片包格式）、§9（安全与资源限额）
* ABI / 限额：[docs/ABI.md](ABI.md)

**边界**：本 crate 只做 **zip 解析 + 结构/路径/预算校验 + sha256**，全部在内存中完成，**不解压到磁盘**。
包从哪来（市场下载、本地文件、内置）、如何落地 `assets/**`、发布者签名与证书链校验，都在 Kotlin 宿主层。

---

## 1. 包布局

`.kkcard` 是一个普通 zip（deflate）：

| 条目 | 必填 | 说明 |
|---|---|---|
| `manifest.json` | ✅ | 精确名；UTF-8 JSON；字段语义见 [docs/ABI.md §7](ABI.md#7-limits-语义) 与 `docs/card-runtime-wasm.md` §4 |
| `card.wasm` | ✅ | 精确名；ABI v1 卡片字节码 |
| `assets/**` | ❌ | 任意嵌套；内容原样放进 `CardPackage::assets`（键为包内全路径，如 `assets/logo.png`） |
| `locales/**` | ❌ | 任意嵌套；同样进 `assets`（键如 `locales/zh.json`） |

除此之外**任何顶层文件条目都被拒绝**（`PackageError::UnknownEntry`），包括名为 `assets` /
`locales` 的文件、`extra.txt`、`src/main.rs`。**目录条目**（名字以 `/` 结尾，如 `assets/`）不算未知条目：
只做路径校验后跳过，不会出现在 `assets` 里。

装载后 `CardPackage` 暴露：

| 字段 / 方法 | 含义 |
|---|---|
| `manifest: Manifest` / `manifest_json: String` | 已校验的 manifest 与原文（原文交给 `kk_init`） |
| `wasm_bytes: Vec<u8>` | card.wasm 字节 |
| `assets: BTreeMap<String, Vec<u8>>` | assets/** 与 locales/**，键为包内全路径 |
| `archive_sha256: String` | **整个 .kkcard 文件**的 sha256（小写 hex） |
| `asset(name) -> Option<&[u8]>` | 按包内全路径取资产 |
| `verify_sha256(hex)` | 与 `archive_sha256` 比对（见 §5） |
| `into_runtime(host) -> Result<CardRuntime<H>, PackageError>` | 用包内 manifest + wasm 实例化运行时 |

---

## 2. 硬限额（与市场限制一致）

| 常量 | 值 | 语义 | 违反时 |
|---|---:|---|---|
| `MAX_ARCHIVE_BYTES` | 8 MiB（8 388 608 B） | `.kkcard` 文件本身大小 | `ArchiveTooLarge { size, limit }` |
| `MAX_UNCOMPRESSED_BYTES` | 32 MiB（33 554 432 B） | 所有条目解压后**总量** | `UncompressedBudgetExceeded` |
| `MAX_ENTRIES` | 256 | zip 条目数（含目录条目） | `TooManyEntries { count, limit }` |

实现细节（源码位置）：

* 8 MiB 闸门有两道：
  * `CardPackage::open` 最前面按 `bytes.len()` 判定（[src/package.rs:281](../src/package.rs#L281)），
    **早于** zip 解析：字节已在内存时不会继续往下解析。
  * `CardPackage::open_path` **先用 `std::fs::metadata(path)?.len()` 与 8 MiB 比较，再 `fs::read`**
    （[src/package.rs:394](../src/package.rs#L394)）：磁盘上超大的 .kkcard 在**读文件之前**就返回
    `ArchiveTooLarge`，不会先读进内存再判大小。读入后 `open` 的大小闸门仍是兜底（覆盖
    stat 与 read 之间文件被换掉的竞态）。
* 32 MiB 是**边读边扣**的预算：`read_entry_capped` 每次读 64 KiB
  （[src/package.rs:554](../src/package.rs#L554)），累计超过预算立即返回，
  **不会先整体解压再检查** —— 这是 zip bomb 的关键防线。
  错误里的 `bytes_read` 是**跨条目的全局累计已读字节数**（先记账、再判预算，因此包含"跨过边界"的
  那一块，落在 `(32 MiB, 32 MiB + 64 KiB]`）；`entry` 是跨过边界的那个条目，它之后的条目一次都没被读过。
* 条目数从 **central directory 原始字节**统计（[src/package.rs:468](../src/package.rs#L468)），
  而不是 `ZipArchive::len()`：`zip` crate 会把重名条目静默去重（后写覆盖先写），
  只信它会同时放过"重名"和"条目数超限"两种攻击。
* 紧接着比对 `archive.len() == declared_entries`（[src/package.rs:307](../src/package.rs#L307)）：
  两边不一致（例如 EOCD 注释里塞了伪签名，让"文件里最后一个 EOCD 签名"与真正解析器读到不同的条目数）
  一律 **fail-closed**，返回 `PackageError::Zip("central directory count mismatch")`。
* **不支持 zip64**：`total_entries == 0xFFFF` 的包按 `PackageError::Zip` 拒绝（卡片包不需要 zip64）。

---

## 3. 路径安全（zip slip 防护）

逐条目校验（**先于**任何内容读取，目录条目也要验），规则见
`check_entry_name`（[src/package.rs:230](../src/package.rs#L230)）：

| 规则 | 拒绝的输入示例 | `PathUnsafeReason` |
|---|---|---|
| 名字非空 | `""` | `Empty` |
| 不含 NUL | `"a\0b"` | `Nul` |
| 不含反斜杠 | `"a\b"` | `Backslash`（Windows 分隔符，绕过 `/` 检查的常见手法） |
| 非绝对路径 | `"/abs"`、`"C:/x"`、`"C:\x"` | `Absolute` |
| 无 `..` 组件 | `"../evil"`、`"assets/../evil"` | `ParentTraversal` |

另外两条来自 zip 元数据：

* **符号链接条目一律拒绝**（unix mode `S_IFLNK`，[src/package.rs:347](../src/package.rs#L347)）→
  `SymlinkEntry`：解压/落盘时符号链接可能指向包外。
* **重名条目一律拒绝** → `DuplicateEntry`（同样由 central directory 判定，见 §2）。

因为条目不落盘，这里防的不是"写坏宿主目录"，而是**别把危险路径带进 Kotlin 侧后续落盘逻辑**：
宿主可以放心把 `assets` 的键当相对路径用，但**仍然禁止**出现 `..` / 绝对路径 / 反斜杠。

---

## 4. 校验顺序

`CardPackage::open` 的固定顺序（可据此预测先报哪个错）：

0. （仅 `open_path`）`metadata().len()` > 8 MiB → `ArchiveTooLarge`，**此时还没读文件**
1. 文件字节数 > 8 MiB → `ArchiveTooLarge`
2. 计算整包 sha256（存入 `archive_sha256`）
3. 解析 central directory：zip 结构非法 → `Zip`；条目数 > 256 → `TooManyEntries`；
   重名 → `DuplicateEntry`；`archive.len() != declared_entries` →
   `Zip("central directory count mismatch")`
4. 逐条目：路径安全 → `UnsafePath`；目录条目跳过；符号链接 → `SymlinkEntry`；
   顶层白名单 → `UnknownEntry`；按 64 KiB 扣解压预算 → `UncompressedBudgetExceeded`
5. 缺 `manifest.json` → `MissingManifest`；缺 `card.wasm` → `MissingCardWasm`
6. manifest 解析/校验（复用 `Manifest::parse_and_validate`）→ `Manifest(ManifestError)`
7. `into_runtime` 阶段才实例化卡片 → `Runtime(RuntimeError)`

顺序的含义：**结构问题永远先于 manifest 语义错误**（包坏了不会先去解释 manifest），
**manifest 永远先于实例化**（不许用半合法的包去跑 wasm）。

---

## 5. sha256 校验

语义：**对整个 .kkcard 文件字节**做 sha256（不是 per-entry），与市场发布字段一致。

```rust
let package = CardPackage::open_path("card.kkcard")?;      // 装载即算好 archive_sha256
package.verify_sha256(market_sha256_hex)?;                 // 与市场记录比对
let mut runtime = package.into_runtime(host)?;
runtime.init(&package.manifest_json)?;
let result = runtime.handle(event_json)?;
```

* `verify_sha256` 接受：纯 64 位 hex、`sha256:<hex>` / `SHA256:<hex>` 前缀、大小写混写、
  首尾空白。
* 格式非法（长度 ≠ 64 或非 hex）→ `InvalidSha256`（**不**当成 mismatch，便于区分"市场字段坏了"
  和"文件被换过"）。
* 内容不匹配 → `Sha256Mismatch { expected, actual }`，其中 `expected` 是 normalize 后的小写，
  `actual` 是实际包哈希。
* 可选：`archive_sha256` 可直接拿去打日志/审计。

发布者签名、证书链、市场 `sha256` 字段来源都不在本 crate。

---

## 6. 错误码表（`PackageError` 全量）

`PackageError::kind()` 是稳定字符串（CLI 打印 `[package:<kind>]`，宿主可用于分支与埋点）。
每个变体、每个 kind 都在 [src/package.rs:89](../src/package.rs#L89)（`kind()` 在
[src/package.rs:134](../src/package.rs#L134)）中定义。

| 变体 | `kind()` | 触发条件 |
|---|---|---|
| `Io(String)` | `io` | 文件读失败（`open_path`） |
| `Zip(String)` | `zip` | 不是 zip / 截断 / central directory 损坏 / zip64 |
| `ArchiveTooLarge { size, limit }` | `archive-too-large` | 整包 > 8 MiB |
| `UncompressedBudgetExceeded { entry, bytes_read, limit }` | `uncompressed-budget-exceeded` | 解压总量 > 32 MiB（边读边扣）；`bytes_read` = 全局累计已读字节数（含触发块），`entry` = 跨过边界的条目（见 §2） |
| `TooManyEntries { count, limit }` | `too-many-entries` | 条目数 > 256 |
| `DuplicateEntry { name }` | `duplicate-entry` | 同名条目出现两次 |
| `UnsafePath { name, reason }` | `path-empty` / `path-nul` / `path-backslash` / `path-absolute` / `path-traversal` | 见 §3；kind 后缀对应 `PathUnsafeReason` |
| `SymlinkEntry { name }` | `symlink-entry` | unix 符号链接条目 |
| `UnknownEntry { name }` | `unknown-entry` | 顶层白名单之外的文件条目（含名为 `assets` / `locales` 的文件；以 `/` 结尾的目录条目跳过，不报错） |
| `MissingManifest` | `missing-manifest` | 没有 `manifest.json` |
| `MissingCardWasm` | `missing-card-wasm` | 没有 `card.wasm` |
| `Manifest(ManifestError)` | 见下表 | manifest 解析/校验失败 |
| `Sha256Mismatch { expected, actual }` | `sha256-mismatch` | `verify_sha256` 不匹配 |
| `InvalidSha256(String)` | `sha256-invalid` | 期望值不是 64 位 hex |
| `Runtime(RuntimeError)` | `runtime` | `into_runtime` 之后的实例化/执行/限额错误（细节看 `RuntimeError::kind()`，见 [docs/ABI.md §5](ABI.md#5-错误码表kkhost_call--kkrand-的负值或非-0-返回值)） |

`PackageError::Manifest` 的 `kind()` 直接映射 `ManifestError`（[src/package.rs:134](../src/package.rs#L134)）：

| `ManifestError` | `PackageError::kind()` |
|---|---|
| `UnsupportedRuntime(_)` | `manifest-unsupported-runtime`（如 `runtime: "js"`） |
| `AbiVersionMismatch { .. }` | `manifest-abi-mismatch` |
| `UnknownCapability(_)` | `manifest-unknown-capability` |
| `InvalidCapability(_)` | `manifest-invalid-capability` |
| `LimitsOutOfRange { .. }` | `manifest-limits-out-of-range` |
| `InvalidJson(_)` | `manifest-invalid-json` |

`PathUnsafeReason::as_str()`（`Display` 与错误消息用）与 kind 后缀不同，别混用：

| `PathUnsafeReason` | `as_str()` | `kind()` 后缀 |
|---|---|---|
| `Empty` | `empty-name` | `path-empty` |
| `Nul` | `nul-byte` | `path-nul` |
| `Backslash` | `backslash` | `path-backslash` |
| `Absolute` | `absolute-path` | `path-absolute` |
| `ParentTraversal` | `parent-traversal` | `path-traversal` |

---

## 7. CLI 用法

```bash
cd Android/Project/KhatKit/docscard-runtime-wasm
CARGO_HOME=/tmp/cargo-home cargo build --release

# 直接跑包（manifest 从包内读，不接受 --manifest）
./target/release/docscard run --package card.kkcard --event '{"type":"tick"}'

# 带整包 sha256 校验（sha256sum 输出可直接给）
./target/release/docscard run --package card.kkcard --sha256 "$(sha256sum card.kkcard | cut -d" " -f1)"

# 失败时 exit 1 + [package:<kind>] 前缀
#   docscard: [package:sha256-mismatch] package sha256 mismatch: expected …, got …
```

`--card` 与 `--package` 互斥；`--sha256` 只适用于 `--package`；`--package` 模式下 `--manifest` 被拒绝
（manifest 必须来自包内，防止"包内一份、外面一份"的混淆）。

---

## 8. 与 docs/card-runtime-wasm.md 的对应关系

| 规格 | 落点 | 状态 |
|---|---|---|
| §4 `.kkcard`（zip）`manifest.json` + `card.wasm` + `assets/**` + `locales/**` | `CardPackage::open` 白名单 | ✅ 一致 |
| §4 `≤8 MiB 压缩 / ≤32 MiB 解压` | `MAX_ARCHIVE_BYTES` / `MAX_UNCOMPRESSED_BYTES`（边读边扣） | ✅ 一致（另加 256 条目上限） |
| §4 `abi_version` 不匹配时拒绝执行 | `Manifest::parse_and_validate` → `manifest-abi-mismatch` | ✅ 一致 |
| §4 `capabilities` 白名单 | 装载只校验字符串；调用期由 `kk.host_call` 强制（[docs/ABI.md §6](ABI.md#6-capability-码表)） | ✅ 两层分工 |
| §9 签名 `sha256` | `archive_sha256` + `verify_sha256`（整包） | ✅ 只做哈希；发布者签名/证书链不在本 crate |
| §9 `能力超集一律拒绝` | manifest 未知能力在装载阶段就失败（`manifest-unknown-capability`） | ✅ 一致 |
| §9 审计 `audit_report` | 未实现（manifest 未知字段按 spec 忽略） | ❌ 不在 M2① |

---

## 9. 已知边界

* **不解压到磁盘**：`.kkcard` 内容全在内存；宿主若要写 `assets`，自己决定路径与覆盖策略。
* **不做发布者签名 / 证书链**：只做整包 sha256（`verify_sha256` 由调用方决定是否调用）。
* **不支持 zip64 / 加密 zip / 非 deflate 压缩**：`Cargo.toml` 只启用 `deflate` feature。
* **不做 `min_app_version` 判定**、不读 `audit_report`、不做市场字段校验。
* **包内 `card.wasm` 的 ABI 校验发生在 `into_runtime`**：装载成功 ≠ 可执行，宿主应把
  `runtime` 类错误和 `package` 类错误分开提示。
