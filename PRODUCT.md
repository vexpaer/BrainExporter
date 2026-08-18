# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin + Jetpack Compose(Material 3,compose-bom 2024.10.01),多模块 Gradle 工程(minSdk 26 / targetSdk 36)。视觉升级在现有代码上进行,不更换堆栈。

## Users

- 主要用户:RT-BCI / ADS1299 脑电采集板的研究者、学生与脑机接口爱好者,在实验室或桌面场景用手机 BLE 直连采集板做实时监测、阻抗检查与数据导出。
- 次要用户:对专注/休息音频场景感兴趣的普通体验者(使用星球主页与在线音频)。
- 界面语言为简体中文。

## Product Purpose

BrainExporter 是一款开源 Android 脑电采集工具:手机 BLE 直连 RT-BCI 采集板,提供 8 通道实时波形、PSD/FFT 频谱、脑电波段功率、ADS1299 交流阻抗估算与本地 CSV 导出,并带一个专注/休息双星球主页与在线音频层。成功标准:研究者可靠地完成"连接 → 采集 → 检查阻抗 → 导出 CSV"闭环,且主页能成为一种有辨识度的品牌体验。

## Positioning

"手机上的开源脑电工作台":把专业 EEG 采集能力装进一部手机,数据只存本机不上传;模块化体系(sdk-core 契约 + 插件)允许换设备、换算法、导入声明式处理模块。其差异化机制是"模块化采集运行时 + 实时多视图可视化 + 本地隐私优先",而非医疗器械。

## Operating Context

- 实验室/桌面:研究者戴电极、打导电膏、检查电极阻抗,手机放在设备旁。
- 环境嘈杂:蓝牙设备多(实验展会),需要在多个 RT_BLE_AT 设备里选择正确的板子。
- 会话长:采集可能持续数分钟到数十分钟,期间需要持续监控链路质量与阻抗。
- 隐私敏感:EEG 数据只写本机 `Documents/eegData`,无上传实现;在线音频由用户自配 URL。

## Capabilities and Constraints

- 已确认能力:BLE 扫描/连接/断开(RT_BLE_AT 与 I6328A/I6329A 透传)、手动开始/停止采集、8 通道时域/PSD/频谱/波段视图、逐通道与全部阻抗估算(6nA·31.2Hz·5s/通道)、`.be-module.json` 模块导入与 EEG→EEG / EEG→特征两种处理契约、在线音频(HTTPS 流式)、GitHub Release 检查/下载/安装、本地 PBKDF2 账号。
- 约束:采集期间禁用阻抗测量;模块安全引擎只运行已实现并校验参数的声明式模块,不执行外部 Dex/JAR;深色主题是用户确认的品牌承诺(保持纯深色,不做浅色/Dynamic Color);界面全中文;研发原型,不作为医疗器械,图表与波段结果不可用于诊断。
- 未决事实:无(视觉方向在本次会话中由用户选定)。

## Brand Commitments

- 产品名 BrainExporter,仓库 GitHub vexpaer/BrainExporter,开源 MIT。
- 用户在本会话明确承诺:全程纯深色主题(不追逐系统浅色,Dynamic Color 不引入)。
- 用户选定方向:沉浸星球主页 + 极简工具页的反差节奏;布局结构允许重排;关键页(首页/监测)可自绘组件,其余页面深度特调 M3。
- 品牌语义色体系:青=连接/良好、琥珀=过渡/警告、红=错误、紫=模块特征(已 token 化于 BrainExporterTheme.kt)。

## Evidence on Hand

- 真实产品代码:modular Gradle 工程,UI 在 plugin-ui-monitor(9 个 Compose 文件),运行时在 core-runtime(BrainExporterRuntime,80ms 快照发布),SDK 契约在 sdk-core。
- 真实文案与免责声明在 README.md(简体中文,含"非医疗器械"声明、incompetech 音乐署名)。
- 真实设备协议:RT-BCI 33 字节帧、µV 标定、FFF0/FFF3/FFF4 与 FFE0/FFE1/FFE2 透传。
- 无营销素材、无用户证言、无官方视觉样张;这些不得虚构。

## Product Principles

1. 数据可信优先:波形、频谱、阻抗与 CSV 必须精确,图表标注单位与算法来源可查。
2. 隐私默认安全:EEG 只存本机;账号本地哈希存储;任何"联网"都必须是用户可见、可解释的(音频、更新检查)。
3. 玩法分层:普通人先被星球主页吸引(情感峰值),研究者随后深入工具页(效率核心),两者共用一套语义色,互不打架。
4. 模块化是护城河:UI 不得写死任意算法或设备,一切经 sdk-core 契约表达。
5. 纯深色即品牌:深色不是"没做浅色",而是刻意的、氛围化的设计声明。

## Accessibility & Inclusion

- 无障碍基线(已审计、待修复):TalkBack 标签、触控目标 ≥48dp、字号随系统缩放(sp)、对比度 ≥4.5:1、Reduce Motion 降级。这些作为发布门槛,不因视觉升级而放松。