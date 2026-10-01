pluginManagement {
    includeBuild("build-logic")

    repositories {
        // 国内镜像优先（直连 Maven Central 会 403）
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
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
        // 国内镜像优先（直连 Maven Central 会 403）
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
        // Kedge/Khromia 的发布仓库（本地无同级目录时从 GitHub Packages 解析）
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/heizigelovecode/Khromia")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_USER")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// 本地 UI 组件库以 composite build 接入（存在同级目录时用源码构建）
//
// 注意：`heizige.kk:kedge` 与 `heizige.kk:khromia` 目前**没有发布到任何制品仓库**
// （GitHub Packages 上不存在这两个包，mavenLocal 里也没有），所以下面的
// includeBuild 不是"可选优化"而是**硬性前提**：同级目录没有 Kedge/Khromia 时，
// 构建会退化到 libs.versions.toml 里的坐标然后拉取失败，报错很难定位。
// 克隆本仓库时请把 Kedge、Khromia 放在同级目录：
//
//     <workspace>/
//       KhatKit/     <- 本仓库
//       Kedge/
//       Khromia/
//
// Miuix 迁移引用的 KedgeListItem / KedgeFilterChip / KedgeDropdownMenuSlots /
// KedgeFloatingActionButton / KedgeBlur / MiuixMaterialBridge 等组件只存在于源码，
// 没有任何已发布的制品包含它们。
val localKedge = file("../Kedge")
val localKhromia = file("../Khromia")
if (localKedge.exists()) {
    includeBuild(localKedge) {
        dependencySubstitution {
            substitute(module("heizige.kk:kedge")).using(project(":kedge"))
        }
    }
}
if (localKhromia.exists()) {
    includeBuild(localKhromia) {
        dependencySubstitution {
            substitute(module("heizige.kk:khromia")).using(project(":khromia"))
        }
    }
}

rootProject.name = "KhatKit"
include(":app")
include(":khatkit")
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
// 原生依赖包模块：不随应用编译，只通过 buildDependencyDex 产出 Hub 分发的 dex 依赖 jar
include(":image-toolbox-dependency")
