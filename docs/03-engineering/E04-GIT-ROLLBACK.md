# CULINARY 代码回档与版本管理方案

**文档编号**：E04
**版本**：`0.2.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人
**关联文档**：[A07 版本与迁移](../02-architecture/A07-VERSIONING-MIGRATION.md)

---

## 1. 核心原则

1. **代码可回档，数据不可随意回档。** 两者必须分开设计。
2. **每次高风险修改前有回档点。** 不是出事后再找 commit。
3. **合并前必须通过自动化门禁。** 不靠“我记得测过”。
4. **构建产物可追溯。** 每个发布版本有 tag、校验和、mapping。
5. **数据库只前进。** 旧代码不得打开新 schema；回退代码前必须确认数据兼容。

## 2. Git 仓库

### 2.1 初始化

```powershell
git init
git branch -M main
git config core.autocrlf false
git config core.eol lf
```

### 2.2 `.gitignore`（必须包含）

```gitignore
# Gradle / Android
.gradle/
build/
**/build/
local.properties
*.iml
.idea/
captures/
.externalNativeBuild/
.cxx/

# 签名与密钥
*.jks
*.keystore
keystore.properties
secrets.properties

# 构建产物
dist/
*.apk
*.aab
*.ap_
*.dex
mapping.txt
output-metadata.json

# 日志/临时
*.log
*.hprof
tmp/
.cache/

# 系统
.DS_Store
Thumbs.db
```

### 2.3 `.gitattributes`

```gitattributes
* text=auto eol=lf
*.bat text eol=crlf
*.ps1 text eol=crlf
*.png binary
*.webp binary
*.jpg binary
*.zip binary
```

### 2.4 不提交的内容

- 签名 keystore、密码、API key。
- 本地 SDK 路径 `local.properties`。
- 构建产物、APK/AAB、mapping。
- 用户数据、备份文件、数据库文件。
- 大型二进制素材（放 LFS 或独立归档，需 ADR）。

## 3. 分支策略

采用**主干开发 + 短生命周期分支**，适合单人或小团队。

| 分支 | 用途 | 生命周期 |
|---|---|---|
| `main` | 始终可构建、测试通过 | 永久 |
| `feat/<topic>` | 功能 | 几天 |
| `fix/<topic>` | 修复 | 几天 |
| `docs/<topic>` | 文档 | 几天 |
| `chore/<topic>` | 构建/依赖 | 几天 |
| `release/<x.y>` | 需要稳定线时才建 | 发布周期 |

规则：

- 不直接在 `main` 提交；至少走 PR/自查清单。
- 分支从最新 `main` 切出；合并前 rebase。
- 合并后删除分支。
- 一个 PR 一个主题；不混格式化、重构、功能。
- 大重构拆成“无行为变化的重构”+“行为变化”两个 PR。

## 4. 提交规范

详见 [E01](E01-CODE-DIRECTORY-STANDARDS.md#8-git-提交规范)。

摘要：

```text
feat(inventory): reserve stock on plan confirmation
fix(backup): reject manifest with mismatched schema
docs(adr): record native Android decision
```

- 一个提交一个逻辑变更。
- 提交信息写“为什么”，不只写“改了什么”。
- 数据库迁移、Entity、DAO、测试必须在同一提交。
- 不提交格式化全文件导致的巨大 diff；单独 `style` 提交。

## 5. 标签与回档点

### 5.1 发布标签

```powershell
git tag -a v0.1.0 -m "CULINARY 0.1.0"
git push origin v0.1.0
```

- 标签只打在 `main` 上。
- 标签对应可构建的 commit。
- 发布标签不可移动；发现错误发新 patch，不重打旧标签。

### 5.2 检查点标签

高风险修改前创建检查点：

```powershell
# 数据库迁移前
git tag -a checkpoint/db-v3-20261104 -m "Before schema v4 migration"

