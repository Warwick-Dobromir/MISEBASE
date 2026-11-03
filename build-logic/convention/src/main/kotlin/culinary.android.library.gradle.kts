import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

configureQuality()

val moduleNamespace = project.culinaryNamespace()

extensions.configure<LibraryExtension> {
    namespace = moduleNamespace
    compileSdk = CulinaryConfig.COMPILE_SDK

    defaultConfig {
        minSdk = CulinaryConfig.MIN_SDK
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val catalog = libs
    add("testImplementation", catalog.findLibrary("junit").get())
    add("testImplementation", catalog.findLibrary("truth").get())
}
