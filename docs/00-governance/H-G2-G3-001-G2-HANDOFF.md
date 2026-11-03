# CULINARY G2 封存与 G3 项目交接文档

**文档编号**：H-G2-G3-001
**文档版本**：1.0.0
**项目阶段**：G2 已正式收口
**交接目标**：G3 初步准备
**日期**：2026-11-04
**归档说明**：本文件由项目负责人于 2026-11-04 提交，工程组原样归档；归档不改变其效力。

---

## 一、封存声明

CULINARY 项目 G2 阶段已经完成全部逐阶段授权、实施、审查及验收。

阶段 3 至阶段 9 均已完成并通过验收，G2 正式收口。

自本文件生效起：

1. G2 进入封存状态。
2. 已验收阶段不得无理由重新打开。
3. 不得擅自修改已验收的阶段基线。
4. 不得将 G2 遗留事项自动转化为 G3 的开发任务。
5. 后续开发以 G2 最终工程基线为基础。
6. 任何涉及既有架构或数据结构的重大调整，必须先说明原因、影响和迁移方案，并取得项目负责人授权。

本文件是 G2 的正式交接记录，不是 G3 开发授权书。

## 二、G2 最终交付情况

| 阶段 | 名称 | 交付报告 | 验收状态 |
|---|---|---|---|
| 1 | 工程骨架 | R11 | ACCEPTED |
| 2 | 核心契约 | R12 | ACCEPTED |
| 3 | 数据库 v1 | R13 | ACCEPTED |
| 4 | 数据层 | R14 | ACCEPTED |
| 5 | 平台能力 | R15 | ACCEPTED |
| 6 | 应用外壳 | R16 | ACCEPTED |
| 7 | 食材纵向切片 | R17 | ACCEPTED |
| 8 | 算法验证 | R18 | ACCEPTED |
| 9 | 质量门禁与基线 | R19 v1.1.0 | ACCEPTED |

G2 的核心成果：

- 建立完整 Android 工程骨架。
- 建立领域模型、业务契约和数据库结构。
- 完成 Repository、Mapper、事务协调及基础数据访问。
- 建立图片存储、备份归档及恢复相关能力。
- 建立 WorkManager、SAF、相机权限与条码识别平台能力。
- 完成应用导航、启动门禁、错误处理和调试入口。
- 完成食材模块最小业务纵向切片。
- 完成配方展开、需求汇总、库存缺口及快照算法验证。
- 建立本地质量门禁、Release/R8 构建及恢复测试。

## 三、最终工程基线

**最终框架基线**：`framework-0.1.0`

当前指向：`2b523de`

阶段基线：

- `baseline/g2-stage1`
- `baseline/g2-stage2`
- `baseline/g2-stage3`
- `baseline/g2-stage4`
- `baseline/g2-stage5`
- `baseline/g2-stage6`
- `baseline/g2-stage7`
- `baseline/g2-stage8`
- `baseline/g2-stage9`

工作区状态：clean。

决策登记册：`docs/00-governance/DECISION-REGISTER.md`，当前版本 `0.20.0`。

以上信息作为 G2 最终交接基准。新工作区应在实际仓库中核验标签、提交和文件状态，不得仅凭本文件推断仓库当前状态。

## 四、工程架构

项目采用 Kotlin + Jetpack Compose + Room/SQLite + Hilt + Gradle。

项目目标：

- Android 原生应用。
- 离线优先。
- 本地数据为核心。
- 网络仅用于未来明确授权的同步等能力。
- 长期可维护。
- 模块化、可测试、可回档。
- 数据备份和恢复具有明确的安全边界。
- 避免数据库锁冲突、死锁、数据损坏及不完整恢复。

### 4.1 模块结构

工程共 15 个真实 Gradle 模块：

1. `:app`
2. `:core:common`
3. `:core:domain`
4. `:core:database`
5. `:core:data`
6. `:core:designsystem`
7. `:core:platform`
8. `:feature:ingredients`
9. `:feature:sauces`
10. `:feature:recipes`
11. `:feature:products`
12. `:feature:inventory`
13. `:feature:planning`
14. `:feature:procurement`
15. `:feature:settings`

其中 `:core` 和 `:feature` 是容器项目，不是独立业务模块。

### 4.2 已确认的架构边界

- 领域模型位于 `:core:domain/model`。
- 不创建独立 `:core:model`。
- feature 模块之间零直接依赖。
- UI 不直接依赖 data/database。
- database 不依赖 domain。
- domain 不依赖 Android、Room、Compose。
- `:core:platform` 负责 Android 平台能力。
- `:core:data` 负责备份格式、文件校验、归档及恢复编排。
- `:core:data` 不直接依赖 Android SAF。
- `:core:platform` 与 `:core:data` 按既定端口边界协作。

不得因为开发方便而随意改变这些边界。

## 五、关键技术决策

以下内容已经过前期确认和阶段验收，后续应作为既定约束：

### 5.1 数据标识

