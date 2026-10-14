# APK 体积归因与瘦身方案（t33 实测，2026-10-14）

> **本轮只测量 + 出方案，未改任何 Gradle 配置 / 源码 / 资源。**
> 树状态：HEAD `2505cf7a1`（未提交）+ 本轮未提交改动；跑前跑后 `git status --porcelain` 未变（详见 §8）。
> 所有数字都是**本机实测**，每条都附可复现命令；凡「用不到」的判定都给了 `file:line` 或全仓 0 命中证据。

---

## 0. 结论摘要（先看这个）

### 0.1 首要问题的答案：release 包多大？

**release 比 debug 小一半以上，而 R8 一直是开着的 —— 只是它只作用于 release，而你现在装的是 debug。**

| 产物 | 字节数 | MiB | 相对「你现在装的 debug universal」 |
|---|---:|---:|---:|
| **debug universal（当前装的）** | 110,880,336 | 105.74 | ±0 |
| debug arm64-v8a | 94,073,173 | 89.72 | −15.16% |
| debug x86_64 | 94,833,434 | 90.44 | −14.47% |
| release universal | 56,695,215 | 54.07 | **−48.87%** |
| **release arm64-v8a** | **40,801,108** | **38.91** | **−63.20%** |
| release x86_64 | 41,349,037 | 39.43 | −62.71% |

⇒ **`optimization { enable = true }`（`app/build.gradle.kts:78-80`）就是 AGP 9.x 的「开 R8 代码收缩 + 资源收缩」开关**（§3.3 有 AGP 源码原文），它是**生效的**：release 的 dex 从 **39 个 / 156 MB 降到 3 个 / 24 MB**，资源表从 **8,956 条降到 5,797 条**，并产出了 238 MB 的 `mapping.txt`（= 真的在做混淆重命名）。

**所以最大的收益在「发布哪个产物」，不在删代码。**

### 0.2 一句话建议

**零风险、不删任何功能：把发行物从 `app-universal-debug.apk`（105.74 MiB）换成 `app-arm64-v8a-release.apk`（38.91 MiB），体积降到 36.8%，功能一个不少。** 唯一要做的事是准备签名密钥与包名迁移（§6.2 有完整前置条件清单）。

### 0.3 三级方案速览（详见 §5）

| 档 | 内容 | 预计节省（压缩后实测口径） | 风险 | 影响功能 | 需你拍板 |
|---|---|---:|---|---|---|
| **T0** | 换 release 包 + 只发 arm64 | **−70.1 MB**（105.74 → 38.91 MiB） | 极低 | 否 | **是**（签名/包名） |
| T1 | Gradle 配置微调（去掉 universal、精简 ABI） | 再 −0 / 已含在 T0 | 低 | 否 | 是 |
| T2 | 大资源改按需下载（simple_dict / mermaid） | 再 −6.4 MB | 中 | 离线能力降级 | 是 |
| T3 | 删 OCR / 扫码 / mupdf / 终端 | 最多再 −15 MB | 高 | **是，核心功能** | 不建议（§7） |

---

## 1. 测量环境与口径

### 1.1 环境

- 机器：16 GB，全程**串行**（`flock /tmp/kk-gradle.lock` + `--max-workers=1` + `--offline` + `-Dorg.gradle.daemon.idletimeout=120000`）。
- 跑前 `pgrep -af GradleDaemon` = 无；跑后 `./gradlew --stop` 收尾。
- Gradle 9.7.1 / AGP **9.3.1** / JDK 21。

### 1.2 两个口径（全文都标清楚是哪一个）

| 口径 | 含义 | 用途 |
|---|---|---|
| **未压缩** | `zipinfo`/`zipfile` 的 `file_size`（条目解压后字节） | 看「代码/资源真实体量」，R8 前后对比用这个 |
| **压缩后** | `compress_size`（APK 内实际占用） | **等于用户下载/安装的字节数**，`ls -l` 与「包太大」的感受用这个 |

⚠️ 两个口径差距很大（debug universal 未压缩 249.35 MiB vs 压缩后 102.15 MiB，压缩率 41%），**混用会得出错误结论**。

### 1.3 关键构建配置事实（只读核对，未改）

| 事实 | 位置 | 对体积的影响 |
|---|---|---|
| ABI splits 已开，`include("arm64-v8a", "x86_64")`，`isUniversalApk = true` | `app/build.gradle.kts:38-48` | 一次构建出 **3 个包**，universal 同时含两套 .so |
| `ndk.abiFilters += listOf("arm64-v8a", "x86_64")` | `app/build.gradle.kts:33-35` | 只打进这两个 ABI |
| **`optimization { enable = true }`**（仅 release） | `app/build.gradle.kts:78-80` | **R8 代码收缩 + 资源收缩**（§3.3） |
| **全仓没有 `isMinifyEnabled` / `isShrinkResources`** | `grep -rn 'isMinifyEnabled\|isShrinkResources\|optimization' --include='*.kts' .` → 只命中 `app/build.gradle.kts:78` | 唯一的开关就是上面那条 |
| `packaging.jniLibs.useLegacyPackaging = true` | `app/build.gradle.kts:104-109` | **.so 被 deflate 压缩**（`method=8`）：APK 变小、安装后解压占用变大 |
| `debug { applicationIdSuffix = ".debug" }` | `app/build.gradle.kts:84-88` | debug 包名 `heizige.kk.khatkit.debug`、release 包名 `heizige.kk.khatkit` ⇒ **两者数据不互通**（§6.2） |
| 无 `app/proguard-rules.pro` | — | R8 只用 AGP 默认规则 + 各 AAR 的 `proguard.txt`（本次 release 构建成功，无缺规则报错） |

---

## 2. 三个 debug 产物的体积剖面

### 2.1 分类总表（压缩后口径 + 未压缩对照）

reproduce 命令：

```bash
cd /home/heizige/文档/Android/Project/KhatKit
python3 /tmp/t33-profile.py app/build/outputs/apk/debug/app-universal-debug.apk \
                               app/build/outputs/apk/debug/app-arm64-v8a-debug.apk \
                               app/build/outputs/apk/debug/app-x86_64-debug.apk
# 等价的手工命令（单条目看压缩方法）：
zipinfo -l app/build/outputs/apk/debug/app-universal-debug.apk | grep -E 'libmlkit_google_ocr_pipeline.so|resources.arsc'
```

| 分类 | 条目 | 未压缩 B | 压缩后 B | arm64 包压缩后 | x86_64 包压缩后 |
|---|---:|---:|---:|---:|---:|
| `dex`（根目录 `classes*.dex`） | **39** | **156,126,152** | **54,649,604** | 54,649,604 | 54,649,604 |
| `lib/arm64-v8a` | 17 | 33,828,872 | 16,044,507 | 16,044,507 | — |
| `lib/x86_64` | 17 | 36,046,680 | 16,804,869 | — | 16,804,869 |
| `assets/` | 216 | 23,741,536 | 10,495,482 | 10,495,482 | 10,495,482 |
| `res/` | 1104 | 7,859,251 | 5,548,298 | 5,548,298 | 5,548,298 |
| `resources.arsc` | 1 | 3,402,712 | 3,402,712 | 3,402,712 | 3,402,712 |
| `META-INF/` | 159 | 127,396 | 46,811 | 46,811 | 46,811 |
| 其他根文件 | 162 | 332,400 | 123,717 | 123,717 | 123,717 |
| **合计（zip 内容）** | 1715 / 1698 / 1698 | 261,464,999 | 107,116,000 | 90,311,131 | 91,071,493 |
| **APK 文件系统字节** | — | — | **110,880,336** | **94,073,173** | **94,833,434** |

