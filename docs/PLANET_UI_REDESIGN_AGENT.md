# BrainExporter「星球」UI 全面重构执行提示词

> 这是一份给 Codex/Claude Code 等本地 coding agent 的执行规格。目标是让 agent 从审计、设计、实现、验证到 CI/CD 自己闭环，不把半成品交回用户。

## 0. 任务目标

在 **不改变任何现有功能、协议、数据流、算法契约与隐私行为** 的前提下，彻底重构 BrainExporter Android App 的 UI/UX，使其从“普通 Material 3 开发者工具”升级为有明确品牌识别度和高级感的产品。

唯一总主题：**Planet / 星球**。

设计关键词：**cinematic planetary minimalism、precision instrument、quiet luxury、deep space、editorial hierarchy、restrained motion**。

不要做成：赛博朋克、霓虹灯、电竞 UI、满屏玻璃拟态、默认 Material 组件拼装、廉价渐变、绿色主题、卡片套卡片、到处发光、为了动画而动画。

## 1. 开始前必须做

1. 完整阅读：`PRODUCT.md`、`DESIGN.md`、`AGENTS.md`、现有 UI 源码、现有 GitHub Actions。
2. 运行：

   ```bash
   bash scripts/setup-agent-skills.sh codex
   ```

3. 本次视觉设计必须显式使用 `ui-ux-pro-max`；Jetpack Compose 实现与最终审查必须显式使用 `modern-jetpack-compose`。
4. 先审计再改代码。列出当前所有页面、组件、导航路径、状态、对话框、空状态、加载状态、错误状态和关键交互，确认功能覆盖表后再开始重构。
5. 不要向用户反复询问普通设计细节。按本文件给出的方向自主完成；只有遇到会改变产品功能/数据语义的决策才允许停下来确认。

## 2. 功能绝对不可回归

至少完整保留并验证：

- RT-BCI / ADS1299 BLE 扫描、设备选择、连接、断开。
- 8 通道 EEG 实时时域波形。
- PSD、FFT/频谱、脑电波段功率视图。
- 逐通道/全部阻抗估算；保持采集期间不能测阻抗等既有约束。
- 开始/停止采集、本地 CSV 写入与既有目录行为。
- `.be-module.json` 导入、启停与 EEG→EEG / EEG→特征两类处理契约。
- 专注/休息双星球与在线音频播放、停止、URL 设置。
- 本地账号登录/注册/退出与 PBKDF2 存储逻辑。
- GitHub Release 检查、下载与安装更新流程。
- 所有既有权限请求、错误提示、免责声明与安全边界。

UI 可以重排，业务层尽量不动。不得为了“重构得更干净”顺手重写设备协议、算法、文件格式、网络层或 SDK 契约。

## 3. 新品牌视觉：Planetary Premium

### 3.1 总体视觉语言

整个应用是“一台来自行星观测站的精密脑电仪器”，而不是“太空主题皮肤”。

- **首页 = 情绪峰值**：沉浸、留白、单一视觉焦点、星球作为品牌核心。
- **工具页 = 精密仪器**：清楚、克制、数据优先、几乎没有装饰噪音。
- 两者共享同一套 token、字体节奏、圆角、线条、动效与状态语义。
- 深色唯一，不做浅色，不跟随 Dynamic Color。

### 3.2 配色

废除当前“青绿=全局主色”的品牌使用方式。绿色/青绿色不得用于主按钮、选中态、大面积图形、标题或品牌强调。

建议建立以下方向的语义 token；具体数值可以基于 `ui-ux-pro-max` 的设计系统结果微调，但视觉关系不能偏离：

- `Void`：近黑背景，约 `#07080D`
- `Obsidian`：主要表面，约 `#0D111A`
- `Eclipse`：抬升表面，约 `#151B28`
- `Starlight`：主文字，偏暖白，约 `#F2F0EA`
- `MoonDust`：次文字，约 `#9298A8`
- `OrbitBlue`：主要交互/选中，低饱和冰蓝，约 `#7F98FF`
- `LunarViolet`：次级品牌强调，约 `#9B8CFF`
- `SolarMetal`：极少量暖金/沙金强调，约 `#D2AD70`
- `Danger`：错误，约 `#FF6E7A`
- `Success`：只允许极小连接/成功状态点，低饱和绿色；不得扩散为主题色

所有颜色进入统一 Theme/token 文件。组件中禁止散落 raw hex。

### 3.3 星球视觉

首页星球必须有高级感，但不要靠廉价高饱和 glow：

- 使用 Compose Canvas/Brush/Path 等原生方式优先，避免大体积视频、Lottie 和网络图片。
- 球体应有真实的光照方向、暗部、边缘大气、低频纹理或带状结构，层级明显。
- 专注与休息是两颗不同气质的行星，而不是简单换颜色。
- 可有极少星尘/轨道/远景层，但背景不能比星球本身抢眼。
- 动效使用慢速自转、轻微视差/呼吸、状态切换的连续性；禁止快速旋转和持续闪烁。
- Reduce Motion 下所有无限动画静止，只保留必要的状态变化。

