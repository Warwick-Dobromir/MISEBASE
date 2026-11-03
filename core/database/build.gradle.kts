plugins {
    id("culinary.android.library")
    id("culinary.android.room")
}

// 持久化层：Room 实体/DAO/迁移；不依赖 domain/data（A03A §4）。
dependencies {
    api(project(":core:common"))
}
