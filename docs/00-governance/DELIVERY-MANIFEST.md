# CULINARY 交付清单与校验记录

**文档编号**：GOV-MANIFEST
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**架构基线**：`0.1.0`
**Git 检查点**：`checkpoint/architecture-draft-0.1.0`

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
| 3 | 技术选型对比与最终决策 | [A01](../02-architecture/A01-TECH-STACK-DECISION.md) | 0.1.0 | Review | `6ce87a6ad5281026661aef9a45b0ea17ef3d2e5853f63ed0a45a05beb793e5e3` |
| 4 | 软件整体架构设计书 | [A02](../02-architecture/A02-SYSTEM-ARCHITECTURE.md) | 0.1.0 | Review | `a1bd1092eeed47842a767a17fdd5b57a540931b2ac0f1b4eec7eca31bb2b26e0` |
| 5 | 模块职责与依赖关系说明 | [A03](../02-architecture/A03-MODULE-DEPENDENCIES.md) | 0.1.0 | Review | `edbd9352d5c4ee75657a96b6b2d11e2be2c197d8d02d000bf77c29658d0edd7f` |
| 6 | 核心业务流程设计 | [A04](../02-architecture/A04-BUSINESS-FLOWS.md) | 0.1.0 | Review | `37b5fb8ba1ef7a001aeff51bd28a4caded08f6bd048cdb73c698b26417cea8dd` |
| 7 | 数据库 ER 图与数据字典 | [A05](../02-architecture/A05-DATA-MODEL-ER.md) + [A06](../02-architecture/A06-DATA-DICTIONARY.md) | 0.1.0 | Review | `69dd1181a7463d421fd441c4fd826b7984d5482a390a9a466172bd39fbc32960` + `88d5b43376fc2e18a8affff92fbddc709dbb74b9cc8b96ceb548e3e6590969e4` |
| 8 | 数据版本与迁移设计 | [A07](../02-architecture/A07-VERSIONING-MIGRATION.md) | 0.1.0 | Review | `033ed9f455c0a692dc2c6031736aa042f95fd1b1a5246828502dc42cdfd1d88d` |
| 9 | 代码规范与工程目录规范 | [E01](../03-engineering/E01-CODE-DIRECTORY-STANDARDS.md) | 0.1.0 | Review | `45ba4f839cd3e898568a877a1ae5e3025faa01ca7a225a3aa955355db238aaa2` |
| 10 | 数据库并发与事务安全方案 | [E02](../03-engineering/E02-DATABASE-CONCURRENCY.md) | 0.1.0 | Review | `948240b7c0689ce7376048d9a25a67c7a0502faf037be90fee8f1dcfd6bd55d0` |
| 11 | 异常处理与日志规范 | [E03](../03-engineering/E03-ERROR-LOGGING.md) | 0.1.0 | Review | `7f0f890fc59286e0617d2647f30150c95a1e2d6d27523c37f3020a02f00170f9` |
| 12 | 代码回档与版本管理方案 | [E04](../03-engineering/E04-GIT-ROLLBACK.md) | 0.1.0 | Review | `839877046d80ce236d3f4202873ccdd0e11683388914df75362b0b6780cf9e52` |
| 13 | 数据备份与恢复方案 | [E05](../03-engineering/E05-BACKUP-RESTORE.md) | 0.1.0 | Review | `3eb037d252c72db96cd5ee26c050bd62b734c7cc326712723babf6a432c2e7d2` |
| 14 | 自动化测试与验收标准 | [E06](../03-engineering/E06-TESTING-ACCEPTANCE.md) | 0.1.0 | Review | `80d77582251f676c0ad9f51a7953bfbf1c33aa8cbd39eb9ddb96356428ba8307` |
| 15 | 框架搭建实施顺序 | [R01](../04-readiness/R01-FRAMEWORK-SEQUENCE.md) | 0.1.0 | Review | `44c85cbd9c32ee12194be77f5e053d3727a42687bd37d78edcf6396a0b9668dc` |
| 16 | 开发环境与依赖清单 | [R02](../04-readiness/R02-ENVIRONMENT-DEPENDENCIES.md) | 0.1.0 | Review | `19a344b0a7d80aa884e90e07b52908f6d9bedfd0df966ffcec62dc04ee6c37c2` |
| 17 | 框架制作前置检查清单 | [R03](../04-readiness/R03-PREFLIGHT-CHECKLIST.md) | 0.1.0 | Review | `fa06abfc3fb2adffd949ca1785a4db113f23ff8399b7c0ab547ba13501c9017a` |
| — | 框架制作启动报告 | [R04](../04-readiness/R04-FRAMEWORK-START-REPORT.md) | 0.1.0 | Review | `f66ad7a151e4516087d4bf60c922d9db4be8208ff9c9d6ede34ba8c296cc8de3` |
| — | 依赖兼容性验证（附件） | [V01](../04-readiness/V01-DEPENDENCY-VALIDATION.md) | 0.1.0 | Review | 见文件；不计入 17 项 |
| — | ADR-001 ~ ADR-010（附件） | [docs/adr](../adr/) | 0.1.0 | Proposed | 10 份 |
| — | 架构/ER/流程 图（附件） | [docs/diagrams](../diagrams/) | — | — | 3 份 `.mmd` |

## 3. 完整性检查

| 检查 | 结果 |
|---|---|
| Markdown 文件总数 | 35（含附件/README） |
| 17 项交付物文件 | 18 个文件全部存在（ER 与字典合并为第 7 项） |
| 断链 | 0 |
| UTF-8 替换字符/乱码 | 0 |
| 虚假 `Approved` | 0（全部 `Review` / ADR `Proposed`） |
| 每份是否有版本/状态/日期 | 是 |
| 数据库表数量 | 31 |
| Gradle 模块数量 | 16 |
| ADR 数量 | 10 |
| 图表数量 | 3 |
| 依赖构建验证 | 通过 |
| 模拟器安装/启动验证 | 通过 |
| 设备端无 INTERNET 权限 | 通过 |

## 4. 当前开工门禁

| # | 门禁 | 状态 |
|---|---|---|
| G1 | 8 项关键决策签收 | **PENDING（需项目负责人）** |
| G2 | 依赖解析 | PASS |
| G3 | JDK/SDK 环境变量 | PASS |
| G4 | 构建 + 安装启动 | PASS |
| G5 | Release 签名 keystore | **PENDING（发布前；可由负责人创建）** |
| G6 | 版本锁定 | PASS |
| G7 | 无架构级冲突 | PASS |

**结论：** 架构/工程准备已完成；开工只差 G1 签收与 G5 发布签名准备。

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
