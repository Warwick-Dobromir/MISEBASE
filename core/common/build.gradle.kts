plugins {
    id("culinary.jvm.library")
}

// 基础设施层：纯 Kotlin/JVM，不依赖任何业务模块（A03A §4）。
dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
