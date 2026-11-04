# MB-S1-03 模块依赖检查（配置化 + import 扫描 + 拓扑）

**文档编号**：MB-S1-03
**版本**：`0.1.0`
**状态**：`Review`（已实施；可复现）
**日期**：2026-11-04
**依据**：MB-D-003（ACCEPTED）、MB-S1-001 任务书 §4.4、S0-R-03
**产物**：`config/module-dependencies.json`、`scripts/check-module-deps.ps1`、本文件

---

## 1. 目标与实现

| 要求 | 实现 |
|---|---|
| 模块集配置化 | `config/module-dependencies.json` 声明 13 模块、包名、允许边、禁止规则 |
| 不硬编码模块数 | 脚本从配置读取 `expectedModuleCount` 与 `modules`，再与 `settings.gradle.kts` 比对 |
| 检查声明依赖 | 解析每个 `build.gradle.kts` 的 `project(":...")` |
| 检查源码 import | 扫描 `app`、`core`、`feature` 的 `src/main/**/*.kt`，用最长包前缀映射到模块 |
| 分层禁忌 | feature→feature、feature→data/database、database→domain/data、domain→Android/Room/Compose/Hilt、data→SAF/Uri、Compose→DAO/SQLite/Data、任意→app |
| 循环依赖 | 对“声明边 + import 边”并集做 Kahn 拓扑排序；有环即失败 |
| 可读输出 | 模块数、声明边、import 边、拓扑序、违规 `文件:行号` |
| CI 可跑 | 非 0 退出码；`-SelfTest` 在临时目录注入违规 |
| 休眠表 | 独立脚本 `check-dormant-tables.ps1`（见 MB-S1-04） |

## 2. 配置

`config/module-dependencies.json` 的关键字段：

```json
{
  "expectedModuleCount": 13,
  "modules": [
    { "id": "app", "dir": "app", "package": "com.culinary", "type": "app" },
    { "id": "core:domain", "dir": "core/domain", "package": "com.culinary.core.domain", "type": "core" },
    { "id": "feature:inventory", "dir": "feature/inventory", "package": "com.culinary.feature.inventory", "type": "feature" }
  ],
  "allowedDependencies": { "app": ["core:data", "core:common", "..."], "feature:inventory": ["core:domain", "..."] },
  "rules": { "featureToFeature": true, "featureToCoreImpl": ["core:data", "core:database"], "...": "..." }
}
```

规则以配置为准；改模块集或允许边时只改 JSON，不改脚本逻辑。

## 3. 实测结果（2026-11-04）

```text
MISEBASE 13-MODULE DEPENDENCY CHECK
Decision: MB-D-003; stage: MB-S1-001
Modules: 13 (expected 13)
Declared dependency edges: 35
Source import edges: 155
Topological order (dependencies first):
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
Result: PASS (DAG, no violations)

PASS: module set, declared edges, source imports and topology match MB-D-003.
```

结果文件（生成物）：[module-dependency-check-13.txt](../02-architecture/module-dependency-check-13.txt)。

## 4. 违规注入测试

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1 -SelfTest
```

实测：

```text
MISEBASE dependency checker self-test (violation injection)
  clean tree: modules=13, declared=36, imports=0, violations=0
  injected imports: violations=8, codes=V-DB-REVERSE,V-DOMAIN-ANDROID,V-FEATURE-FEATURE,V-FEATURE-IMPL,V-IMPORT-UNDECLARED,V-UI-DAO
  injected module count: detected V-MODULE-COUNT
  injected cycle: detected V-CYCLE

SELF-TEST PASS: all injected violations were detected.
```

注入内容：

| 注入 | 位置 | 期望码 | 实测 |
|---|---|---|---|
| feature 直接 import 另一个 feature 的 Route | `feature/ingredients` | `V-FEATURE-FEATURE` | 捕获 |
| feature import `core.database.dao` | `feature/inventory` | `V-FEATURE-IMPL` | 捕获 |
| database 反向 import domain | `core/database` | `V-DB-REVERSE` | 捕获 |
| domain import `androidx.room` | `core/domain` | `V-DOMAIN-ANDROID` | 捕获 |
| Compose 文件 import `androidx.room.Query` | `feature/recipes` | `V-UI-DAO` | 捕获 |
| 删除一个 `include` | `settings.gradle.kts` | `V-MODULE-COUNT` | 捕获 |
| `core:common` 反向依赖 `feature:ingredients` | 临时 build 文件 | `V-CYCLE` | 捕获 |

> 自检在系统临时目录创建完整的 13 模块树，注入后运行真实扫描，最后删除临时目录；
> 不修改工作区。干净树应为 0 违规，这一点也被断言。

## 5. 规则与退出码

| 码 | 含义 |
|---|---|
| `V-MODULE-COUNT` / `V-MODULE-MISSING` / `V-MODULE-UNKNOWN` / `V-MODULE-FORBIDDEN` | 模块集不一致或出现禁止模块 |
| `V-DECLARED-EDGE` | build.gradle 声明了不允许的边 |
| `V-IMPORT-UNDECLARED` | 源码 import 了未允许的模块 |
| `V-FEATURE-FEATURE` / `V-FEATURE-IMPL` | feature 越界 |
| `V-DB-REVERSE` | database 反向依赖 |
| `V-DOMAIN-ANDROID` | domain 依赖 Android/Room/Compose/Hilt/实现 |
| `V-DATA-SAF` | data 泄漏 SAF/Uri |
| `V-UI-DAO` | Compose 页面直接依赖 DAO/SQLite/Data |
| `V-APP-DEPENDENCY` | 任意模块依赖 app |
| `V-CYCLE` | 拓扑排序发现环 |
| `V-MISSING-BUILD` | 缺少 build 文件 |

退出码：`0` PASS；`1` 违规；`2` 配置错误。

## 6. 已知限制

1. **词法扫描**：只解析 `import` 行，不做类型解析/字节码分析；动态反射、同包引用、
   全限定名内联调用不在覆盖范围。同包引用由 Gradle 模块依赖兜底。
2. **包名映射**：依赖约定的 `com.culinary.*` 包结构；新增模块必须同时在配置中登记
   `package`，否则不会被识别。
3. **app → core:common**：因 `core:domain`/`core:data` 的 `api` 暴露而显式允许；
   这是基础契约，不视为实现泄漏。
4. **测试源码不扫描**：`src/test`/`androidTest` 可依赖实现；生产边界只约束 `src/main`。
5. **Gradle 任务未新增**：本阶段只提供 PowerShell 入口，避免超出“模块集与 settings”授权；
   后续可在 build-logic 增加 `checkModuleDeps` 任务，复用同一配置。

## 7. 复现

```powershell
# 正式检查
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1

# 生成报告（可选）
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1 `
  -ReportPath docs\02-architecture\module-dependency-check-13.txt

# 违规注入
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1 -SelfTest
```

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 重写依赖检查为配置化 + import 扫描 + 拓扑；加入 7 类违规注入自检 | 接管工程组 |
