# MB-BK-002 检查点回执

**文档编号**：MB-BK-002-CHECKPOINT
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**用途**：记录 Schema 版本阻断修复检查点的标签对象哈希、解析提交与旧标签完整性

---

## 1. 新检查点

| 项 | 值 |
|---|---|
| 标签名 | `checkpoint/misebase-backup-consistency-v2-20261104` |
| 类型 | annotated tag |
| 对象哈希 | `fba896452614495d49fe1328adb6d19338f0968e` |
| 解析提交 | `a69b5d4ef22cf8ada5f03578020ef70e1e40979d` |
| 解析提交信息 | `docs(startup): record mb-bk-002 schema version fix` |
| 标签信息 | `MISEBASE backup consistency v2 checkpoint: startup schema version fix and test statistics correction` |
| 打标签时间 | 2026-11-04 23:52:44 +0800 |

## 2. 修复提交

| 提交 | 完整哈希 | 内容 |
|---|---|---|
| 修复 | `30d57bbc630b92431903dc084631d6116851986b` | `fix(startup): align expected schema version with database v2` |
| 报告 | `a69b5d4ef22cf8ada5f03578020ef70e1e40979d` | 本回执之外的修复报告与索引；新标签解析提交 |

## 3. 旧标签与基线完整性

| 标签 | 对象哈希 | 解析提交 | 是否移动 |
|---|---|---|---|
| `checkpoint/misebase-backup-consistency-20261104` | `4a271e8ac0081b8ff21b1966756d0432a7767671` | `8787c059a4a888f16ad8837ef8722316d762c257` | **未移动** |
| `checkpoint/misebase-mb-d-007-20261104` | `b3a429f9d2698c245f8106a421c1329686a61d61` | `3c3db1eb7c835b42c671b2094d8ee4249d599c6b` | 未移动 |
| `framework-0.1.0` | — | `2b523de4b87e37a2eb08b4c7c851549350ad811a` | 未移动 |
| `checkpoint/misebase-stage4-start-20261104` | — | `c5ea6feeef0a843b4f84590c7ea7f72b0e6693f2` | 未移动 |
| 其他既有 `checkpoint/*` / `baseline/*` | — | — | 未移动 |

## 4. 两个备份一致性检查点的关系

```text
…backup-consistency-20261104 (8787c05)
  = MB-BK-001 代码/报告坐标（旧检查点，保留）
        │
        ├─ f8ede2c  报告统计口径更正（344 → 374 唯一用例）
        ├─ 30d57bb  StartupCoordinator 版本修复 + 回归测试
        └─ a69b5d4  MB-BK-002 报告/索引
             ↑
…backup-consistency-v2-20261104 (a69b5d4)
  = 审查后修复坐标（新检查点，后继而非替换）
```

- 旧标签保持指向 `8787c05`，用于回档与审查追溯；
- 新标签包含版本修复、回归测试、测试统计更正与 MB-BK-002 报告；
- 两个标签都不构成 Stage 4 启动授权。

## 5. 检查点内容

| 项 | 结果 |
|---|---|
| `EXPECTED_SCHEMA_VERSION` | `2`，与 `CulinaryDatabase @Database(version = 2)` 对齐 |
| `StartupCoordinatorTest` | 5 例 PASS（全新 / 持久化 v2 / v1→v2 / v3 拒绝 / 损坏） |
| 全量 JVM | 612 次执行 / 376 唯一用例 / 0 失败 |
| Debug APK | 构建成功 |
| ktlint / detekt | PASS |
| Schema/Entity/迁移/备份/UI/模块集 | 未改 |

## 6. 工作树

`git status --porcelain` 为空（本回执提交后复核）。

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录新检查点对象/解析提交、修复提交、旧标签未移动与两检查点关系 | 接管工程组 |
