import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** 约定插件中访问版本目录的唯一入口；禁止在模块脚本里硬编码依赖版本。 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * 15 模块共用的构建常量。
 *
 * SDK 基线来自 D-002（min 26 / compile & target 36），JVM 目标来自 A01。
 */
internal object CulinaryConfig {
    const val COMPILE_SDK: Int = 36
    const val MIN_SDK: Int = 26
    const val TARGET_SDK: Int = 36
    const val JVM_TARGET: Int = 17
}

/**
 * 从 Gradle 模块路径推导 Android namespace。
 *
 * `:core:domain` -> `com.culinary.core.domain`；`:feature:ingredients` ->
 * `com.culinary.feature.ingredients`。模块数固定为 D-004 的 15 个，路径即边界。
 */
internal fun Project.culinaryNamespace(): String =
    "com.culinary." + path.removePrefix(":").replace(':', '.').replace('-', '.')
