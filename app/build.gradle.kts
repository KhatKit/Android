import com.android.build.api.dsl.Packaging
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

val ciVersionCode = providers.gradleProperty("VERSION_CODE").orNull
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: 191
val ciVersionName = providers.gradleProperty("VERSION_NAME").orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "2.5.6"

android {
    namespace = "heizige.kk.khatkit.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "heizige.kk.khatkit"
        minSdk = 26
        targetSdk = 37
        versionCode = ciVersionCode
        versionName = ciVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    splits {
        abi {
            // AppBundle tasks usually contain "bundle" in their name
            //noinspection WrongGradleMethod
            val isBuildingBundle = gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
            isEnable = !isBuildingBundle
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")

            if (localPropertiesFile.exists()) {
                localProperties.load(FileInputStream(localPropertiesFile))

                val storeFilePath = localProperties.getProperty("storeFile")
                val storePasswordValue = localProperties.getProperty("storePassword")
                val keyAliasValue = localProperties.getProperty("keyAlias")
                val keyPasswordValue = localProperties.getProperty("keyPassword")

                if (storeFilePath != null && storePasswordValue != null &&
                    keyAliasValue != null && keyPasswordValue != null
                ) {
                    storeFile = file(storeFilePath)
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
            buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
    androidResources {
        generateLocaleConfig = true
        // SZ-D：只保留 app 自己翻译过的语言（依赖库带的其它语言资源不打包，resources.arsc 变小）
        localeFilters += listOf("en", "ar", "de", "ja", "ko", "ko-rKR", "ru", "zh", "zh-rTW")
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        compilerOptions.optIn.add("androidx.navigation3.runtime.ExperimentalNavigation3Api")
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        project.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

tasks.register("buildAll") {
    dependsOn("assembleRelease", "bundleRelease")
    description = "Build both APK and AAB"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// SZ-D：kotlin-reflect 只由 ktor-server-core 传递引入；它在 classpath 上时 stdlib 的 Reflection
// 会 Class.forName 加载 ReflectionFactoryImpl，R8 因此保留整个实现（约 0.7MB dex）。
// 只作用应用运行时；*UnitTestRuntimeClasspath 也匹配 "RuntimeClasspath"，单测仍需 kotlin-reflect。
configurations.matching { it.name.endsWith("RuntimeClasspath") && !it.name.contains("Test") }.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
}

// Local JVM tests need the desktop native library instead of the Android AAR.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.github.dokar3:quickjs-kt-android"))
            .using(module("io.github.dokar3:quickjs-kt-jvm:${libs.versions.quickjs.get()}"))
    }
}

// 统一 exifinterface / guava 的版本，保证 --offline 构建能取到 artifact。
//
// 这两个是既有依赖树里就存在的坑（不是本次扫码依赖引入的：把本次改动 stash 掉后
// 同样报这两条），但它会让 :app:debugCompileClasspath 在 --offline 下直接失败，
// 因此必须在此处钉死。
//
// - exifinterface：app 显式声明了 1.4.1（ImageHelper 读 EXIF 方向），
//   而 debugRuntimeClasspath 实际解析到的是 1.4.2。编译期与运行期选到不同版本，
//   离线时编译期找不到 1.4.1 的 aar 就整包失败。这里统一顶到 1.4.2（补丁版本，
//   API 兼容），顺带消除这个长期存在的编译/运行不一致。
// - guava：camera-camera2-pipe 会引入 32.0.1-android，本地缓存只有 32.0.1-jre，
//   没有 -android 的 jar。运行期本来就经 ktor 解析到 33.6.0-jre，统一到它即可。
configurations.configureEach {
    resolutionStrategy {
        force("androidx.exifinterface:exifinterface:1.4.2")
        force("com.google.guava:guava:33.6.0-jre")
    }
}

dependencies {
    implementation(libs.quickjs)
    implementation(libs.androidx.core.ktx)
    // AppCompatResources（RabbitLoading 用）需要 appcompat
    implementation(libs.androidx.appcompat)
    // ImageHelper 读 EXIF 方向，靠显式声明避免依赖树变化时断掉
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.termux.terminal.view)
    // SSH1：远程 SSH 插件不再编译进基础包，改为 Hub 下载 + DexClassLoader（kind=dependency）。
    implementation(libs.guava.listenablefuture)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)


    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.material3.adaptive.navigation3)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Image metadata extractor
    // https://github.com/drewnoakes/metadata-extractor
    implementation(libs.metadata.extractor)

    // Haze (background blur and glass)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.material3)
    implementation(libs.haze.glass)
    implementation(libs.haze.glass.material3)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    // jetbrains markdown parser
    implementation(libs.jetbrains.markdown)
    // richeditor 会传递引入 org.jetbrains:markdown(-jvm)，与上面 rikkahub 的 markdown fork
    // 提供同一批 org.intellij.markdown.* 类，checkDebugDuplicateClasses 直接报重复类。
    // 两者是同一上游代码的 fork，统一保留 rikkahub 那份。
    implementation(libs.richeditor.compose) {
        exclude(group = "org.jetbrains", module = "markdown")
        exclude(group = "org.jetbrains", module = "markdown-jvm")
    }

    // ktor client（networking 全量走 Ktor，无 OkHttp）
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // pebble (template engine)
    implementation(libs.pebble)

    // java-diff-utils (unified diff)
    implementation(libs.diffutils)

    // coil
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.ktor3)
    implementation(libs.coil.svg)
    implementation(libs.coil.cache.control)

    // serialization
    implementation(libs.kotlinx.serialization.json)

    // YAML front matter
    implementation(libs.snakeyaml)

    // zxing（二维码生成 + 图片解码 + 实时扫码的帧解码，纯 Java 约 0.5MB）
    implementation(libs.zxing.core)

    // 实时扫码取景（CameraX 1.6.2 + zxing 解码）
    // 选型理由：不自己手写 camera2。
    // - camera-view 提供 PreviewView 承载取景画面；
    // - camera-core/camera-camera2/camera-lifecycle 提供 Preview + ImageAnalysis 用例与生命周期绑定；
    // - 解码不用 ML Kit 的 bundled barcode-scanning（barhopper 模型 ~4.7MB），改成
    //   core/ui/components/ui/QRCodeDecoder（zxing MultiFormatReader，只认 QR_CODE + TRY_HARDER），
    //   与上游 d8ba9cf67 同方案；顺带能纯 JVM 单测带行填充的相机帧。
    // camera-camera2-pipe / camera-video 由 camera-view 传递引入，无需显式声明。
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    baselineProfile(project(":app:baselineprofile"))
    ksp(libs.androidx.room.compiler)

    // Paging3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Apache Commons Text
    implementation(libs.commons.text)

    // Toast (Sonner)
    implementation(libs.sonner)

    // Reorderable (https://github.com/Calvin-LL/Reorderable/)
    implementation(libs.reorderable)

    // image viewer
    implementation(libs.image.viewer)

    // JLatexMath
    // https://github.com/rikkahub/jlatexmath-android
    implementation(libs.jlatexmath)
    implementation(libs.jlatexmath.font.greek)
    implementation(libs.jlatexmath.font.cyrillic)

    // mcp
    implementation(libs.modelcontextprotocol.kotlin.sdk)

    // jmDNS (mDNS/Bonjour for .local hostname)
    implementation(libs.jmdns)

    // SLF4J Android binding — routes Ktor/SLF4J logs to logcat
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)

    // sqlite-android (requery SQLite for Android)
    implementation(libs.sqlite.android)
    // sqlite-vector provides the vector extension used by memory retrieval.
    implementation(libs.sqlite.vector)

    // modules
    implementation(project(":ai"))
    implementation(project(":web"))
    implementation(project(":document"))
    implementation(project(":highlight"))
    implementation(project(":search"))
    implementation(project(":speech"))
    implementation(project(":common"))
    implementation(project(":material3"))
    implementation(project(":workspace"))
    implementation(project(":oauth"))
    implementation(project(":mediapicker"))
    implementation(project(":khatkit"))
    implementation(project(":yunx-parser"))
    implementation(libs.okhttp)
    implementation(project(":khatkit-ui"))

    // mochi-compose 0.2.0（P1 spike：UI 外壳/组件的目标库）
    implementation(libs.mochi.compose)
    implementation(libs.mochi.ios)
    implementation(libs.mochi.icons)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // Leak Canary
    // debugImplementation(libs.leakcanary.android)

    // tests
    testImplementation(libs.junit)
    // SZ-D：kotlin-reflect 只在运行期排除（dex 不打包）；单测需要它（sealedSubclasses / 序列化测试）。
    testImplementation(kotlin("reflect"))
    androidTestImplementation(libs.bundles.androidTest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
