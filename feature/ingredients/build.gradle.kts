plugins {
    id("culinary.android.library")
    id("culinary.android.compose")
    id("culinary.android.hilt")
}

// 表现层：只含 UI 与 ViewModel；业务规则在 core:domain，持久化在 core:data（A03A §4.9）。
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
