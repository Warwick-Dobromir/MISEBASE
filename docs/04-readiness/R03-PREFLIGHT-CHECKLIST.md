# CULINARY 框架制作前置检查清单

**文档编号**：R03
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人

---

## 1. 使用方式

- 每个检查项必须有**证据**（命令输出、文件、截图、签收记录），不能只写“已完成”。
- `FAIL` 且标记“阻塞”的项未清零，不得进入正式框架搭建。
- `PENDING` 若标记“阻塞”，同样视为未通过。
- 本文状态是 2026-11-04 的快照；每次开工前重新核对。

状态：`PASS` / `FAIL` / `PENDING` / `N/A`。

## 2. 开工门禁（最小集合）

以下 7 项全部 `PASS` 才允许创建正式工程：

| # | 门禁 | 当前 |
|---|---|---|
| G1 | 8 项架构决策已签收（D-001/002/003/004/006/008/009/011） | PENDING |
| G2 | Google Maven 依赖解析已验证（能拉取 AGP/Compose/Room/Hilt） | PASS（镜像 + V01） |
| G3 | JDK 21 + Android SDK 36 环境变量配置完成 | PASS（用户级已设置） |
| G4 | 空 Android 工程可 `assembleDebug` 并安装 | PASS（V01：构建 + 安装 + 启动） |
| G5 | Release 签名 keystore 已创建并离线备份 | PENDING |
| G6 | 版本目录实际版本锁定（非候选/占位） | PASS（版本值已实测；`libs.versions.toml` 阶段 1 建） |
| G7 | 无未解决的架构级冲突（P0） | PASS |

> 结论：**当前仍不满足开工门禁**，剩余 2 项：G1 决策签收、G5 签名 keystore。依赖解析、版本锁定、构建、安装/启动均已通过。

## 3. 产品与架构

| # | 检查项 | 要求 | 状态 | 证据/行动 | 阻塞 |
|---|---|---|---|---|---|
| A01 | 产品边界明确 | 非目标、核心库、执行体系 | PASS | P01 | 否 |
| A02 | 已确认需求清单 | 有 ID、来源、验收点 | PASS | P02 | 否 |
| A03 | 待确认事项 | 每项有默认值/影响 | PASS | P02、DECISION-REGISTER | 否 |
| A04 | 技术选型 | 每项有对比、风险、结论 | PASS | A01 | 否 |
| A05 | 分层架构 | 层职责/禁止/通信 | PASS | A02 | 否 |
| A06 | 模块边界 | 16 模块、依赖矩阵 | PASS | A03 | 否 |
| A07 | 数据模型 | ER + 字典 + 约束 | PASS | A05/A06 | 否 |
| A08 | 版本与迁移 | 六类版本、兼容矩阵 | PASS | A07 | 否 |
| A09 | 并发方案 | WAL/单写者/重试/恢复 | PASS | E02 | 否 |
| A10 | 备份恢复 | 格式/校验/回滚 | PASS | E05 | 否 |
| A11 | 测试体系 | 分层/场景/验收 | PASS | E06 | 否 |
| A12 | 架构决策签收 | 8 项必须签收 | PENDING | 见 DECISION-REGISTER 第 4 节 | **是** |
| A13 | 无架构级冲突 | 文档一致性审查 | PASS | 见第 11 节 | 否 |

## 4. 开发环境

| # | 检查项 | 要求 | 状态 | 证据/行动 | 阻塞 |
|---|---|---|---|---|---|
| E01 | JDK 21 | Studio JBR 21.0.8 | PASS | `java -version` | 否 |
| E02 | JAVA_HOME | 指向 JBR 21 | PASS | 用户级已设置 | 否 |
| E03 | Android SDK 36 | platforms/build-tools | PASS | SDK 目录 | 否 |
| E04 | ANDROID_HOME | 指向 SDK | PASS | 用户级已设置 | 否 |
| E05 | adb | 可用，能列设备 | PASS | `adb version` | 否 |
| E06 | Gradle Wrapper | 8.14.3 + 校验和 | PENDING | 阶段 0 创建 | 否 |
| E07 | cmdline-tools | latest | PENDING | sdkmanager 安装 | 否 |
| E08 | API 26 镜像 | 最低版本测试 | PENDING | 安装 system image | 否 |
| E09 | 模拟器 | API 33/36 可启动 | PASS | `Pixel_5` + `-feature -Vulkan` 启动 | 否 |
| E10 | 真机 | 至少一台 Android 手机 | PENDING | 验收前连接 | 否（验收） |
| E11 | 磁盘空间 | ≥ 30 GB 可用 | PASS | D: 156.4 GB | 否 |
| E12 | 内存 | ≥ 8 GB | PASS | 15.8 GB | 否 |
| E13 | Git | 可用 | PASS | `git --version` | 否 |
| E14 | Git 仓库 | 初始化 + .gitignore | PENDING | 阶段 0/1 | 否 |
| E15 | 行尾/编码 | LF + UTF-8 | PENDING | `.gitattributes`/`.editorconfig` | 否 |