> 差 110,880,336 − 107,116,000 = 3.76 MB 是 zip 中央目录 + 条目头 + APK Signing Block 开销（1698 个条目 × ~100 B + 目录）。
> **三个包的 dex / assets / res / arsc / META-INF 完全相同**（同一份代码与资源），唯一差别就是带了哪套 `lib/<abi>`。

### 2.2 dex（39 个，未压缩 156,126,152 B = 148.9 MiB）

| dex | 未压缩 B | 压缩后 B | methods | classes | 主要内容（按符号串数） |
|---|---:|---:|---:|---:|---|
| `classes34.dex` | 45,084,292 | 11,704,734 | **65,432** | 17,161 | `androidx/compose/material`(11508)、`material3`(3838) |
| `classes.dex` | 16,276,444 | 6,256,709 | **65,020** | 9,396 | `compose/foundation`(3476)、`camera/camera2`(1580) |
| `classes39.dex` | 15,168,676 | 5,593,410 | 52,734 | 7,326 | `org/apache/commons`(2955)、`top/yukonga/miuix`(1716) |
| `classes38.dex` | 14,141,564 | 5,309,989 | **65,490** | 9,247 | `kotlin/reflect/jvm`(2614)、`io/ktor/server`(1242)、MCP SDK(1039) |
| `classes36.dex` | 12,415,208 | 5,178,113 | **65,081** | 9,538 | `com/google/android`(2968)、`fasterxml/jackson`(1299)、`media3/extractor` |
| `classes37.dex` | 11,107,140 | 4,484,978 | **65,270** | 8,872 | `com/google/common`(3846)、`haze`(607) |
| `classes35.dex` | 11,486,384 | 4,834,586 | **65,202** | 6,906 | `compose/ui`、`media3/exoplayer`(1161)、`datastore/preferences` |
| 其余 32 个 | 30,446,444 | 11,299,085 | 各 485–13,199 | — | `heizige/kk/khatkit` 自身为主（28 个包含它） |
| **合计 39** | **156,126,152** | **54,649,604** | **553,100** | **86,962** | — |

**为什么 debug 有 39 个 dex（本地实测，非猜测）：**

1. **debug 不跑 R8** ⇒ 一份代码都没被删/合并/改短名。合计 **553,100 个方法引用 / 86,962 个类**要原样落盘。
2. DEX 格式的**单个 dex 上限是 65,536 个方法引用**。实测其中 **6 个 dex 正好顶到天花板**（65,020 / 65,081 / 65,202 / 65,270 / 65,432 / 65,490），其余按来源桶切分。
3. AGP 的 debug 去重链路是**三个桶**分别合并，再一起进 APK：
   `app/build/intermediates/dex/debug/mergeExtDexDebug` = **7** + `mergeLibDexDebug` = **15**（= 15 个 library 模块各一份）+ `mergeProjectDexDebug` = **16**（app 自身按上限切） = **38**，合并后落盘 **39** 个。
   ```bash
   find app/build/intermediates/dex/debug -name '*.dex' | wc -l      # 38
   unzip -l .../app-universal-debug.apk | grep -c 'classes[0-9]*\.dex'  # 39
   ```
4. ⇒ **dex 的 54.6 MB（压缩后）几乎全部是「没开 R8」造成的**。release 同一份代码只剩 **3 个 dex / 10,594,863 B**（§3.4）。

### 2.3 `lib/<abi>` 逐个 `.so`

所有 `.so` 的压缩方法都是 **`defN`（deflate）**——因为 `useLegacyPackaging = true`：

```bash
zipinfo -l app/build/outputs/apk/debug/app-universal-debug.apk | grep libmlkit_google_ocr_pipeline.so
# 11064544 b-  4886463 defN  ... lib/arm64-v8a/libmlkit_google_ocr_pipeline.so
```

**arm64-v8a（17 个，未压缩 33,828,872 / 压缩后 16,044,507）**

| .so | 未压缩 B | 压缩后 B |
|---|---:|---:|
| `libmlkit_google_ocr_pipeline.so` | 11,064,544 | 4,886,463 |
| `libmupdf_java.so` | 9,444,584 | 4,890,194 |
| `libbarhopper_v3.so` | 4,946,720 | 2,338,617 |
| `libkhatkit_core.so` | 2,097,912 | 1,046,509 |
| `libsqlite3x.so` | 1,844,104 | 983,172 |
| `libsimple.so` | 1,705,672 | 621,661 |
| `libucrop.so` | 1,162,840 | 536,965 |
| `libquickjs.so` | 790,688 | 406,295 |
| `libtermux.so` | 285,648 | 102,252 |
| `libproot_exec.so` | 267,752 | 128,721 |
| `vector.so` | 141,152 | 74,298 |
| `libimage_processing_util_jni.so` | 32,528 | 17,064 |
| `libproot_loader.so` | 18,136 | 1,339 |
| `libandroidx.graphics.path.so` | 10,096 | 4,682 |
| `libdatastore_shared_counter.so` | 7,784 | 3,053 |
| `libsurface_util_jni.so` | 4,896 | 1,885 |
| `libworkspace.so` | 3,816 | 1,337 |

**x86_64（17 个，未压缩 36,046,680 / 压缩后 16,804,869）**

| .so | 未压缩 B | 压缩后 B |
|---|---:|---:|
| `libmlkit_google_ocr_pipeline.so` | 11,626,128 | 4,929,375 |
| `libmupdf_java.so` | 9,818,488 | 5,078,483 |
| `libbarhopper_v3.so` | 5,909,280 | 2,681,022 |
| `libkhatkit_core.so` | 2,205,192 | 1,110,561 |
| `libsqlite3x.so` | 1,847,920 | 1,012,753 |
| `libsimple.so` | 1,694,168 | 630,316 |
| `libucrop.so` | 1,293,184 | 585,982 |
| `libquickjs.so` | 833,096 | 425,444 |
| `libproot_exec.so` | 282,968 | 133,836 |
| `libtermux.so` | 281,200 | 102,911 |
| `vector.so` | 160,464 | 77,056 |
| `libimage_processing_util_jni.so` | 49,416 | 24,587 |
| `libproot_loader.so` | 18,352 | 1,450 |
| `libandroidx.graphics.path.so` | 10,760 | 4,913 |
| `libdatastore_shared_counter.so` | 7,336 | 2,928 |
| `libsurface_util_jni.so` | 4,912 | 1,910 |
| `libworkspace.so` | 3,816 | 1,342 |

> `libsimple.so` 源码是 9,076,576 B（`app/src/main/jniLibs/arm64-v8a/libsimple.so`），打进包只剩 1,705,672 B ⇒ 是 `:app:stripDebugDebugSymbols` 剥掉符号的结果；`libmupdf_java.so`（9,444,584）与 `libkhatkit_core.so`（2,097,912）源文件与包内**一样大**，本来就是 strip 过的。

### 2.4 `assets/` 前 15 大项（三个 debug 包完全相同：216 项 / 未压缩 23,741,536 / 压缩后 10,495,482）

