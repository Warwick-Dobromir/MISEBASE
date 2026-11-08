# MISEBASE 原签名重建与覆盖升级报告

- 日期：2026-11-08
- 目标：使用旧版 MISEBASE 的原始签名重新构建 APK，解决覆盖安装签名冲突，并保留应用数据
- 结果：**通过**

## 1. 最终交付

| 项目 | 值 |
|---|---|
| APK | `D:\MISEBASE\dist\KITCHEN-HOME-001\MISEBASE-1.0.1-kitchen-home-20261108-release.apk` |
| 大小 | 24,467,519 字节 |
| SHA-256 | `b8cff1334d0b821399412be5631ee85355ba513f4c73ef3fc87db31651d0f68a` |
| SHA-256 文件 | `D:\MISEBASE\dist\KITCHEN-HOME-001\MISEBASE-1.0.1-kitchen-home-20261108-release.apk.sha256` |
| 包名 / applicationId | `com.culinary`（未修改） |
| versionName | `1.0.1` |
| versionCode | `2` |
| 构建类型 | Release |
| minSdk / targetSdk | 26 / 36 |
| 签名证书 SHA-256 | `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61` |
| APK 签名方案 | APK Signature Scheme v2，`apksigner verify` 通过 |
| 安装方式 | **直接覆盖安装，无需卸载旧版** |
| 安装说明 | [RELEASE-1.0.1-README.md](../../dist/KITCHEN-HOME-001/RELEASE-1.0.1-README.md) |

## 2. 签名配置检查

- 使用项目现有 Gradle `release` signingConfig，没有生成新密钥。
- 本地原配置使用的 release keystore 与用户提供的 backup keystore 指向同一证书。
- Key Alias 与原始凭据一致。
- `applicationId` 保持 `com.culinary`。
- `local.properties` 未被覆盖或修改。
- 签名密码、Keystore 内容、Alias 值和凭据文件内容均未输出到报告或日志。

证书 SHA-256 对比：

| 对象 | 证书 SHA-256 |
|---|---|
| 用户提供的原始 Keystore | `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61` |
| 本地现有 release Keystore | `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61` |
| 旧 Release APK（1.0.0 / 1） | `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61` |
| 新 Release APK（1.0.1 / 2） | `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61` |
| 覆盖安装后的设备包体 | `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61` |

结论：五者完全一致，无签名冲突。

## 3. 版本号检查

| 项目 | 旧版 | 新版 |
|---|---|---|
| versionName | 1.0.0 | **1.0.1** |
| versionCode | 1 | **2** |
| applicationId | com.culinary | **com.culinary（不变）** |

`versionCode` 从 1 提升到 2，满足 Android 更新安装要求；包名未变化。

## 4. 覆盖安装与数据保留验证

验证过程：

1. 在模拟器安装旧版 `1.0.0 (1)` 原签名 APK。
2. 在旧版内创建一条测试食材记录，旧列表显示“共 1 个食材”。
3. **不卸载旧应用**，直接执行：
   ```powershell
   adb install -r "MISEBASE-1.0.1-kitchen-home-20261108-release.apk"
   ```
   结果：`Success`
4. 启动新版并进入厨房页，食材数量显示 `1 项`，测试记录仍存在。

系统包信息：

- 更新前：`versionCode=1`，`versionName=1.0.0`
- 更新后：`versionCode=2`，`versionName=1.0.1`
- `firstInstallTime=2026-11-08 15:07:41`（覆盖安装前后不变，证明是原位更新）
- `lastUpdateTime=2026-11-08 15:08:54`（已更新）
- 冷启动：`Status: ok`，`LaunchState: COLD`，`TotalTime: 917ms`
- 日志：无 FATAL EXCEPTION，无应用 ANR

数据未被清除，数据库未被重置。

## 5. 构建

```powershell
.\gradlew.bat :app:assembleRelease
```

结果：`BUILD SUCCESSFUL`。

本轮只修改了版本号并重新构建签名 Release；上一轮厨房功能的 38 个测试保持 0 失败。

## 6. 证据

- [签名与升级验证汇总](evidence/00-signature-update-verification.txt)
- [旧版 1.0.0 数据基线：共 1 个食材](evidence/01-old-1.0.0-baseline-data.xml)
- [覆盖更新后的厨房页：食材 1 项](evidence/02-updated-1.0.1-kitchen.png)
- [覆盖更新后的厨房 UI 层级](evidence/03-updated-1.0.1-kitchen.xml)
- [覆盖更新后的首页](evidence/04-updated-1.0.1-home.png)

## 7. 安全说明

- 未重新生成 Keystore。
- 未更换签名证书。
- 未修改 applicationId。
- 未要求用户卸载旧版。
- 未清除或重置数据库。
- 未在聊天记录、报告或日志中输出 Keystore 密码、密钥文件内容或其他签名凭据。
