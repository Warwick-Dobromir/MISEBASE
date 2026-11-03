import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

configureQuality()

extensions.configure<ApplicationExtension> {
    // D-017：包名暂定 com.culinary；首次发布前可改，不影响数据。
    namespace = "com.culinary"
    compileSdk = CulinaryConfig.COMPILE_SDK

    defaultConfig {
        minSdk = CulinaryConfig.MIN_SDK
        targetSdk = CulinaryConfig.TARGET_SDK
        versionCode = 100
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