| # | 条目 | 未压缩 B | 压缩后 B |
|---:|---|---:|---:|
| 1 | `simple_dict/idf.utf8` | 5,998,717 | 2,389,951 |
| 2 | `simple_dict/jieba.dict.utf8` | 5,071,204 | 2,182,850 |
| 3 | `html/mermaid.min.js` | 3,565,102 | 1,109,120 |
| 4 | `simple_dict/pos_dict/prob_emit.utf8` | 1,687,686 | 661,000 |
| 5 | `mlkit-google-ocr-models/gocr/gocr_models/line_recognition_legacy_mobile/Hani_ctc/optical/lstm_model.fb` | 891,872 | 598,909 |
| 6 | `emoji/categories.with.modifiers.min.json` | 738,579 | 59,303 |
| 7 | `simple_dict/hmm_model.utf8` | 519,739 | 235,273 |
| 8 | `mlkit_barcode_models/barcode_ssd_mobilenet_v1_dmp25_quant.tflite` | 390,456 | 390,456 |
| 9 | `mlkit-google-ocr-models/taser/detector/rpn_text_detector_mobile_space_to_depth_quantized_mbv2_v1.tflite` | 340,760 | 340,760 |
| 10 | `simple_dict/pos_dict/char_state_tab.utf8` | 327,139 | 120,711 |
| 11 | `mlkit-google-ocr-models/gocr/gocr_models/line_recognition_legacy_mobile/tflite_langid.tflite` | 315,520 | 315,520 |
| 12 | `mlkit-google-ocr-models/gocr/gocr_models/line_recognition_legacy_mobile/Latn_ctc/optical/lstm_model.fb` | 309,568 | 181,046 |
| 13 | `mlkit_barcode_models/oned_feature_extractor_mobile.tflite` | 276,552 | 276,552 |
| 14 | `mlkit_barcode_models/oned_auto_regressor_mobile.tflite` | 213,880 | 213,880 |
| 15 | `mlkit-google-ocr-models/taser/segmenter/tflite_script_detector_0.3.lstm_model` | 161,448 | 118,347 |

**按子树汇总（更有用）：**

| assets 子树 | 条目 | 未压缩 B | 压缩后 B | 来源 |
|---|---:|---:|---:|---|
| `simple_dict/**` | 9 | 13,742,014 | 5,646,112 | 签入仓库（§4） |
| `mlkit-google-ocr-models/**` | 25 | 2,490,310 | 1,923,334 | `text-recognition-chinese` AAR |
| `html/**`（mermaid.min.js + mark.html） | 2 | 3,576,038 | 1,112,799 | 签入仓库 |
| `org/scilab/forge/**`（jlatexmath 字体） | 117 | 2,124,815 | 732,790 | jlatexmath 依赖 |
| `mlkit_barcode_models/**` | 3 | 880,888 | 880,888 | `barcode-scanning` AAR |
| `icons/**`（供应商图标 svg/png） | 54 | 180,715 | 136,284 | 签入仓库 |
| `emoji/**` | 1 | 738,579 | 59,303 | 签入仓库 |
| `builtin_skills` | 1 | 6,124 | 2,805 | 签入仓库 |
| 其余（`libs`、`holidays_cn.json`） | 4 | 2,053 | 1,167 | 签入仓库 |

> 注意 `.tflite` 是 **stored（不压缩）**，所以「未压缩 = 压缩后」。同理 OCR 的大 `.fb` 模型压缩率也只有 ~30%。

### 2.5 `res/` / `resources.arsc` / `META-INF/`

| 项 | debug | release | 说明 |
|---|---:|---:|---|
| `res/` 条目数 | 1104 | 713 | 资源收缩删掉 391 个文件 |
| `res/` 未压缩 / 压缩后 | 7,859,251 / 5,548,298 | 3,080,682 / 2,675,648 | −60.8% / −51.8% |
| `resources.arsc` | 3,402,712（stored） | 2,119,564（stored） | **−37.7%** |
| 资源表条目（`aapt2 dump resources \| grep -c '^    resource 0x'`） | 8956 | 5797 | **−35.3%** |
| `META-INF/` | 159 项 / 127,396 | 157 项 / 127,499 | 全是 `*.version` 与 LICENSE，可忽略 |

debug `res/` 最大的 3 项（都在 release 里被重编码或删掉）：

| debug 条目 | 未压缩 B | release 对应 | 未压缩 B |
|---|---:|---|---:|
| `res/font/google_sans_flex.ttf` | 4,153,300 | **已被删除** | 0 |
| `res/drawable/placeholder.png` | 1,201,271 | `res/FD.png`（= `drawable/placeholder`） | 1,101,775 |
| `res/drawable/placeholder_dark.png` | 1,070,756 | `res/8K.png`（= `drawable/placeholder_dark`） | 953,677 |

> release 的资源名被 AGP 优化成短名（`res/FD.png`），**用 `aapt2 dump resources` 才能映射回原名**：
> ```bash
> aapt2 dump resources app/build/outputs/apk/release/app-arm64-v8a-release.apk | grep -B2 'FD.png'
> #   resource 0x7f080192 drawable/placeholder
> #     () (file) res/FD.png type=PNG
> ```
> `google_sans_flex.ttf` 为什么被删：它是**真正的死资源**——见 §3.6。

---

## 3. release 实测与 R8 语义

### 3.1 构建命令与结果

```bash
cd /home/heizige/文档/Android/Project/KhatKit
nice -n 19 flock /tmp/kk-gradle.lock ./gradlew --offline --max-workers=1 \
  -Dorg.gradle.daemon.idletimeout=120000 :app:assembleRelease
```

- 执行方式：`setsid nohup … < /dev/null & disown`（脱离进程组），跑前 `pgrep -af gradle` 确认无其他 gradle。
- 时间：2026-10-14 14:32:35 → 14:40:08，**实测耗时 7m 32s**。
- **EXIT = 0**，逐字：`BUILD SUCCESSFUL in 7m 32s` / `614 actionable tasks: 571 executed, 43 up-to-date`。
- 日志：`/tmp/t33-release.log`（59,105 B）sha256 `848c9782bf145d15b1b2e9f425a1cfdfca1d9f2c247aafac91cb8b9e0a5c2b80`。
- **签名前置条件已满足**：`local.properties` 里 `storeFile` / `storePassword` / `keyAlias` / `keyPassword` 四个键都在（`app/build.gradle.kts:50-73` 读取），构建直接成功、无需补任何键。

### 3.2 release 体积

| 产物 | 字节数 | MiB | mtime |
|---|---:|---:|---|
| `app-universal-release.apk` | 56,695,215 | 54.07 | 14:40:05.293260240 |
| `app-arm64-v8a-release.apk` | 40,801,108 | 38.91 | 14:40:07.155249459 |
| `app-x86_64-release.apk` | 41,349,037 | 39.43 | 14:40:05.827257148 |

release universal 的分类剖面（对比 §2.1）：

| 分类 | 条目 | 未压缩 B | 压缩后 B |
|---|---:|---:|---:|
| `dex` | **3** | 24,237,644 | 10,594,863 |
| `lib/arm64-v8a` | 17 | 33,809,520 | 15,343,779 |
| `lib/x86_64` | 17 | 36,029,240 | 15,891,810 |
| `assets/` | 218 | 23,756,894 | 9,687,173 |
| `res/` | 713 | 3,080,682 | 2,675,648 |
| `resources.arsc` | 1 | 2,119,564 | 2,119,564 |
| `META-INF/` | 157 | 127,499 | 47,157 |
| 其他根文件 | 162 | 331,836 | 123,611 |
| **合计** | 1288 | 123,492,879 | 56,483,605 |

> `assets/` 从 216 项涨到 218 项、压缩后反而从 10,495,482 降到 9,687,173：多出的两项是 **baseline profile**（`assets/dexopt/baseline.prof` 14,434 + `baseline.profm` 924），debug 没有；压缩后变小是 release 打包用了不同的 deflate 参数。

