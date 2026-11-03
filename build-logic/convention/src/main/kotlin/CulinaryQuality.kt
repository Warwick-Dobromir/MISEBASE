import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Project

/**
 * 为模块统一挂载格式与静态分析：ktlintCheck 与 detekt。
 *
 * 所有 15 个模块都必须经过同一套检查，避免“某个模块忘了加”的质量漏洞（E01 §12）。
 */
internal fun Project.configureQuality() {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    tasks.withType(Detekt::class.java).configureEach {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }
}
