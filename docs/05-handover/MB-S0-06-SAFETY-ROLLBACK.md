# MISEBASE 代码安全与回档规范

**文档编号**：MB-S0-06
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**依据**：总任务书第 21–23、27 节；G2 E04（Git 回档）、E02（并发事务）、A07（迁移）、E05（备份恢复）
**适用范围**：MISEBASE 全部阶段；所有修改代码、数据库、备份格式、依赖的任务

---

## 1. 第一原则

1. **代码可回档，数据不可随意回档。** 两者分开设计。
2. **每次高风险修改前有回档点。** 不是出事后再找 commit。
3. **G2 原始基线永久保留。** `framework-0.1.0` / `2b523de` 及其标签不得移动、覆盖、删除。
4. **数据库只前进。** 旧代码不得打开新 schema；回退代码前必须确认数据兼容。
5. **没有验证的能力，不写成已完成。**
6. **最小变更。** 不为“顺手优化”扩大任务范围。

---

## 2. 基线、标签与保护规则

### 2.1 必须永久保留的基线

| 基线 | 提交 | 标签 | 规则 |
|---|---|---|---|
| G1 权威基线 | `76d6272` | `checkpoint/g1-to-g2-handoff-20261104` | 只读；不移动 |
| G2 最终代码基线 | `2b523de` | `framework-0.1.0`、`baseline/g2-stage9-20261104` | 只读；不移动 |
| G2 收档 | `705e0ca` | 无独立标签（在历史中） | 只读 |
| G2 交接资料 | `850ea63` | 无独立标签（在历史中） | 只读 |
| 9 个阶段基线 | `11b8862`…`2b523de` | `baseline/g2-stage1..9-20261104` | 只读；不移动 |
| G1/G2 检查点 | 见 `git tag -l` | `checkpoint/*` | 只读；不移动 |

### 2.2 MISEBASE 新增标签

| 标签 | 时点 | 说明 |
|---|---|---|
| `misebase/takeover-20261104` | 阶段 0 签字后 | 接管基线；建议指向 `705e0ca` 或负责人指定的提交 |
| `baseline/mb-stage<N>-YYYYMMDD` | 每阶段验收后 | 阶段基线 |
| `checkpoint/db-v<N>-YYYYMMDD` | 每次 schema 变更前 | 数据库回档点 |
| `checkpoint/refactor-<name>-YYYYMMDD` | 大重构前 | 代码回档点 |
| `checkpoint/backup-format-v<N>` | 备份格式变更前 | 兼容性回档点 |
| `release/v<X.Y.Z>` | 正式发布 | 不可移动；错误发新 patch |

### 2.3 保护规则

- 禁止 `git push --force` 到 `main`/`misebase/main`；
- 禁止 `git tag -f` 移动已发布/已验收标签；
- 禁止删除 `framework-0.1.0`、`baseline/g2-*`、`checkpoint/g1-*`、`checkpoint/g2-*`；
- 禁止改写 G2 历史（rebase/filter-branch/commit --amend 已推送提交）；
- 远端仓库（如启用）必须开启分支保护：`main` 需 PR + 检查通过。

---

## 3. 任务范围模板（每个任务必须填写）

```markdown
## 任务：<一句话>

- 目标：
- 允许修改的模块：
- 允许修改的目录：
- 允许修改的文件：
- 禁止修改的模块/文件：
- 是否涉及共享组件：是/否（影响范围：）
- 是否涉及 Domain：是/否
- 是否涉及数据库：是/否（迁移编号：）
- 是否涉及备份格式：是/否
- 测试要求：
- 验收要求：
- 回档点（tag/commit）：
- 回滚方案：
```

未填写范围的任务，不进入开发。

---

## 4. Git 工作流

### 4.1 分支

| 分支 | 用途 | 生命周期 |
|---|---|---|
| `misebase/main` | 始终可构建、测试通过 | 永久 |
| `feat/<topic>` | 功能 | 几天 |
| `fix/<topic>` | 修复 | 几天 |
| `docs/<topic>` | 文档 | 几天 |
| `chore/<topic>` | 构建/依赖 | 几天 |
| `release/<x.y>` | 稳定线（需要时） | 发布周期 |

规则：

- 不直接在 `misebase/main` 提交；
- 分支从最新 `misebase/main` 切出；
- 合并前 rebase/整理提交；
- 一个 PR 一个主题；不混格式化、重构、功能；
- 大重构拆成「无行为变化」+「行为变化」两个 PR。

### 4.2 提交信息

