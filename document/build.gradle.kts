plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "heizige.kk.khatkit.document"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(libs.bundles.android.view.support)
    testImplementation(libs.junit)
    androidTestImplementation(libs.bundles.androidTest)
}