### 3.3 `optimization { enable = true }` 到底是什么（AGP 9.3.1 源码原文）

从 AGP 的 sources jar 里取出权威定义（`gradle-api-9.3.1-sources.jar` → `com/android/build/api/dsl/Optimization.kt`）：

```kotlin
interface Optimization {
  /**
   * Specifies whether to enable code shrinking and resource optimization. Property works for applications only.
   * For other types it will be ignored
   *
   * By default, value is false to avoid runtime issue. When enabled the Android plugin uses R8 for optimization.
   *
   * Flavours merge this property with "or" rule. That means having this enable=true sticks.
   */
  var enable: Boolean
  ...
}
```

配套的内部语义（同 jar 的 `OptimizationDslInfo.kt`）：

```kotlin
/** Returns true if R8 optimization is on and only for application module */
val applicationOptimizationEnabled: Boolean
```

**结论（明确）：`optimization { enable = true }` ≡ `isMinifyEnabled = true` + `isShrinkResources = true` 的 AGP 9 新写法，默认值为 `false`。本项目只在 `release` 上开了它，`debug` 没有。**
`grep -rn 'isMinifyEnabled|isShrinkResources|optimization' --include='*.kts' .` 全仓只命中 `app/build.gradle.kts:78` 一处 ⇒ 没有第二处开关，也没有重复/冲突配置。

复现取源码：

```bash
G=$(find ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/gradle-api/9.3.1 -name '*sources.jar' | head -1)
unzip -p "$G" com/android/build/api/dsl/Optimization.kt
```

### 3.4 R8 生效的实测证据（5 条，互相独立）

| # | 证据 | 数值 |
|---:|---|---|
| 1 | **任务真的跑了**，且不是 UP-TO-DATE | `/tmp/t33-release.log:634` → `> Task :app:minifyReleaseWithR8` |
| 2 | **产出了混淆映射表** | `app/build/outputs/mapping/release/mapping.txt` = **238,459,222 B**；另有 `usage.txt` 13,002,737 B、`seeds.txt` 5,455,967 B、`resources.txt` 874,997 B |
| 3 | **dex 数 39 → 3，方法引用 553,100 → 129,491** | 未压缩 156,126,152 → 24,237,644（−84.5%） |
| 4 | **类定义 86,962 → 20,336**（−76.6%） | R8 真的删了/内联/合并了代码 |
| 5 | **资源被收缩** | `res/` 1104 → 713 项；资源表 8956 → 5797 条；`resources.txt` 记录 3,343 条 `not reachable` |

release 的 3 个 dex：

| dex | 未压缩 B | 压缩后 B | methods | classes |
|---|---:|---:|---:|---:|
| `classes.dex` | 4,819,744 | 2,306,633 | 30,432 | 4,476 |
| `classes2.dex` | 8,516,284 | 3,751,235 | 53,038 | 7,515 |
| `classes3.dex` | 10,901,616 | 4,536,995 | 46,021 | 8,345 |
| **合计** | **24,237,644** | **10,594,863** | **129,491** | **20,336** |

**结论：当前 release 已经处于「代码收缩 + 混淆 + 资源收缩」状态**，不需要再补配置；要变小的唯一动作是**改用它**。

### 3.5 release 与 debug 的行为差异（切换前必须知道）

R8 + 资源收缩会让 release 与 debug **不等价**。本轮实测发现了一个具体案例，并顺手排查了一个可疑项：

**(a) `google_sans_flex.ttf` 被 release 删除 —— 经核查是无害的（死资源）**

- 现象：`resources.txt:7177` → `font:google_sans_flex:2131296256 is not reachable.`；release 的 `res/` 里确实没有它。
- 排查：全仓引用 `google_sans_flex` 的**唯一**活性位置本应在 `app/src/main/java/heizige/kk/khatkit/app/core/ui/theme/GoogleSans.kt:40/49/67/…`（`R.font.google_sans_flex`）；
  但 `GoogleSansFlex`（`GoogleSans.kt:29`）**唯一被引用的地方全是注释**——`app/src/main/java/heizige/kk/khatkit/app/core/ui/theme/Type.kt:19-75` 每一行都以 `//` 开头。
- ⇒ R8 判定 `GoogleSansFlex` 是死代码并移除（`usage.txt:93620` 列出 `heizige.kk.khatkit.app.R$font: public static int google_sans_flex`），资源收缩随之删掉这个 **4.15 MB / 压缩后 2.42 MB** 的字体。**没有任何活性代码路径依赖它。**
- 真正的活字体是 `jetbrains_mono`（`Type.kt:83`、`WorkspaceTerminalPage.kt:376`），它在 release 里**仍在**（`res/zy.ttf` 303,144 B）。
- ⚠️ 副作用提醒：如果将来有人把 `Type.kt` 里那段注释取消掉，引用会变成活性、R8 会自动保留 ⇒ 不会踩坑；但**反过来说，debug 与 release 的字体集不同**，用 debug 包做视觉验收时看不到这个差别。

**(b) UCrop 被资源收缩削掉 5 条内部资源 —— 抽查结论：活的裁剪页不受影响**

- `resources.txt` 里 `ucrop_` 相关：**123 条 `reachable from`**（含 `layout/ucrop_activity_photobox`、`ucrop_aspect_ratio` 等活路径）/ **5 条 `is not reachable`**（`layout:ucrop_fragment_photobox`、`drawable:ucrop_ic_next`、`dimen:ucrop_height_divider_shadow`、`styleable:ucrop_AspectRatioTextView`、`styleable:ucrop_UCropView`）。
- 应用侧走的是 `UCropActivity`（`app/.../core/ui/components/ai/CropLauncher.kt`），不是 `UCropFragment` ⇒ 被删的都是用不到的 ucrop 内部件。
- **仍建议**在切换 release 前把 §6.2 的真机清单过一遍（见下表），因为 R8 的差异无法靠静态检查穷尽。

**(c) 需要真机走一遍的清单（切换 release 后）**

