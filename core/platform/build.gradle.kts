plugins {
    id("culinary.android.library")
    id("culinary.android.hilt")
}

// 平台能力层：SAF、权限、CameraX、WorkManager；只实现 domain 端口（A03A §4、§8.1）。
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
}
