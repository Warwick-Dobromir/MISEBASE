# MB-S1-02 模块调整：15 → 13

**文档编号**：MB-S1-02
**版本**：`0.1.0`
**状态**：`Review`（已实施并构建通过）
**日期**：2026-11-04
**依据**：MB-D-003（ACCEPTED）、MB-D-002（ACCEPTED）、A03A §13
**结论**：模块集实际为 **13**；删除 `feature:planning`、`feature:procurement`；`feature:products`
缩减为商品/包装/条码/食材映射；未新建 `feature:scan`；构建与回档可用。

---

## 1. 调整前（G2 / 15 模块）

```text
:app

:core:common
:core:domain
:core:database
:core:data
:core:designsystem
:core:platform

:feature:ingredients
:feature:sauces
:feature:recipes
:feature:products
:feature:inventory
:feature:planning
:feature:procurement
:feature:settings
```

- 模块数：**15**（app + 6 core + 8 feature）；
- 声明依赖边：**43**；
- 拓扑序（依赖在前）：`common → domain → database → designsystem → platform → data →
  ingredients → sauces → recipes → products → inventory → planning → procurement → settings → app`；
- 历史依据：`docs/02-architecture/module-dependency-check.txt`（G2 产物，保留不改）。

## 2. 调整后（MISEBASE / 13 模块）

```text
:app

:core:common
:core:domain
:core:database
:core:data
:core:designsystem
:core:platform

:feature:ingredients
:feature:sauces
:feature:recipes
:feature:products      # 商品 / 包装 / 条码 / 食材映射
:feature:inventory     # 含需求计算、缺料/采购清单
:feature:settings
```

- 模块数：**13**（app + 6 core + 6 feature）；
- 声明依赖边：**35**（43 − 2 条 `app -> planning/procurement` − 6 条两个模块的 3 条内部边）；
- 源码 import 边：**155**（扫描 `src/main`）；
- 拓扑序（实测，依赖在前）：

```text
 1. core:common
 2. core:database
 3. core:designsystem
 4. core:domain
 5. core:data
 6. core:platform
 7. feature:ingredients
 8. feature:inventory
 9. feature:products
10. feature:recipes
11. feature:sauces
12. feature:settings
13. app
```

> 同一层内按字母序；`core:database`/`designsystem` 先于 `domain` 只是排序结果，不存在反向边。

## 3. 允许依赖（13 模块）

```text
app -> feature:{ingredients,sauces,recipes,products,inventory,settings}
app -> core:{data,platform,designsystem,common}

feature:* -> core:{domain,designsystem,common}

core:domain      -> core:common
core:database    -> core:common
core:data        -> core:{domain,database,common}
core:designsystem-> core:common
core:platform    -> core:{domain,common}
```

说明：`app -> core:common` 是本次检查显式允许的边。`core:domain` 以 `api` 暴露 `core:common`，
`core:data` 以 `api` 暴露 `core:domain`，因此 `app` 源码可直接使用 `Clock`/`Outcome` 等基础契约；
它不是新增实现依赖，也不形成环。

## 4. 禁止依赖（仍生效）

```text
feature -> feature                          （含 UI/ViewModel）
feature -> core:data / core:database        （实现细节）
core:database -> core:domain / core:data    （反向）
core:domain -> Android / Room / Compose / Hilt / data / database / platform
core:data -> Android SAF / Uri / Intent / ActivityResult
任意模块 -> app
:core:model、feature:scan 不得出现
```

## 5. 实际变更

| # | 文件/目录 | 变更 | 说明 |
|---:|---|---|---|
| 1 | `settings.gradle.kts` | 15 → 13 个 `include` | 删除 planning/procurement；注释改为 MB-D-003 |
| 2 | `feature/planning/` | 删除 | 8 行占位 + build 文件；无测试、无业务代码 |
| 3 | `feature/procurement/` | 删除 | 同上 |
| 4 | `app/build.gradle.kts` | 删除 2 条 `implementation(project(...))` | 组合根不再装配 |
| 5 | `CulinaryDestination.kt` | 删除 `PLANNING`/`PROCUREMENT` 枚举 | 导航目的地 9 → 7（HOME + 6 入口） |
| 6 | `strings.xml` | 删除 2 个目的地字符串 | 无残留引用 |
| 7 | `CulinaryDestinationTest.kt` | 断言 8 → 6 个业务入口 | 测试数不变（仍 3 例） |
| 8 | `HomeScreen.kt` | 仅注释 | 入口顺序未变，未重排导航 |
| 9 | `FeatureProductsModule.kt` | 仅 KDoc | 范围缩减为商品/包装/条码/食材映射；不含供应商/扫码 UI |
| 10 | `config/module-dependencies.json` | 新增 | 13 模块与依赖规则配置 |
| 11 | `scripts/check-module-deps.ps1` | 重写 | 配置化 + import 扫描 + 拓扑 + 自检 |

**未做**：

- 未新建 `feature:scan`；
- 未实现扫码 UI、功能页面、视觉定稿、导航重排；
- 未修改任何 `core` 业务代码、Schema/Entity/DAO/迁移/数据库文件；
- 未删除任何休眠表。

## 6. 分步可构建证据

| 步骤 | 命令 | 结果 |
|---|---|---|
| 调整前基线 | `.\gradlew.bat test assembleDebug` | BUILD SUCCESSFUL（缓存命中；基线 197 例见阶段报告） |
| 改 settings/app/删除模块/改导航后 | `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` | **BUILD SUCCESSFUL**，53s |
| 最终全量 | `.\gradlew.bat test assembleDebug --console=plain --no-daemon` | 见 [MB-S1-01](MB-S1-01-EXECUTION-REPORT.md) |

每一步都在提交前验证；失败则回退该步，不跨步累积。

## 7. 回档设计

| 层 | 方式 | 说明 |
|---|---|---|
| Git 提交 | 本调整单独提交 | 可用 `git revert <commit>` 或 `git checkout <commit>^` |
| 检查点 | `checkpoint/misebase-stage1-*` | 见阶段报告的实际哈希 |
| 模块恢复 | 恢复 `settings.gradle.kts` + `app/build.gradle.kts` + 两个模块目录 | 目录内容仅占位，恢复成本低 |
| 导航恢复 | 恢复枚举/字符串/测试 | 与模块恢复同一提交 |
| 数据库 | 不涉及 | 本调整不改 schema/数据 |

**回档验证**：`git revert` 后 `settings` 应回到 15 模块，`check-module-deps` 会因配置期望 13 而失败；
因此回档需同时回退 `config/module-dependencies.json` 与脚本（同一提交），这是预期行为。

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 记录 15→13 前后清单、依赖图、实际变更、构建与回档 | 接管工程组 |
