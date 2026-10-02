import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "heizige.kk.khatkit.speech"
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // 本地语音识别：仅打包 sherpa-onnx Kotlin 类（~235KB，已把 loadLibrary 换成 NativeLibLoader），
    // 原生库 .so 由用户按需下载（见 SherpaModelStore.downloadEngine）。
    //
    // 这里依赖 jar 而不是原始 aar：AGP 禁止 library 模块直接依赖本地 .aar（产出的 AAR 不会
    // 打进 classes/resources，全量 assembleDebug 会直接失败）。上游 aar 里只有 classes.jar，
    // 所以抽成 jar 是 1:1 等价：
    //   unzip -o libs/sherpa-onnx-arm64-nolib-1.13.8.aar classes.jar && mv classes.jar libs/*.jar
    implementation(files("libs/sherpa-onnx-arm64-nolib-1.13.8.jar"))
    implementation(libs.commons.compress)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
