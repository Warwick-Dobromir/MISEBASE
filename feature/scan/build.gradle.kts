plugins {
    id("culinary.android.library")
    id("culinary.android.compose")
    id("culinary.android.hilt")
}

android {
    defaultConfig {
        // 真机解码验证（MB-D-011）跑在 connectedDebugAndroidTest。
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// 扫码纵向切片（MB-D-004，Stage 5）：
// - 扫码 UI、CameraX 生命周期、ML Kit（bundled）识别归本模块；
// - 只依赖 core 公共层，不依赖 feature:products / feature:inventory 的实现；
// - 识别结果经 core:domain 端口交给业务模块，本模块不写食材/库存表。
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // 技术栈锁定：CameraX 1.4.2 / ML Kit 17.3.0 Bundled（D-013，禁止升级）。
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.mlkit.barcode.scanning)
}