### 3.4 排版与留白

- 建立明确标题层级，不要所有页面都用默认 `headlineMedium + Bold`。
- 重要数值继续使用 tabular numerals/等宽数字。
- 允许用字距、大小、权重营造编辑感，但不能牺牲中文可读性。
- 关键页面主动增加留白；工具密集区域则使用一致的 4/8dp 基础节奏。
- 不要通过“把所有东西包进 Card”制造层级。优先用间距、分组、细边线、背景层级解决。

### 3.5 形状与表面

- 避免所有组件都 16dp 同一种圆角。
- 建立 3–4 档 radius token，例如 8 / 12 / 18 / capsule，并按用途固定。
- 主表面尽量平整；抬升层用轻微明度差、细描边和非常克制的透明度，不使用明显投影。
- 不做泛滥玻璃拟态。Android API 26 也必须表现完整，不依赖高版本 blur 才成立。

### 3.6 图标

- 使用一致的 Material Symbols/Material Icons 风格；不要混用 Unicode 符号。
- 图标只是导航和状态辅助，不要用巨大图标填补空白。
- TalkBack 标签完整；纯装饰图形不得制造语义噪音。

## 4. 页面重构要求

### 4.1 星球首页

首页必须成为整个 App 最强的品牌画面：

- 第一眼只看见一个明确主角，不要像 dashboard。
- 星球占据视觉中心；专注/休息切换应像“选择轨道/世界”，而不是普通 tab。
- 播放/暂停是首页唯一一级 CTA，其他信息弱化。
- EEG 连接状态变成精致的小型状态组件，不使用亮绿色大块提示。
- 底部导航不要是一条厚重默认 Material NavigationBar；重做成低噪音、悬浮/贴底但轻量的导航容器。

### 4.2 实时监测

这是效率最高优先级页面：

- 连接后首先展示 EEG 主信号，不要让控制面板盖过数据。
- 波形/PSD/频谱/波段之间的切换使用高质量 segmented control / compact tabs，而不是默认按钮堆。
- 连接、采集、阻抗是三种不同语义的主动作，层级必须清晰。
- 复杂设置收进 bottom sheet / secondary panel；默认界面只留实验中常用操作。
- 图表轴、单位、图例、通道颜色与状态必须可读；不允许为了“高级感”把对比度压得过低。
- 实时数据更新不能因为装饰动画造成卡顿或增加明显重组成本。

### 4.3 模块页

- 从“功能列表”升级为插件/信号处理工作台。
- 模块名称、类型、启停状态、输入输出、主要结果一眼可见。
- 导入模块是明确但非抢眼的主操作。
- 特征数值和历史曲线使用统一数据可视化语言。

### 4.4 在线音频设置

- 保持两条 URL、保存、恢复默认、版权/来源说明全部功能。
- 表单不再像 Material 示例页；使用清晰字段容器、说明文本、状态反馈。
- 长 URL 输入保证可编辑性，不做为了外观牺牲可用性的单行截断。

### 4.5 我的/账号/更新

- 账号状态、隐私说明、版本/更新、GitHub 入口形成清晰层级。
- 登录/注册 Dialog 或 Sheet 统一品牌样式。
- GitHub 更新下载/安装的 AlertDialog 也要纳入新设计系统，不能留下默认组件“穿帮”。

### 4.6 Drawer / Bottom Navigation / App Bars

全面审查现有全局导航。目标：

- 手机主导航保持最多 5 个一级入口。
- Drawer 如果只是与底部导航重复，允许移除或改造成只承载低频入口；但不能删除原有可访问功能。
- 首页可无传统 TopAppBar；工具页标题栏与内容融合。
- 系统返回行为、状态恢复、safe area、IME 不能回归。

## 5. Compose 实现约束

- 保持 Kotlin + Jetpack Compose + Material 3；不要换 Flutter/React Native/XML View。
- 现有模块架构保持不变。
- 将颜色、typography、shape、spacing、motion、chart palette、semantic state 全部 token 化。
- 建立少量真正可复用的品牌组件，例如：`PlanetScaffold`、`OrbitalNavigation`、`InstrumentSurface`、`SegmentedModeControl`、`StatusPill`、`PrimaryActionDock`；不要抽象成过度通用的框架。
- 遵守 modern Compose 的 state hoisting、稳定性、生命周期、安全区、无障碍和性能规则。
- 不要为视觉效果新增重型第三方 UI 框架。
- 如果引入字体/图标资源，必须是可再分发的开源资源，并将许可证一并加入仓库；不要依赖运行时在线字体。
- 任何 Canvas 无限动画必须只在页面可见且对应状态需要时运行。
- 触控目标 ≥48dp；正文/数据对比度满足 WCAG AA；1.3x 字体缩放不截断。