# 大重构前
git tag -a checkpoint/refactor-plan-20261110 -m "Before plan module refactor"
```

- `checkpoint/*` 不是发布，只是回档坐标。
- 保留至少到该变更稳定运行一个版本。
- 推送标签到远端，避免本地丢失。

### 5.3 回档点清单

| 时机 | 动作 |
|---|---|
| 数据库 schema 变更前 | tag + 自动备份当前 DB + 导出 schema |
| 备份格式变更前 | tag + 兼容性测试 |
| 大模块重构前 | tag + 全量测试基线 |
| 依赖大版本升级前 | tag + 构建产物 |
| 发布前 | tag + release build + mapping |
| 紧急修复前 | 从故障 tag 切 hotfix 分支 |

## 6. CI 门禁

### 6.1 PR 必须通过

| 检查 | 命令/说明 |
|---|---|
| 代码格式 | `./gradlew ktlintCheck` |
| 静态分析 | `./gradlew detekt` |
| Android Lint | `./gradlew lintDebug` |
| 单元测试 | `./gradlew testDebugUnitTest` |
| 数据库测试 | `./gradlew testDebugUnitTest`（Robolectric）或 instrumented |
| 迁移测试 | 独立任务，覆盖 v1→当前 |
| 构建 | `./gradlew assembleDebug` |
| 依赖规则 | 自定义任务/依赖分析 |
| 文档 | 影响架构的 PR 必须带 ADR/文档更新 |

### 6.2 合并后

- `main` 上跑一次完整构建。
- 每晚跑 instrumented 测试（模拟器矩阵）。
- 失败立即修复；不允许长期红。

### 6.3 发布门禁

- 全部测试通过。
- Release 构建成功。
- 手工冒烟（安装、启动、核心流程、离线、备份恢复）。
- 版本号/CHANGELOG 更新。
- 签名与产物校验和归档。

### 6.4 签名策略（G5 补充）

| 规则 | 要求 |
|---|---|
| Debug/Release 隔离 | Debug 使用 Android debug keystore；Release 使用独立 release keystore；不得共用 |
| Keystore 位置 | 仓库外（如 `D:\CULINARY-secrets\`）；`.gitignore` 排除 `*.jks`/`*.keystore`/`keystore.properties` |
| 密码 | 不写入源码、`gradle.properties`、CI 日志；本地通过环境变量/受控属性文件读取 |
| 创建时机 | 框架阶段不创建；正式发布前创建，G5 为**发布门禁**，不阻塞框架 |
| 备份 | 至少两份独立加密备份，密码与文件分开保管 |
| 验证 | 发布前验证密钥别名、密码、证书指纹、APK 签名 |
| 发布检查 | 签名验证纳入第 6.3 节发布门禁与第 8 节发布流程 |

> 当前不创建、不宣称“签名准备完成”；只记录规则与门禁。

## 7. 构建产物留档

### 7.1 产物

| 文件 | 说明 |
|---|---|
| `culinary-<version>-release.apk` | 发布 APK |
| `culinary-<version>-release.aab` | 如需要 |
| `mapping.txt` | R8 混淆映射，必须保留 |
| `build-info.json` | commit、tag、构建时间、JDK/Gradle/AGP 版本 |
| `SHA256SUMS` | 产物校验和 |
| `CHANGELOG.md` | 该版本变更 |

### 7.2 归档

- `dist/` 不提交 Git；放本地/私有云/移动硬盘。
- 至少保留最近 5 个发布版本与对应 schema。
- 每个产物记录 SHA-256；安装前可校验。
- 签名 keystore 离线备份两份，密码分开保管；丢失将无法覆盖升级。

## 8. 发布流程

```text
1. 确认 main 绿
2. 更新 versionName/versionCode
3. 更新 CHANGELOG
4. 创建 release 分支（可选）
5. 构建 release APK/AAB
6. 运行发布冒烟清单
7. 打 tag v<version>
8. 归档产物 + mapping + SHA256
9. 在设备上安装验证
10. 合并回 main（如用 release 分支）
```

### 8.1 发布冒烟清单

- [ ] 冷启动正常。
- [ ] 数据库打开/迁移正常。
- [ ] 创建食材/菜谱/商品。
- [ ] 计划确认 + 缺口。
- [ ] 收货入库 + 库存流水。
- [ ] 扫码/手工输入。
- [ ] 飞行模式下核心流程。
- [ ] 手动备份 + 恢复。
- [ ] 图片可选状态。
- [ ] 错误提示可读。

## 9. 回滚流程

### 9.1 只回滚代码（数据兼容）

适用：新版本没有数据库 schema 变更，或旧代码 schema ≥ 当前 DB。

```powershell
git revert <bad-commit> --no-edit
# 或从上一个 tag 构建
git checkout v0.1.0
./gradlew assembleRelease
```

- 保留问题版本 APK 与日志。
- 回滚后验证核心流程。
- 记录事故与根因。

### 9.2 代码回滚 + 数据库已升级

**不能直接安装旧 APK 打开新 DB。**

步骤：

1. 停止使用，保留当前数据。
2. 找到升级前自动备份（`PRE_MIGRATION`）。
3. 先对当前数据再做一次手动备份（留证据）。
4. 恢复升级前备份（会丢失升级后数据）。
5. 安装旧 APK。
6. 记录数据损失范围。

### 9.3 数据库损坏

- 不删除；进入 Recovery。
- 从最近验证备份恢复。
- 导出损坏文件与日志。
- 若备份也损坏，尝试导出可用表；不承诺完整恢复。

### 9.4 紧急修复

```powershell
git checkout -b hotfix/<issue> v<last-good>
# 修复 + 测试
git tag -a v<last-good>-hotfix.1
```

- 只修问题，不夹带功能。
- 修复后合并回 `main`。
- 记录事故时间线。

## 10. 数据库迁移与代码版本的兼容

| 场景 | 允许 | 处理 |
|---|---|---|
| 新 App + 旧 DB | 是 | 自动迁移，前备份 |
| 新 App + 新 DB | 是 | 正常 |
| 旧 App + 旧 DB | 是 | 正常 |
| 旧 App + 新 DB | **否** | 拒绝打开，提示升级/恢复 |
| 新 App + 未来 DB | 否 | 拒绝，提示安装更新版本 |
| 备份 schema > App | 否 | 拒绝恢复 |
| 备份 schema ≤ App | 是 | 恢复后迁移 |

**禁止** 通过 `fallbackToDestructiveMigration` 或清空数据库让旧代码启动。

## 11. 代码评审清单

- [ ] 变更是否有明确范围与回滚点？
- [ ] 是否修改 schema？迁移与测试是否齐全？
- [ ] 是否修改备份格式？兼容性测试是否齐全？
- [ ] 是否新增跨层依赖？
- [ ] 是否所有写操作有事务？
- [ ] 是否所有库存变化有流水？
- [ ] 是否新增用户可见错误？错误码/文案/恢复动作是否齐全？
- [ ] 是否新增图片路径？缺失时是否降级？
- [ ] 是否更新文档/ADR/CHANGELOG？
- [ ] 是否通过 CI？

## 12. 事故记录模板

```markdown
## 事故：<一句话>

- 时间：
- 版本/commit：
- 影响：
- 发现方式：
- 直接原因：
- 根本原因：
- 处理步骤：
- 数据是否受影响：
- 回滚/恢复：
- 后续改进：
- 责任人/日期：
```

事故记录放 `docs/incidents/`，不删改；后续事故可引用。

## 13. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 初稿；Git、标签、CI、产物、回滚与事故模板 | 架构组 |
| 0.2.0 | 2026-11-04 | 增加签名策略：Debug/Release 隔离、发布前创建、双备份、指纹验证 | 架构组 |
