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
include(":videogen")
include(":oauth")
