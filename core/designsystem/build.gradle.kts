plugins {
    id("culinary.android.library")
    id("culinary.android.compose")
}

// 表现基础层：只放主题与通用组件，不依赖 domain/data/database/feature（A03A §4）。
dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
}
