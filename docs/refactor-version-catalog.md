# Version Catalog 重构与模板版本核查

## 1. 范围、基线与结论

- 核查日期：2026-11-28。规则来源：[ARCHITECTURE §5–§6](../../ARCHITECTURE.md#5-version-catalog-同步策略)。本次是构建声明重构，不是整栈升级。
- 开始时 HEAD 为 `2ad2ae270e94cd05fc6d0c15733eaff3286e1b52`；t1 的早期基线随后已有并行提交，因此以本次开始时内容核对，不恢复早期树。
- [catalog](../gradle/libs.versions.toml) 中已有用户 WIP `mochi = "0.4.0-khatkit.3"`，原样保留。未修改 Maven 缓存、源码、第三方 submodule 或共享架构映射；未执行 Gradle、APK/设备操作、commit/push。
- 结果：79 个版本键、136 个 library alias、12 个 plugin alias、4 个 bundles。增加 3 组 bundle，8 个使用点将原 22 条声明归组；有效依赖版本、configuration 和 API 暴露保持不变。

## 2. 版本唯一来源与具体变更

| 声明 | 原状 | 本次处理 |
|---|---|---|
| ExifInterface | [app](../app/build.gradle.kts) 直接依赖 1.4.1，但所有 configuration 已 force 1.4.2 | 新增 `exifinterface = "1.4.2"` / `androidx-exifinterface`；直接依赖和 force 共用 alias。与原有效解析值一致，不是把运行时升级到新版本。官方 1.4.2 POM HTTP 200。 |
| Guava | app force 33.6.0-jre | 新增 `guava` 版本键与 `guava-core` alias；force 从 alias 获取同一个坐标，不改变既有 jre 选择。 |
| 中文 ML Kit | [OCR 插件](../plugins/ocr-mlkit/build.gradle.kts) 硬编码 16.0.1 | 新增 `mlkitTextRecognitionChinese` / `mlkit-text-recognition-chinese`，仍仅属于可下载插件的 implementation，不带进 app。缓存 POM 确认坐标/版本；远程请求遇到 TLS EOF，不宣称远程验证成功。 |
| Foojay settings 插件 | [settings](../settings.gradle.kts) 硬编码 1.0.0 | 版本放 `foojayResolver`，并登记 `foojay-resolver` plugin alias。settings 的 catalog accessors 尚不存在，故在 pluginManagement 中通过 ProviderFactory.fileContents 读取同一 TOML，严格匹配唯一版本键，再在 plugins 中请求无版本 ID；不维护第二份版本常量。官方 marker POM HTTP 200，指向 foojay-resolver 1.0.0。 |
| Kotlin/JVM | [card-validator](../card-validator/build.gradle.kts) 使用无版本 ID，从构建插件 classpath 间接取得 Kotlin | 改用 kotlin-jvm alias，根 [build](../build.gradle.kts) 显式 apply false；与 Compose、serialization、build-logic 共用现有 kotlin 2.4.20，不新增第二条 Kotlin 版本线。 |
| Material Icons | [khatkit-ui](../khatkit-ui/build.gradle.kts) 直接写无版本坐标 | 复用已有 androidx-material-icons-extended alias；仍由原 Compose BOM 管理，仍为 implementation。 |
| catalog 内联版本 | xz / slf4j-android / guava-listenablefuture | 各自搬入 versions 并用 version.ref 引用；原字符串完全保留，包括防重复类的 9999.0-empty-to-avoid-conflict-with-guava sentinel。 |

CMake 3.22.1、SDK/JDK 数字、app 发布版本、imageToolbox 分发协议版本以及本地 sherpa JAR 文件名不属于 Maven 库/插件版本，不机械搬成依赖 bundle。[build-logic settings](../build-logic/settings.gradle.kts) 已导入根 TOML；构建插件依赖也已使用 alias，不重复修改。

## 3. Bundles 与 configuration/API 约束

| bundle | 精确成员 | 使用者 | configuration |
|---|---|---|---|
| androidTest（已有） | androidx-junit、androidx-espresso-core | app、ai、common、document、speech、web、workspace | androidTestImplementation；baselineprofile 的 implementation 保持单独声明 |
| android-view-support（新增） | androidx-core-ktx、androidx-appcompat、material | common、document、web、workspace | 全部 implementation |
| kotlinx-api（新增） | kotlinx-serialization-json、kotlinx-coroutines-core、kotlinx-datetime | ai、common | 全部 api；其余模块的 implementation 不混入此组 |
| ktor-client-json（新增） | ktor-client-content-negotiation、ktor-serialization-kotlinx-json | app、khatkit | 全部 implementation |

保留独立声明的边界：common 的 ktor-client-core 是 api，而 client-cio/websockets 是 implementation；web 的公开 Ktor server 与内部 middleware 混合；Compose ui 在 mediapicker 是 api、其他模块是 implementation。故不做宽泛的 ktor/compose bundle，不把 BOM/platform、ksp processor、debug tooling、仪器测试并进普通库组。Exif、Guava、ML Kit、settings 插件没有两个语义相同且共同使用的成员，不为满足字面分组而制造单元素或跨功能 bundle。

## 4. ARCHITECTURE §6 逐项核查

官方 Maven metadata 的 11 次抓取均 HTTP 200；表中“存在”指精确 version 元素，不以 latest/release 字段代替可用性判断。最新版本只是观测值，不是本次升级请求。来源链接见 §6。

| 模板项 | 模板要求 / 本仓当前 | 可用性、兼容性与决定 |
|---|---|---|
| Compose / Material3 | composeBom + material3 / BOM 2026.09.00、Material3 1.5.0-alpha29 | 两个当前值均在 Google metadata。缓存 BOM POM 的 ui/runtime 为 1.12.1、material3 为 1.4.0；本仓显式覆盖 Material3 到 1.5.0-alpha29，不能误说 BOM 就等于该 alpha。保持现有组合；模板没有另一个具体数字可直接对齐。 |
| Navigation3 runtime/ui | 1.2.0-alpha07 / 1.2.0 | runtime metadata 同时有两者；1.2.0 是同一版本线的正式版，高于 alpha07。保留正式版，不倒退；不改导航源码。 |
| navigationevent（上项的一部分） | 1.2.0-alpha04 / 无直接 alias | 1.2.0-alpha04 已发布，metadata 最新正式版是 1.2.0。当前 nav3-ui-android 1.2.0 的 POM/module 请求 navigationevent-compose 1.1.1；这是传递下限，不是最终 resolution 报告。1.2.0-alpha04 确实高于 1.1.1；宿主现用 Mochi 导航，不为复制模板新增一条无直接用途的依赖/强制约束；最终 resolved 图由 verifier 核查。 |
| lifecycle-viewmodel-navigation3 | 2.12.0-alpha01 / 2.11.0 | 两者可用，alpha01 的语义版本高于 2.11.0，不能叫降级。AAR/javap 检查见 §5：桥接公开重载一致，但 POM 把 viewmodel/compose/savedstate 精确版本切到 2.12 alpha，并通过组内约束扩散；这是整组 lifecycle 的行为升级，不是单一桥接替换。本次无该 API 需求，保留当前组，升级需独立验证重建/恢复流程。 |
| Ktor | 3.5.2 / 3.5.2 | Central metadata 存在 3.5.2；客户端、服务端所有 alias 共用同一版本键。模板列出的 android/logging/auth/websockets 不是导入全部引擎的指令：本仓用 CIO，不为模板额外引 Android 引擎或 logging。保持。 |
| DataStore preferences | 1.3.0-alpha10 / 1.2.1 | 两者可用，1.3 alpha10 高于 1.2.1，并非降级。Factory 公开签名和最低工具链未阻断候选；详见 §5。当前 Android 路径不使用候选新增的 Web/Tink/Builder API，迁移涉及持久化和并发行为回归，本次不引入无需求的运行时升级；不是因为 alpha 身份就跳过。 |
| Hilt | 2.60.1 / 2.60.1 | Central metadata 精确存在。Hilt runtime、compiler、plugin 同源；AndroidX Hilt 是不同坐标家族，不混用版本。保持。 |
| KSP（Hilt 行的一部分） | 2.2.10-2.0.2 / 2.3.12 | Central metadata 同时存在；当前 plugin POM 绑定 API/common-deps 2.3.12，模板绑定 2.2.10-2.0.2。2.3.12 release notes 要求最低 AGP 8.12.0，当前 AGP 9.3.1 满足；新 backing-field 行为由 processor 显式 opt-in。保持现有 Kotlin 2.4.20/KSP 2.3.12 与 Hilt/Room 组合，不退回旧处理器 API。最终 processor 执行由 verifier 编译确认。 |
| kotlinx-serialization | 1.11.0 / 1.11.0 | Central metadata 精确存在；各 consumer 同一 alias，serialization Gradle plugin 仍取 Kotlin 版本而非 runtime 版本。保持。 |
| kotlinx-coroutines | 1.11.0 / 1.11.0 | Central metadata 精确存在。保留同一 core 版本；不因 metadata 中出现其他 latest 值升级整栈。 |

核查时的 latest：Nav3 1.3.0-alpha05、navigationevent 1.2.0、lifecycle bridge 2.12.0-alpha06、DataStore 1.3.0、Ktor 3.6.2、Hilt 2.60.1、KSP 2.3.14、serialization 1.13.0、coroutines 1.11.0、Compose BOM 2026.11.01、Material3 1.6.0-alpha01。尤其 DataStore 已有 1.3.0 正式版，因此“alpha10 是最新”已不成立；但本次也不擅自跳到另一个未要求的版本。

## 5. 两个较新 alpha 的实证与升级门槛

### DataStore

- 官方 datastore-preferences-android POM：1.2.1 请求 Kotlin stdlib 2.0.21，alpha10 请求 2.2.20；coroutines 同为 1.9.0 下限。当前 Kotlin 2.4.20 / coroutines 1.11.0 满足这两组下限。
- 官方 datastore-preferences-core-android AAR 两版均 HTTP 200；metadata 均为 minCompileSdk 34、minAGP 8.1.1，当前 compileSdk 37 / AGP 9.3.1 满足。
- 对两版 classes.jar 的 PreferenceDataStoreFactory 执行 javap -public，退出码均 0；create/createWithPath 的现有公开重载一致。没有据此宣称“候选不兼容”。
- release notes：alpha07 引入 DataStore.Builder，CoroutineContext/Job 管理不同，并推荐迁移；alpha10 修改 AeadSerializer 的异常包装。当前 [SettingsRepository](../app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/SettingsRepository.kt#L89) 仍使用 Factory、7 代迁移和 ReplaceFileCorruptionHandler；OCR/SSH/terminal 的 preferencesDataStore 同样是已落盘持久化路径。
- 决定：保留 1.2.1，本次只改变依赖声明。若后续主动升级，需覆盖旧文件读取、7 代迁移、损坏备份/恢复、并发 updateData 与进程重启；签名相同不替代运行时验证，也不强迫此轮资源化任务同时重写 Builder。

### Lifecycle bridge

- 官方桥接 AAR 两版 HTTP 200；2.11.0 metadata 为 minCompileSdk 37 / minAGP 9.1.0，alpha01 为 36 / 8.9.1，当前环境均满足，不能用工具链不支持作为拒绝理由。
- 两版 ViewModelStoreNavEntryDecoratorKt 的三个 rememberViewModelStoreNavEntryDecorator 公开重载相同，javap 均退出 0。
- alpha01 POM 将 lifecycle-viewmodel-android、viewmodel-compose、viewmodel-savedstate 的精确依赖从 [2.11.0] 改为 [2.12.0-alpha01]，并提供同组 lifecycle 约束；不能只比较桥接 JAR 就宣称整个 lifecycle 图兼容。
- release notes 明确改变 SavedStateViewModelFactory / AbstractSavedStateViewModelFactory：owner 不实现 ViewModelStoreOwner 时改成立即失败，并增加 LifecycleRegistry 系统追踪。决定保持 2.11.0；独立升级需要同步 lifecycle 组并验证 ViewModel owner、保存/恢复、配置变更、Mochi push/pop，不以“alpha 不稳定”作唯一理由。

## 6. 可复核来源

- Google metadata：[Compose BOM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml)、[Material3](https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml)、[Navigation3](https://dl.google.com/dl/android/maven2/androidx/navigation3/navigation3-runtime/maven-metadata.xml)、[navigationevent](https://dl.google.com/dl/android/maven2/androidx/navigationevent/navigationevent/maven-metadata.xml)、[lifecycle bridge](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-navigation3/maven-metadata.xml)、[DataStore](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml)。
- Central metadata：[Ktor](https://repo.maven.apache.org/maven2/io/ktor/ktor-client-core/maven-metadata.xml)、[Hilt](https://repo.maven.apache.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml)、[KSP](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml)、[serialization](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml)、[coroutines](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml)。
- 图证据：[Nav3 UI POM](https://dl.google.com/dl/android/maven2/androidx/navigation3/navigation3-ui-android/1.2.0/navigation3-ui-android-1.2.0.pom)、[DataStore 1.2.1 POM](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences-android/1.2.1/datastore-preferences-android-1.2.1.pom)、[alpha10 POM](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences-android/1.3.0-alpha10/datastore-preferences-android-1.3.0-alpha10.pom)、[bridge 2.11.0 POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-navigation3-android/2.11.0/lifecycle-viewmodel-navigation3-android-2.11.0.pom)、[alpha01 POM](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-navigation3-android/2.12.0-alpha01/lifecycle-viewmodel-navigation3-android-2.12.0-alpha01.pom)。对应目录下同名 .aar 是本次 javap/AAR metadata 的输入。
- 行为说明：[DataStore 1.3 release notes](https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0-alpha10)、[Lifecycle alpha01 release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.12.0-alpha01)、[KSP 2.3.12 release notes](https://github.com/google/ksp/releases/tag/2.3.12)、[Foojay marker](https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/1.0.0/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-1.0.0.pom)。

## 7. 静态验证与集成交接

- 静态展开 21 个 include 加 build-logic，共 22 份模块脚本、261 条 dependency configuration 记录；重构前后 (configuration, coordinate/version) 多重集相同。Exif 按原 force 后的有效版本比较；Mochi 按开始时已有 WIP .3 比较；不把既有 .2→.3 计作本任务升级。
- Python tomllib 解析成功；全部 version.ref、bundle 成员存在；所有 library/plugin 的版本引用集中至 versions；bundle 至少两个不同 artifact。
- 验证命令：`python3 /tmp/khatkit-t3-verify.py` 退出码 0；`git diff --check` 全共享工作树检查退出码 0（不是只检查本任务文件）。未发现模块库/force/settings 插件的硬编码版本残留。
- Foojay 的 TOML 唯一键匹配值为 1.0.0；最终 Kotlin settings bootstrap、plugin alias 解析及 Gradle 配置缓存仍须由 verifier 在允许时执行验证。
- 详细临时证据：静态脚本 /tmp/khatkit-t3-verify.py、结果 /tmp/khatkit-t3-verify-result.json、AAR/API 报告 /tmp/khatkit-t3-api-report.txt；官方 metadata 与 POM 快照同为 /tmp/khatkit-t3-*。这些是辅助证据，不是 Maven 发布产物，也未写入缓存。
- 本任务不执行 Gradle；继承 t1 编译成功只说明旧基线，不冒充本次构建结果。交接 verifier 运行配置/编译、JVM 测试，并核查 app 的 exifinterface/guava、navigationevent 最终解析以及 OCR 插件隔离；禁止本地 assemble 或设备操作。
