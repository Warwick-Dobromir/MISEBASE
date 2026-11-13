pluginManagement {
    // 约定插件以 included build 形式提供（R01 §4.1）。
    includeBuild("build-logic")
    repositories {
        // 官方仓库优先，镜像作为回退；避免镜像缺失插件标记时阻断后续仓库解析。
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "culinary"

// MB-D-003 的 13 模块是 Stage 1–4 目标；MB-D-004（2026-11-05 ACCEPTED）在 Stage 5 起
// 增加第 14 个模块 :feature:scan，模块集为 14。13 不是历史错误，14 只从 Stage 5 起成立。
// 删除 :feature:planning 与 :feature:procurement；采购清单归 :feature:inventory；
// :feature:products 缩减为商品/包装/条码/食材映射（不含扫码 UI，扫码 UI 归 :feature:scan）。
include(":app")

include(":core:common")
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:platform")

include(":feature:ingredients")
include(":feature:sauces")
include(":feature:recipes")
include(":feature:products")
include(":feature:inventory")
include(":feature:scan")
include(":feature:settings")
