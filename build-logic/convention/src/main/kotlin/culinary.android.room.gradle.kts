plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    val catalog = libs
    add("implementation", catalog.findLibrary("androidx-room-runtime").get())
    add("implementation", catalog.findLibrary("androidx-room-ktx").get())
    add("ksp", catalog.findLibrary("androidx-room-compiler").get())
    add("testImplementation", catalog.findLibrary("androidx-room-testing").get())
    add("testImplementation", catalog.findLibrary("robolectric").get())
    add("testImplementation", catalog.findLibrary("kotlinx-coroutines-test").get())
}

ksp {
    // 导出 schema 供迁移测试使用（阶段 3 起必须有）。
    arg("room.schemaLocation", "${project.projectDir}/schemas")
}
