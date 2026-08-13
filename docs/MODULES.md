# BrainExporter 模块接口（schemaVersion 1）

模块层只接收标准化后的原始 EEG 批次，不接触 BLE、文件系统或 Compose。运行时支持两种稳定输出：

- `eeg_to_eeg`：输出与输入采样索引、时间戳和通道结构一致的 EEG。监测页自动复用时域、PSD、频谱和波段功率可视化。
- `eeg_to_features`：输出一个或多个带 `key`、名称、单位和可选通道号的标量。监测页自动生成当前值卡片及历史曲线。

源码模块实现 `sdk-core` 中的 `EegProcessingModule`。模块是流式、有状态对象：`process` 会连续接收原始 EEG 批次，`reset` 在新采集或重新启用时调用，`close` 用于释放资源。

## 可导入包

应用只导入声明式 `.be-module.json`，不会执行未签名的外部 Dex/JAR。清单示例见 [`examples/modules`](../examples/modules)。目前安全引擎包括：

- `butterworth_bandpass`，必须声明 `eeg_to_eeg`；参数为 `lowCutHz`、`highCutHz` 和 `order`（2 或 4）。
- `window_statistics`，必须声明 `eeg_to_features`；参数为 `windowSeconds` 和 `strideSeconds`。

```json
{
  "schemaVersion": 1,
  "id": "example.bandpass-1-40",
  "name": "1–40 Hz 示例带通",
  "version": "1.0.0",
  "description": "Butterworth 1–40 Hz 带通滤波",
  "type": "eeg_to_eeg",
  "engine": "butterworth_bandpass",
  "config": {
    "lowCutHz": 1.0,
    "highCutHz": 40.0,
    "order": 2
  }
}
```

导入时会校验 schema、ID、版本、类型、引擎和参数。导入模块保存在应用私有偏好中；卸载应用会移除这些模块定义，但不会影响公共 `Documents/eegData` 中的数据。
