plugins { id("rikkahub.android.library") }

android {
    // Preserve the upstream namespace so its resource references stay auditable.
    namespace = "com.yunx.app"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.room.runtime)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
