# MISEBASE 发布构建指南（换机可复现）

**文档编号**：MB-S6-05  
**版本**：`1.0.0`  
**状态**：`Review`  
**日期**：2026-11-05  
**依据**：MB-S6-001 §3.4；MB-D-010（本地构建与验收，不采用远端 CI）  
**脚本**：[`scripts/build-release.ps1`](../../scripts/build-release.ps1)

---

## 1. 环境要求

| 项 | 要求 | 本轮实测 |
|---|---|---|
| 操作系统 | Windows 10/11 x64 | Windows 10.0.26300 |
| PowerShell | 5.1 或 7.x | 5.1.26100 |
| JDK | 17 或更高（`JAVA_HOME`） | Android Studio JBR 21.0.8（Temurin 17.0.20.1 亦可） |
| Android SDK | Platform `android-36` + Build-Tools（含 `apksigner`、`aapt2`） | Platform 36；Build-Tools 36.1.0 |
| Gradle | 由 wrapper 提供（8.14.3），无需预装 | 8.14.3 |
| Python | 仅在**重新生成图标**时需要 3.x + Pillow | Python 3 + Pillow 12.3.0 |
| 设备 | API 26+ 真机（发布验收） | vivo V2429A / Android 16 / API 36 |

Gradle 需要的 `sdk.dir` 写在 `local.properties`；若未写则回退 `ANDROID_HOME` / `ANDROID_SDK_ROOT`。

---

## 2. 一次性准备

### 2.1 取得代码

```powershell
git clone <仓库地址> MISEBASE
cd MISEBASE
git checkout main
```

### 2.2 恢复发布签名（**不在仓库里**）

向项目负责人索取 `release.keystore` 与两个密码，放到仓库之外，例如：

```powershell
New-Item -ItemType Directory -Force -Path D:\MISEBASE_RELEASE | Out-Null
# 把 release.keystore 放到 D:\MISEBASE_RELEASE\release.keystore
```

校验指纹必须与交付记录一致：

```powershell
keytool -list -v -keystore D:\MISEBASE_RELEASE\release.keystore -alias misebase-release
# 期望：SHA256: 5C:A4:19:59:D9:F4:50:CD:C8:51:DC:B9:F0:52:AC:91:30:AA:EA:B7:1C:64:06:21:73:A6:99:35:50:56:AF:61
```

### 2.3 创建 `local.properties`

该文件已被 `.gitignore` 忽略，**不得提交**：

```properties
sdk.dir=C:/Users/<用户>/AppData/Local/Android/Sdk
MISEBASE_RELEASE_STORE_FILE=D:/MISEBASE_RELEASE/release.keystore
MISEBASE_RELEASE_STORE_PASSWORD=<STORE_PASSWORD>
MISEBASE_RELEASE_KEY_ALIAS=misebase-release
MISEBASE_RELEASE_KEY_PASSWORD=<KEY_PASSWORD>
```

也可以改用同名环境变量（适合 CI 或临时机器），优先级为 `local.properties` → 环境变量。

---

## 3. 一条命令完成发布

```powershell
# 若本机执行策略限制脚本（默认 Restricted），两种写法任选：
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release.ps1
# 或先放开当前会话：
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\build-release.ps1
```

脚本会依次执行：

1. 校验 JDK（≥17）、SDK Platform 36、Build-Tools、`keytool`；
2. 校验签名配置与 keystore 指纹，并拒绝仓库内的 keystore；
3. 运行 **`test assembleRelease ktlintCheck detekt`**（全模块）；
4. 用 `aapt2` 校验 `applicationId = com.culinary`、`versionCode`、`versionName`；
5. 用 `apksigner` 校验签名，并比对 APK 证书 SHA-256 与 keystore 是否一致；
6. 输出 SHA-256，把 APK 复制到 `dist\MISEBASE-<versionName>-release.apk` 并写 `.sha256`。

可选参数：

| 参数 | 作用 |
|---|---|
| `-SkipTests` | 跳过 `test`（仅本地快速冒烟，正式发布不要用） |
| `-DistDir <目录>` | 自定义分发目录（默认 `dist`） |

---

## 4. 产物

