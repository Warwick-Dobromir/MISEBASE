# CULINARY 开发环境与依赖清单

**文档编号**：R02
**版本**：`0.2.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人
**检查时间**：2026-11-04 00:30–01:00（Asia/Hong_Kong）

---

## 1. 本机环境事实

以下为实际检查结果，不是假设。

| 项 | 实际值 | 状态 |
|---|---|---|
| 操作系统 | Windows `10.0.26300`（Windows 11） | 可用 |
| CPU | 12th Gen Intel Core i3-12100F | 可用 |
| 内存 | 15.8 GB | 可用 |
| C: 空闲 | 27.3 GB / 464.5 GB | 偏紧但可用 |
| D: 空闲 | 156.4 GB / 1,863.0 GB | 充足（工作区在 D:） |
| 系统 JDK | Eclipse Adoptium JDK 17.0.20 | 可用（AGP 8.x 最低 17） |
| Android Studio | `AI-252.27397.103.2522.14617522` | 可用 |
| Studio 内置 JDK | `21.0.8`（JBR） | **推荐构建 JDK** |
| Android SDK | `C:\Users\21697\AppData\Local\Android\Sdk` | 已安装 |
| SDK Platforms | `android-34`, `android-36` | 可用 |
| Build Tools | `34.0.0`, `35.0.0`, `36.1.0` | 可用 |
| Platform Tools | `36.0.0`（ADB 1.0.41） | 可用 |
| System Images | API 30 / 33 / 35 / 36.1 | 可用 |
| AVD | `Medium_Phone_API_36.1`, `Pixel_5` | 可用 |
| cmdline-tools | **未安装** | 需补（CI/命令行） |
| Gradle | 无系统 Gradle；Wrapper 缓存 `8.14.3`、`9.0.0` | 用 8.14.3 |
| Git | `2.56.0.windows.3` | 可用 |
| `JAVA_HOME` | 已设置：Studio JBR 21 | 已配置 |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | 已设置：SDK 路径 | 已配置 |
| 已连接真机 | **无** | 验收前必须补 |
| 模拟器安装验证 | `Pixel_5` + `-feature -Vulkan`：install/launch 成功 | PASS |
| Google Maven | 直连失败；阿里云镜像可用 | 已用镜像构建验证 |
| 依赖构建验证 | `tools/dependency-spike` 构建成功 | 通过（见 V01） |

## 2. 必须安装/配置

### 2.1 JDK

- 构建使用 **JDK 21**（Android Studio JBR）。
- Gradle Java 工具链目标：**JVM 17**（`sourceCompatibility/targetCompatibility = 17`，`jvmTarget = 17`）。
- 系统 JDK 17 保留，但不要让 Android Studio 用它构建；统一 JBR 21 避免两套 JDK 行为差异。

PowerShell（当前会话）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
java -version
adb version
```

永久设置（用户级）：

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
```

### 2.2 SDK 组件

已具备：

- `platforms;android-36`
- `build-tools;36.1.0`
- `platform-tools`
- `emulator`
- `system-images;android-33`, `35`, `36.1`

需要补：

```powershell
# cmdline-tools（CI/命令行）
sdkmanager "cmdline-tools;latest"

# API 26 模拟器镜像（最低版本兼容测试）
sdkmanager "platforms;android-26" "system-images;android-26;google_apis;x86_64"

# 可选：API 34（当前已装 platform，无 system image 也可）
```

> `sdkmanager` 位于 `%ANDROID_HOME%\cmdline-tools\latest\bin`。当前未安装，需要先通过 Android Studio SDK Manager 或下载 commandline tools。

### 2.3 Gradle

- 使用 Wrapper，不依赖系统 Gradle。
- 基线：**Gradle 8.14.3**（本机已缓存）。
- 不使用 Gradle 9.0（AGP 8.13 的兼容性未经本项目验证；不要为了“新”冒险）。
- `gradle-wrapper.properties` 锁定 `distributionUrl` 与校验和。

### 2.4 `local.properties`

```properties
sdk.dir=C\:\\Users\\21697\\AppData\\Local\\Android\\Sdk
```

- 不提交 Git。
- CI 用 `ANDROID_HOME` 或 secret 注入。

### 2.5 `gradle.properties` 建议

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- `configuration-cache` 若与某些插件不兼容，可先关闭，逐个排查；不要全局禁用。
- 内存不要超过物理内存的 1/3（本机 15.8 GB，4 GB 合理）。

### 2.6 签名（发布前门禁，不阻塞框架）

框架阶段统一使用 Android Debug 签名；**Release keystore 不提前创建**，正式发布前执行：

```powershell
keytool -genkeypair -v `
  -keystore culinary-release.jks `
  -alias culinary `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storetype PKCS12
```

规则：

