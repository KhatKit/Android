# SZ-B 移植方案：PDF 去 mupdf（纯 Kotlin 文本提取，-9MB so）

> 结论：**不在 50 步内直搬**。上游 `815e9c20b` 是「删 64 个 JNI 绑定 Java（6557 行）+ 删 2 个 .so（19MB）+ 新增 13 个 Kotlin 文件（3320 行）+ 7 个资源 + 17 个测试资产」的整体替换。
> 按 captain 口径：本文件只交方案与分片清单，**不分片落树**，避免半成品进 document 模块。

## 1. 上游事实（`git show 815e9c20b`）

| 项 | 数字 |
|---|---|
| 变更量 | 105 文件，+9159 / −6566（37 A / 66 D / 2 M） |
| 删除 | 64 个 `com/artifex/mupdf/fitz/*.java`（6557 行）+ `libmupdf_java.so` ×2（arm64 9,444,584 B / x86_64 9,818,488 B） |
| 修改 | `document/.../PdfParser.kt`（实现换掉，**公共签名不变**）、`document/consumer-rules.pro`（删 3 行 `-keep com.artifex.mupdf.fitz.**`） |
| 新增 main Kotlin | 13 文件 3320 行：PdfTextExtractor 835 / PdfLexer 443 / PdfFile 439 / PdfFont 375 / PdfCMap 357 / PdfFilters 260 / PdfSecurity 241 / CffFont 233 / PdfEncodings 213 / TrueTypeCmap 153 / CidToUnicode 80 / StandardFontMetrics 65 / PdfObjects 51 |
| 新增 main 资源 | 7 个：`cid-{CNS1,GB1,Japan1,Korea1}.bin`、`core14-widths.txt`、`glyphlist.txt`(4282 行)、`texglyphs.txt`(131 行)，目录 `resources/me/rerere/document/pdf/` |
| 新增测试 | 17 个：`PdfParserTest.kt`(183) / `PdfCorpusTest.kt`(50) + 15 个 PDF 语料（加密 RC4/AES、对象流、增量、坏 xref、CJK 预定义 CMap、裁剪/表单、RTL 竖排…） |
| 实测收益 | commit message：arm64 APK 35.4MB → 30.9MB |

## 2. 本 fork 现状（与上游 pre-fix 同构）

| 项 | 位置 |
|---|---|
| 入口（唯一公共符号） | `document/src/main/java/heizige/kk/khatkit/document/PdfParser.kt:6-18`：`PDFDocument.openDocument(...).asPDF()` 逐页 `toStructuredText().asText()`，输出 `---Page N:\n` 拼接 |
| JNI 绑定 | `document/src/main/java/com/artifex/mupdf/fitz/**`：64 个 .java / 6557 行（与上游删除量逐字相等） |
| 原生库 | `document/src/main/jniLibs/{arm64-v8a,x86_64}/libmupdf_java.so`：9.44MB / 9.82MB（jniLibs 共 19MB） |
| R8 keep | `document/consumer-rules.pro:1-3` 与上游完全相同 |
| 唯一调用点 | `app/.../core/data/ai/transformers/DocumentAsPromptTransformer.kt:47`（`PdfParser.parserPdf(file)` → String），:78 只对 `application/pdf` 走此路径 |
| 模块解耦度 | `document/build.gradle.kts` 无 mupdf 依赖、无 ndk/jni 段；全仓 `com.artifex` 引用只出现在 `document/`（外部 grep = 0） ⇒ 删文件即解耦 |
| 测试现状 | `document/src/test` 只有 `ExampleUnitTest.kt`，无 PDF 语料 ⇒ 上游 17 个测试资产必须一并搬，否则回归无护栏 |

## 3. API 面与依赖点

- **对外 API 面 = 1 个函数**：`PdfParser.parserPdf(file: File): String`（签名、返回格式都不变）⇒ app 侧零改动。
- 上游 13 个新类型**全部 `internal`**（`PdfFile`/`PdfTextExtractor` 也是 internal）⇒ 不构成模块 API，后续可自由重构。
- 资源加载走 `PdfEncodings::class.java.getResourceAsStream("$RESOURCE_DIR/$file")`，常量 `RESOURCE_DIR = "/me/rerere/document/pdf"`（PdfObjects.kt:51）⇒ 移植必须改为 `/heizige/kk/khatkit/document/pdf`，并同步资源目录。
- 三方依赖：无（只用 kotlin stdlib + `java.util.zip.Inflater`），与 document 现有 `core-ktx/appcompat/material` 无冲突。
- `LegacyImportGuard` 白名单不涉及（只查 Kedge/Miuix/Khromia）。

