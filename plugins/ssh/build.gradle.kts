plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "heizige.kk.khatkit.plugins.ssh"

    defaultConfig {
        minSdk = 26
    }
}

// SSH1：远程 SSH 插件。common 提供 SPI 接口，sshj/BouncyCastle 只在这个可下载插件包里，
// 基础 APK 不再打包它们（app 侧改为 Hub 下载 + DexClassLoader）。
dependencies {
    implementation(project(":common"))
    implementation(libs.sshj)
    testImplementation(libs.junit)
}

// buildPlugin.sh 用：打印 release 运行时 classpath，保证打进 dex 的 sshj/BouncyCastle
// 版本与依赖解析结果一致（缓存里可能并存多个版本，不能靠文件名猜）。
tasks.register("printReleaseRuntimeClasspath") {
    val pluginClasspath = configurations.getByName("releaseRuntimeClasspath")
        .incoming
        .artifactView { lenient(true) }
        .files
    inputs.files(pluginClasspath).withPropertyName("pluginClasspath")
    doLast {
        inputs.files.files.map { it.absolutePath }.sorted().forEach { println(it) }
    }
}