- Debug 与 Release 签名严格隔离，不共用 keystore。
- keystore 放仓库外（如 `D:\CULINARY-secrets\`）；不入 Git。
- 密码不写入源码/`gradle.properties`/CI 日志；用环境变量或受控属性文件。
- 至少两份独立加密备份；密码与文件分开保管。
- 发布前验证密钥、密码、证书指纹与 APK 签名。
- 丢失 keystore 将无法覆盖升级已安装应用。

> 当前不创建、不标记“签名准备完成”；G5 为 Release 门禁。

## 3. 版本基线

### 3.1 已在本机缓存/确认

| 组件 | 版本 | 验证方式 |
|---|---|---|
| Gradle | 8.14.3 | Wrapper 缓存 |
| Android Gradle Plugin | 8.13.0 | Gradle 缓存 |
| Kotlin | 2.2.20 | Gradle 缓存 |
| JDK | 21.0.8 | Studio JBR |
| compileSdk / targetSdk | 36 | SDK 已安装 |
| minSdk | 26 | 决策 |

### 3.2 已通过依赖验证的版本

> 以下版本已在 `tools/dependency-spike` 中实际构建通过（AGP 8.13 + Gradle 8.14.3 + JDK 21）。详见 [V01 技术验证](V01-DEPENDENCY-VALIDATION.md)。Google Maven 直连失败，但阿里云 Google 镜像可用。

| 组件 | 版本 | 用途 |
|---|---|---|
| Compose BOM | `2025.10.01` | Compose UI |
| Activity Compose | `1.11.0` | `setContent` |
| Lifecycle | `2.9.4` | ViewModel/collectAsStateWithLifecycle |
| Navigation Compose | `2.9.8` | 导航 |
| Room | `2.8.5` | 本地数据库 |
| Hilt | `2.57.2` | DI（**2.59+ 要求 AGP 9**） |
| Hilt Navigation Compose | `1.3.0` | ViewModel 注入 |
| CameraX | `1.4.2` | 相机预览/分析 |
| ML Kit Barcode | `17.3.0`（bundled） | 本地扫码 |
| Coil | `3.3.0` | 图片加载 |
| WorkManager | `2.10.5` | 自动备份 |
| kotlinx.serialization | `1.9.0` | JSON |
| kotlinx-coroutines | `1.10.2` | 异步 |
| Timber | `5.0.1` | 日志门面 |
| Core KTX | `1.17.0` | Android 扩展 |
| JUnit4 | `4.13.2` | 单元测试 |
| Truth | `1.4.4` | 断言 |
| MockK | `1.14.7` | Mock |
| Turbine | `1.2.1` | Flow 测试 |
| Robolectric | `4.17`（候选） | JVM Android 测试 |
| Compose UI Test | 随 Compose BOM | UI 测试 |
| Detekt | `1.23.8`（候选） | 静态分析 |
| ktlint | 以插件解析为准 | 格式 |

**锁定规则：**

- 技术路线锁定；版本已写入版本目录，禁止 `+`/SNAPSHOT。
- Hilt 2.57.2 是 AGP 8.13 兼容上限；升级 AGP 9 时再同步升级。
- 升级必须走 PR + 全量回归；不允许开发中途随意升级。

## 4. 依赖清单（按模块）

### 4.1 `:app`

- AndroidX Activity Compose
- Navigation Compose
- Hilt Android
- Compose BOM / Material 3
- core:designsystem / feature:* / core:data / core:platform
- Timber（通过 core:common）

### 4.2 `:core:common`

- Kotlin stdlib
- kotlinx-coroutines-core
- Timber（仅日志实现）
- `java.time`（JDK）

### 4.3 `:core:model`

- Kotlin stdlib
- `java.time`
- kotlinx-serialization-json（快照模型需要；或放 data）

### 4.4 `:core:domain`

- Kotlin stdlib
- kotlinx-coroutines-core
- `core:model` / `core:common`
- 不依赖 Android/Room/Hilt

### 4.5 `:core:database`

- Room Runtime / Room KTX / Room Compiler（KSP）
- AndroidX SQLite
- `core:common`
- 测试：Room Testing、JUnit4、Truth、Robolectric

### 4.6 `:core:data`

- `core:domain` / `core:database` / `core:model` / `core:common`
- kotlinx-serialization-json
- Hilt
- WorkManager（备份调度经 platform）
- 测试：JUnit4、Truth、MockK、Turbine、Robolectric、TemporaryFolder

### 4.7 `:core:platform`

- CameraX Core/Camera2/Lifecycle/View
- ML Kit Barcode Scanning（bundled）
- AndroidX Activity Result / Core
- WorkManager
- Hilt
- 测试：JUnit4、Truth、MockK、Robolectric

### 4.8 `:core:designsystem`

- Compose BOM、Material 3、Foundation、UI Tooling（debug）
- Coil Compose（图片展示）
- `core:common`

### 4.9 `:feature:*`

- Compose BOM、Material 3
- Lifecycle ViewModel Compose
- Hilt Navigation Compose
- Navigation Compose（仅 Route/回调类型）
- `core:domain` / `core:model` / `core:common` / `core:designsystem`
- 测试：Compose UI Test、JUnit4、Turbine

### 4.10 构建插件

- AGP 8.13.0
- Kotlin Android 2.2.20
- Kotlin Serialization 插件（如使用）
- KSP（匹配 Kotlin）
- Hilt 插件
- Detekt / ktlint 插件

## 5. 依赖版本目录示例

```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.20"
ksp = "2.2.20-2.0.4"
composeBom = "2025.10.01"
room = "2.8.5"
hilt = "2.57.2"                 # 2.59+ requires AGP 9+
navigation = "2.9.8"
camerax = "1.4.2"
mlkitBarcode = "17.3.0"
coil = "3.3.0"
work = "2.10.5"
serialization = "1.9.0"
coroutines = "1.10.2"
timber = "5.0.1"
junit = "4.13.2"
truth = "1.4.4"
mockk = "1.14.7"
turbine = "1.2.1"
robolectric = "4.17"
```

```toml
[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
# ...
```

> 上述版本已在依赖 spike 中构建通过；正式工程直接以它们为初始值。

## 6. 网络与依赖解析

### 6.1 实测结果

- `https://dl.google.com/android/maven2/...`、`https://maven.google.com/...` 在当前代理下 **TLS 握手失败**（证书/吊销检查问题，`--ssl-no-revoke` 也无效）。
- `https://repo1.maven.org/maven2/` 可间歇访问。
- 阿里云 Google 镜像 `https://maven.aliyun.com/repository/google` **可访问**；腾讯云、华为云镜像也可访问。
- 通过镜像优先的仓库配置，已成功构建包含 AGP/Compose/Room/Hilt/CameraX/ML Kit 的 spike 工程。

### 6.2 结论

**Google Maven 直连不可用，但依赖解析已通过可信镜像解除阻塞。** 这不再是阶段 0 的阻塞项；但镜像配置必须写入构建脚本并记录，不能依赖开发者本机偶然缓存。

### 6.3 仓库配置

`settings.gradle.kts` 使用“镜像优先、官方回退”：

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}
```

规则：

- 镜像只解决可达性，不降低版本校验；依赖版本仍由版本目录锁定。
- 记录镜像地址、使用日期与回退方案（R02 本文）。
- 若公司/个人环境有官方代理，优先使用官方源 + 代理；镜像作为备选。
- 不把镜像地址硬编码到每个模块；只在 `settings.gradle.kts` 配置。

### 6.4 Gradle 代理（可选）

若代理恢复，可在 `gradle.properties` 配置：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7897
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7897
```

