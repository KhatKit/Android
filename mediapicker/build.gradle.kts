plugins {
    id("rikkahub.android.library.compose")
}

android {
    namespace = "heizige.kk.khatkit.mediapicker"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
    compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
    compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    // 全屏预览的缩放/翻页（app 的 ImagePreviewDialog 用的是同一个库）
    implementation(libs.image.viewer)
    implementation(libs.kotlinx.coroutines.core)
    // 图标：khatkit-ui 已把 material-icons-extended 带进最终 APK，这里不额外增大包体
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kedge)

    testImplementation(libs.junit)
}
