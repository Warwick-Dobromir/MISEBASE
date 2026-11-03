plugins {
    id("culinary.jvm.library")
}

// 领域层：模型 + 端口 + 用例 + 规则；禁止依赖 Android/Room/Compose/Hilt（D-004）。
dependencies {
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
}