代理与镜像不要同时排障；一次只改一个变量。

## 7. 开发环境检查命令

```powershell
java -version
adb version
adb devices -l
& "$env:ANDROID_HOME\emulator\emulator.exe" -list-avds
git --version
./gradlew --version
./gradlew :app:assembleDebug
```

## 8. 真机与验收环境

- 当前未连接真机；模拟器可用于框架开发。
- 验收前必须有至少一台 Android 手机（O-23），覆盖：
  - 相机扫码；
  - SAF 备份目录；
  - WorkManager 后台限制；
  - 厂商 ROM 的电池优化；
  - 进程死亡/重启。
- 若目标机 API < 26，不支持；需确认用户设备版本。

## 9. 环境风险

| 风险 | 等级 | 影响 | 缓解 |
|---|---|---|---|
| Google Maven 直连不可达 | 中 | 无法直连官方源 | 镜像已验证；保留回退；记录来源 |
| 镜像长期可用性 | 中 | 镜像失效导致无法构建 | 依赖版本锁定 + 缓存 + 多镜像备选 |
| 无真机 | 中 | 扫码/SAF/后台未验证 | 验收前补；模拟器先开发 |
| C: 空间偏紧 | 中 | Gradle 缓存/模拟器占满 | 缓存迁 D: 或清理 |
| 无 cmdline-tools | 低 | CI/命令行 SDK 管理不便 | 安装 latest |
| 签名密钥未建 | 中 | 无法发布/覆盖升级 | 发布前创建；双备份；不阻塞框架 |
| JDK 双版本 | 低 | 构建行为不一致 | 固定 Studio JBR 21 |
| API 26 镜像缺失 | 低 | 最低版本未真机验证 | 补镜像或用 Robolectric |

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 初稿；记录本机实际环境、依赖候选与网络阻塞 | 架构组 |
| 0.2.0 | 2026-11-04 | 依赖/构建/安装已验证；签名改为发布前门禁，补 Debug/Release 隔离规则 | 架构组 |
