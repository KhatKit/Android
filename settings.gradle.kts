pluginManagement {
    includeBuild("build-logic")

    repositories {
        // 国内镜像优先（直连 Maven Central 会 403）。但 GitHub Actions runner 访问 aliyun
        // 会返回 502，而 Gradle 解析插件时遇到 5xx 不会回退到后面的仓库，所以 CI 里跳过
        // 镜像、直连官方源。
        if (System.getenv("CI") != "true") {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.itextsupport.com/android")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // androidx 的 alpha/beta 构件阿里云镜像经常只同步 .pom 不带 .aar，
        // Gradle 命中残缺响应后不会再回退到下一个仓库，直接走官方源更稳。
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\.android\\..*")
            }
        }
        // CI 里 aliyun 会 502，跳过镜像直连官方源（本地开发仍走镜像，直连 Maven Central 会 403）
        if (System.getenv("CI") != "true") {
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        // 本地 m2 可能有自发布依赖（如 mochi 0.3.0-khatkit），放最前面：JitPack 等远端仓库
        // 对未知坐标会返回 401，Gradle 会直接失败而不会继续走到 mavenLocal
        mavenLocal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "KhatKit"
include(":app")
include(":khatkit")
// OCR1：ML Kit OCR 拆成独立插件模块，app 不依赖它（不进基础包）
include(":plugins:ocr-mlkit")
include(":khatkit-ui")
include(":card-validator")
include(":highlight")
include(":ai")
include(":search")
include(":speech")
include(":common")
include(":document")
include(":web")
include(":material3")
include(":workspace")
include(":app:baselineprofile")
include(":oauth")
// 自研图片选择器（对齐 ImageToolbox 的媒体网格），宿主 APK 的 UI 模块
include(":mediapicker")
// 原生依赖包模块：不随应用编译，只通过 buildDependencyDex 产出 Hub 分发的 dex 依赖 jar
include(":image-toolbox-dependency")
