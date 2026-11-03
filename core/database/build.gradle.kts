plugins {
    id("culinary.android.library")
    id("culinary.android.room")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // 迁移测试从导出的 schema JSON 读取 v1，必须与 schemas/ 目录保持一致。
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

// 持久化层：Room 实体/DAO/迁移；不依赖 domain/data（A03A §4）。
dependencies {
    api(project(":core:common"))

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