| 路径 | 为什么 | 相关大件 |
|---|---|---|
| 设置 → 字体/排版 | 字体资源集不同（§3.5a） | `google_sans_flex.ttf` 已删 |
| 聊天附件 → 裁剪图片 | 资源收缩动过 ucrop 内部资源（§3.5b） | `libucrop.so` |
| 附件发 PDF 并提问 | mupdf JNI + `DocumentAsPromptTransformer` | `libmupdf_java.so` |
| 卡片脚本 `tool.ocrText/ocrBoxes` | MLKit OCR JNI + 模型 assets | `libmlkit_google_ocr_pipeline.so` + OCR 模型 |
| 群聊二维码扫描 | MLKit barcode JNI + 模型 assets | `libbarhopper_v3.so` + barcode 模型 |
| 消息搜索（中文） | FTS jieba 扩展 + 词典 assets | `libsimple.so` + `simple_dict/**` |
| 回复里的 ```mermaid 代码块 | WebView 加载 assets 里的 JS | `html/mermaid.min.js` |
| 工作区终端 | proot + termux JNI | `libproot_*`、`libtermux.so` |
| 表情选择器 | 读 assets JSON | `emoji/categories.with.modifiers.min.json` |

---

## 4. 逐个大件的可达性判定（四层：工具/入口 → 服务/仓库 → 实体/存储 → DI/装配）

> 判定口径：**「在用」= 有活性代码路径 + 依赖/装配都接上了**；**「待定」= 证据不足，并写明还缺什么**。
> 大小以 debug universal 为准（未压缩 / 压缩后）。

### 4.1 总表

| 大件 | 未压缩 B | 压缩后 B | 判定 | 一句话依据 |
|---|---:|---:|---|---|
| `lib/libmlkit_google_ocr_pipeline.so` | 11,064,544 | 4,886,463 | **在用** | 卡片 `tool.ocrText/ocrBoxes` 的 MLKit 中文 OCR JNI |
| `assets/mlkit-google-ocr-models/**`（25 项） | 2,490,310 | 1,923,334 | **在用** | 同一 OCR 依赖带的模型，运行时由 MLKit 内部加载 |
| `lib/libmupdf_java.so` | 9,444,584 | 4,890,194 | **在用** | PDF 附件 → 文本，`PdfParser` 唯一实现 |
| `lib/libbarhopper_v3.so` | 4,946,720 | 2,338,617 | **在用** | 群聊二维码扫描（MLKit barcode bundled） |
| `assets/mlkit_barcode_models/**`（3 项） | 880,888 | 880,888 | **在用** | 同上依赖带的 tflite 模型 |
| `lib/libucrop.so` | 1,162,840 | 536,965 | **在用** | 聊天附件裁剪 |
| `assets/simple_dict/**`（9 项） | 13,742,014 | 5,646,112 | **在用** | 中文 FTS 的 jieba 词典，`jieba_dict()` / `jieba_query()` |
| `lib/libsimple.so` | 1,705,672 | 621,661 | **在用** | 同一 FTS 分词扩展的 native 实现 |
| `assets/html/mermaid.min.js` | 3,565,102 | 1,109,120 | **在用** | Mermaid 代码块在 WebView 里渲染 |
| `assets/emoji/categories.with.modifiers.min.json` | 738,579 | 59,303 | **在用** | 表情选择器数据，Hilt 注入 `EmojiData` |
| `lib/libtermux.so` + `libproot_exec.so` + `libproot_loader.so` | 571,536 | 232,312 | **在用** | 工作区终端（proot rootfs） |
| `assets/org/scilab/forge/**`（117 项） | 2,124,815 | 732,790 | **在用** | JLatexMath 数学字体 |
| `lib/libkhatkit_core.so` | 2,097,912 | 1,046,509 | **在用** | 卡片 Rust 桥核心 |
| `lib/libsqlite3x.so` | 1,844,104 | 983,172 | **在用** | requery SQLite（含 FTS），Room 与 `jieba` 扩展宿主 |
| `lib/libquickjs.so` | 790,688 | 406,295 | **在用** | 卡片 JavaScript 引擎 |
| `lib/vector.so` | 141,152 | 74,298 | **在用** | `sqlite-vector`，记忆向量检索 |
| `lib/libimage_processing_util_jni.so` | 32,528 | 17,064 | **在用** | MLKit vision-common（OCR/条码共用） |
| `lib/libworkspace.so`、`lib/libdatastore_shared_counter.so`、`lib/libsurface_util_jni.so`、`lib/libandroidx.graphics.path.so` | 26,592 | 9,750 | 在用（体量可忽略） | workspace CMake / datastore / compose / graphics |
| `res/font/google_sans_flex.ttf`（debug 有、release 无） | 4,153,300 | 2,415,921 | **不用（死资源）** | 唯一引用方 `GoogleSansFlex` 只被注释引用，见 §3.5a |

### 4.2 逐条证据（可直接复跑）

**① `libmlkit_google_ocr_pipeline.so` + `assets/mlkit-google-ocr-models/**` —— 在用**
- 依赖/装配层：`khatkit/build.gradle.kts:53` → `implementation("com.google.mlkit:text-recognition-chinese:16.0.1")`；`.so` 来自其传递依赖 `text-recognition-bundled-common-17.0.0.aar` 的 `jni/arm64-v8a/libmlkit_google_ocr_pipeline.so`（11,064,544 B 与包内**逐字节同尺寸**），模型 assets 来自 `text-recognition-chinese-16.0.1.aar` 的 `assets/mlkit-google-ocr-models/…`。
  ```bash
  for f in $(find ~/.gradle/caches/modules-2/files-2.1/com.google.mlkit -name '*.aar'); do
    unzip -l "$f" | grep -q 'libmlkit_google_ocr_pipeline' && echo "HIT $f"; done
  ```
- 服务/仓库层：`khatkit/src/main/java/heizige/kk/khatkit/bridge/impl/AndroidToolBridge.kt:316 override fun ocrText(path)`、`:333 override fun ocrBoxes(path)`，客户端在 `:366-367 private val ocrRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }`。
- DI/装配层：`:app` 侧 `app/.../core/data/ai/tools/KhatKitTools.kt:210 private val ocrBridge by lazy { AndroidToolBridge(...) }`；卡片侧 `khatkit/.../bridge/impl/BridgeFactory.kt:72 tool = AndroidToolBridge(appContext, http)`。
- 用户面：`docs/script-api-reference.md:123-124`（`tool.ocrText` / `tool.ocrBoxes`）、`docs/lua-card-development.md:547 / 897 / 1290`。
- ⇒ **卡片脚本能力，删掉会破坏 `tool.ocr*`。**

**② `libmupdf_java.so` —— 在用**
- 签入仓库：`git ls-files | grep libmupdf` → `document/src/main/jniLibs/arm64-v8a/libmupdf_java.so`（9,444,584 B，与包内同尺寸）。
- 实体层：`document/src/main/java/heizige/kk/khatkit/document/PdfParser.kt:6 object PdfParser`（唯一实现）。
- 服务/调用层：**全仓唯一调用者** `app/.../core/data/ai/transformers/DocumentAsPromptTransformer.kt:11 import …PdfParser` + `:47 return PdfParser.parserPdf(file)`。
  ```bash
  grep -rn 'PdfParser' --include='*.kt' . | grep -v '/build/'   # 只有上面两条 + 定义
  ```

**③ `libbarhopper_v3.so` + `assets/mlkit_barcode_models/**` —— 在用**
- 依赖层：`app/build.gradle.kts:276 implementation(libs.mlkit.barcode.scanning)`（`gradle/libs.versions.toml:157` → `com.google.mlkit:barcode-scanning:17.3.0`，bundled）；`.so` 与 tflite 均来自 `barcode-scanning-17.3.0.aar`。
- 使用层：`app/.../core/ui/components/ui/QrScannerSheet.kt:42-46`（`BarcodeScanner` / `BarcodeScanning` / `InputImage` imports）、`:468 .addOnSuccessListener { barcodes ->`。
- 入口层（四层最后一层）：**唯一入口** `app/.../feature/chat/GroupChatPage.kt:45 import …QrScannerSheet` + `:260 QrScannerSheet(`。
  ```bash
  grep -rn 'QrScannerSheet' --include='*.kt' . | grep -v '/build/' | grep -v test
  ```
- ⚠️ 注意：群聊「入口按钮」在 t19 被有意隐藏（`ChatDrawer.kt:949 ShowGroupChatEntry = false`），但**已存在的群聊会话仍能打开**（t19 结论），且扫码只存在于群聊页 ⇒ 判定仍是「在用」；如果将来群聊整体下线，这一项才变成「不用」。
- 附注：`app/build.gradle.kts:262-263` 的注释说「实时扫码的 libbarhopper 已移除」指的是 **zxing 那条路**，说的不是 MLKit barcode；两者不要混。

**④ `libucrop.so` —— 在用**
- 依赖层：`app/build.gradle.kts:241 implementation(libs.ucrop)`（`ucrop = "2.2.11-native"`）。
- 使用层：`app/.../core/ui/components/ai/CropLauncher.kt`、`app/.../core/ui/components/ai/ChatAttachmentPicker.kt`、`app/.../core/util/ImageUtils.kt`（`ImageUtils` 还负责 EXIF 方向）。

**⑤ `assets/simple_dict/**` + `lib/libsimple.so` —— 在用**
- 签入仓库：`git ls-files | grep simple_dict` → 9 个文件（`idf.utf8` / `jieba.dict.utf8` / `hmm_model.utf8` / `pos_dict/*` / `stop_words.utf8` / `user.dict.utf8`）；`git ls-files | grep jniLibs` → `app/src/main/jniLibs/{arm64-v8a,x86_64}/libsimple.so`。
- 服务层：`app/.../core/data/db/fts/SimpleDictManager.kt:8 private const val DICT_ASSET_DIR = "simple_dict"`，`:15` 注释「将 assets/simple_dict 解压到 files/simple_dict，返回词典目录」。
- 实体/存储层：`app/.../core/data/db/AppDatabaseFactory.kt:34 db.query("SELECT jieba_dict(?)", arrayOf(dictDir.absolutePath))`（注册 jieba 分词器），失败会报 `:42 "jieba_dict failed: …"`。
- 查询层：`app/.../core/data/db/fts/MessageFtsManager.kt:86 WHERE text MATCH jieba_query(?)`。
- ⇒ **消息全文搜索（中文分词）直接依赖它**，`libsimple.so` 就是同一套 jieba/simple 分词器的 native 实现。

**⑥ `assets/html/mermaid.min.js` —— 在用**
- 加载层：`app/.../core/ui/components/richtext/Mermaid.kt:193 <script src="${WEB_VIEW_ASSET_URL}/html/mermaid.min.js"></script>`（WebView 注入）。
- 触发层：`app/.../core/ui/components/richtext/HighlightCodeBlock.kt:174 completeCodeBlock && normalizedLanguage == "mermaid" ->`。
- ⇒ 回复里出现 ```` ```mermaid ```` 代码块时用它渲染；同一目录下另一个文件 `html/mark.html`（10,936 B）也是这条链的一部分。

**⑦ `assets/emoji/categories.with.modifiers.min.json` —— 在用**
- 存储层：`app/.../core/util/EmojiUtils.kt:10 context.assets.open("emoji/categories.with.modifiers.min.json")`。
- DI/装配层：`app/.../core/di/AppHiltModule.kt:86 fun provideEmojiData(@ApplicationContext context: Context): EmojiData = EmojiUtils.loadEmoji(context)`。
- ⚠️ 收益很低（压缩后只有 59,303 B）——虽然判定「在用」，但**不值得为它做任何事**。

**⑧ `libtermux.so` + `libproot_exec.so` + `libproot_loader.so` —— 在用**
- 签入仓库：`workspace/src/main/jniLibs/{arm64-v8a,x86_64}/libproot_{exec,loader}.so`；`libtermux.so` 来自 `libs.termux.terminal.view`（`gradle/libs.versions.toml:205` `com.termux.termux-app:terminal-view:0.118.0`）。
- 装配层：`app/build.gradle.kts:107 pickFirsts += "lib/*/libtermux.so"`。
- 使用层：`app/.../feature/workspace/WorkspaceTerminalSession.kt:37 val proot = File(nativeLibraryDir, "libproot_exec.so")`、`:38 val loader = File(nativeLibraryDir, "libproot_loader.so")`、`:85 proot.absolutePath`（真正 exec 起来）；`app/.../feature/workspace/WorkspaceTerminalPage.kt:57-58`（`TerminalSession` / `TerminalView`）。
- 提示词层：`app/.../transformers/WorkspaceReminderTransformer.kt:97` 会向模型宣告沙箱 Linux 工作区 ⇒ 是**对外承诺的能力**。
- ⇒ 合计压缩后只有 **232 KB**，为省这点钱砍掉整个工作区终端完全不划算（§7）。