## 4. 分片清单（每片可独立编译，按依赖顺序）

| 片 | 内容（上游文件） | 行数 | 依赖 | 退出条件 |
|---|---|---:|---|---|
| S1 对象/词法/文件 | PdfObjects + PdfLexer + PdfFile + PdfFilters | 1193 | 无 | 能打开普通 PDF，`pages()` 数与 mupdf 一致；xref 表/流、对象流、增量更新、坏 xref 修复 |
| S2 安全 | PdfSecurity | 241 | S1 | RC4-40/128、AES-128/256、user/owner 密码 5 个语料全部可读 |
| S3 编码 | PdfEncodings + PdfCMap + CidToUnicode + StandardFontMetrics + 7 个资源 | 715 | S1 | StandardEncoding/WinAnsi/MacRoman、预定义 CJK CMap、TeX 字形名（含 cmex 尺寸后缀） |
| S4 字体 | PdfFont + CffFont + TrueTypeCmap | 761 | S1,S3 | Type1/TrueType/Type0(CID)/Type3 + ToUnicode 缺失时 CID→Unicode 回退 |
| S5 抽取 | PdfTextExtractor | 835 | S2,S4 | TJ/Tj、ActualText、可选内容、注释外观流、BBox 裁剪、RTL 重排 |
| S6 切换/删依赖 | `PdfParser.kt` 改写（13 行）+ 删 64 java + 删 2 so + consumer-rules 清空 + 包名/资源路径适配 | — | S1–S5 | `document` 目录零 `com.artifex`；`assembleDebug` 过；apk 内无 `libmupdf_java.so` |
| S7 验收 | 搬 2 个测试 + 15 个 PDF 语料；`:app:test`；`assembleRelease` 量体积；真机导入 PDF 回归 | — | S6 | arm64 release 体积对比（预期 −4.5MB APK），arm64/x86_64 so 从产物消失 |

**建议批次**（每批一次提交，便于回滚）：①S1+S2 ②S3+S4 ③S5+S6+S7。共约 3320 行主代码 + 233 行测试。

## 5. 移植适配点（fork ≠ upstream 的四处）

1. **包名**：`me.rerere.document.pdf` → `heizige.kk.khatkit.document.pdf`（13 文件 + 2 测试 + 资源目录三处一致）。
2. **资源路径**：`PdfObjects.kt` 的 `RESOURCE_DIR` 常量必须同步改，否则 S3 起所有字形名/CMap 查找返回空（静默降级，最难发现）。
3. **consumer-rules.pro**：整块删（含注释），否则 release 仍保留已不存在的 `com.artifex` keep 规则。
4. **jniLibs**：删 `arm64-v8a` 与 `x86_64` 两个 `libmupdf_java.so`；确认无 `ndk { abiFilters }` 或 `sourceSets.jniLibs` 残留引用。

## 6. 风险与验收

- **行为等价面只有文本**：fork 无页面渲染（不调 `loadPage().toPixmap()`），因此「功能不变」= 同一 PDF 的文本输出可比。建议 S6 时对 5 份真实语料做 `mupdf 旧输出 vs 新输出` 的 diff，记录已知差异（字距空格、连字、RTL 重排、竖排顺序），差异写进本文件附录。上游 commit 已声明支持范围，但**文本细节不保证逐字一致**。
- **加密 PDF**：两边默认都用空密码；需要用户口令的场景行为一致（失败），移植时不要顺手加密码交互。
- **体积口径**：只认 `assembleRelease`（R8 后）的 arm64 universal/native 产物，且要确认 `libmupdf_java.so` 不在 apk 内；debug 包看不出收益。
- **默认字体回退**：上游有 `FallbackFont`（PdfFont.kt:68），旧 mupdf 有内置字体集 ⇒ 语料里「无嵌入字体的中文 PDF」要单独验。
- **上游漂移**：pdf/* 后续若再有提交，按 `me.rerere→heizige.kk` 映射同步；建议在本文件维护「上游 commit ↔ fork 映射」表。

## 7. 工作量估算

| 批次 | 内容 | 估步数 |
|---|---|---:|
| ① | S1+S2（1434 行 + 少量单测） | 25–35 |
| ② | S3+S4（1476 行 + 7 资源） | 25–35 |
| ③ | S5+S6+S7（835 行 + 删依赖 + 2 测试 + 15 语料 + 体积验收） | 25–35 |
| 合计 | | **75–105 步 / 2–3 个会话** |

⇒ 单个 50 步任务装不下，建议按上表拆成 3 个 implementation 任务 + 1 个 verification（体积 + 功能回归）。
