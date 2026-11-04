# MISEBASE 阶段 0 接管检查清单

**文档编号**：MB-S0-04
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**基线**：G2 `framework-0.1.0` = `2b523de`；接管工作树 `705e0ca`
**判定**：`PASS` / `PARTIAL` / `BLOCKED` / `PENDING` / `N/A`

---

## 1. 总览

| 类别 | 结果 | 说明 |
|---|---|---|
| A. 交接资料完整性 | **PASS** | G1 哈希、G2 MANIFEST、bundle、源码 ZIP 全部通过 |
| B. 接管仓库与基线 | **PASS** | `D:\MISEBASE` @ `misebase/main`，19 个 G2 标签保留 |
| C. 独立构建与测试 | **PASS** | `test assembleDebug` exit 0；197 例 0 失败 |
| D. 架构与依赖 | **PASS（现状）** / **BLOCKED（目标）** | 15/43 PASS；目标模块集待签 |
| E. 数据库 | **PASS（现状）** / **BLOCKED（残留处置）** | 31 表 v1 可验证；11 表待定性 |
| F. 数据安全 | **BLOCKED** | WAL 下备份一致性未实现（U06） |
| G. 产品范围 | **BLOCKED** | P01 与新任务书冲突，待签字 |
| H. UI/设计系统 | **PENDING** | 设计书骨架已交；视觉方向未确认 |
| I. 治理与回档 | **PARTIAL** | 决策/回档规范已成稿；接管标签待打 |
| J. 阶段 0 关闭 | **未关闭** | 待负责人签字 + 外部审查 |

---

## 2. A. 交接资料完整性

| # | 检查项 | 命令/证据 | 结果 |
|---|---|---|---|
| A1 | G1 压缩包 SHA-256 与 `.sha256` 一致 | `Get-FileHash` | **PASS** |
| A2 | G1 解压结构完整 | 13 份报告 + evidence + snapshot | **PASS** |
| A3 | G2 MANIFEST 295 条全部匹配 | 哈希+大小多重集合比对 | **PASS** |
| A4 | G2 交付目录无缺失/多余 | 0 / 0 | **PASS** |
| A5 | Bundle 可读、fsck 无错误 | `git bundle list-heads` / `git fsck` | **PASS** |
| A6 | `framework-0.1.0` = `2b523de` | `git rev-parse` | **PASS** |
| A7 | 9 个 stage 标签存在 | `git tag -l` | **PASS** |
| A8 | 源码 ZIP 与 `2b523de` 一致 | 380 文件，0 哈希不匹配 | **PASS** |

---

## 3. B. 接管仓库与基线

| # | 检查项 | 命令/证据 | 结果 |
|---|---|---|---|
| B1 | 工作区指向 `D:\MISEBASE` | `pwd` | **PASS** |
| B2 | 仓库从 G2 bundle 克隆 | `git log` 含 G2 历史 | **PASS** |
| B3 | 接管分支 `misebase/main` | `git status` | **PASS** |
| B4 | 工作树基于 `705e0ca` | `git log -1` | **PASS** |
| B5 | 19 个 G2 标签保留 | `git tag -l` | **PASS** |
| B6 | `origin` 已移除（防止误推 bundle） | `git remote -v` | **PASS** |
| B7 | 工作区无未跟踪业务文件 | `git status --short` | **PASS** |
| B8 | G3 代码不在工作树 | 无 `DatabaseSnapshotter` / `AttachmentRestorer` | **PASS** |
| B9 | 接管基线标签 | 尚未创建（待负责人确认 `705e0ca`） | **PENDING** |

**说明**：选择 `705e0ca` 的理由——它是 G2 收档提交，代码与 `2b523de` 完全相同，含 `A-G2-001`，且不含 `850ea63` 的 G3 资料包。若负责人要求“严格等于 `2b523de`”，可改为 `2b523de`，差异仅 3 个文档。

---

## 4. C. 独立构建与测试

