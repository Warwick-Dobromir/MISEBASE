plugins {
    id("culinary.android.library")
    id("culinary.android.compose")
    id("culinary.android.hilt")
}

// 表现层：只含 UI 与 ViewModel；业务规则在 core:domain，持久化在 core:data（A03A §4.9）。
// Stage 5 起额外提供扫码识别结果的"创建/映射食材"接口实现（MB-D-004）。
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    testImplementation(libs.kotlinx.coroutines.test)
}
