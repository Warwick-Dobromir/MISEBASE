# Dependency Spike（隔离技术验证）

**不是产品代码，不属于 `culinary` 正式工程。**

用途：验证 AGP/Kotlin/Compose/Room/Hilt/CameraX/ML Kit/Coil/WorkManager 的版本兼容性，以及离线权限能否从 APK 中移除。

## 事实

- 构建通过：AGP 8.13.0 + Gradle 8.14.3 + Kotlin 2.2.20 + Hilt 2.57.2 + Room 2.8.5。
- `build-log-2.txt` 是成功构建日志（Hilt 2.57.2）。
- `build-log.txt` 是 Hilt 2.59.2 失败记录（证明 2.59+ 要求 AGP 9）。
- `build-log-3.txt` 是移除 INTERNET/ACCESS_NETWORK_STATE 后的构建与权限检查记录。

## 构建

需要本地 `local.properties`（不提交）：

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14.3-all\10utluxaxniiv4wxiphsi49nj\gradle-8.14.3\bin\gradle.bat"
& $gradle :app:assembleDebug --no-daemon --console=plain
```

## 结论

不要把此目录的代码复制进正式工程；正式工程按 R01 阶段 1 新建，并使用 V01 的版本表。

验证完成后可删除整个目录，或保留为只读证据。
