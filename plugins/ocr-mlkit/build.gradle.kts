plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "heizige.kk.khatkit.ocr.mlkit"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
}

// OCR1：ML Kit OCR 只在这个插件模块里出现，app 基础包不再依赖它（省约 10.6MB）。
dependencies {
    api(project(":common"))
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
