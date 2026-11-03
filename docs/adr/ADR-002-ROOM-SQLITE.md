# ADR-002：Room + Android 平台 SQLite

**状态**：Proposed
**版本**：`0.1.0`
**日期**：2026-11-04
**决策者**：首席架构师 / 项目负责人
**关联**：D-003、A01、E02

## 背景

需要离线、单用户、强关系、可迁移、可测试的本地数据库；数据包含配方版本、库存流水、计划快照等强约束结构。

## 决策

采用 Room 2.8.5 + Android 平台 SQLite：

- `@Entity`/`@Dao`/`@Database`，KSP 编译。
- WAL 模式；单进程；应用级单写者。
- 显式事务边界；禁止破坏性迁移。
- 不引入 SQLCipher、SQLDelight、Realm、ObjectBox。

## 理由

1. Android 官方方案，编译期 SQL 校验、迁移测试工具、Flow/Paging 集成成熟。
2. 平台 SQLite 无需额外原生库；min 26 可用。
3. Room 的 schema 导出与 `MigrationTestHelper` 适合长期迁移治理。
4. 关系模型与约束（外键、唯一、CHECK）直接表达业务不变量。

## 后果

- 受平台 SQLite 版本限制：只使用 3.18 兼容子集。
- 迁移必须手写并测试；禁止 `fallbackToDestructiveMigration`。
- 单进程约束；未来多进程需重新设计。
- 复杂分析查询能力有限；报表需求需评估。

## 替代方案

- SQLDelight：类型安全 SQL，但多一套代码生成/驱动/测试链。
- Realm/ObjectBox：长期生态/许可/迁移风险，关系查询不匹配。
- 原生 SQLiteOpenHelper：样板与错误风险高。

## 复审条件

- 平台 SQLite 在目标设备出现不可规避的缺陷；
- 数据规模/查询复杂度超出 SQLite 能力；
- 需要多进程访问。
