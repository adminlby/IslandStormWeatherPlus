# CLAUDE.md — IslandStorm 项目开发约定

> 本文件对 AI 具有**强制约束力**，优先级高于默认行为。开始任何工作前必须先读完本文件。
> 当书面需求 `需求.txt` 与本文件「关键决策」冲突时，**以本文件为准**。

---

## 一、项目是什么

**IslandStorm** —— 为 100 小时荒岛生存直播服务器开发的 Paper 1.20.6 / Java 21 **高级人工天气系统插件**。

- 主类：`io.lbynb.islandstorm.IslandStormPlugin`
- 主指令：`/islandstorm`（别名 `/storm` `/weatherplus` `/isweather`）
- 构建：**Maven**（产物 `target/IslandStorm-1.0.0.jar`）

### 核心功能（需求摘要）

1. **9 种天气**：CLEAR / CLOUDY / RAIN / HEAVY_RAIN / THUNDERSTORM / FOG / WINDY / TYPHOON / EXTREME_STORM，每种带显示名/图标/默认风/危险等级/能见度/破坏属性。
2. **独立风系统**：风速 km/h + 八方位风向；影响地面移动、鞘翅顺/逆/侧风、极端风吹飞；性能友好（定时任务 + 限幅，可绕过）。
3. **原版天气同步（混合模式）**：每种天气可配 `sync-vanilla-weather`，支持 全局 / 区域 / 完全独立 三模式。
4. **区域天气**：矩形区域独立天气/风/危险/时长/破坏，存 `regions.yml`，指令或网页画框创建。
5. **台风 / 极端风暴路径**：多点路径随时间插值移动，半径内增强风，存 `storm-paths.yml`。
6. **方块破坏**（默认关）：分批抽查、概率、黑/白名单、可取消的 `StormBlockDamageEvent`、WorldGuard 占位 Hook。
7. **双轨时间系统**：所有时长/到达/预警/预报均支持 `REAL_TIME` 与 `MC_TIME`。
8. **天气预报 / 排期**：导演预排时间线；当前天气到期自动切下一条（空则回落默认）。
9. **小时级预报**：未来 24 小时，由排期时间线渲染。
10. **HTML 天气卡**：赛事风 `weather-preview.html` + 央视风 `hourly-forecast.html`，自包含内联 CSS、16:9、适配 OBS。
11. **网页控制台**：JDK 内置 HttpServer + PBKDF2 鉴权 + 内存令牌 + REST API + 2D 地图画框。

---

## 二、关键决策（业主已确认，**优先于 `需求.txt`**）

冲突时一律按本节执行：

- **构建工具 = Maven**，不是需求里写的 Gradle。`pom.xml` 在，gradle 文件已删，**不要重新引入 Gradle**。
- **包名 = `io.lbynb.islandstorm`**，主类 `IslandStormPlugin`（需求里的 `studio.cyanbukkit.islandstorm` 作废）。
- **天气 100% 人工控制**：天气只来自 指令 / 后台 / 网页控制台 / 配置预设。
  **不接外部天气 API、不做现实天气换算、不做随机/自动生成算法**——即使需求 config 里写了 `auto-cycle: true` 和「天气随机权重」也不实现（`auto-cycle` 默认 false 且无随机）。
  `WeatherManager` 只负责 保存 / 应用（含原版同步）/ 广播；`ForecastManager` 与小时预报 = 人工预设/排期，**不是预测**。
- **天气到期 = 方案 C**：自动切到排期里导演预排的下一条；排期空则回落 `default-weather`。即 **预报 = 导演排好的时间线**。
- **依赖 = 零额外依赖路线**：JSON 用 Gson（已 shade 重定位到 `io.lbynb.islandstorm.lib.gson`）、密码用 JDK 原生 PBKDF2WithHmacSHA256、网页用 JDK 自带 `com.sun.net.httpserver.HttpServer`。**不要新增需要 shade 的重依赖**，新增任何依赖前先问。

---

## 三、构建环境（本机事实，不可凭直觉）

- **JDK 21**：`C:\Program Files\Java\jdk-21`。**必须手动设 `JAVA_HOME`**——PATH 上的 `java` 是 Oracle javapath 跳板，自动推断会得到错误目录导致 mvn 失败。
- **Maven 未在 PATH**：已下载到 `C:\Users\admin\AppData\Local\apache-maven-3.9.9\apache-maven-3.9.9\bin\mvn.cmd`。
- **标准构建命令（PowerShell）**：
  ```powershell
  $env:JAVA_HOME='C:\Program Files\Java\jdk-21'
  & 'C:\Users\admin\AppData\Local\apache-maven-3.9.9\apache-maven-3.9.9\bin\mvn.cmd' -B -ntp clean package
  ```
- **文件操作一律用 PowerShell 或专用工具（Read/Write/Edit/Glob/Grep）**。
  ⚠️ **不要用 Bash 工具做项目文件的删/查**（rm/find/cat/ls）——本机 Bash 看到的是与真实磁盘不一致的视图，会导致严重状态误判。Bash 仅用于无副作用的只读探查，且结果需用 PowerShell 复核。

---

## 四、AI 必须遵守的开发流程（铁律）

> 以下为**强制**步骤。违反任何一条都视为未完成工作。

### 1. 小步快跑，每步必编译
- **每写完一组相关文件（一个功能/一个包），立即编译验证**：`mvn -B -ntp clean compile`，确认 `EXITCODE=0` 才继续。
- **绝不**连续写十几个文件却不编译。引用了尚未创建的类时，必须在同一轮把它补齐并编译通过，不能留下「编译不过」的中间状态。

