plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "heizige.kk.khatkit.web"

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ktor server
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.cors)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.sse)
    api(libs.ktor.server.cio)

    testImplementation(libs.junit)
    androidTestImplementation(libs.bundles.androidTest)
}
