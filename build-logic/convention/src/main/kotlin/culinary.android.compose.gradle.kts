import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        buildFeatures {
            compose = true
        }
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        buildFeatures {
            compose = true
        }
    }
}

dependencies {
    val catalog = libs
    val composeBom = platform(catalog.findLibrary("androidx-compose-bom").get())

    add("implementation", composeBom)
    add("androidTestImplementation", composeBom)
    add("implementation", catalog.findLibrary("androidx-compose-ui").get())
    add("implementation", catalog.findLibrary("androidx-compose-ui-graphics").get())
    add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
    add("implementation", catalog.findLibrary("androidx-compose-material3").get())
    add("debugImplementation", catalog.findLibrary("androidx-compose-ui-tooling").get())
    add("debugImplementation", catalog.findLibrary("androidx-compose-ui-test-manifest").get())
    add("androidTestImplementation", catalog.findLibrary("androidx-compose-ui-test-junit4").get())
}