**⑨ `assets/org/scilab/forge/**` —— 在用**（超出验收清单，顺手补的）
- 依赖层：`app/build.gradle.kts:303-305 implementation(libs.jlatexmath)` + `.font.greek` + `.font.cyrillic`。
- 路径即证据：`assets/org/scilab/forge/jlatexmath/fonts/{base,euler}/jlm_*.ttf`（47 个 ttf）。

### 4.3 「待定」项与还缺什么证据

| 项 | 现状 | 还缺什么证据才能定 |
|---|---|---|
| `assets/icons/**`（54 项 / 未压缩 180,715 / 压缩后 136,284） | **倾向「在用」**：`AIIconRoutes.kt:26 val assetPath = "icons/$iconPath"`（内置 Web API 对外提供供应商图标） | 需要确认**所有** 54 个图标是否都被 `AIIconRoutes` 的映射表引用，还是要按供应商动态拼路径（动态拼就删不得）。**收益只有 136 KB，不值得投入更多取证。** |
| `res/raw/*.mp3`（`gangguan.mp3` / `asr_start.mp3` / `asr_stop.mp3` / `bingbingbing.mp3`，合计 144,402 B） | 未查 | 需要 grep 音频播放调用点。**收益 ~141 KB，不建议为它做改动。** |
| `assets/builtin_skills`（`skill-creator`，6,124 B） | **在用**（CHANGELOG:31「内置技能接线」），已够判定 | — |

---

## 5. 分级瘦身方案（按「收益 ÷ 风险」排序）

> **节约数字统一用「压缩后」口径**（= 用户实际下载/安装的字节），基线 = 当前 debug universal 110,880,336 B / 105.74 MiB。
> 每一项都标：改动面 / 风险与回退 / 是否影响功能 / 是否需要你拍板。

### 🟢 Tier 0：只改发布方式，不删任何功能（**零风险，收益最大**）

| # | 动作 | 预计节省 | 改动面 | 风险与回退 | 影响功能 | 需拍板 |
|---|---|---:|---|---|---|---|
| **T0-1** | **把发行物从 debug universal 换成 release arm64** | **−70,079,228 B = −66.83 MiB（105.74 → 38.91 MiB，−63.2%）** | 发布流程（不改任何代码/配置） | 极低。回退 = 装回 debug 包。**但要注意包名不同（§6.2）** | **无**（同一份源码 + R8；§3.5 有真机清单） | **是**（签名 + 包名迁移） |
| **T0-2** | 发行物改用 **release universal**（要兼容 x86_64 时） | −54,185,121 B = −51.67 MiB（→ 54.07 MiB，−48.9%） | 发布流程 | 极低 | 无 | 是（同上） |
| **T0-3** | 更新通道**按 ABI 分发**：服务端对 `arm64` 设备返回 arm64 包 | 相对 universal 再省 **−15,894,107 B = −15.16 MiB** | 服务端（`/api/khatkit/app/update` 增加 ABI 判断） | 低。回退 = 恢复返回 universal | 无（但**必须**保证判错时不给出装不上的包） | 是 |

> **T0 是本次报告的核心结论**：不删一行业务代码、不动一个 Gradle 配置，包体从 105.74 MiB 降到 38.91 MiB。

### 🟡 Tier 1：低风险 Gradle 配置（不删功能，但要重新出包）

