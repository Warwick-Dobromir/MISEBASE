# MB-S6-03 发布签名与 Keystore 管理

**文档编号**：MB-S6-03  
**版本**：`1.0.0`  
**状态**：`Review`  
**日期**：2026-11-05  
**依据**：MB-S6-001 §3.2；MB-D-012（发布签名策略，2026-11-05 签收）；MB-D-010（本地构建）  
**适用范围**：`com.culinary` 正式发布包

---

## 1. 结论

1. 已生成独立发布签名 `release.keystore`，位于**主仓库之外**的 `D:\MISEBASE_RELEASE\`。
2. 仓库内**不含任何** `.keystore`、密码或凭据；签名信息只在被 `.gitignore` 忽略的
   `local.properties` 或同名环境变量中出现。
3. Release APK 已用该签名打包并通过 `apksigner verify`；证书指纹与 Keystore 一致。
4. 已准备两份本地备份（`backup-01` / `backup-02`），三份文件的 SHA-256 完全一致。
5. **密码不在本仓库、不在本文档**：keystore 与 key 密码由交付消息单独给出，
   项目负责人须立即抄录到密码管理器并删除该记录。

---

## 2. Keystore 事实

| 项 | 值 |
|---|---|
| 绝对路径 | `D:\MISEBASE_RELEASE\release.keystore` |
| 类型 | PKCS12 |
| 别名（alias） | `misebase-release` |
| 密钥算法 | RSA 4096 |
| 签名算法 | SHA384withRSA |
| 有效期 | 10,000 天 |
| 主体（DN） | `CN=MISEBASE Release, OU=Culinary, O=MISEBASE, L=Hong Kong, ST=Hong Kong, C=HK` |
| 创建时间 | 2026-11-05 04:11:53（Asia/Hong_Kong） |
| 文件大小 | 4,460 字节 |
| 文件 SHA-256 | `34538C3D818BF5F8B8AC0D2B3E4B2F354D07F952DEEDF422CB8155DFF37A77CA` |
| 证书 SHA-256 指纹 | `5C:A4:19:59:D9:F4:50:CD:C8:51:DC:B9:F0:52:AC:91:30:AA:EA:B7:1C:64:06:21:73:A6:99:35:50:56:AF:61` |
| 证书 SHA-1 指纹 | `E4:B8:58:6C:D0:8F:B9:74:8E:DD:D4:F6:4D:ED:7B:6C:99:7B:FC:96` |

> 证书 SHA-256（无冒号小写）也可写作
> `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61`，
> 与 `apksigner verify --print-certs` 的输出一一对应。

---

## 3. 生成命令（不含真实密码）

在**主仓库之外**的目录执行；密码通过交互输入或临时变量传入，不要写进脚本文件：

```powershell
$rel = 'D:\MISEBASE_RELEASE'
New-Item -ItemType Directory -Force -Path $rel | Out-Null
keytool -genkeypair -v `
  -keystore "$rel\release.keystore" `
  -alias misebase-release `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storepass '<STORE_PASSWORD>' -keypass '<KEY_PASSWORD>' `
  -storetype PKCS12 `
  -dname "CN=MISEBASE Release, OU=Culinary, O=MISEBASE, L=Hong Kong, ST=Hong Kong, C=HK"
```

校验：

```powershell
keytool -list -v -keystore D:\MISEBASE_RELEASE\release.keystore -alias misebase-release
# 输出应包含：
#   SHA256: 5C:A4:19:59:...:AF:61
#   Signature algorithm name: SHA384withRSA
#   Subject Public Key Algorithm: 4096-bit RSA key
```

---

## 4. 备份

| 备份 | 路径 | 状态 |
|---|---|---|
| 主文件 | `D:\MISEBASE_RELEASE\release.keystore` | 使用中 |
| 备份 1 | `D:\MISEBASE_RELEASE\backup-01\release.keystore` | 已复制，SHA-256 一致 |
| 备份 2 | `D:\MISEBASE_RELEASE\backup-02\release.keystore.bak` | 已复制，SHA-256 一致 |

三份文件的 SHA-256 均为 `34538C3D…77CA`（见 §2）。

**负责人必须完成的双备份建议**（至少一份离开本机）：

