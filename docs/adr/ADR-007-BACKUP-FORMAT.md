# ADR-007：自定义 ZIP 备份格式

**状态**：Proposed
**版本**：`0.2.0`
**日期**：2026-11-04
**决策者**：首席架构师 / 项目负责人
**关联**：D-011、E05、A07

## 背景

需要完整备份数据库、图片、版本信息；可校验、可恢复、可回滚；不依赖云。

## 决策

采用自定义 ZIP 容器：

```text
manifest.json
database/culinary.db
images/...
checksums.sha256
```

可选 AES-256-GCM 加密容器（`.culinary`）：

```text
magic + header + ciphertext + tag
```

- manifest 记录格式/schema/App 版本、文件 SHA-256、表计数。
- 备份前 checkpoint + 复制 DB；恢复前预检 + 预恢复备份。
- 自动备份用 WorkManager + SAF；保留 7 日 + 4 周。
- 不使用 Android Auto Backup 作为唯一方案。
- 必须满足 [E05](../03-engineering/E05-BACKUP-RESTORE.md) 的 7 项合规矩阵：DB 一致性、图片/附件完整性、manifest、版本兼容、校验、恢复前验证、失败回滚。

## 理由

- 完全可控、可测试、可版本化。
- 图片与 DB 一起校验，避免“恢复后缺图”。
- 可在恢复前做完整预检，失败不触碰现有数据。
- 加密可选，不强制影响恢复体验。

## 后果

- 需要自己实现/维护打包、校验、加密、恢复回滚。
- 备份文件较大；复制/加密耗时。
- 用户忘记密码则无法恢复（必须明确警告）。

## 替代方案

- Android Auto Backup：配额/内容不透明，无法预检/版本清单。
- 只备份 DB：丢图片。
- 云备份：本阶段不做。

## 复审条件

- 备份/恢复成为主要故障源；
- 数据规模导致 ZIP 不可用，需要增量/分卷；
- 需要跨设备同步（另立 ADR）。