| # | 动作 | 预计节省 | 改动面 | 风险与回退 | 影响功能 | 需拍板 |
|---|---|---:|---|---|---|---|
| T1-1 | `splits.abi.isUniversalApk = false`（不再产 universal） | 0（只是不再产出那个大包，防止误发） | Gradle（`app/build.gradle.kts:46`） | 极低，回退改回 `true` | 无 | 是（若 CI/脚本依赖 universal 产物名会断） |
| T1-2 | `abiFilters` / `splits.include` 里去掉 `x86_64` | universal 也省 **−16,804,869 B = −16.03 MiB** | Gradle（`:34` 与 `:45`） | 低。回退改回列表 | **模拟器 / Intel 设备装不上**（真机 PKG110 是 arm64） | 是 |
| T1-3 | 保留 `useLegacyPackaging = true`（**不要**改成 false） | 0（这是「不做」的建议） | — | 改成 `false` 会让 APK **变大** +17.8 MiB（详见 §7-R1） | — | 不需要 |

### 🟠 Tier 2：大资源改按需下载（保功能但离线降级，属架构改动）

| # | 动作 | 预计节省 | 改动面 | 风险与回退 | 影响功能 | 需拍板 |
|---|---|---:|---|---|---|---|
| T2-1 | `assets/simple_dict/**` 改成首次进入消息搜索时下载 | **−5,646,112 B = −5.38 MiB** | 源码 + 资源 + 服务端托管 | 中：**离线无网时中文全文搜索不可用**；要加下载/校验/版本化/失败降级。回退 = 放回 assets | 降级（非删除） | **是** |
| T2-2 | `html/mermaid.min.js` 改成首次渲染 mermaid 时下载，或换 `mermaid` 精简构建 | **−1,109,120 B = −1.06 MiB** | 源码 + 资源 | 中：离线时 mermaid 图渲染失败（要给出降级为代码块的提示）。回退 = 放回 assets | 降级 | 是 |
| T2-3 | 词典做**裁剪**（只留常用字/词）而不是外置 | 未知（需先量化命中率损失） | 资源 | 中：搜索召回率下降，且**无法预先量化** | 降级 | 是 |

> T2-1 + T2-2 合计再省 **−6.44 MiB**（38.91 → ~32.5 MiB）。但两者都要**新增网络依赖**，与「这个 App 的搜索是本地能力」的现状冲突，建议**先只做 T0**，T2 等确有需求再说。

### 🔴 Tier 3：删功能（收益有限，**不建议**，理由见 §7）

| # | 动作 | 最多省 | 为什么不做 |
|---|---|---:|---|
| T3-1 | 去掉 MLKit OCR 中文识别 | ~6.81 MiB（`.so` 4.89 + 模型 1.92） | 破坏 `tool.ocrText` / `tool.ocrBoxes`（卡片自动化能力，文档公开承诺） |
| T3-2 | 去掉 MLKit barcode（改用 zxing 软解） | ~3.22 MiB | 群聊扫码体验显著下降；为 3 MB 换掉一条可用链路不划算 |
| T3-3 | 去掉 mupdf（PDF 解析） | 4.89 MiB | 破坏 PDF 附件提问 |
| T3-4 | 去掉 jlatexmath 字体 | 0.73 MiB | 破坏数学公式渲染 |
| T3-5 | 去掉工作区终端（proot/termux） | 0.23 MiB | **收益最小、损失最大** |
| T3-6 | 全部加起来 | ~15.9 MiB | 只相当于 T0 的一个零头，却砍掉 5 个功能 |

---

## 6. 两个发布层面的问题

### 6.1 `splits` 已开，为什么还有 universal？只发 arm64 能省多少？

**为什么会有 universal**：`app/build.gradle.kts:46 isUniversalApk = true` 明确要求 AGP 额外打一个「含所有已选 ABI」的包。所以一次 `assembleDebug`/`assembleRelease` 会产出 **3 个 APK**：

```
app-universal-*.apk   ← 同时含 arm64-v8a + x86_64 两套 .so（给「不确定设备架构」的分发用）
app-arm64-v8a-*.apk   ← 只含 arm64（真机几乎都是这个）
app-x86_64-*.apk      ← 只含 x86_64（模拟器 / 极少数 Intel 设备）
```

**x86_64 那 36 MB 的账（未压缩，正好等于你说的 36.0 MB）**：

| 口径 | 数值 |
|---|---:|
| `lib/x86_64` 未压缩 | 36,046,680 B（34.38 MiB） |
| `lib/x86_64` **压缩后**（真正占包） | **16,804,869 B（16.03 MiB）** |
| universal − arm64（实测差） | 110,880,336 − 94,073,173 = **16,807,163 B** |
| 差额对不上部分 | 2,294 B（= 去掉 17 个 x86_64 条目省下的 zip 头/目录开销） |

⇒ **「未压缩 36 MB」是解压后的量；用户实际下载时省的是压缩后的 16.03 MiB。** 两者别混。
⇒ **只发 arm64 省 16.03 MiB**（对 debug 是 105.74 → 89.72 MiB；对 release 是 54.07 → 38.91 MiB）。
⇒ 真机 `PKG110`（Android 16）就是 arm64；`x86_64` 只有模拟器与极少数 Intel 平板需要。

### 6.2 若改用 release 包，前置条件清单

| # | 前置条件 | 现状 / 需要做什么 |
|---:|---|---|
| 1 | **签名密钥** | ✅ **已具备**：`local.properties` 有 `storeFile`/`storePassword`/`keyAlias`/`keyPassword`，`app/build.gradle.kts:50-73` 读取；本次 release 构建成功并已签名：`apksigner verify --print-certs` → `CN=Hei zige`，SHA-256 `a83f9cce3182f79e9b77dae7d3fc3306832c63659bbf48dc3c64671e08b5fd8d`。⚠️ **这个密钥必须与更新通道上已发布的 APK 用同一把**，否则下次自更新会因签名不匹配而失败。 |
| 2 | **包名会变（最需要注意的一条）** | debug 包名 `heizige.kk.khatkit.debug`（`applicationIdSuffix = ".debug"`，`app/build.gradle.kts:85`），release 包名 `heizige.kk.khatkit`。**当前设备上只装了 debug 版**（`pm list packages` → `heizige.kk.khatkit.debug`）。⇒ 装 release 是**全新安装**，聊天记录 / 设置 / 工作区 rootfs **不会自动继承**，需要先导出、装完再导入。 |
| 3 | **签名不匹配已装 debug 版** | 已装 debug 包是 Android Debug 证书（SHA-256 `6411a05c…`），release 是 `a83f9cce…` ⇒ 即使包名相同也无法覆盖安装（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），必须先卸载。 |
| 4 | **版本号** | `versionCode = 190` / `versionName = "2.5.5"`（`app/build.gradle.kts:28-29`），debug 与 release 相同。若要**通过更新通道分发**这个更小的包，需要出一个 `versionCode > 190` 的新版本发布（否则客户端 `hasUpdate` 为 false）。 |
| 5 | **更新通道 `GET /api/khatkit/app/update` 是否受影响** | **协议不变、代码不用改**：客户端只发 `currentVersionCode` / `currentVersion` / `channel`（`app/.../core/util/UpdateChecker.kt:53-64`），响应是 `{hasUpdate, forceUpdate, latestVersion, downloadUrl, description}`（`README.md:25-38`）。⚠️ **但它只返回「一个」`downloadUrl`，没有 ABI 字段** ⇒ 想按 ABI 分发（T0-3）**必须改服务端**；不改就只能继续发 universal。 |
| 6 | **`force_min` / `stable` / `beta` 通道语义** | 与包体无关，不受影响。 |
| 7 | **真机验收** | 按 §3.5c 的 9 条清单走一遍（字体、裁剪、PDF、OCR、扫码、中文搜索、mermaid、终端、表情）。`debug` 与 `release` 有 R8 差异，这步不能省。 |

