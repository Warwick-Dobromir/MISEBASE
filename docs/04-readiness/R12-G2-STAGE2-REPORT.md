# G2 阶段 2（核心契约）完成报告

**文档编号**：R12
**版本**：`1.0.0`
**状态**：`Review`（待项目负责人验收）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §5、E03 0.2.0、A06、D-008、D-006、D-012
**回档点**：`baseline/g2-stage1-20261104`

---

## 1. 任务目标

建立可被后续阶段依赖的核心契约：

1. `core:common`：`Outcome`、错误模型与错误码、`Clock`、`IdGenerator`、`DispatcherProvider`、`AppLogger`；
2. `core:domain`：领域模型（单位/食材/菜谱/版本/组成）、Repository 端口、领域规则；
3. 纯 JVM 测试覆盖 `Outcome`、单位换算、配方图循环检测。

**不在本阶段**：Room、Repository 实现、Hilt 绑定、UI、库存/计划模型、备份。

## 2. 涉及文件

```text
core/common/src/main/kotlin/com/culinary/core/common/
├─ Outcome.kt / OutcomeExt.kt          # 统一结果类型与 map/flatMap
├─ CulinaryError.kt / ErrorCode.kt     # 8 类错误 + 29 个稳定错误码
├─ DomainDetail.kt                     # 领域错误结构化明细
├─ Clock.kt / SystemClock.kt
├─ IdGenerator.kt                      # 端口，无实现（D-005 未定）
├─ DispatcherProvider.kt / DefaultDispatcherProvider.kt
└─ AppLogger.kt                        # 门面，实现留到阶段 6

core/domain/src/main/kotlin/com/culinary/core/domain/
├─ model/      # Unit、Quantity、Ingredient、Recipe、RecipeVersion、ComponentTarget 等 16 个类型
├─ repository/ # IngredientRepository、UnitRepository、RecipeRepository
├─ rule/unit/  # UnitConverter
├─ rule/recipe/# RecipeGraphValidator + RecipeGraphSource
└─ usecase/ingredient/ListIngredientsUseCase.kt

core/common/src/test/…  # OutcomeTest、ErrorCodeTest
core/domain/src/test/…  # UnitConverterTest、RecipeGraphValidatorTest
config/detekt/detekt.yml  # 允许守卫式提前返回（ReturnCount max=4）
```

## 3. 实施内容与依据

### 3.1 错误模型（E03 §2/§3/§4）

- `CulinaryError` 为 sealed interface，含 `Validation`、`NotFound`、`Conflict`、`Domain`、`Data`、
  `Platform`、`Transfer`、`Unexpected` 八类；每类都带 `code`、`retryable`、`cause`、`context`。
- `ErrorCode` 收录 E03 §3.2 的首批 29 个错误码，格式 `CUL-<DOMAIN>-<NNN>`，**不重复使用**。
- `DomainDetail` 提供 `RecipeCycle`、`UnitIncompatible`、`InvalidState`、`InsufficientStock`，
  让 UI 能显示"哪条配方、哪个单位、哪批库存"。
- `Outcome` + `map`/`flatMap`/`valueOrNull`/`errorOrNull`；不携带堆栈。

### 3.2 单位与数量（D-008 / A06 §3.1）

- `UnitDimension` 只有 `MASS / VOLUME / COUNT`，与数据字典一致。
- `Unit` 在构造期校验 `baseFactor > 0`、`displayPrecision ∈ 0..6`。
- `Quantity = 数值 + Unit`，拒绝负数、NaN、Infinity。
- `IngredientUnitConversion` 表达食材级显式换算，构造期拒绝 `factor <= 0` 与自映射。

**`UnitConverter` 规则**：同单位直返；同维度（非 COUNT）按 `baseFactor` 比例；
COUNT 不同单位、跨维度必须走食材级换算；无依据返回 `CUL-UNIT-001`；因子非法返回 `CUL-UNIT-002`。
**不猜密度、不返回 0 掩盖失败。**

### 3.3 食材与菜谱模型（A06 §3.2 / §4.4-4.6，D-006）

- `Ingredient`：id、名称、规范化名、默认单位、状态；食材与商品分离。
- `Recipe` / `RecipeCategory` / `EntityStatus`；`RecipeVersion` 含版本号、状态、产量、组成。
- `RecipeComponent` 的 `lineNo` 唯一、数量 > 0；`ComponentTarget` 目前支持 `INGREDIENT` 与 `RECIPE`，
  子菜谱固定引用 `RecipeVersionId`（不跟随"当前版本"漂移）。

### 3.4 配方图循环检测

`RecipeGraphValidator` 从根版本做 DFS：命中"正在访问"节点即返回 `CUL-REP-001`，
并把循环路径（首个重复节点 → 再次出现）放进 `DomainDetail.RecipeCycle`；
被引用版本不存在返回 `CUL-REP` 之外的 `CUL-NF-001`（`NotFound`）。
共享子配方（菱形引用）不会误报。

### 3.5 D-005 的边界处理（关键）

D-005（UUID v4 TEXT 主键）仍为 `PROPOSED`，因此本阶段**不实现也不假设**主键格式：