## 5. 依赖与网络

| # | 检查项 | 要求 | 状态 | 证据/行动 | 阻塞 |
|---|---|---|---|---|---|
| D01 | Google Maven | 能下载 AGP/Compose/Room/Hilt | PASS | 阿里云镜像；V01 构建成功 | 否 |
| D02 | Maven Central | 能下载 Kotlin/测试库 | PASS（镜像/直连） | 阿里云 public + 官方 | 否 |
| D03 | 代理配置 | Gradle/Studio 一致 | PASS | 镜像优先；代理可选 | 否 |
| D04 | 镜像信任 | 地址/来源记录 | PASS | R02 第 6 节 | 否 |
| D05 | 版本目录 | 无 `+`/快照/占位 | PASS | V01 版本表 | 否 |
| D06 | 依赖许可证 | Apache/Google 条款核查 | PENDING | 依赖清单 | 否 |
| D07 | Gradle 缓存 | 8.14.3 + AGP/Kotlin | PASS | 本机缓存 | 否 |
| D08 | KSP/Hilt 版本匹配 | Kotlin 2.2.20 对应 | PASS | KSP 2.2.20-2.0.4 + Hilt 2.57.2 | 否 |

## 6. 构建与签名

| # | 检查项 | 要求 | 状态 | 证据/行动 | 阻塞 |
|---|---|---|---|---|---|
| B01 | 空工程构建 | `assembleDebug` | PASS | V01 构建成功 | 否 |
| B02 | 安装启动 | 模拟器安装打开 | PASS | V01：install Success + Activity resumed | 否 |
| B03 | Release 构建 | R8 + 签名 | PENDING | 阶段 9 | 否 |
| B04 | Keystore | 创建 + 两处离线备份 | PENDING | 阶段 0 | 是（发布） |
| B05 | 版本号规则 | versionName/Code | PASS | E01 | 否 |
| B06 | 构建产物归档 | APK/mapping/SHA | PENDING | 阶段 9 | 否 |
| B07 | 依赖校验和 | Gradle verification | PENDING | 可选但建议 | 否 |

## 7. 数据库与数据安全

| # | 检查项 | 要求 | 状态 | 证据/行动 | 阻塞 |
|---|---|---|---|---|---|
| DB01 | Schema v1 设计 | 31 表、约束、索引 | PASS | A06 | 否 |
| DB02 | ER 一致性 | 实体/关系/字典一致 | PASS | A05/A06 | 否 |
| DB03 | 迁移策略 | 前向、备份、回滚 | PASS | A07 | 否 |
| DB04 | 并发策略 | WAL/单写者/重试 | PASS | E02 | 否 |
| DB05 | 备份格式 | manifest/校验/加密 | PASS | E05 | 否 |
| DB06 | 恢复回滚 | staging/journal/rollback | PASS | E05 | 否 |
| DB07 | 数据库加密 | 首版 FBE，不 SQLCipher | PASS（决策） | D-016 | 否 |
| DB08 | 网络权限 | v1 不申请 INTERNET | PASS（已验证可移除） | V01：aapt 确认移除 | 否 |
| DB09 | 日志脱敏 | 无名称/电话/路径 | PASS（设计） | E03 | 否 |
| DB10 | 备份加密默认 | 待确认（O-17） | PENDING | 不阻塞 | 否 |

## 8. 测试与 CI