```text
feat(inventory): reserve stock on plan confirmation
fix(backup): reject manifest with mismatched schema
docs(adr): record scope freeze decision
```

- 一个提交一个逻辑变更；
- 写“为什么”，不只写“改了什么”；
- 数据库迁移、Entity、DAO、测试在同一提交；
- 不提交格式化全文件导致的巨大 diff。

### 4.3 每个阶段完成后的固定动作

1. 测试通过；
2. 依赖检查通过；
3. 工作区检查（`git status` 干净）；
4. 提交；
5. 打阶段标签；
6. 保存阶段报告；
7. 更新文档索引。

---

## 5. 构建与测试门禁

### 5.1 本地/CI 必跑

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat ktlintCheck detekt lintDebug test assembleDebug --no-daemon
.\gradlew.bat :app:assembleRelease --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1
```

### 5.2 涉及数据库时追加

```powershell
.\gradlew.bat :core:database:connectedDebugAndroidTest
```

### 5.3 发布前追加

- 真机安装/冷启动；
- 飞行模式核心流程；
- 备份/恢复演练；
- APK 权限审计（无 `INTERNET`）；
- 签名校验；
- `mapping.txt` / SHA-256 归档。

### 5.4 测试报告口径

- 区分「实际执行」「未执行」「无法执行」；
- 不把历史结果写成本次结果；
- 不把“代码存在”写成“已验证”；
- 测试总数以当前基线实测为准（当前 197）。

---

## 6. 数据库安全规则

### 6.1 禁止事项

- 禁止 `fallbackToDestructiveMigration()`；
- 禁止降级删除重建；
- 禁止未经审查直接改 schema；
- 禁止直接改库存数字（必须走流水）；
- 禁止在迁移中做网络/图片解码/UI；
- 禁止删除休眠表而不走迁移/备份/测试。

### 6.2 每次 schema 变更必须

1. 新增 `Migration(n, n+1)`，一个迁移一个类；
2. 更新 `@Database(version=)`，`exportSchema = true`；
3. 提交新的 schema JSON；
4. 写 MigrationTest（v1→当前、相邻版本、空库、真实旧数据）；
5. 迁移前自动备份 + 校验；
6. 迁移后 `foreign_key_check` + `quick_check`；
7. 已发布配方版本内容不得被迁移改写；
8. 旧快照仍可读；
9. 降级路径明确（拒绝 + 指引）。

### 6.3 当前状态

- schema v1，31 表；`CulinaryMigrations.ALL = emptyArray()`；
- 无 destructive migration；
- 迁移测试 2 例，历史通过，本次未重跑；
- **任何新增表/列都将触发 v2 迁移**，不得直接改 v1 JSON。

---

## 7. 锁、事务与并发规则

### 7.1 运行模型

- 单进程、单用户；
- 一个 `CulinaryDatabase` 单例；
- 应用级单写者：`Mutex` + 单线程事务执行器；
- WAL：读写并发，写写串行；
- 备份/恢复与写事务共用同一把写锁（`WriteGate`），不新增第二把锁。

### 7.2 锁顺序

```text
writeMutex -> SQLite 事务 -> 行更新
```

禁止反向；禁止在事务内获取文件锁/图片锁；禁止嵌套 `write`（Kotlin `Mutex` 不可重入，会死锁）。

### 7.3 事务边界

- 事务边界在 UseCase，不在 Repository；
- 一个业务操作一个写事务；
- 事务内只做 SQL + 纯领域计算；
- 禁止网络、图片解码、大文件复制、用户交互、长循环；
- 批量写入 500–1000 行/批；
- 写事务目标 ≤ 500 ms，硬上限 5 s；外层 `withTimeout(10_000)`。

### 7.4 重试

- 仅 `SQLITE_BUSY` / `SQLITE_LOCKED` / `BUSY_SNAPSHOT` 可重试；
- 最多 3 次，50/150/400 ms + 抖动；
- 重试包住整个事务，重试前重新读取；
- 约束错误/损坏/磁盘满不重试；
- 禁止无限重试、禁止靠加大超时掩盖死锁。

### 7.5 库存并发

- 条件 `UPDATE` 带 `quantity_on_hand + delta >= 0`、`reserved <= on_hand`、`revision = expected`；
- `rowsAffected == 1` 才写流水；
- 流水与余额同一事务；
- `idempotency_key` 全局唯一；
- 不在事务外“先查再改”。

---

## 8. 备份与恢复安全

### 8.1 备份前

1. 获取写锁（阻止新写）；
2. `PRAGMA wal_checkpoint(TRUNCATE)`；
3. 复制 DB 到临时文件；
4. SHA-256 + `quick_check`；
5. 释放写锁；
6. 打包图片/manifest；
7. 整包校验后原子 rename。

### 8.2 当前缺口（必须在阶段 1 处理）

G2 基线**没有在线快照/checkpoint**（G2-U06），因此：

- 备份文件的结构/哈希校验通过，不代表 WAL 下内容一致；
- 恢复测试使用 `TRUNCATE` journal mode，生产 WAL 场景未验证（G2-U09）；
- 附件未随备份恢复（G2-U07）。

**行动**：在阶段 1 增加「备份前 checkpoint + 写屏障 + WAL 场景恢复」测试；在此之前，备份功能不得对外宣称“一致备份”。

### 8.3 恢复

- 停止新写并保持写锁；
- 关闭 Room；
- 当前 DB/图片移到 `pre_restore_<timestamp>/`；
- 装库、`quick_check`、`foreign_key_check`；
- 失败回滚文件，不删除现场；
- journal 支持中断恢复；
- 恢复 UI 未实现前，只能通过受控入口执行。

### 8.4 禁止

- 恢复失败后删除数据库“重来”；
- 手动删 `-wal`/`-shm`；
- 用旧 APK 打开新 schema；
- 无备份直接迁移。

---

## 9. 回滚运行手册

### 9.1 只回滚代码（数据兼容）

适用：没有 schema 变更，或旧代码 schema ≥ 当前 DB。

```powershell
git revert <bad-commit> --no-edit
# 或
git checkout <last-good-tag>
.\gradlew.bat :app:assembleRelease
```

- 保留问题版本 APK/日志；
- 回滚后跑核心流程；
- 记录事故与根因。

### 9.2 代码回滚 + 数据库已升级

**不能直接安装旧 APK 打开新 DB。**

1. 停止使用，保留当前数据；
2. 找到升级前自动备份（`PRE_MIGRATION`）；
3. 先对当前数据再做一次手动备份；
4. 恢复升级前备份（会丢失升级后数据）；
5. 安装旧 APK；
6. 记录数据损失范围。

### 9.3 数据库损坏

- 不删除；移动到 `databases/quarantine/`；
- 进入 Recovery；
- 从最近验证备份恢复；
- 导出损坏文件/日志；
- 无备份时提供导出，不承诺完整恢复。

### 9.4 发布版本错误

- 不回滚/移动已发布 tag；
- 从 last-good 切 hotfix 分支；
- 修问题、测试、发新 patch tag；
- 保留问题版本产物。

---

## 10. 构建产物归档

| 文件 | 要求 |
|---|---|
| APK/AAB | 按版本命名，仓库外归档 |
| `mapping.txt` | 必须保留 |
| `build-info.json` | commit、tag、构建时间、JDK/Gradle/AGP |
| `SHA256SUMS` | 所有产物 |
| `CHANGELOG.md` | 该版本变更 |

- `dist/` 不提交 Git；
- 至少保留最近 5 个发布版本与对应 schema；
- Release keystore 仓库外双备份，密码分开保管；
- 当前 Release APK 为 unsigned，签名是阶段 6 门禁。

---

## 11. 事故记录模板

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

放 `docs/incidents/`，不删改；后续事故可引用。

---

## 12. 当前安全缺口台账

| ID | 缺口 | 等级 | 阶段 |
|---|---|---|---|
| G2-U01 | 远端 CI 未实跑 | 中 | 1 |
| G2-U02 | 扫码设备验证 | 高 | 5 |
| G2-U03 | SAF 设备验证 | 高 | 5 |
| G2-U04 | Compose/仪器化 E2E | 高 | 3+ |
| G2-U05 | 恢复 UI 接线 | 高 | 4 |
| G2-U06 | 在线快照/checkpoint | **高** | 1 |
| G2-U07 | 附件随备份恢复 | 高 | 4 |
| G2-U08 | 备份加密 | 中 | 发布前 |
| G2-U09 | 活跃 WAL 恢复验证 | **高** | 1 |
| G2-U10 | 需求来源建模 | 低 | 2 |
| G2-U11 | 商品映射 | 中 | 5 |
| G2-U12 | 快照元数据 | 低 | 2 |
| G2-U13 | APK/mapping 入库归档 | 中 | 6 |

> 这些缺口不因阶段 0 文档完成而关闭；每项必须在对应阶段有测试/证据后才能标 PASS。

---

## 13. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立基线/标签、范围模板、门禁、数据库/锁/备份/回滚规范 | 接管工程组 |
