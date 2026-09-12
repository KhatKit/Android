import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library.compose")
}

android {
    namespace = "heizige.kk.khatkit.uikit"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
    compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
}

dependencies {
    // 卡片运行时（UiRequest / bridge 实现）
    api(project(":khatkit"))

    // 多风格 UI 桥接（MD3Exp / Miuix）与 MD3 组件库
    api(libs.kedge)
    api(libs.khromia)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