- 所有 ID 是 `@JvmInline value class`，只承诺"非空不透明字符串"；
- 不解析 UUID、不校验版本位、不生成 ID；
- `IdGenerator` 只有接口，**没有实现**，实现与格式在 D-005 接受后的阶段 3 落地。

这样阶段 2 的模型与测试不会被一个未确认的决策绑死。

## 4. 测试要求与实际结果

| # | 要求（R01 §5.2） | 命令 | 结果 |
|---|---|---|---|
| 1 | `core:domain` 不依赖 Android/Room/Compose | 模块为 Kotlin/JVM；源码扫描 0 处 android/androidx import | **PASS** |
| 2 | 纯 JVM 单测可运行 | `.\gradlew.bat :core:common:test :core:domain:test --rerun-tasks` | **PASS** |
| 3 | `UnitConverter` 同维度/跨维度/COUNT | `UnitConverterTest`（10 例） | **PASS** |
| 4 | `RecipeGraphValidator` 自引用/多层循环 | `RecipeGraphValidatorTest`（5 例） | **PASS** |
| 5 | `Outcome` 与错误映射 | `OutcomeTest`（6 例）+ `ErrorCodeTest`（3 例） | **PASS** |
| 6 | 质量门禁 | `.\gradlew.bat ktlintCheck detekt` | **PASS** |

测试合计 **24 例，0 失败**：

```text
TEST-com.culinary.core.common.ErrorCodeTest                       tests=3 failures=0
TEST-com.culinary.core.common.OutcomeTest                         tests=6 failures=0
TEST-com.culinary.core.domain.rule.recipe.RecipeGraphValidatorTest tests=5 failures=0
TEST-com.culinary.core.domain.rule.unit.UnitConverterTest         tests=10 failures=0
```

覆盖的关键用例：

- 1 kg → 1000 g、500 g → 0.5 kg；
- cup → g **无换算必须失败**（`CUL-UNIT-001`，含单位码与食材 ID）；
- cup → g 使用食材级换算 120，2 cup → 240 g，且支持反向 `1/factor`；
- piece → clove 无换算失败，有换算 1:10 时 2 piece → 20 clove；
- 非法换算（factor=0、自映射）在构造期即被拒绝；
- 自引用 `A→A`、三层循环 `A→B→C→A` 均返回 `CUL-REP-001` 且路径正确；
- 缺失子版本返回 `CUL-NF-001`；菱形共享子配方不误报。

## 5. 失败与偏差记录

| 项 | 说明 |
|---|---|
| 未实现 `RecipeExploder` / `RequirementAggregator` / `InventoryAllocator` | R01 §5.1 列为"骨架"，但其输出依赖需求/快照模型；现在实现会产生阶段 8 必删的代码。**留给阶段 8（算法验证）**，届时用真实模型与场景测试 |
| 未建模库存与计划 | 计划快照涉及 D-007（`PROPOSED`），库存批次涉及 D-009 落地与阶段 6；阶段 2 不预建半成品模型 |
| `ComponentTarget` 暂不含酱汁 | 需要 `SauceVersion` 模型与跨类型遍历；随酱汁功能（阶段 8 前后）加入，避免"能构造但无法校验"的洞 |
| ktlint 首轮失败 | `function-signature`/`class-signature` 官方风格要求多行签名；用 `ktlintFormat` 统一格式化，测试与门禁复跑通过 |
| detekt `ReturnCount` | 默认上限 2 与守卫式提前返回冲突；配置 `max=4 + excludeGuardClauses`，并同步收敛 `UnitConverter`/`validate` 的出口数量 |

## 6. 文档一致性问题（需负责人确认）

R09 §8 的阶段表（…4 纵向切片 / 5 平台 / 6 库存 / 7 计划采购 / 8 备份 / 9 质量）
与本任务书 §五及 **R01 0.2.2**（4 数据层 / 5 平台 / 6 应用外壳 / 7 纵向切片 / 8 算法 / 9 质量）不一致。
本次实施以任务书与 R01 0.2.2 为准。**建议由负责人确认后修订 R09 §8**，以免后续阶段误判"备份属于阶段 8"。

## 7. 遗留

1. 库存/计划/酱汁模型与端口：随阶段 6-8 落地。
2. `IdGenerator` 实现与 UUID 格式：等待 D-005（阶段 3 前）。
3. Hilt 绑定、Repository 实现、Room：阶段 3-4。
4. `AppLogger` 实现（Timber）与日志脱敏：阶段 6。

## 8. Git

| 项 | 值 |
|---|---|
| 阶段 1 基线 | `baseline/g2-stage1-20261104` |
| 阶段 2 提交 | `feat(domain): add core contracts and domain rules` |
| 阶段 2 基线标签 | `baseline/g2-stage2-20261104` |

## 9. 结论

R01 §5.2 的退出条件全部满足：domain 纯净、纯 JVM 测试可运行、
单位换算与配方图循环检测有真实用例覆盖、`Outcome` 与错误映射有测试。

**请项目负责人验收阶段 2。** 阶段 3（DB v1）开始前必须先确认 **D-005（UUID 主键）**，
我不会自行把 PROPOSED 当成已确认。

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录阶段 2 契约、规则、测试、D-005 边界与偏差 | 工程组 |
