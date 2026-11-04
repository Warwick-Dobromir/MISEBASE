import java.util.Properties

plugins {
    id("culinary.android.application")
    id("culinary.android.compose")
    id("culinary.android.hilt")
}

// 发布签名信息只从仓库外读取：local.properties（已被 .gitignore 忽略）或环境变量。
// 仓库内不保存任何 .keystore、密码或凭据（MB-S6-001 §3.2）。
val localProperties: Map<String, String> =
    runCatching {
        val file = rootProject.file("local.properties")
        if (!file.exists()) {
            emptyMap()
        } else {
            val properties = Properties().apply { file.inputStream().use(::load) }
            properties.stringPropertyNames().associateWith { properties.getProperty(it) }
        }
    }.getOrDefault(emptyMap())

fun signingValue(name: String): String? =
    localProperties[name]?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("MISEBASE_RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("MISEBASE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("MISEBASE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("MISEBASE_RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
    listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { it != null }

if (!hasReleaseSigning) {
    logger.lifecycle(
        "MISEBASE: 未配置发布签名（local.properties / 环境变量），release 变体将不签名；" +
            "正式发布请使用 scripts/build-release.ps1。",
    )
}

android {
    defaultConfig {
        // 包名锁定 com.culinary（MB-S6-001 §2），禁止修改。
        applicationId = "com.culinary"
        // Stage 6 发布基线（MB-S6-001 §3.3）。
        versionCode = 1
        versionName = "1.0.0"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // 仅用于升级安装验证：让 Debug 变体与 Release 使用同一签名，
            // 否则系统会以 INSTALL_FAILED_UPDATE_INCOMPATIBLE 直接拒绝覆盖安装。
            // 用法：.\gradlew.bat :app:assembleDebug -PdebugWithReleaseSigning=true
            if (hasReleaseSigning &&
                providers.gradleProperty("debugWithReleaseSigning").orNull.toBoolean()
            ) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
