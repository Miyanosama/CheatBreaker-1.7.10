# CheatBreaker AI 开发说明

本文件记录 2026-09-28 实际使用的工具链和增量构建流程，供后续 AI 助手与维护者使用。命令默认在仓库根目录的 `CheatBreaker/` 子目录执行。

## 项目与工具链

- 目标客户端：Minecraft 1.7.10，LWJGL 2.9.1，OptiFine HD U D6。保留 LWJGL 2，不能把 LWJGL 3 API 直接用于现有窗口或输入代码。
- 编译：JDK 8 的 `javac`，`-encoding UTF-8 -source 8 -target 8`。2026-09-28 本机使用 Zulu 8u502；玩家日志中的运行环境为 Temurin 8u332。不要用系统默认的新版本 JDK 覆盖 Java 8 目标。
- 依赖：`pom.xml` 声明 Maven 依赖；本次增量编译使用用户目录下的 `.m2/repository`，把 Guava 17.0 放在类路径最前，排除其他 Guava 和 `google-collections` JAR，避免同名类冲突。JNA 3.4.0、LWJGL 2.9.1 等由该仓库提供。
- 构建工具：Windows PowerShell、Python 3（本机 3.14.7）、`CheatBreaker/tools/compile_changed.ps1` 与 `CheatBreaker/tools/build_release.py`。`pom.xml` 配有 Java 8 编译器插件和 Shade 插件，但本次发布使用下面的增量流程。

## 当前增量构建流程

1. 确认 `javac -version` 输出 `1.8.*`，且 `target/classes/`、`target/test-classes/Start.class` 和 Maven 本地依赖存在。`target/` 被 Git 忽略；全新检出并不自带这些基础产物。若缺失，需先通过项目的 Maven 配置或可信的既有构建恢复基础产物，不能把旧版本 JAR 误当作已重新编译的源码。
2. 修改源码后，将**本次改动的 Java 文件**一起传给编译脚本。互相依赖的文件应在同一次调用中编译。例如：

   ```powershell
   cd CheatBreaker
   .\tools\compile_changed.ps1 -Sources @(
       'src/main/java/net/minecraft/client/gui/ServerListEntryNormal.java'
   )
   ```

   编译结果进入 `target/rebuild-classes/`。脚本检查 JDK 版本，并用 `target/rebuild-classes`、`target/classes` 和 Maven 依赖组成类路径。
3. 在 `tools/build_release.py` 的 `overlay` 中登记每个新编译的顶层类及其 `Outer$Inner.class`、匿名类等伴随类。只打包明确登记的覆盖类；`target/rebuild-classes/` 可能留有其他历史编译产物，不得整目录覆盖。打包命令：

   ```powershell
   python tools/build_release.py
   ```

   脚本以 `target/classes/` 为基础，覆盖 `overlay`，加入 `target/test-classes/Start.class` 与 `Main-Class: Start` 清单，生成 `target/cheatbreaker-client-1.0-SNAPSHOT.jar`，并复制一份到 `target/launcher/CheatBreaker/CheatBreaker.jar`。
4. 要安装到当前用户的 Minecraft 版本目录，再执行：

   ```powershell
   python tools/build_release.py --install
   ```

   目标为 `%APPDATA%\.minecraft\versions\CheatBreaker\CheatBreaker.jar`。安装前须已有同目录的 `CheatBreaker.json`。脚本**不生成版本 JSON**，并会在缺失时明确失败；不要把只有 JAR 的目录称作可启动版本。
5. 打包脚本检查 ZIP 完整性、必需的覆盖类以及 Raw Mouse Input 旧内部类残留，并输出 SHA-256。安装时还会比较构建 JAR 与安装 JAR 的哈希。编译和打包成功只证明产物一致；涉及鼠标、输入法、GUI 和全屏的行为仍需在 Windows 游戏内验证，报告中要明确区分。

## 代码约定

- 保持改动范围聚焦；先找到实际调用路径，再修改对应实现。不要删除用户已有的输入、全屏或界面改动，也不要用反编译类名推断行为。
- 新的 CheatBreaker Settings 选项放在 `GlobalSettings.settingsList`，用 `Setting` 的默认值和 `acceptedValues`，由现有配置管理器保存。改变选项时确认启动时默认值、旧配置兼容性和生效时机。
- 模组设置保存在当前 Profile，CheatBreaker Settings 保存在全局 `global.cfg`。新增模组设置必须有默认值和唯一标签；旧 Profile 缺少该键时应使用默认值。模组的缩放、锚点、位置、启用状态和 HUD 显示状态均须在 Profile 读写中保持一致。
- 全局配置和 Profile 的数值读取统一使用 `ConfigValueCodec`，不要在两个读取分支分别编写范围判断。修改配置格式或解析逻辑时扩展 `ConfigValueCodecTest`；`build_release.py` 会在打包前运行该检查。读取配置时不得覆盖原文件，写入采用同目录临时文件替换。
- 游戏快捷键只在没有 GUI 接管该按键时进入游戏动作队列。GUI 关闭时要同步物理按键状态，避免同一次按下被当作新按键重放；聊天和其他文字输入框保持原版的禁止移动规则。
- Windows 原始鼠标、输入法和无边框全屏使用 JNA/Win32 辅助类时，限制在适用平台、保存并恢复原状态、处理调用失败；保留 LWJGL 2 原有路径。文字输入法仅在真正获得文字焦点时启用，普通游戏与背包操作不能触发选词框。
- 普通服务器、置顶服务器等并行 UI 实现要分别检查绘制与点击路径，保持同一交互的视觉反馈和命中区域一致。
- 不把机器绝对路径、玩家账号、会话令牌或完整日志写入源码与文档。构建产物、日志和临时文件留在被忽略的 `target/`；构建脚本与必要说明应纳入版本控制。

## 键盘事件路由约定

- 只有 `Minecraft.runTick()` 可以调用 `Keyboard.next()`；`GuiScreen.handleInput()` 只读取鼠标事件。读取一个键盘事件时，先记录当时的 `currentScreen`，随后只交给游戏或该 GUI 其中一方。界面切换后，剩余事件在下一 tick 根据新界面处理。
- GUI 的 `handleKeyboardInput()` 负责该 GUI 的字符、快捷键和一次全局快捷键调用；游戏循环不得再次为 GUI 的按下事件调用 `func_152348_aa()`。GUI 中的 Shift+Tab 也只走 GUI 路径。
- `PhysicalKeyboard` 和 `KeyboardPressTracker` 负责物理按键边沿。Esc 打开界面后的持续按住不能被当成新的 Esc；真实松开后再按必须正常生效。文字输入的重复字符和 `key == 0` 的输入法提交事件必须保留。
- `Modern Keybind Handling` 只控制从 GUI 回到游戏时是否立即恢复仍按住的游戏按键；GUI 内始终禁止移动，关闭此选项后仍须保留输入法按键补偿与 Esc 去重。默认开启，以保持现有客户端体验。
- 每次修改键盘队列、GUI 焦点或快捷键路由，都要运行 `python tools/check_keyboard_routing.py`。发布脚本会自动运行同一检查；检查覆盖路由结构和边沿状态，但仍需在 Windows 游戏内验证实际键盘与输入法行为。

## 已知限制

- 当前工具是**增量打包工具**，依赖本机已有的 `target/classes/` 和 `target/test-classes/Start.class`。它不是从空目录直接生成完整客户端的独立构建系统。
- Minecraft 启动所需的 `CheatBreaker.json`、资源索引、依赖库和原生库须由启动器环境提供；此脚本只生成并安装 JAR。
