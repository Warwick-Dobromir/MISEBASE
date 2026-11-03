# ADR-009：CameraX + ML Kit Bundled 扫码

**状态**：Proposed
**版本**：`0.1.0`
**日期**：2026-11-04
**决策者**：首席架构师 / 项目负责人
**关联**：D-013、A01、A02、V01

## 背景

需要二维码/条形码识别，本地优先，离线可用；不能依赖在线查询或 Play Services 运行时下载。

## 决策

- CameraX `1.4.2` 预览/分析。
- ML Kit Barcode Scanning `17.3.0` **Bundled** 模型。
- 领域层 `BarcodeScanner` 接口；实现放 `core:platform`。
- 失败/无权限/无相机时保留手工输入。
- 在线商品查询只留接口，v1 不实现。

## 理由

- Bundled 模型离线可用，不依赖运行时下载。
- CameraX 官方相机抽象，生命周期集成好。
- ML Kit 同时支持二维码与一维码。
- 接口隔离可在必要时替换 ZXing。

## 后果

- APK 增大（debug spike 约 36.7 MB，含 Compose/CameraX/模型；release 会收缩）。
- 需相机权限与真机验证。
- 部分无 GMS 设备需实测；失败有手工输入兜底。
- ML Kit 依赖可能合并网络权限，必须 manifest 移除并验证。

## 替代方案

- ZXing：更轻/无 GMS，但识别率与相机集成较弱。
- Google Code Scanner：依赖 Play Services，不满足离线保证。
- 商业 SDK：费用/许可。

## 复审条件

- 目标真机识别率不达标；
- 包体不可接受，需要 ZXing 替代；
- 需要 NFC/RFID 等新识别能力。