---

## 7. 明确「不建议做」的项（列出来，避免以后被当成待办）

| # | 不建议的方案 | 理由 |
|---|---|---|
| **R1** | 把 `packaging.jniLibs.useLegacyPackaging` 从 `true` 改成 `false`（现代默认） | **APK 会变大**：`.so` 从 deflate 压缩改为原样存储 ⇒ arm64 侧从 16.04 MiB 涨到 32.24 MiB（+16.2 MiB）。它换来的是更小的**安装后**占用与更快的启动，但本任务的诉求是「包太大」⇒ **方向相反**。除非改口径为「安装体积」，否则别动。 |
| **R2** | 为了省几 MB 去砍 OCR / 扫码 / PDF / 终端 / 数学公式 | 见 §5-Tier 3：全部加起来 ~15.9 MiB（只有 T0 的 1/4），却砍掉 5 个功能，且这些都在 `docs/script-api-reference.md` / `docs/lua-card-development.md` / README 里对外承诺过。 |
| **R3** | 把 `assets/simple_dict` 换成一个更小的通用词典 | 需要先量化中文搜索召回损失，**当前没有这个数据**；而且收益（5.38 MiB）远小于 T0。 |
| **R4** | 为 `assets/icons/**`（136 KB）或 `res/raw/*.mp3`（141 KB）做取证与清理 | 收益 < 0.15 MiB，取证成本高于收益 ⇒ 已在 §4.3 标为「待定且不值得继续」。 |
| **R5** | 给 debug 也开 R8（`debug { optimization { enable = true } }`） | 会破坏 debug 的可调试性（断点行号、堆栈可读性、`BuildConfig.DEBUG` 路径），而 debug 包本来就不该发给用户。**正确做法是发 release**（T0-1），不是把 debug 也压一遍。 |
| **R6** | 引入只对体积生效的第三方「资源压缩」魔法配置 | 当前 `optimization.enable = true` 已经同时开了 R8 + 资源收缩，本次实测 `res/` 已从 1104 项降到 713 项、资源表 −35.3%；再加自定义 `shrinkResources`/`resConfigs` 只会增加回归面，收益未知。 |
| **R7** | 直接删掉 `assets/font/google_sans_flex.ttf` 的**源码文件** | 它已经是死资源（§3.5a），删源码文件能省下 **debug** 包的 2.42 MiB，但既然要走 T0（发 release），release 本来就不会打它。**要么只做 T0，要么把「删死代码」当独立小任务**（`Type.kt:19-75` 那些注释块 + `GoogleSans.kt`），不要混在瘦身里。 |

---

## 8. 本轮边界自证（未改任何配置/源码/资源）

- **未改动任何 Gradle 配置**：`app/build.gradle.kts`、`gradle/libs.versions.toml`、`settings.gradle.kts` 一字未动。
- **未改动任何源码 / 资源**：全程只跑测量命令（`stat` / `md5sum` / `unzip` / `zipinfo` / `aapt2 dump` / `apksigner verify` / `grep` / `python3`）与一次**只读**的构建（`:app:assembleRelease`）。
- **HEAD 仍 `2505cf7a1`**，未 `git add` / `commit` / `push`。
- **`git status --porcelain` 跑前 27 行 → 跑后 27 行 + 本文件**（唯一新增 = `?? docs/apk-size-reduction.md`）：
  ```bash
  git status --porcelain | wc -l          # 27（跑前/跑后一致）
  git rev-parse --short HEAD              # 2505cf7a1
  git status --porcelain | grep '^??'     # 其中包含 ?? docs/apk-size-reduction.md
  ```
- **构建产物如实说明**：本次新建了 `app/build/outputs/apk/{debug,release}/*.apk` 与 `app/build/outputs/mapping/release/**`（`mapping.txt` 238 MB、`mapping.prt` 26 MB 等）——全部在 `build/` 目录下，**被 `.gitignore` 忽略**，因此不出现在 `git status` 中，也不是源码改动。
  - ⚠️ 磁盘占用提示：`app/build/outputs/mapping/release/` 合计 **~284 MB**（`mapping.txt` 单文件 238 MB）。若不需要后续排查混淆问题，可以删掉该目录释放空间。
- **未提交**。

---

## 9. 复现命令附录

```bash
cd /home/heizige/文档/Android/Project/KhatKit

# ---------- 0) 串行前提 ----------
pgrep -af gradle            # 必须为空

# ---------- 1) 体积剖面（本报告的 §2 / §3.2 全部数字）----------
python3 /tmp/t33-profile.py app/build/outputs/apk/debug/app-universal-debug.apk
# 或手工：
unzip -l app/build/outputs/apk/debug/app-universal-debug.apk        # 未压缩长度
zipinfo -l app/build/outputs/apk/debug/app-universal-debug.apk      # 压缩后长度 + 压缩方法

# ---------- 2) dex 归属与 64K 上限证据（§2.2 / §3.4）----------
python3 - <<'PY'
import zipfile,struct
z=zipfile.ZipFile('app/build/outputs/apk/debug/app-universal-debug.apk')
for i in z.infolist():
    if i.filename.endswith('.dex'):
        b=z.read(i.filename)
        print(i.filename, i.file_size,
              'methods=', struct.unpack_from('<I',b,0x58)[0],
              'classes=', struct.unpack_from('<I',b,0x60)[0])
PY
find app/build/intermediates/dex/debug -name '*.dex' | wc -l       # 38（三个桶合并前的输入）

# ---------- 3) release 构建（§3.1）----------
setsid nohup /tmp/t33-release.sh < /dev/null > /dev/null 2>&1 & disown
# 脚本内部 = nice -n 19 flock /tmp/kk-gradle.lock ./gradlew --offline --max-workers=1 \
#   -Dorg.gradle.daemon.idletimeout=120000 :app:assembleRelease
sha256sum /tmp/t33-release.log
# 848c9782bf145d15b1b2e9f425a1cfdfca1d9f2c247aafac91cb8b9e0a5c2b80

# ---------- 4) R8 是否生效（§3.3 / §3.4）----------
grep -n 'minifyReleaseWithR8' /tmp/t33-release.log        # :634
ls -l app/build/outputs/mapping/release/mapping.txt        # 238,459,222 B

# ---------- 5) AGP 9.3.1 `optimization` 语义（§3.3）----------
G=$(find ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/gradle-api/9.3.1 -name '*sources.jar' | head -1)
unzip -p "$G" com/android/build/api/dsl/Optimization.kt

# ---------- 6) 资源收缩与短名映射（§2.5 / §3.5）----------
aapt2 dump resources app/build/outputs/apk/release/app-arm64-v8a-release.apk | grep -B2 'FD.png'
grep 'is not reachable' app/build/outputs/mapping/release/resources.txt | wc -l   # 3343
grep 'google_sans_flex' app/build/outputs/mapping/release/resources.txt           # :7177 not reachable

# ---------- 7) 签名（§6.2）----------
AS=$(ls ~/Android/Sdk/build-tools/*/apksigner | tail -1)
"$AS" verify --print-certs app/build/outputs/apk/release/app-universal-release.apk

# ---------- 8) 收尾 ----------
./gradlew --stop
```

> 脚本 `/tmp/t33-profile.py` 与 `/tmp/t33-dex.py` 是本轮为归因写的临时分析器（只读 APK，不改任何东西）；正文已给出等价的手工命令，脚本丢了也能复算。
