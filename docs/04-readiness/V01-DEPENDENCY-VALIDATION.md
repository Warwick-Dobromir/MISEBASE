# CULINARY 依赖兼容性技术验证报告

**文档编号**：V01（技术验证，不计入 17 项正式交付物）
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人
**验证目录**：`tools/dependency-spike/`（**不是产品代码**，验证后应删除或保留为只读证据）

---

## 1. 验证目标

在正式框架搭建前，验证以下假设：

1. 本机 JDK/SDK/Gradle 能构建 Android 应用。
2. 锁定版本的 AGP、Kotlin、Compose、Room、Hilt、Navigation、CameraX、ML Kit、Coil、WorkManager、序列化、Timber 能在同一工程中共存。
3. Google Maven 在当前网络下不可直连时，可信镜像能否完成依赖解析。
4. Hilt 与 AGP/Kotlin/KSP 的版本约束是否如预期。
5. “v1 不申请 INTERNET 权限”是否会被传递依赖破坏。

## 2. 验证环境

| 项 | 值 |
|---|---|
| OS | Windows 11 `10.0.26300` |
| JDK | Studio JBR 21.0.8 |
| Gradle | 8.14.3（本机缓存） |
| Android SDK | 36 / build-tools 36.1.0 |
| 网络 | Google Maven 直连 TLS 失败；使用阿里云 Google 镜像 |
| 工程 | 单模块 `:app`，minSdk 26 / target 36 |

## 3. 锁定版本（已验证可构建）

| 组件 | 版本 |
|---|---|
| Android Gradle Plugin | `8.13.0` |
| Gradle | `8.14.3` |
| Kotlin | `2.2.20` |
| Compose Compiler Plugin | `2.2.20`（随 Kotlin） |
| KSP | `2.2.20-2.0.4` |
| Compose BOM | `2025.10.01` |
| Activity Compose | `1.11.0` |
| Lifecycle | `2.9.4` |
| Navigation Compose | `2.9.8` |
| Room | `2.8.5` |
| Hilt | `2.57.2` |
| Hilt Navigation Compose | `1.3.0` |
| CameraX | `1.4.2` |
| ML Kit Barcode (bundled) | `17.3.0` |
| Coil | `3.3.0` |
| WorkManager | `2.10.5` |
| kotlinx.serialization JSON | `1.9.0` |
| Timber | `5.0.1` |
| JUnit4 | `4.13.2` |
| Truth | `1.4.4` |
| MockK | `1.14.7` |
| Turbine | `1.2.1` |
| Coroutines Test | `1.10.2` |

## 4. 构建结果

```text
> Task :app:kspDebugKotlin
> Task :app:compileDebugKotlin
> Task :app:hiltJavaCompileDebug
> Task :app:packageDebug
> Task :app:assembleDebug

BUILD SUCCESSFUL
```

- Debug APK 约 **36.7 MB**（含 Compose、CameraX、ML Kit bundled 模型、未压缩调试资源）。
- Room KSP、Hilt KSP、Compose 编译均通过。
- 未发现依赖冲突/重复类。

## 5. 关键发现

### 5.1 Hilt 2.59+ 要求 AGP 9+

首次使用 Hilt `2.59.2` 构建失败：

```text
The Hilt Android Gradle plugin is only compatible with
Android Gradle Plugin (AGP) version 9.0.0 or higher
(found Android Gradle Plugin version 8.13.0).
```

**处理：** 锁定 Hilt `2.57.2`，与 AGP 8.13.0 成功构建。

**含义：**

- 若未来升级 AGP 9.x，可同步升级 Hilt；这是独立的升级任务。
- 不要在 AGP 8.13 基线上使用 Hilt 2.59+。
- 该约束必须写入版本目录注释，防止误升级。

### 5.2 Google Maven 直连不可用，镜像可用

- `https://dl.google.com/android/maven2/`、`https://maven.google.com/` 在当前代理下 TLS 握手失败。
- `https://maven.aliyun.com/repository/google`、腾讯云、华为云镜像可访问。
- 通过在 `settings.gradle.kts` 中把镜像放在仓库列表首位，成功解析 AGP/Compose/Room/Hilt/CameraX/ML Kit。

**含义：**