### 2. 工具调用批次纪律（重要，曾因此浪费大量 token）
- **不要把高风险命令（首次 mvn、网络下载、可能失败的 PowerShell）和重要的 Write 放在同一个并行批次里。**
  并行批次中**任一调用失败会取消整批**，已写好的 Write 会被一起取消并产生混乱的乱序结果，极难排查。
- 高风险/探测性命令**单独执行**，确认成功后，再批量写文件。

### 3. 编辑前先读、改后必编译
- `Edit` 前若不确定原文精确内容（尤其含中文注释），**先 Read 该处**再改，避免 old_string 不匹配导致静默失败。
- 编译通过 ≠ 功能接通：新增的字段/回调/setter 若没有真正被调用，编译也会过。**改完要确认调用链真的接上了**（例如回调要在对应位置 `.run()`）。

### 4. 诚实报告
- 只声称**实际验证过**的结论。编译过就说编译过；**没在真实服务器加载测试过，就要明确说明**，不得暗示已通过运行测试。
- 失败、跳过、占位实现都要如实说明（如 WorldGuard 为占位 Hook、BlueMap/Dynmap 为占位 Provider）。

### 5. Git 工作流：一切改动必须走 Pull Request（强制）

> **绝对禁止直接向 `main` 分支 commit 或 push。** 所有代码改动，无论大小，都必须经由「功能分支 → PR → 审核合并」流程。

标准流程：

1. **永远先开分支，不在 main 上改**：
   ```powershell
   git checkout main; git pull          # 先同步最新 main
   git checkout -b feat/<简短描述>       # 如 feat/wind-tuning、fix/region-overlap、docs/readme
   ```
   分支命名前缀：`feat/`（功能）、`fix/`（修复）、`docs/`（文档）、`ci/`（CI）、`refactor/`（重构）、`chore/`（杂项）。

2. **在功能分支上提交**（提交信息结尾保留协作者署名）：
   ```
   Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
   ```

3. **推送功能分支并开 PR**（用 `gh` CLI；目标 base 分支为 `main`）：
   ```powershell
   git push -u origin feat/<简短描述>
   gh pr create --base main --head feat/<简短描述> --title "<标题>" --body "<说明>"
   ```
   PR 说明里写清：改了什么、为什么、如何验证（是否编译通过、是否服务器实测）。

4. **等 CI 通过**：PR 会触发 `build.yml`（完整打包）与 `codeql.yml`（静态分析）。
   **CI 红的 PR 不允许合并**，先修复再说。

5. **合并由用户决定**：AI **不得自行合并 PR**，也不得用 `--admin` 等方式绕过分支保护或 CI。是否合并、何时合并由用户拍板。

附加约束：
- **不要新增或改动 remote**；已有 `origin = git@github.com:adminlby/IslandStormWeatherPlus.git`。
- `.gitignore` 已忽略 `target/ *.jar .idea/ *.iml`；**不要提交构建产物或 IDE 文件**。
- 仅在用户明确要求时才执行 push / 开 PR；不要擅自发起。
- 若误在 main 上做了本地提交，**先停下告知用户**，再按其指示改用分支，不要直接 push 到 main。

### 6. 不偏离既定架构与决策
- 遵守第二节「关键决策」。要做与之冲突的改动前，**先问用户**。
- 新增功能沿用现有包结构与代码风格（注释密度、命名、强类型 ConfigManager getter）。

---

## 五、代码结构

```
io.lbynb.islandstorm
├── IslandStormPlugin            装配各管理器、启停任务、reload
├── command/StormCommand         /storm 全子指令 + TabComplete + 权限
├── config/ConfigManager         config.yml 强类型 getter（不缓存，reload 即时生效）
├── weather/                     WeatherType, WeatherState, WeatherManager, ForecastEntry, DangerLevel, VanillaSyncMode
├── wind/                        WindDirection, WindState, WindManager（effectiveWindAt 叠加区域+风暴）
├── time/                        TimeMode, ParsedDuration, GameTimeUtil（双轨解析换算）
├── forecast/                    ForecastManager(排期源), HourlyForecastEntry, HourlyForecastManager
├── region/                      WeatherRegion(矩形), RegionManager(regions.yml)
├── storm/                       StormPath(centerAt 插值), StormPathPoint, StormPathManager(influenceAt)
├── damage/                      BlockDamageManager, BlockDamageLevel, BlockDamageMode, StormBlockDamageEvent
├── map/                         MapProvider, VanillaMapProvider, BlueMap/Dynmap 占位, MapData
├── html/                        HtmlWeatherCardGenerator（预览卡 + 小时预报卡）
├── web/                         WebServerManager, WebAuthManager, WebUser, WebPermission, PasswordHasher,
│                                SessionToken, StaticFileHandler, api/*（ApiRouter + 各 Handler）
├── task/                        WeatherCycleTask, WindEffectTask, StormMovementTask, BlockDamageTask
└── util/MessageUtil             颜色/前缀/广播/messages.yml 文案
```

配置文件：`config.yml` `messages.yml` `regions.yml` `storm-paths.yml` `web-users.yml`（首启自动释放，`/storm reload` 生效，重载不重复启动任务）。

---

## 六、提交前自检清单

- [ ] **改动在功能分支上，不在 `main`**；通过 PR 合并，未直接 push main
- [ ] `mvn -B -ntp clean package` → `EXITCODE=0`，产出 `IslandStorm-1.0.0.jar`
- [ ] 新增/修改的字段、回调、监听是否真的被调用（不止编译过）
- [ ] 未提交 `target/`、`*.jar`、`.idea/`、`*.iml`
- [ ] 偏离需求处是否符合第二节「关键决策」
- [ ] 报告中区分了「已编译验证」与「未在服务器实测」
- [ ] PR 的 CI（build / codeql）全绿，未用 `--admin` 绕过
