plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    val catalog = libs
    add("implementation", catalog.findLibrary("hilt-android").get())
    add("ksp", catalog.findLibrary("hilt-compiler").get())
}
