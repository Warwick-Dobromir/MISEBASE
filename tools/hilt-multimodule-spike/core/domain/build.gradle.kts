plugins {
    id("org.jetbrains.kotlin.jvm")
}

// 纯 JVM 模块：不应用 Hilt/KSP/Android，验证 domain 纯度可行。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
