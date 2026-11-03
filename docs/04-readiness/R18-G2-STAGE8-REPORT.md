# G2 阶段 8（算法验证）完成报告

**文档编号**：R18
**版本**：`1.1.0`
**状态**：`Approved`（项目负责人 2026-11-04 验收通过）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §11、A06 §4.7-§4.9/§9、E06 §6、D-006、D-008
**回档点**：`baseline/g2-stage7-20261104`（`5522692`）

---

## 1. 任务目标

在完整 UI 之前，用真实领域模型验证高风险算法：配方图循环、单位换算、配方展开、需求汇总、缺口计算；并保证结果可序列化为快照 payload。

## 2. 交付内容

| 算法 | 位置 | 说明 |
|---|---|---|
| 配方图循环 | `RecipeGraphValidator`（阶段 2 已有） | DFS 灰色节点；自身引用、多层循环、缺失版本、菱形复用 |
| 单位换算 | `UnitConverter`（阶段 2 已有） | 同维度比例、COUNT 不互转、食材级显式换算、无依据返回 `CUL-UNIT-001` |
| 配方展开 | `RecipeExploder`（本轮） | 根倍率缩放；子配方按“引用量 ÷ 子产量”递归；可选行默认排除；成环/缺版本/单位错误的错误码 |
| 需求汇总 | `RequirementAggregator`（本轮） | 同单位相加；同维度换算到首个单位；COUNT/跨维度保留多行；可选标记取“全可选” |
| 缺口计算 | `InventoryAllocator`（本轮） | 库存换算到需求单位后相减；换算失败按 0 可用，不猜密度 |
| 快照 payload | `RequirementSnapshotPayload` + `SnapshotPayloadCodec`（本轮） | 稳定 JSON 字段；`generatedAt`；未知字段容忍；损坏 → `CUL-BACKUP-003` |

## 3. 退出条件核对（R01 §11.2）

| # | 条件 | 状态 | 证据 |
|---|---|---|---|
| 1 | 12 个必测场景中的算法类场景通过 | **PASS（1/2/4）/ 部分（3）** | 见 §4 |
| 2 | 200 节点展开 ≤ 500 ms（JVM 基准） | **PASS** | `RecipeExploderTest` 断言 `< 500 ms` |
| 3 | 错误路径有明确错误码 | **PASS** | `CUL-REP-001`、`CUL-NF-001`、`CUL-UNIT-001`、`CUL-VAL-001`、`CUL-BACKUP-003` |
| 4 | 结果可序列化为快照 payload | **PASS** | 往返测试 + 未知字段容忍 |

### 3.1 12 场景中的算法类

| # | 场景 | 结果 |
|---|---|---|
| 1 | 菜谱引用自身 | **PASS**：`RecipeGraphValidatorTest.detects a direct self reference` |
| 2 | 多层循环 A→B→C→A | **PASS**：`detects a multi level cycle and reports the path` |
| 3 | 多菜谱引用相同物料 | **算法 PASS / 来源未建模**：`RequirementAggregatorTest` 证明合并为一行；**“来源两条”未记录**（见 §5） |
| 4 | 单位无法换算（杯 + 库存 g，无密度） | **PASS**：COUNT/跨维度保留多行，`UnitConverter` 返回 `CUL-UNIT-001`，不猜 120 g |

## 4. 测试

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| `RecipeExploderTest` | 6 | 缩放、子配方换算、可选、缺版本、循环、200 节点基准 |
| `RequirementAggregatorTest` | 4 | 同单位/同维度合并、COUNT 分离、可选标记 |
| `InventoryAllocatorTest` | 3 | 满足、不足、kg→g 库存换算 |
| `SnapshotPayloadCodecTest` | 3 | 往返、未知字段、损坏 JSON |
| 既有 `RecipeGraphValidatorTest` / `UnitConverterTest` | 15 | 循环、路径、单位规则 |

工程总测试 **183 例**（domain 40 + database 77 + data 38 + platform 19 + app 6 + feature 3）；
`ktlintCheck`、`detekt`、`assembleDebug` 全绿。

## 5. 偏差与遗留

- 场景 3 只验证“合并为一行”，**没有记录“来源两条”**：`RequirementGroup` 不保存来源配方；若产品需要展示来源，需要扩展模型；
- 配方展开/汇总未接数据库与计划模块（阶段 8 只做纯 JVM 算法验证，符合 R01 定位）；
- 缺口计算暂用“食材 + 库存量”简化输入，商品映射（商品单位/包装）未接入：商品模型尚未在领域层建立；
- 快照 payload 只覆盖需求行，不含计划/版本元数据（阶段 9 计划快照时扩展）。

## 6. Git

| 项 | 值 |
|---|---|
| 阶段 7 基线 | `baseline/g2-stage7-20261104`（`5522692`） |
| 阶段 8 提交 | `ddb14e6`（展开）、`d28f470`（汇总/缺口）、`5fccb76`（快照） |
| 阶段 8 基线标签 | 验收通过后打 `baseline/g2-stage8-20261104` |

## 7. 结论

算法类退出条件均有纯 JVM 证据；200 节点性能达标；错误码与快照序列化完成。
场景 3 的来源计数与商品映射缺口已如实标注，未伪装为完成。

**请项目负责人验收阶段 8。** 验收通过后进入阶段 9（质量门禁与基线）。

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录算法实现、退出条件、183 例测试与遗留项 | 工程组 |
| 1.1.0 | 2026-11-04 | 项目负责人验收通过阶段 8；状态改为 Approved | 工程组 |
