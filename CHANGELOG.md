# Changelog

## 0.3.0 — 2026-08-18

- 视觉语言整体升级：“沉浸星球主页 × 极简工具页”双域反差 —— 主页是深空仪式,工具页是实验室工作台。
- 主页重建 3D 双星球：球面经纬纹理按三维坐标投影并横向自转,前后遮挡的连续星轨、大气呼吸脉冲、星尘与星云底景;专注(青)/休息(紫)双色相。
- 监测页重构为“由简入繁”：未连接时全屏只留一个扫描按钮,连接后整页沉浸式单画布脑电波形,复杂控制（视图/通道/采集/阻抗/指标/数据源）收进底部控制面板。
- 图标体系从 Unicode 字符全面替换为 Material Icons——修复符号族混杂、语义错配（♙ 白兵代表“我的”）与 TalkBack 不可读问题。
- 全应用主题 token 化：语义色（青/蓝/琥珀/红/紫）、统一圆角节奏（卡片 16/控件 10/胶囊）、动效时长节奏集中到单一主题文件。
- 深色数据可视化通用化：坐标轴标签 ≥11sp 并随系统字体缩放,等宽数字（tabular numerals）让采样率/阻抗/百分比横向对齐。
- 尊重系统“减弱动画”设置：行星自转、星尘闪烁、星云漂移等无限动画全部退化为静态。

## 0.2.0 — 2026-08-13

- Rebuild the focus/rest scene with horizontally rotating 3D spherical projection, continuous surface texture and a correctly occluded continuous orbit.
- Restore the gray/green EEG connection indicator, add mode descriptions and a two-dot swipe affordance.
- Check GitHub Latest Release automatically and support direct APK download plus Android installer handoff.
- Add a module navigation page with validated, persistent `.be-module.json` imports.
- Add stable EEG-to-EEG and EEG-to-features processing contracts with automatic visualizations.
- Ship a continuous Butterworth 1–40 Hz band-pass module and a multi-feature window-statistics reference module.
- Add module package examples, interface documentation and processing tests.

## 0.1.0 — 2026-08-12

- Rename the application and package namespace to BrainExporter.
- Add swipeable focus and rest planets with playback-driven rotation.
- Add configurable HTTPS streaming audio without bundled media files.
- Add explicit EEG acquisition controls and CSV export to `Documents/eegData`.
- Add device-local registration and login with salted PBKDF2 password hashes.
- Link Help & About to the public GitHub repository.
- Preserve the existing BLE monitor, EEG analysis, and impedance workflows.