| # | 检查项 | 命令 | 结果 |
|---|---|---|---|
| C1 | JDK 21 | `$env:JAVA_HOME\bin\java -version` | **PASS**（21.0.8） |
| C2 | Gradle 8.14.3 | wrapper | **PASS** |
| C3 | Android SDK 36 | `ANDROID_HOME` | **PASS** |
| C4 | `test assembleDebug` | `.\gradlew.bat test assembleDebug` | **PASS**（1m 1s，833 tasks） |
| C5 | 测试数/失败 | XML 解析 | **PASS**（197 / 0） |
| C6 | Debug APK 大小 | `app-debug.apk` | **PASS**（36,603,939） |
| C7 | 无 `INTERNET` | `aapt2 dump permissions` | **PASS** |
| C8 | 模块依赖检查 | `check-module-deps.ps1` | **PASS**（15/43） |
| C9 | Release 构建 | 本次未跑（G2 有历史证据） | **N/A（本阶段）** |
| C10 | 迁移测试（仪器化） | 本次未跑（无设备/未要求） | **N/A（本阶段）** |

---

## 5. D. 架构与依赖

| # | 检查项 | 证据 | 结果 |
|---|---|---|---|
| D1 | 15 模块与 A03A 一致 | settings + 检查脚本 | **PASS** |
| D2 | feature 间零依赖 | 检查脚本 | **PASS** |
| D3 | domain 纯 JVM | 构建配置 | **PASS（现状）** |
| D4 | database 不依赖 domain | 构建配置 | **PASS** |
| D5 | UI 不直接访问 DAO/DB | 抽查 feature/ingredients | **PASS（现状）** |
| D6 | Feature 内部分页隔离 | `feature:ingredients` 只有单包 4 个文件 | **PARTIAL**：不符合任务书 §14 的 `presentation/list|detail|editor` 结构 |
| D7 | 目标模块集（13） | 尚未签字 | **BLOCKED** |
| D8 | 依赖检查脚本可覆盖新规则 | 当前脚本硬编码 15 模块，只查 `build.gradle.kts` | **PARTIAL**：需重写 |

---

## 6. E. 数据库

| # | 检查项 | 证据 | 结果 |
|---|---|---|---|
| E1 | Schema v1 / 31 表 | `1.json` | **PASS** |
| E2 | identityHash | `b75e6e1b0cb2ce5b1eb24bad59e0a987` | **PASS** |
| E3 | 外键/WAL/busy_timeout | `DatabaseFactory` / callback | **PASS（代码）** |
| E4 | 无 destructive migration | `fallbackToDestructiveMigration` 不存在 | **PASS** |
| E5 | 迁移测试 | 2 例，历史通过，本次未跑 | **PARTIAL** |
| E6 | 11 张残留表处置 | 未决 | **BLOCKED** |
| E7 | 备份前 checkpoint | 未实现 | **BLOCKED** |
| E8 | 迁移前自动备份 | 设计有；实现待核 | **PENDING** |

---

## 7. F. 数据安全

| # | 检查项 | 证据 | 结果 |
|---|---|---|---|
| F1 | 单写者 + Mutex | `RoomTransactionRunner` | **PASS（代码）** |
| F2 | BUSY 有限重试 | `BusyRetryPolicy` 3 次 | **PASS（测试）** |
| F3 | 备份归档/校验 | `BackupArchive*` + 测试 | **PASS** |
| F4 | 恢复 journal/回滚 | `RestoreExecutor*` + 测试 | **PASS** |
| F5 | 附件随备份恢复 | 未实现 | **BLOCKED（U07）** |
| F6 | 活跃 WAL 恢复 | 未验证 | **BLOCKED（U09）** |
| F7 | 备份加密 | 未实现（D-016 OPEN） | **PENDING** |
| F8 | 恢复 UI | 未实现 | **BLOCKED（U05）** |
| F9 | 崩溃日志/启动 Recovery | 已实现/设备历史 | **PASS** |

---

## 8. G. 产品范围

