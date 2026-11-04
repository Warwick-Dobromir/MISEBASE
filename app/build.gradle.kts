plugins {
    id("culinary.android.application")
    id("culinary.android.compose")
    id("culinary.android.hilt")
}

android {
    defaultConfig {
        applicationId = "com.culinary"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

// 组合根：只做模块装配，不承载业务规则（A03A §4；MB-D-004 的 14 模块）。
dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:platform"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:ingredients"))
    implementation(project(":feature:sauces"))
    implementation(project(":feature:recipes"))
    implementation(project(":feature:products"))
    implementation(project(":feature:inventory"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.timber)

    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
