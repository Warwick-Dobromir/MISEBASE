plugins {
    id("culinary.android.library")
    id("culinary.android.hilt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 数据访问层：Repository 实现、Mapper、事务协调、文件与备份（A03A §4）。
dependencies {
    api(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.ktx)
    implementation(libs.timber)

    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
