import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins { id("rikkahub.android.library") }

android { namespace = "heizige.kk.khatkit.dependencies.musicKey" }

dependencies { compileOnly(project(":khatkit")) }

val dependencyName = "musicKey"
val dependencyVersion = "1.0.0"
val dependencyEntry = "heizige.kk.khatkit.dependencies.musicKey.MusicKeyDependency"
val outputDir = layout.buildDirectory.dir("dependency")
val aarFile = layout.buildDirectory.file("outputs/aar/${project.name}-debug.aar")
val workDir = layout.buildDirectory.dir("tmp/dependencyDex")
val sdkDirectory: File = run {
    val local = rootProject.file("local.properties").takeIf { it.isFile }?.let {
        Properties().apply { it.reader(Charsets.UTF_8).use(::load) }.getProperty("sdk.dir")
    }
    File(System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: local
        ?: error("未找到 Android SDK"))
}
val d8Executable = sdkDirectory.resolve("build-tools").listFiles().orEmpty()
    .map { File(it, "d8") }.filter { it.isFile }.maxByOrNull { it.parentFile.name }
    ?: error("未找到 D8")
val androidJar = sdkDirectory.resolve("platforms").listFiles().orEmpty()
    .map { File(it, "android.jar") }.filter { it.isFile }.maxByOrNull { it.parentFile.name }

tasks.register("buildDependencyDex") {
    group = "dependency"
    dependsOn("assembleDebug")
    inputs.file(aarFile)
    outputs.dir(outputDir)
    doLast {
        val outDir = outputDir.get().asFile.apply { mkdirs() }
        val work = workDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val classesJar = File(work, "classes.jar")
        ZipFile(aarFile.get().asFile).use { zip ->
            zip.getInputStream(zip.getEntry("classes.jar")).use { input -> classesJar.outputStream().use(input::copyTo) }
        }
        val dexDir = File(work, "dex").apply { mkdirs() }
        val command = mutableListOf(d8Executable.absolutePath, "--min-api", "26", "--output", dexDir.absolutePath)
        androidJar?.let { command += listOf("--lib", it.absolutePath) }
        command += classesJar.absolutePath
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "D8 转换失败：$log" }
        val target = File(outDir, "music-key-$dependencyVersion.jar")
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            dexDir.listFiles().orEmpty().filter { it.extension == "dex" }.sortedBy { it.name }.forEach { dex ->
                zip.putNextEntry(ZipEntry(dex.name).apply { time = 0L }); dex.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("META-INF/khatkit-dependency.properties").apply { time = 0L })
            zip.write("name=$dependencyName\nversion=$dependencyVersion\nentry=$dependencyEntry\n".toByteArray())
            zip.closeEntry()
            project.file("src/main/resources/kugou_key.bin").takeIf { it.isFile }?.let { resource ->
                zip.putNextEntry(ZipEntry("kugou_key.bin").apply { time = 0L })
                resource.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n <= 0) break; digest.update(buffer, 0, n) } }
        logger.lifecycle("依赖包产物：${target.absolutePath} sha256=" + digest.digest().joinToString("") { "%02x".format(it) })
    }
}