- 阶段 0 的环境阻塞已解除，可用镜像继续。
- 镜像不是长期唯一方案；应记录地址、校验依赖、保留切换回官方源的能力。
- Maven Central 依赖仍可直连（间歇），镜像 public 也可用。

### 5.3 传递依赖会合并 INTERNET 权限

首次构建的 APK 包含：

```text
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
```

来源不是应用代码，而是依赖库 manifest 合并（WorkManager/ML Kit 等）。

**处理：**

- `INTERNET` **必须移除**：它才是“能不能联网”的权限。
- `ACCESS_NETWORK_STATE` 只授予“查询网络状态”，不授予网络访问；spike 中一并移除，但运行期尚未验证。若 WorkManager 初始化/网络约束需要它，**保留它不会破坏离线承诺**，只移除 `INTERNET` 即可。

```xml
<!-- 必须：禁止网络访问 -->
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
<!-- 可选：若运行期验证 WorkManager 不需要，再移除 -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
```

spike 再次构建后 `aapt2 dump permissions` 结果不再包含 INTERNET / ACCESS_NETWORK_STATE，仅保留：

```text
WAKE_LOCK
RECEIVE_BOOT_COMPLETED
FOREGROUND_SERVICE
```

**含义：**

- “离线优先、v1 无网络权限”必须通过 manifest 显式移除 + 构建后 `aapt` 验证，不能只靠“代码里没写网络调用”。
- `ACCESS_NETWORK_STATE` 的移除是运行期优化，不是离线承诺的必要条件；安装启动测试后决定。
- 每个发布版本都要在 CI 检查权限清单：`INTERNET` 必须不存在。
- 若未来实现在线查询，再显式加回 INTERNET，并走 ADR。

### 5.4 未验证项

- 真机安装/启动（模拟器已安装/启动，见第 6 节）。
- 扫码、SAF、WorkManager 后台限制。
- Release R8 构建。
- 数据库迁移（spike 只有 v1）。
- 备份/恢复。

这些属于框架阶段/验收阶段，不在本次依赖验证范围。

## 6. 安装验证

| 项 | 结果 |
|---|---|
| 模拟器 | `Pixel_5`（headless，`-feature -Vulkan`） |
| `adb install` | **Success** |
| 启动 Activity | `com.culinary.spike/.MainActivity` 已 `mResumedActivity` |
| 进程 | `pidof com.culinary.spike` 返回 PID |
| 设备端权限 | `dumpsys package` 仅 WAKE_LOCK / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE；**无 INTERNET / ACCESS_NETWORK_STATE** |

> 首次 headless 启动因 Vulkan 加载失败崩溃；加 `-feature -Vulkan` 后正常启动。该参数仅用于本机模拟器验证，不影响产品代码。安装、启动与设备端权限检查均通过。

## 7. 复现命令

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14.3-all\10utluxaxniiv4wxiphsi49nj\gradle-8.14.3\bin\gradle.bat"
& $gradle :app:assembleDebug --no-daemon --console=plain
```

权限检查：

```powershell
& "$env:ANDROID_HOME\build-tools\36.1.0\aapt2.exe" dump permissions `
  "app\build\outputs\apk\debug\app-debug.apk"
```

## 8. 对正式框架的输入

1. 使用本报告第 3 节版本作为 `libs.versions.toml` 的初始锁定值。
2. 在 `settings.gradle.kts` 保留镜像优先、官方源回退的仓库顺序。
3. 在 `app/src/main/AndroidManifest.xml` 加入权限移除规则。
4. CI 增加“APK 权限清单不得包含 INTERNET/ACCESS_NETWORK_STATE”检查。
5. Hilt 版本注释：`2.57.2` 是 AGP 8.13 兼容上限；升级 AGP 9 时再评估。
6. 删除或归档 `tools/dependency-spike/`，不得把 spike 代码混入正式工程。

## 9. 结论

**依赖兼容性验证通过。** 在 AGP 8.13.0 / Kotlin 2.2.20 / Hilt 2.57.2 组合下，核心依赖可共同构建；镜像可解决当前网络问题；离线权限可通过 manifest 移除实现。

尚未验证的是运行期行为（扫码/SAF/后台/迁移），属于后续阶段，不影响“依赖能否协同”的结论。

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 首次依赖验证；记录 Hilt/AGP 约束与权限合并问题 | 架构组 |
