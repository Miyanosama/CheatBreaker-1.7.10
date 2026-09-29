# 开发与交付流程

## 1. 项目边界与工具链

- 客户端目标为 Minecraft 1.7.10、LWJGL 2.9.1、OptiFine HD U D6。现有窗口、鼠标和键盘路径基于 LWJGL 2。
- 使用 JDK 8 的 `javac`，编译参数为 `-encoding UTF-8 -source 8 -target 8`。`pom.xml` 声明 Maven 依赖与编译插件；当前常用发布方式是增量编译后由 Python 脚本打包。
- 需要 Windows PowerShell、Python 3，以及 Maven 本地依赖缓存 `%USERPROFILE%\.m2\repository`。`compile_changed.ps1` 固定将 Guava 17.0 放在类路径前面，并排除其他 Guava 与 `google-collections` JAR。
- `target/` 被 Git 忽略。全新检出没有基础 `.class` 文件，不能仅靠增量脚本从零生成完整客户端。

开始前在 `CheatBreaker/` 执行：

```powershell
javac -version
python --version
Test-Path target/classes
Test-Path target/test-classes/Start.class
Test-Path "$env:USERPROFILE\.m2\repository\com\google\guava\guava\17.0\guava-17.0.jar"
git status --short
```

`javac` 必须显示 `1.8.*`。基础类或依赖缺失时，先通过项目 Maven 配置或可信的已有构建恢复；不要把旧 JAR 当成最新源码的编译结果。

## 2. 修改前定位完整调用链

1. 明确用户看到的界面、动作及触发条件。使用 `rg` 查找界面类、构造调用、事件处理和状态清理路径。
2. 同时检查首次连接失败、登录失败、游戏中断线等分支。可能存在多个相似界面或普通服务器与置顶服务器两套入口。
3. 先看 `git status --short`，保留已有未提交改动；只修改与本次需求相关的文件。
4. 确定数据的生命周期。例如重连地址必须在 `loadWorld(null)` 清除当前服务器数据之前保存，并传到失败界面。
5. 对配置选项，先确定它属于全局 `global.cfg` 还是当前 Profile，再确定默认值、旧文件缺键时的行为和保存时机。

优先按调用路径判断行为，不根据反编译命名或屏幕截图猜测内部实现。

## 3. 增量编译

将本次修改的、互相依赖的 Java 文件放在同一次调用中：

```powershell
.\tools\compile_changed.ps1 -Sources @(
    'src/main/java/net/minecraft/client/gui/GuiDisconnected.java',
    'src/main/java/net/minecraft/client/multiplayer/GuiConnecting.java',
    'src/main/java/net/minecraft/client/network/NetHandlerLoginClient.java',
    'src/main/java/net/minecraft/client/network/NetHandlerPlayClient.java'
)
```

脚本检查 JDK 8，使用 `target/rebuild-classes/`、`target/classes/` 和本地 Maven 依赖作为类路径，将结果写入 `target/rebuild-classes/`。编译成功只说明源码可编译；玩家运行的 JAR 尚未更新。

## 4. 维护覆盖清单并打包

在 `tools/build_release.py` 的 `overlay` 集合中登记每个更新的顶层 `.class` 和伴随的 `$1`、`$Inner` 等类。可在编译后查看实际产物：

```powershell
Get-ChildItem target/rebuild-classes/net/minecraft/client/network -Filter 'NetHandlerLoginClient*.class'
```

不要把整个 `target/rebuild-classes/` 覆盖进 JAR；其中可能留有以前任务编译的类。脚本会从 `target/classes/` 复制基础类，用 `overlay` 覆盖，加入 `target/test-classes/Start.class` 与 `Main-Class: Start` 清单，并输出：

- `target/cheatbreaker-client-1.0-SNAPSHOT.jar`
- `target/launcher/CheatBreaker/CheatBreaker.jar`

仅生成本地 JAR：

```powershell
python tools/build_release.py
```

脚本会自动执行键盘路由与配置值检查，并检查 ZIP 完整性、覆盖类是否齐全及 Raw Mouse Input 旧内部类残留，最后输出 SHA-256。若修改了键盘路由，按根目录 `AGENTS.md` 的要求在发布前运行 `python tools/check_keyboard_routing.py`；打包脚本也会再次运行它。

## 5. 安装到启动器版本

要让当前用户的 CheatBreaker 版本实际使用新代码，执行：

```powershell
python tools/build_release.py --install
```

安装目标为 `%APPDATA%\.minecraft\versions\CheatBreaker\CheatBreaker.jar`。同目录必须已有 `CheatBreaker.json`；脚本不生成版本 JSON。缺少 JSON、依赖库、资源索引或原生库时，不能把仅有 JAR 的目录称作可启动版本。

确认版本 JSON 的 `id`、`jar` 和 `mainClass` 指向预期版本，并比较构建 JAR 与安装 JAR：

```powershell
$version = Get-Content -Raw -LiteralPath "$env:APPDATA\.minecraft\versions\CheatBreaker\CheatBreaker.json" | ConvertFrom-Json
$version | Select-Object id, jar, mainClass, inheritsFrom
Get-FileHash target/cheatbreaker-client-1.0-SNAPSHOT.jar -Algorithm SHA256
Get-FileHash "$env:APPDATA\.minecraft\versions\CheatBreaker\CheatBreaker.jar" -Algorithm SHA256
```

`--install` 会自动比较两个 JAR 的哈希。玩家必须**完全退出游戏并重新启动**，运行中的 JVM 不会载入刚安装的类。只编译而不打包安装，游戏里仍会显示旧界面；这是最近一次 Reconnect 按钮“没效果”的直接原因。

## 6. 交付确认与报告

按实际完成程度区分以下四层：

1. **源码已修改**：说明改动文件和行为。
2. **Java 已编译**：说明 `compile_changed.ps1` 是否成功。
3. **JAR 已构建并安装**：给出安装路径、SHA-256 和版本 JSON 是否存在。
4. **游戏内已验证**：说明启动器版本、操作步骤和观察结果；没进入游戏就明确写“尚未在游戏内验证”。

涉及 GUI、输入法、鼠标、全屏、服务器连接时，应在 Windows 游戏内检查目标行为，并留意关闭/重开、连接失败、超时和断线等相邻路径。日志可用于定位问题，但不要把会话令牌、账号或完整私人日志写入提交。

提交前查看 `git diff --check` 与 `git status --short`，确认只包含预期文件。构建产物和临时文件放在被忽略的 `target/`。除非任务要求提交，否则不要把“源码完成”等同于“已提交 Git”。

## 7. 常见故障

| 现象 | 先检查 |
| --- | --- |
| `javac` 版本错误 | `javac -version` 是否为 JDK 8；调整当前会话的 JDK 路径。 |
| 缺基础类或 `Start.class` | `target/classes/`、`target/test-classes/Start.class` 是否存在。 |
| 编译成功但游戏没变化 | 是否运行了 `build_release.py --install`、哈希是否一致、游戏是否彻底重启。 |
| 新类在 IDE 中正常但启动时报 `NoClassDefFoundError` | 顶层类和所有新内部类是否登记在 `overlay` 并实际存在于 JAR。 |
| 安装失败 | `CheatBreaker.json` 是否存在，启动器版本目录是否正确。 |
| 某一断线分支没有新按钮 | 该分支是否构造相同 GUI、是否在状态清理前传递服务器数据。 |
