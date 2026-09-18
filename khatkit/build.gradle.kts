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
}

dependencies {
    // 卡片 manifest / 校验 / 词表：纯 JVM，独立成模块供卡片仓库 CI 复用
    api(project(":card-validator"))

    implementation(project(":common"))

    // Compose 已迁至 :khatkit-ui（多风格 UI 组件库）
    implementation(libs.androidx.core.ktx)

    // Bridge implementations: PDF merge + Shizuku (L1)
    implementation(libs.pdfbox.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // OCR: ML Kit 中文文字识别（模型随包，无需 GMS）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

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
