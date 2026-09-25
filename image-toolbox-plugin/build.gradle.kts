import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "heizige.kk.khatkit.plugins.imageToolbox"
}

dependencies {
    // 仅编译期依赖宿主 bridge 接口；插件产物 dex 不包含宿主类，
    // 运行时由应用的 classloader 通过 DexClassLoader(parent) 提供。
    compileOnly(project(":khatkit"))
}

// ══════════════ 原生插件打包 ══════════════
//
// 产物：build/plugin/image-toolbox-<version>.jar
//   ├── classes.dex                      （D8 由模块 AAR 的 classes.jar 转换）
//   └── META-INF/khatkit-plugin.properties（name/version/entry）
//
// 构建命令：./gradlew :image-toolbox-plugin:buildPluginDex
// 产物上传：POST /api/admin/plugins（KodeHeadServer），随后卡片 manifest 的
// requires.plugins[].sha256 由服务端在种子/打包时计算，见 docs/plugin-system.md。

val pluginName = "imageToolbox"
val pluginVersion = "1.0.0"
val pluginEntry = "heizige.kk.khatkit.plugins.imageToolbox.ImageToolboxPlugin"
val pluginArtifactName = "image-toolbox-$pluginVersion.jar"

val pluginOutputDir = layout.buildDirectory.dir("plugin")
val aarFile = layout.buildDirectory.file("outputs/aar/${project.name}-debug.aar")
val dexWorkDir = layout.buildDirectory.dir("tmp/pluginDex")

val sdkDirectory: File = run {
    val fromLocalProperties = rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.let { file ->
            Properties().apply { file.reader(Charsets.UTF_8).use(::load) }.getProperty("sdk.dir")
        }
    val path = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: fromLocalProperties
        ?: error("未找到 Android SDK：请设置 ANDROID_HOME 或配置 local.properties 的 sdk.dir")
    File(path).also {
        require(it.isDirectory) { "Android SDK 目录不存在：${it.absolutePath}" }
    }
}

val d8Executable: File = sdkDirectory.resolve("build-tools")
    .listFiles().orEmpty()
    .map { File(it, "d8") }
    .filter { it.isFile }
    .maxByOrNull { it.parentFile.name }
    ?: error("未找到 D8：${sdkDirectory.absolutePath}/build-tools/*/d8")

val androidJar: File? = sdkDirectory.resolve("platforms")
    .listFiles().orEmpty()
    .map { File(it, "android.jar") }
    .filter { it.isFile }
    .maxByOrNull { it.parentFile.name }

val buildPluginDex = tasks.register("buildPluginDex") {
    group = "plugin"
    description = "构建原生插件 jar（classes.dex + META-INF/khatkit-plugin.properties）"
    dependsOn("assembleDebug")
    inputs.file(aarFile)
    inputs.property("pluginVersion", pluginVersion)
    inputs.property("pluginEntry", pluginEntry)
    outputs.dir(pluginOutputDir)

    // 配置缓存：动作内只捕获局部值，避免引用 Gradle 脚本对象
    val moduleName = project.name
    val outputDirProvider = pluginOutputDir
    val aarProvider = aarFile
    val dexWorkProvider = dexWorkDir
    val artifactName = pluginArtifactName
    val pName = pluginName
    val pVersion = pluginVersion
    val pEntry = pluginEntry
    val d8 = d8Executable
    val androidJarFile = androidJar
    doLast {
        val outputDir = outputDirProvider.get().asFile.apply { mkdirs() }
        val aar = aarProvider.get().asFile
        require(aar.isFile) { "AAR 不存在：${aar.absolutePath}，先执行 :$moduleName:assembleDebug" }

        val work = dexWorkProvider.get().asFile.apply { deleteRecursively(); mkdirs() }
        val classesJar = File(work, "classes.jar")
        ZipFile(aar).use { zip ->
            val entry = zip.getEntry("classes.jar")
                ?: error("AAR 缺少 classes.jar：${aar.absolutePath}")
            zip.getInputStream(entry).use { input ->
                classesJar.outputStream().use { input.copyTo(it) }
            }
        }

        val dexDir = File(work, "dex").apply { mkdirs() }
        val command = mutableListOf(
            d8.absolutePath,
            "--min-api", "26",
            "--output", dexDir.absolutePath,
        )
        androidJarFile?.let { command += listOf("--lib", it.absolutePath) }
        command += classesJar.absolutePath
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        require(exit == 0) { "D8 转换失败（exit=$exit）：\n$log" }

        val dexFiles = dexDir.listFiles().orEmpty()
            .filter { it.extension == "dex" }
            .sortedBy { it.name }
        require(dexFiles.isNotEmpty()) { "D8 未产出 classes.dex：\n$log" }

        val target = File(outputDir, artifactName)
        val properties = buildString {
            appendLine("# KhatKit 原生插件产物元数据（见 docs/plugin-system.md）")
            appendLine("name=$pName")
            appendLine("version=$pVersion")
            appendLine("entry=$pEntry")
        }
        ZipOutputStream(target.outputStream().buffered()).use { zipOut ->
            dexFiles.forEach { dex ->
                zipOut.putNextEntry(ZipEntry(dex.name).apply { time = 0L })
                dex.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
            zipOut.putNextEntry(ZipEntry("META-INF/khatkit-plugin.properties").apply { time = 0L })
            zipOut.write(properties.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        logger.lifecycle(
            "插件产物：${target.absolutePath}（${target.length()} 字节，sha256=$sha256）"
        )
    }
}