1. 备份 1 保留在本机之外的加密 U 盘/移动硬盘；
2. 备份 2 存入密码管理器的附件，或离线加密压缩包；
3. 备份介质与密码分开保管：**只有 keystore 没有密码 = 无法升级**；只有密码没有 keystore = 同样无法升级；
4. 每次发布前用 §3 的校验命令确认指纹未变。

> 已发布版本一旦丢失签名，只能更换 `applicationId` 重新发布，用户数据无法平滑升级。

---

## 5. 仓库内如何读取签名

`app/build.gradle.kts` 依次从 **`local.properties`** 与**同名环境变量**读取四个键；
任一缺失时 release 变体不签名（构建会提示），`scripts/build-release.ps1` 会直接失败并指出缺哪一项。

| 键 | 含义 |
|---|---|
| `MISEBASE_RELEASE_STORE_FILE` | keystore 绝对路径（须在仓库外） |
| `MISEBASE_RELEASE_STORE_PASSWORD` | keystore 密码 |
| `MISEBASE_RELEASE_KEY_ALIAS` | `misebase-release` |
| `MISEBASE_RELEASE_KEY_PASSWORD` | key 密码 |

`local.properties` 示例（**填入真实值后不要提交**；该文件已被 `.gitignore` 第 4 行忽略）：

```properties
sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk
MISEBASE_RELEASE_STORE_FILE=D:/MISEBASE_RELEASE/release.keystore
MISEBASE_RELEASE_STORE_PASSWORD=<STORE_PASSWORD>
MISEBASE_RELEASE_KEY_ALIAS=misebase-release
MISEBASE_RELEASE_KEY_PASSWORD=<KEY_PASSWORD>
```

安全边界：

- `.gitignore` 覆盖 `*.jks`、`*.keystore`、`local.properties`、`keystore.properties`、`secrets.properties`；
- `scripts/build-release.ps1` 在发现 keystore 位于仓库目录内时**直接失败**；
- 构建脚本只校验、不打印密码；
- `git status` 中不出现任何签名文件（见 §7）。

---

## 6. 签名验证

对最终 APK：

```powershell
& "$env:ANDROID_HOME\build-tools\36.1.0\apksigner.bat" verify --print-certs --verbose `
  app\build\outputs\apk\release\app-release.apk
```

本轮实测输出（节选）：

```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
Signer #1 certificate DN: CN=MISEBASE Release, OU=Culinary, O=MISEBASE, L=Hong Kong, ST=Hong Kong, C=HK
Signer #1 certificate SHA-256 digest: 5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61
```

- v1（JAR 签名）文件已随包产出（`META-INF/CERT.SF`、`CERT.RSA`），但 `minSdk = 26` 时
  `apksigner` 不要求也不校验 v1，因此上面输出 `Verified using v1 scheme: false`；
  **实际参与校验与信任的是 v2**。未来若把 `minSdk` 降到 24 以下，v1 会重新成为必需；
- v3/v4 未启用：本机 AGP 配置未开启，`apksigner` 对 API 26+ 的校验以 v2 为准。
- 若未来把 `minSdk` 降到 24 以下，必须重新开启 v1。

---

## 7. 仓库清洁性检查

```powershell
git status --porcelain            # 不应出现 *.keystore / local.properties
git ls-files | Select-String 'keystore|\.jks|local\.properties'   # 应为空
```

本轮实测：以上两条命令均无匹配（见 [MB-S6-01 执行报告](MB-S6-01-EXECUTION-REPORT.md) §7）。

---

## 8. 风险与交接

| 风险 | 影响 | 处置 |
|---|---|---|
| keystore/密码丢失 | 无法为已发布版本升级 | 双备份 + 密码管理器；见 §4 |
| keystore 误提交 | 签名泄露，可被伪造升级包 | `.gitignore` + 构建脚本仓库内检查 + 提交前 `git ls-files` 核对 |
| 密码写入文档/代码 | 同上 | 本文档只写占位符；密码仅在交付消息中一次性给出 |
| 更换签名密钥 | 已安装用户必须卸载重装，数据丢失 | 禁止；如需更换只能改 `applicationId` |

---

## 9. 复核清单（待填）

| 项 | 结论 | 复核人 | 日期 |
|---|---|---|---|
| keystore 位于仓库外 | 待复核 | | |
| 仓库内无凭据 | 待复核 | | |
| 证书指纹与 APK 一致 | 待复核 | | |
| 双备份已完成（含离机副本） | 待复核 | | |
| 密码已抄录并删除交付记录 | 待复核 | | |
