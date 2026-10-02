import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "heizige.kk.khatkit"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }



    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

tasks.withType<Test>().configureEach {
    System.getenv("KHATKIT_NATIVE_LIB")?.let { systemProperty("khatkit.native.path", it) }
    // BridgeApiDocTest 校验 docs/script-api-reference.md，把 docs 目录登记为输入：
    // 文档或接口代码变了就重跑，防止接口清单悄悄漂移。
    inputs.dir(layout.projectDirectory.dir("../docs"))
        .withPropertyName("docsDir")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    // 卡片 manifest / 校验 / 词表：纯 JVM，独立成模块供卡片仓库 CI 复用
    api(project(":card-validator"))

    implementation(project(":common"))

    // Compose 已迁至 :khatkit-ui（多风格 UI 组件库）
    implementation(libs.androidx.core.ktx)

    // Bridge implementations: Shizuku (L1)
    //
    // PDF 能力已剥离到 imageToolbox 依赖包（见 docs/dependency-system.md）：卡片声明
    // requires.dependencies 后由宿主下载 dex 包加载，APK 不再内置 pdfbox。
    // 代价是 PDF 编辑改为 PdfRenderer 光栅化重建（文字不可选中），且无法合并加密 PDF。
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // OCR: ML Kit 中文文字识别（模型随包，无需 GMS）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // store.sql：卡片自己的 SQLite 库（androidx.sqlite，Room 底层同一引擎）。
    // 刻意不引 Room：卡片要运行期 CREATE TABLE，注解处理器帮不上忙（决策见 docs/bridge-expansion-spec.md T2.1）。
    implementation(libs.androidx.sqlite)

    // Networking: Ktor client only, deliberately not OkHttp
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
