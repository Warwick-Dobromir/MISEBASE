pluginManagement {
    // 约定插件以 included build 形式提供（R01 §4.1）。
    includeBuild("build-logic")
    repositories {
        // 镜像优先、官方回退（R02 §6.3）。镜像只解决可达性，不降低版本校验。
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "culinary"

// MB-D-003 已接受的 13 模块（MISEBASE 目标），顺序与拓扑序一致。
// 删除 :feature:planning 与 :feature:procurement；采购清单归 :feature:inventory；
// :feature:products 缩减为商品/包装/条码/食材映射（不含扫码 UI）。
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
include(":feature:settings")