## 6. 审美验收：逐项自检

实现完每个页面后，问自己：

- 去掉 logo 后，这个界面仍能被认作同一款产品吗？
- 是否还有明显的“Material 示例项目”痕迹？
- 是否还有绿色/青色主按钮、大面积选中背景或廉价霓虹？
- 是否通过堆卡片解决所有布局问题？
- 是否有任何动画只是为了炫技？
- 首页是否真正只有一个视觉焦点？
- 工具页是否比旧版更快找到连接、采集、阻抗和视图切换？
- 数据可读性是否比装饰优先？
- 低端/API 26 设备是否仍有完整体验？

任何答案不理想，继续打磨，不要仅因为“能编译”就结束。

## 7. 本地验证

至少完成：

```bash
./gradlew --no-daemon test lint assembleDebug
```

并检查：

- 主要页面 Preview/模拟状态覆盖：未连接、连接中、已连接、采集中、阻抗测量、错误、空模块、有模块、登录/未登录、更新可用。
- Android 8+/API 26 不因高级视觉 API 崩溃。
- TalkBack 语义、48dp 触控、字体放大、Reduce Motion。
- 长时间实时波形页面无明显卡顿/内存增长。

## 8. Git / PR / CI 规则

这次最终 UI 重构必须形成 **一个最终 commit**。

推荐流程：

1. 从最新 `main` 创建 `redesign/planet-v0.4.0`。
2. 开发期间尽量不提交；需要本地检查点可使用 stash/临时本地分支。
3. 完成本地全部验证后，把本次改动整理为一个 commit，例如：

   `feat: redesign BrainExporter with planetary premium UI`

4. 推送分支并创建 PR。
5. 查看 GitHub Actions；如果失败，自己读取失败日志、修复并重新验证。
6. 若远端分支产生多个修复 commit，在最终交付前重新基于 `main` 整理为一个 commit 并 force-with-lease 推送。
7. PR 最终必须显示相对 base **1 commit**。
8. CI 必须全绿后才允许 merge。
9. 使用 squash merge，保证 `main` 上该次重构仍只有一个发布 commit。

不要把 CI 红灯、lint 问题或构建错误交给用户手工处理。

## 9. CI/CD 与签名

仓库应已有：

- `.github/workflows/android.yml`：PR/main 的 test + lint + debug build。
- `.github/workflows/release.yml`：tag 的 test + lint + signed release build + APK 签名校验 + SHA256 + GitHub Release。

Release workflow 需要 GitHub Actions Secrets：

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

如果 secrets 尚未配置，而开发机存在仓库约定的 `.signing/brainexporter-release.jks`、`.signing/signing-password.txt`、`.signing/key-password.txt`，使用 `gh secret set` 安全配置；不要 echo 密码，不要提交 keystore，不要把 secret 写进日志或 Markdown。

示例思路（自行根据本机环境执行，不要泄露值）：

```bash
base64 -w0 .signing/brainexporter-release.jks | gh secret set SIGNING_KEYSTORE_BASE64
gh secret set SIGNING_KEY_ALIAS --body brainexporter
gh secret set SIGNING_STORE_PASSWORD < .signing/signing-password.txt
gh secret set SIGNING_KEY_PASSWORD < .signing/key-password.txt
```

## 10. 版本与发布

重要：仓库已经在 2026-08-18 发布了 `v0.3.0`，不可重复创建、移动或删除该 tag/release。

因此本次完整 UI 重构默认发布为：

- `versionCode = 4`
- `versionName = "0.4.0"`
- tag `v0.4.0`

只在最终 PR 准备发布时改版本号。

合并后：

1. 确认 `main` CI 全绿。
2. 确认版本号为 0.4.0，且 main 上只有一个本次重构 commit。
3. 创建 annotated/lightweight tag `v0.4.0` 并 push。
4. `Android Release` workflow 自动构建并发布。
5. 用 `gh run watch` 等到 Release workflow 成功。
6. 检查 GitHub Release 中存在签名 APK 与 `SHA256SUMS.txt`。
7. 失败则定位并修复；不要把失败 release 当作完成。

## 11. 最终交付标准

只有以下全部满足才可以向用户汇报完成：

- UI 已全局统一为 Planetary Premium，不再以绿色/青绿色为品牌主色。
- 所有页面均纳入同一设计系统，没有默认 Material 组件穿帮。
- 所有原功能可用，没有业务/数据回归。
- 本地 `test lint assembleDebug` 通过。
- PR 只有一个最终 commit。
- PR GitHub Actions 全绿。
- 合并后的 main CI 全绿。
- v0.4.0 tag 与 app version 一致。
- Release workflow 成功，APK 已签名并通过 `apksigner` 验证，SHA256 已生成。

完成后给用户的汇报只需要：设计重构摘要、保留功能说明、CI/CD 状态、单 commit SHA、PR 链接、Release 链接与 APK 校验信息。不要写冗长过程日志。
