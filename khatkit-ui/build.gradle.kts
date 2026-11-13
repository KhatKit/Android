import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library.compose")
    // 导航栏样式枚举（NavBarLabelStyle）要进 Settings 的 @Serializable JSON
    alias(libs.plugins.kotlin.serialization)
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

    // mochi-compose：P4 起 UI 组件由 Mochi 提供（api：app 侧页面直接用同一套）
    api(libs.mochi.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
