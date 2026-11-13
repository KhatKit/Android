plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "heizige.kk.khatkit.plugins.terminal"
    defaultConfig {
        minSdk = 26
        externalNativeBuild {
            cmake { cppFlags += "" }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.termux.terminal.view)
}

tasks.register("printReleaseRuntimeClasspath") {
    val pluginClasspath = configurations.getByName("releaseRuntimeClasspath")
        .incoming.artifactView { lenient(true) }.files
    inputs.files(pluginClasspath).withPropertyName("pluginClasspath")
    doLast { inputs.files.files.map { it.absolutePath }.sorted().forEach(::println) }
}
