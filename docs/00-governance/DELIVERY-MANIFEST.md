# CULINARY 交付清单与校验记录

**文档编号**：GOV-MANIFEST
**版本**：`0.4.0`
**状态**：`Review`
**日期**：2026-11-04
**架构基线**：`0.4.0`（修订版，待 G1 签收）
**Git 检查点**：`checkpoint/architecture-revision-0.3.0`

---

## 1. 用途

本文是 17 项正式交付物的**可校验清单**：

- 证明每一项都存在、非空、有版本与状态；
- 记录当前 SHA-256，便于交付后核对未被误改；
- 明确哪些是“附件”，不计入 17 项；
- 记录当前开工门禁还剩什么。

> SHA-256 是 2026-11-04 生成时的值。任何文档修改后必须重新生成本清单，否则校验值失效。

## 2. 17 项正式交付物

| # | 交付物 | 文件 | 版本 | 状态 | SHA-256 |
|---|---|---|---|---|---|
| 1 | 产品需求基线 | [P01](../01-product/P01-PRODUCT-BASELINE.md) | 0.1.0 | Review | `d94a92788b8c51cd5423d798607a29b4e75e0841617821e90d87ba891de74bf2` |
| 2 | 已确认需求与待确认清单 | [P02](../01-product/P02-REQUIREMENTS-OPEN-ITEMS.md) | 0.1.0 | Review | `21ee45174378ba28f150017df615acc9e0df65fcb9c64f42f05a40f6637836f7` |
| 3 | 技术选型对比与最终决策 | [A01](../02-architecture/A01-TECH-STACK-DECISION.md) | 0.2.0 | Review | `2be2786c00d92bb9f41ec17bf919f48188b6bba22df822ec98001d951ba05681` |
| 4 | 软件整体架构设计书 | [A02](../02-architecture/A02-SYSTEM-ARCHITECTURE.md) | 0.2.0 | Review | `c0ef7db1fc9a438e29ab0076ff7e148fae852c4c9d5bc816b61c4af97e755429` |
| 5 | 模块职责与依赖关系说明 | [A03](../02-architecture/A03-MODULE-DEPENDENCIES.md) | 0.3.0 | Review | `d021aa88482068ae6c133610bcbe6d303a0af9b0fd788393ec0be75448b0bdc4` |
| 6 | 核心业务流程设计 | [A04](../02-architecture/A04-BUSINESS-FLOWS.md) | 0.1.0 | Review | `37b5fb8ba1ef7a001aeff51bd28a4caded08f6bd048cdb73c698b26417cea8dd` |
| 7 | 数据库 ER 图与数据字典 | [A05](../02-architecture/A05-DATA-MODEL-ER.md) + [A06](../02-architecture/A06-DATA-DICTIONARY.md) | 0.1.0 | Review | `69dd1181a7463d421fd441c4fd826b7984d5482a390a9a466172bd39fbc32960` + `88d5b43376fc2e18a8affff92fbddc709dbb74b9cc8b96ceb548e3e6590969e4` |
| 8 | 数据版本与迁移设计 | [A07](../02-architecture/A07-VERSIONING-MIGRATION.md) | 0.1.0 | Review | `033ed9f455c0a692dc2c6031736aa042f95fd1b1a5246828502dc42cdfd1d88d` |
| 9 | 代码规范与工程目录规范 | [E01](../03-engineering/E01-CODE-DIRECTORY-STANDARDS.md) | 0.2.0 | Review | `3a2a08b848c673f8de479d0932bedc4eef6059a1dfb9b84c8bb844c2086744cf` |
| 10 | 数据库并发与事务安全方案 | [E02](../03-engineering/E02-DATABASE-CONCURRENCY.md) | 0.1.0 | Review | `948240b7c0689ce7376048d9a25a67c7a0502faf037be90fee8f1dcfd6bd55d0` |
| 11 | 异常处理与日志规范 | [E03](../03-engineering/E03-ERROR-LOGGING.md) | 0.2.0 | Review | `0318dd7007ce8ebad2f711820de7c6cc35aead06a89f79a71c605b3fa44b619f` |
| 12 | 代码回档与版本管理方案 | [E04](../03-engineering/E04-GIT-ROLLBACK.md) | 0.2.0 | Review | `fa0f8b251aedc5deb864f6f1e577aa21dc3de40178f590df30f852a2923513ea` |
| 13 | 数据备份与恢复方案 | [E05](../03-engineering/E05-BACKUP-RESTORE.md) | 0.4.0 | Review | `39049e3db3f7233367181940e3cdb67bda52e8de4690da907af6f68b4c66aff3` |
| 14 | 自动化测试与验收标准 | [E06](../03-engineering/E06-TESTING-ACCEPTANCE.md) | 0.1.0 | Review | `80d77582251f676c0ad9f51a7953bfbf1c33aa8cbd39eb9ddb96356428ba8307` |
| 15 | 框架搭建实施顺序 | [R01](../04-readiness/R01-FRAMEWORK-SEQUENCE.md) | 0.2.0 | Review | `2d1fe012ac4986c47d661607c2475051b177adf41d288fbb8ca48980221f2be8` |
| 16 | 开发环境与依赖清单 | [R02](../04-readiness/R02-ENVIRONMENT-DEPENDENCIES.md) | 0.2.0 | Review | `52cf8dcd0460713894625fa954bf27943bfa014e528d7201392fc409e3f4cbdc` |
| 17 | 框架制作前置检查清单 | [R03](../04-readiness/R03-PREFLIGHT-CHECKLIST.md) | 0.3.0 | Review | `5c752e7ec3ffd46c43494b5cd25eb3ebb20dbe85adb8fe4bbd8b57b5818fbdc5` |
| — | 框架制作启动报告 | [R04](../04-readiness/R04-FRAMEWORK-START-REPORT.md) | 0.4.0 | Review | `2a8efb5409680ca2e93b3c1ff44b8f0d6f4e0ffc1c77f31304262092349af671` |
| — | 模块拆分审查提案（附件） | [A03A](../02-architecture/A03A-MODULE-DECOMPOSITION-REVIEW.md) | 0.3.0 | Approved | 不计入 17 项；D-004 已接受 |
| — | G1 最终复核包（附件） | [G1-REVIEW-PACKET](../04-readiness/G1-REVIEW-PACKET.md) | 0.2.0 | Review | 不计入 17 项；D-011 待复核 |
| — | E05 修订复核包（附件） | [E05A](../03-engineering/E05-REVISION-REVIEW.md) | 0.1.0 | Review | 不计入 17 项；D-011 证据 |
| — | 依赖兼容性验证（附件） | [V01](../04-readiness/V01-DEPENDENCY-VALIDATION.md) | 0.1.0 | Review | 见文件；不计入 17 项 |
| — | ADR-001 ~ ADR-010（附件） | [docs/adr](../adr/) | 0.1.0（ADR-003 0.3.0；ADR-007/008 0.2.0） | Proposed | 10 份 |
| — | 架构/ER/流程 图（附件） | [docs/diagrams](../diagrams/) | — | — | 3 份 `.mmd` |