| # | 检查项 | 证据 | 结果 |
|---|---|---|---|
| G1 | 五项功能清单确认 | MB-S0-02 | **PENDING（待签）** |
| G2 | P01 执行模块取代 | 未签 | **BLOCKED** |
| G3 | 排除项（AI/学习/云/ERP 等） | G2 范围审计无违规代码 | **PASS（现状）** |
| G4 | 酱汁可用 | 仅表 | **未开始** |
| G5 | 食材可用 | 有 CRUD | **PARTIAL（可用切片）** |
| G6 | 菜谱可用 | 表+算法，无 UI | **未开始** |
| G7 | 库存可用 | 表+算法，无 UI | **未开始** |
| G8 | 扫码可用 | 平台代码，无 UI/设备 | **未开始** |

---

## 9. H. UI/设计系统

| # | 检查项 | 证据 | 结果 |
|---|---|---|---|
| H1 | 设计书 22 节骨架 | `docs/design/MISEBASE-UI-DESIGN-SPEC.md` | **PASS（草稿）** |
| H2 | 视觉方向确认 | 未提供参考/未确认 | **PENDING** |
| H3 | 设计 token | 仅 Color/Theme 2 文件 | **PARTIAL** |
| H4 | 通用组件 | 无 | **未开始** |
| H5 | 页面清单 | 设计书内 | **PASS（草稿）** |
| H6 | 页面级隔离规范 | 已写入设计书/模块审查 | **PASS（规范）** |

---

## 10. I. 治理与回档

| # | 检查项 | 证据 | 结果 |
|---|---|---|---|
| I1 | 新决策登记册 | MB-S0-07 | **PASS（草稿）** |
| I2 | 回档规范 | MB-S0-06 | **PASS（草稿）** |
| I3 | 文件索引 | MB-S0-08 | **PASS（草稿）** |
| I4 | 接管标签 | 未创建 | **PENDING** |
| I5 | 阶段标签制度 | 已定义 | **PASS（规范）** |
| I6 | 远端 CI | 无 remote | **BLOCKED** |
| I7 | 文档修正（README/P01） | 待授权 | **PENDING** |

---

## 11. 阶段 0 关闭条件

阶段 0 只能在以下全部满足后关闭：

1. [ ] 本资料包经项目负责人审查（最多 3 轮）；
2. [ ] MB-D-001（接管基线）签字；
3. [ ] MB-D-002（范围冻结）签字；
4. [ ] MB-D-003（模块集）签字；
5. [ ] MB-D-005（数据库残留）签字；
6. [ ] MB-D-006（验收口径）确认；
7. [ ] MB-D-007（备份一致性优先）确认；
8. [ ] README/P01 修正提交（若授权）；
9. [ ] 接管基线 commit + tag 建立；
10. [ ] 外部审查通过。

**当前状态：0/10 完成签字类条件；技术核验类已完成。**

---

## 12. 复现命令（完整）

```powershell
# 1. 资料完整性
Get-FileHash -Algorithm SHA256 'D:\A-1\CULINARY项目\07-交接包\CULINARY-G1-ARCHIVE-20261104.zip'
git bundle list-heads 'D:\A-1\CULINARY项目\07-交接包\G2-交接资料\02-Git基线与历史\G2-完整仓库.bundle'
git bundle verify 'D:\A-1\CULINARY项目\07-交接包\G2-交接资料\02-Git基线与历史\G2-完整仓库.bundle'

# 2. 接管仓库
git -C D:\MISEBASE status --short --branch
git -C D:\MISEBASE log --oneline -n 5
git -C D:\MISEBASE tag -l
git -C D:\MISEBASE rev-parse --short framework-0.1.0

# 3. 构建与测试
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat test assembleDebug --console=plain --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1

# 4. APK 权限
& "$env:ANDROID_HOME\build-tools\36.1.0\aapt2.exe" dump permissions app\build\outputs\apk\debug\app-debug.apk
```

---

## 13. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立 10 类接管检查项与阶段 0 关闭条件 | 接管工程组 |