| # | 检查项 | 要求 | 状态 | 证据/行动 | 阻塞 |
|---|---|---|---|---|---|
| T01 | 测试分层 | 单元/DB/UI/E2E | PASS | E06 | 否 |
| T02 | 12 个必测场景 | 有步骤/期望 | PASS | E06 第 6 节 | 否 |
| T03 | 模块验收标准 | 每模块有清单 | PASS | E06 第 12 节 | 否 |
| T04 | CI 方案 | PR/夜间/发布 | PASS | E04/E06 | 否 |
| T05 | CI 实例 | GitHub Actions 配置 | PENDING | 阶段 1/9 | 否 |
| T06 | 迁移测试工具 | MigrationTestHelper | PENDING | 阶段 3 | 否 |
| T07 | 并发测试 | 20 协程/重试/进程死亡 | PENDING | 阶段 4/9 | 否 |
| T08 | 真机测试 | 扫码/SAF/后台 | PENDING | 验收前 | 否（验收） |
| T09 | 离线测试 | 飞行模式全流程 | PENDING | 阶段 9 | 否 |
| T10 | 备份演练 | 损坏/中断/恢复 | PENDING | 阶段 9 | 否 |

## 9. 文档与治理

| # | 检查项 | 要求 | 状态 | 证据/行动 | 阻塞 |
|---|---|---|---|---|---|
| DOC01 | 17 项交付物 | 全部存在 | PASS | DOCUMENT-INDEX | 否 |
| DOC02 | 版本/状态 | 每份有版本/状态/日期 | PASS | 文档头 | 否 |
| DOC03 | 无虚假 Approved | 全部 Review/Draft | PASS | 检查 | 否 |
| DOC04 | ADR | 关键决策有记录 | PASS | docs/adr | 否 |
| DOC05 | 图表 | 架构/ER/流程 | PASS | docs/diagrams | 否 |
| DOC06 | 目录规范 | 命名/目录/接口 | PASS | E01 | 否 |
| DOC07 | Git 规范 | 分支/提交/标签/回滚 | PASS | E04 | 否 |
| DOC08 | 事故模板 | 记录格式 | PASS | E04 | 否 |
| DOC09 | 文档一致性审查 | 交叉引用/术语/字段 | PASS | 第 11 节 | 否 |

## 10. 签收与责任

| 角色 | 职责 | 当前 |
|---|---|---|
| 项目负责人 | 签收 8 项决策、接受风险 | 待签 |
| 架构/技术负责人 | 维护架构、ADR、一致性 | 已交付 |
| 开发 | 按 R01 顺序实现 | 未开始 |
| 测试 | 按 E06 验收 | 未开始 |

## 11. 一致性审查记录

本次自检确认：

- [x] 三库边界一致（P01/A03/A06）。
- [x] 食材/商品分离（P01/A06）。
- [x] 图片可选规则（P01/A02/A06/E05）。
- [x] 离线优先、v1 无 INTERNET（A01/A02）。
- [x] 配方版本 + 计划快照双重机制（A04/A06/A07）。
- [x] 单位维度/禁止无依据换算（A04/A06）。
- [x] 库存批次/流水/预留（A04/A06/E02）。
- [x] 备份恢复回滚（A07/E05）。
- [x] 代码回档 ≠ 数据回档（A07/E04）。
- [x] 16 模块依赖无环（A03）。
- [x] 文档未标 Approved（全部 Review）。

待修正的小问题：

- [x] A05 ER 字段类型已与 A06 对齐（`real`/`string`）。
- [x] E03 代码块重复的 `sealed interface` 已清理。
- [x] 组件外键统一为 `referenced_*`。
- [x] 跨目录链接已检查（备份/日志/迁移）。
- [x] 文档编码/换行已恢复 UTF-8。

这些不影响架构正确性，已在签收前清理。

## 12. 未通过项汇总

### 12.1 阻塞

| ID | 项 | 行动 | 责任 |
|---|---|---|---|
| G1 | 8 项决策签收 | 项目负责人签收 | 负责人 |
| G5/B04 | 签名 keystore | 创建 + 备份 | 负责人 |

### 12.2 非阻塞但需跟进

- 真机、API 26 镜像、cmdline-tools、CI 实例、迁移/并发/备份演练。
- 依赖许可证核查（D06）。
- 文档小问题已清理，签收时复核。

> 已解除：G2 依赖解析、G3 环境变量、G4 构建/安装、G6 版本锁定、B01/B02。
> 证据：[V01 技术验证](V01-DEPENDENCY-VALIDATION.md)。

## 13. 开工前最后一步

当 G1–G7 全部 `PASS`：

1. 在 DECISION-REGISTER 填写签收记录。
2. 将相关文档状态从 `Review` 改为 `Approved`（只改被签收的文档，不批量）。
3. 打第一个检查点标签 `checkpoint/pre-framework-<date>`。
4. 按 R01 阶段 1 创建工程。
5. 在 R04 启动报告记录“已开工”。

## 14. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 初稿；7 项门禁、环境/依赖/测试/签收检查 | 架构组 |