## 3. 完整性检查

| 检查 | 结果 |
|---|---|
| Markdown 文件总数 | 38（含附件/README） |
| 17 项交付物文件 | 18 个文件全部存在（ER 与字典合并为第 7 项） |
| 断链 | 0 |
| UTF-8 替换字符/乱码 | 0 |
| `Approved` 状态 | A03A 已由负责人批准；其余文档 `Review`，ADR `Proposed`，无虚假批准 |
| 每份是否有版本/状态/日期 | 是 |
| 数据库表数量 | 31 |
| Gradle 模块数量 | 15（D-004 已接受）；16 为历史细粒度参考 |
| ADR 数量 | 10 |
| 图表数量 | 3 |
| 依赖构建验证 | 通过 |
| 模拟器安装/启动验证 | 通过 |
| 设备端无 INTERNET 权限 | 通过 |

## 4. 当前开工门禁

| # | 门禁 | 状态 |
|---|---|---|
| G1 | 8 项关键决策整体签收 | **PENDING**：D-004 已接受；D-011/E05 0.4.0 待复核 |
| G2 | 依赖解析 | PASS |
| G3 | JDK/SDK 环境变量 | PASS |
| G4 | 构建 + 安装启动 | PASS |
| G5 | Release 签名 keystore | **ACCEPTED AS RELEASE GATE**：发布前完成，不阻塞框架 |
| G6 | 版本锁定 | PASS |
| G7 | 无架构级冲突 | PASS |

**结论：** 架构/工程准备已完成；框架开工只剩 G1（D-011 复核）。G5 为发布门禁。

## 5. 重新生成校验值

```powershell
Get-ChildItem docs -Recurse -Filter *.md |
  Where-Object { $_.Name -notlike '*MANIFEST*' } |
  ForEach-Object {
    $h = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    "{0}|{1}" -f $_.FullName, $h
  }
```

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立交付清单、SHA-256 与门禁快照 | 架构组 |
| 0.2.0 | 2026-11-04 | 按负责人意见刷新：D-004/D-011 修订、A03A 附件、G5 发布门禁、G1 保持 PENDING | 架构组 |
| 0.3.0 | 2026-11-04 | 二次修订：15 模块、在线备份/附件异常/manifest 自校验/迁移预检；G1 仍 PENDING | 架构组 |
| 0.4.0 | 2026-11-04 | D-004 接受；D-011/E05 0.4.0 待复核；G5 接受为发布门禁；G1 仍 PENDING | 架构组 |
