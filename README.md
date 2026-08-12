# BrainExporter

BrainExporter 是一个面向 RT-BCI / ADS1299 脑电设备的开源 Android 应用。它通过手机 BLE 直连采集板，提供 8 通道实时监测、频域分析、阻抗估算和本地 CSV 导出，并带有一个极简的专注/休息星球与在线音频层。

> 当前版本是研发原型，不是医疗器械。图表、波段功率和阻抗结果不可用于医学诊断。

## v0.1.0 功能

- 极简双星球主页：左右滑动切换“专注”和“休息”星球；初始静止，点击后流式播放对应在线音乐并开始旋转，再次点击停止。
- 在线音频设置：专注/休息 URL 可在本机修改，音频不会打包进 APK。
- Android BLE 扫描、设备选择、连接和断开，兼容 RT_BLE_AT 的 FFF0/FFF3/FFF4 以及 I6328A/I6329A 的 FFE0/FFE1/FFE2 透传。
- 连接后手动“开始采集 / 停止采集”；CSV 写入手机公共 `Documents/eegData`。
- 8 个独立、自动纵轴的 5 秒电压/时间图，以及 PSD、实时 FFT 频谱和 Delta/Theta/Alpha/Beta/Gamma 相对功率。
- 逐通道或全部通道 ADS1299 交流阻抗估算；采集期间会禁用阻抗测量以避免混入测试电流数据。
- 本地登录/注册：账号只存在当前设备，密码以随机盐 PBKDF2-SHA256 哈希保存，不接入服务器。
- “帮助与关于”直接打开本 GitHub 仓库。

## 使用

1. 安装 Release 中的 APK，并打开蓝牙。
2. 在“监测”页扫描并连接 RT-BCI 设备。
3. 连接成功后点击“开始采集”。Android 8/9 会在此时申请写入公共 Documents 的权限；Android 10+ 不需要传统存储权限。
4. 点击“停止采集”完成 CSV 文件。文件名形如 `BrainExporter_EEG_20260812_183000_000.csv`。

CSV 每行包含 `sample_index`、`packet_id`、`received_at_nanos` 和 8 个以 µV 为单位的通道值。开头的注释行记录 UTC 开始时间和设备名称。

## 在线音频

默认音乐仅在运行时通过 HTTPS 加载，不包含在源码或 APK 中：

- 专注：Study And Relax — Kevin MacLeod
- 休息：Ethereal Relaxation — Kevin MacLeod

两首作品来自 [incompetech.com](https://incompetech.com/)，采用 [Creative Commons Attribution 3.0](https://creativecommons.org/licenses/by/3.0/) 许可。第三方地址可能失效，可在应用的“音频”页替换为任意可直接播放的 HTTPS 音频地址。

## 模块结构

- `sdk-core`：稳定的数据模型与设备、算法、运行时接口。
- `platform-ble-android`：不包含 RT-BCI 协议知识的 Android BLE UART 传输层。
- `plugin-device-rtbci`：UUID、33 字节帧解析、µV 标定、流控制、采样统计和阻抗命令。
- `plugin-algorithm-basic`：Hann 窗、FFT、PSD、频谱和 EEG 波段算法。
- `core-runtime`：连接设备、算法和记录器，维护会话状态并向 UI 发布快照。
- `plugin-ui-monitor`：Compose 星球、设置、账号、设备和信号界面。
- `app`：组装内置插件、处理 Android 权限并将 EEG CSV 写入公共 Documents。

替换耳机时，实现 `DevicePlugin` 并输出标准 `SignalSample` 即可复用运行时、算法和界面；替换算法时实现 `SignalAlgorithm`；其他平台可实现自己的 `EegRecordingSink`。

## 构建

要求：JDK 17+、Android SDK 36。Windows / PowerShell 7：

```powershell
$env:JAVA_HOME = 'E:\Android\AndroidStudio\jbr'
.\gradlew.bat test assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。CI 也会在每次推送和 Pull Request 上执行测试与调试构建。

## 数据与隐私

- EEG 数据只写入用户设备的 `Documents/eegData`，应用没有上传 EEG 的实现。
- 本地账号没有找回密码或跨设备同步能力；卸载/清除应用数据会删除账号信息，但公共 Documents 中的 EEG 文件由用户自行管理。
- 在线音频会让设备直接连接用户配置的音频服务器，对方可能按其隐私政策记录常规网络信息。

## 安全提示

阻抗测试会向电极注入约 6 nA、31.2 Hz 交流测试电流。人体连接电极时必须使用电池或合规隔离供电，不得让佩戴中的采集板通过非隔离 USB 接入市电电脑。正式实验前应验证 BLE 丢包率、采样时序和阻抗标定。

## 许可

BrainExporter 自身源码使用 [MIT License](LICENSE)。本地开发使用的厂商固件、迁移参考文件和采集样本不属于本项目许可范围，也不会提交到公开仓库。
