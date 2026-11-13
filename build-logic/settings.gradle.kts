pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // ktlint-gradle 只发布在 Gradle Plugin Portal；官方仓库优先，镜像作为回退。
        maven("https://plugins.gradle.org/m2")
        google()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