| 产物 | 路径 |
|---|---|
| 构建输出 APK | `app\build\outputs\apk\release\app-release.apk` |
| 分发副本 | `dist\MISEBASE-1.0.0-release.apk` |
| 校验文件 | `dist\MISEBASE-1.0.0-release.apk.sha256` |
| 映射文件（崩溃排查） | `app\build\outputs\mapping\release\mapping.txt` |

`dist/`、`*.apk`、`*.sha256`、`mapping.txt` 均已被 `.gitignore` 忽略，不会误提交。

---

## 5. 换机恢复步骤（照抄即可）

```powershell
# 1) 安装 JDK 17+ 与 Android Studio（或独立 SDK），确认：
java -version
adb version

# 2) 安装 SDK Platform 36 与 Build-Tools（Android Studio SDK Manager）
#    确认存在：%ANDROID_HOME%\platforms\android-36 与 %ANDROID_HOME%\build-tools\<ver>\apksigner.bat

# 3) 克隆代码并切到交付提交
git clone <仓库地址> MISEBASE; cd MISEBASE

# 4) 放回 keystore（仓库外）并校验指纹
keytool -list -v -keystore D:\MISEBASE_RELEASE\release.keystore -alias misebase-release

# 5) 写 local.properties（sdk.dir + 四个签名键）

# 6) 构建
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release.ps1

# 7) 与交付记录比对 SHA-256
Get-Content dist\MISEBASE-1.0.0-release.apk.sha256
```

> 说明：APK 的 SHA-256 会随构建工具/依赖缓存变化（R8、资源压缩、签名块），
> **签名证书指纹**才是跨机器一致的判据；发布记录同时保留两者。

---

## 6. 安装与升级

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"

# 全新安装
& $adb install dist\MISEBASE-1.0.0-release.apk

# 覆盖升级（同签名；已安装版本可保留数据）
& $adb install -r dist\MISEBASE-1.0.0-release.apk

# 版本确认
& $adb shell dumpsys package com.culinary | Select-String 'versionCode|versionName'
```

**签名必须一致**：Debug 包（`debug.keystore`）与 Release 包签名不同，直接
`install -r` 覆盖会得到 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
升级验证需要"同签名的旧包 → 新包"，做法见
[MB-S6-04 升级与数据安全验证](MB-S6-04-UPGRADE-DATA-SAFETY.md)：

```powershell
# 用发布签名打 Debug 变体（保留可调试性，用于抓取升级前数据库）
.\gradlew.bat :app:assembleDebug -PdebugWithReleaseSigning=true
```

---

## 7. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `无法加载文件 build-release.ps1，未进行数字签名` | PowerShell 执行策略默认 Restricted | 用 `-ExecutionPolicy Bypass -File`，或 `Set-ExecutionPolicy -Scope Process Bypass` |
| `缺少签名配置 MISEBASE_RELEASE_STORE_*` | `local.properties` 没写或环境变量未设 | 按 §2.3 补齐 |
| `keystore 位于仓库内` | 把 keystore 放进了工作区 | 移到仓库外（如 `D:\MISEBASE_RELEASE`） |
| `找不到 Android SDK` | `sdk.dir` 路径不对 | 使用正斜杠或转义反斜杠；确认 `platforms\android-36` 存在 |
| `需要 JDK 17 或更高版本` | `JAVA_HOME` 指向旧 JDK | 改为 JDK 17/21 |
| `KtLint found code style violations` | Kotlin 脚本格式 | 按报告修正；`.\gradlew.bat :app:ktlintFormat` 可自动格式化 |
| 构建成功但 APK 未签名 | 四个签名键缺失（脚本会失败，但直接跑 Gradle 不会） | 用脚本构建 |

---

## 8. 本机实测命令与耗时

| 命令 | 结果 | 耗时 |
|---|---|---|
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release.ps1` | 见 [MB-S6-01](MB-S6-01-EXECUTION-REPORT.md) §5 | 约 2–4 分钟（增量）/ 首次全量更久 |
| `.\gradlew.bat :app:assembleDebug -PdebugWithReleaseSigning=true` | 约 15 秒（增量） | 升级验证用 |
