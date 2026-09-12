plugins {
    id("rikkahub.android.library.compose")
}

android {
    namespace = "heizige.kk.khatkit.material3"
    sourceSets {
        named("main") {
            kotlin.srcDir("material-color-utilities/kotlin")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
