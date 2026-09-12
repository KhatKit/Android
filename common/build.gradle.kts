import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "heizige.kk.khatkit.common"
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    }
}

// 真实 API 的 live 测试参数（缺省时测试自动跳过）
tasks.withType<Test>().configureEach {
    listOf(
        "KHATKIT_LIVE_BASE_URL", "KHATKIT_LIVE_API_KEY", "KHATKIT_LIVE_MODEL",
        "KHATKIT_LIVE_GEMINI_BASE_URL", "KHATKIT_LIVE_GEMINI_API_KEY", "KHATKIT_LIVE_GEMINI_MODEL",
    ).forEach { key ->
        System.getenv(key)?.let { systemProperty(key, it) }
    }
}

dependencies {
    // ktor（Ktor 兼容层底座，已完全替代 OkHttp）
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)

    // kotlinx
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    // apache commons
    api(libs.commons.text)

    // floating
    // https://github.com/Petterpx/FloatingX
    api(libs.floatingx)
    api(libs.floatingx.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // quickjs
    api(libs.quickjs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