- 业务表主键统一 TEXT。
- UUID v4 标准格式。
- 不使用自增整数作为业务主键。
- 导入、迁移和恢复不得无理由重新生成已有 ID。

### 5.2 数据库

- Room/SQLite。
- 外键约束开启。
- WAL 模式。
- busy_timeout = 5000。
- 不使用 destructive migration 作为迁移失败的兜底方式。
- 重要历史记录采用追加式保护。
- 已确认计划快照不可原地修改。

### 5.3 事务与并发

- 写操作通过统一事务协调机制。
- 数据库写操作使用既定单写执行器和协调机制。
- BUSY/LOCKED 按既定策略有限重试。
- CancellationException 不得被当作普通数据库错误吞掉。
- 不得随意嵌套非可重入事务。

### 5.4 图片存储

- 文件存放于应用私有持久化目录。
- 数据库保存相对路径和元数据。
- 不使用 BLOB 保存图片。
- 文件使用唯一名称。
- 文件写入采用临时文件、同步及原子替换策略。
- 文件丢失时允许无图降级，并记录诊断。

### 5.5 备份与恢复

- 遵循 E05 恢复规范。
- 备份格式版本与数据库 schema 版本分离。
- 恢复前执行预检。
- 恢复前创建预恢复备份。
- Journal 记录恢复过程。
- 数据库与附件必须考虑一致性。
- 失败时执行回滚。
- 恢复成功后必须重新打开数据库并验证。
- 未完成验证不得清理回滚副本。

### 5.6 平台能力

- CameraX 1.4.2。
- ML Kit Barcode Scanning 17.3.0 bundled。
- 本地条码识别。
- 不依赖网络下载模型。
- 不增加 INTERNET 权限。
- 相机权限拒绝时保留手动输入能力。
- WorkManager 负责后台任务调度。

具体依赖版本、插件坐标和配置以实际版本目录及 D-012 等已批准决策为准。

## 六、G2 质量验证结果

| 验证项目 | 结果 |
|---|---|
| 工程测试 | 188 例 |
| ktlintCheck | PASS |
| detekt | PASS |
| lintDebug | PASS |
| test | PASS |
| assembleDebug | PASS |
| assembleRelease | PASS |
| R8 + shrinkResources | PASS |
| 数据库迁移测试 | 2 例模拟器通过 |
| 恢复完整流程测试 | 3 例 |
| 离线冷启动 | PASS |
| APK INTERNET 权限 | 无 |
| 模块依赖检查 | PASS |

Release APK：23,533,968 bytes。已生成 `mapping.txt`。APK 和 mapping 尚未入库归档。

### 6.1 远端 CI 状态

GitHub Actions workflow 已提交，但本地没有配置远端仓库和 gh CLI，无法完成远端实跑。

状态：`BLOCKED`

本地等价门禁通过不代表远端 CI 已通过。

## 七、未闭合事项登记

以下事项均已如实保留，不构成 G2 重新开启的理由。

| 编号 | 未闭合事项 | 当前状态 |
|---|---|---|
| G2-U01 | GitHub CI 远端实跑 | BLOCKED |
| G2-U02 | 扫码设备级验证 | 未完成 |
| G2-U03 | SAF 设备级验证 | 未完成 |
| G2-U04 | 自动化 Compose/仪器化 E2E | 未实现 |
| G2-U05 | 恢复 UI 接线 | 未完成 |
| G2-U06 | E05 在线快照/checkpoint | 未实现、未验证 |
| G2-U07 | 图片附件完整恢复 | 未验证 |
| G2-U08 | 备份加密 | 未实现 |
| G2-U09 | 活跃 WAL 场景恢复验证 | 未验证 |
| G2-U10 | 场景 3 来源建模 | 未扩展 |
| G2-U11 | 商品映射 | 未扩展 |
| G2-U12 | 快照元数据扩展 | 未扩展 |
| G2-U13 | APK/mapping 入库归档 | 未完成 |

这些事项未来是否进入 G3，必须根据 G3 的目标重新判断。不得默认全部纳入 G3。

## 八、G3 交接规则

G3 是下一阶段，不代表已经取得开发授权。

新工作区应首先完成：

1. 阅读并核验 G2 交接文档。
2. 检查实际仓库和工程结构。
3. 阅读决策登记册及相关 ADR、规范。
4. 检查 G2 实际实现状态。
5. 识别当前功能边界与技术债务。
6. 提出 G3 的目标、范围和阶段划分建议。
7. 形成待负责人确认的 G3 规划。

在负责人正式批准 G3 计划前：

- 不得修改业务代码。
- 不得新增数据库表。
- 不得调整模块依赖。
- 不得升级核心依赖。
- 不得修改既有决策。
- 不得创建 G3 正式基线。
- 不得擅自开展新功能开发。

## 九、封存结论

G2 已正式完成并收口。

本文件用于保存 G2 最终工程状态、验收成果、技术决策和遗留事项。

后续开发以 `framework-0.1.0` 为交接基准。

**G2：CLOSED**

**G3：PREPARATION PENDING**

项目负责人：待签署。
