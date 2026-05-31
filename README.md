# IslandStorm 高级人工天气系统

为 **100 小时荒岛生存直播服务器** 打造的 Paper 1.20.6 / Java 21 天气插件。
天气**完全由导演/管理员人工控制**（指令 / 后台 / 网页控制台 / 配置预设），
**不接入外部天气 API、不做现实天气换算、不做随机生成算法**。

> 兼容 Paper / Purpur / Leaf 1.20.6。构建工具为 **Maven**（非 Gradle）。

---

## 功能总览

- **9 种天气**：CLEAR / CLOUDY / RAIN / HEAVY_RAIN / THUNDERSTORM / FOG / WINDY / TYPHOON / EXTREME_STORM，每种自带显示名、图标、默认风、危险等级、能见度、破坏属性。
- **独立风系统**：风速（km/h）+ 八方位风向；影响地面移动、鞘翅飞行（顺/逆/侧风）、极端风吹飞；性能友好（定时任务 + 限幅，可绕过）。
- **原版天气同步（混合模式）**：每种天气可配 `sync-vanilla-weather`，支持 全局同步 / 区域同步 / 完全独立。
- **区域天气**：矩形区域独立天气/风/危险等级/时长/方块破坏，存 `regions.yml`，可指令或网页画框创建。
- **台风 / 极端风暴路径**：多点路径随时间移动，半径内增强风（越近中心越强），存 `storm-paths.yml`。
- **方块破坏**（默认关闭）：分批抽查、概率、黑/白名单、可取消的 `StormBlockDamageEvent`、WorldGuard 占位 Hook。
- **双轨时间系统**：所有时长/到达/预警/预报均支持 **REAL_TIME** 与 **MC_TIME**。
- **天气预报 / 排期**：导演预排时间线，当前天气到期自动切下一条（排期空则回落默认）。
- **小时级预报**：未来 24 小时，由排期时间线渲染。
- **HTML 天气卡**：赛事风 `weather-preview.html` + 央视风 `hourly-forecast.html`，自包含内联 CSS、16:9、适配 OBS。
- **网页控制台**：内置 HTTP 服务（JDK 自带）、登录鉴权、REST API、2D 地图画框建区域/风暴。

---

## 编译

需要 JDK 21 与 Maven。

```bash
mvn clean package
```

产物：`target/IslandStorm-1.0.0.jar`（已 shade，Gson 重定位到 `io.lbynb.islandstorm.lib.gson`，无需额外依赖）。

> 也可直接用 IntelliJ IDEA 打开本目录（识别为 Maven 项目），Maven 面板执行 `package`。

## 安装

把 `target/IslandStorm-1.0.0.jar` 放进服务器 `plugins/` 文件夹，然后重启服务器。
首次启动会在 `plugins/IslandStorm/` 释放默认配置，并**自动创建网页用户 `admin`，在控制台打印随机临时密码**（请尽快修改）。

---

## 时间系统：REAL_TIME 与 MC_TIME

所有涉及时间的功能都是**双轨制**：

- **REAL_TIME（现实时间）**：按服务器真实时钟计时。单位 `s` / `m` / `h` / `d`。
- **MC_TIME（Minecraft 时间）**：按游戏内时间计时。单位 `tick`/`ticks` / `mcminute` / `mchour` / `mcday`。

换算（与原版一致，按 20 TPS）：

```
1 MC 天   = 24000 ticks      （可在 config.time.minecraft-day-ticks 调整）
1 MC 小时 = 1000 ticks
1 MC 分钟 ≈ 16.6667 ticks
1 tick    = 50 毫秒（现实）
```

**时间格式示例：**

| 写法 | 含义 |
|---|---|
| `30s` | 现实 30 秒 |
| `30m` | 现实 30 分钟 |
| `1h` | 现实 1 小时 |
| `1d` | 现实 1 天 |
| `1200ticks` | MC 1200 tick |
| `1mcminute` | MC 1 分钟 |
| `6mchour` | MC 6 小时 |
| `1mcday` | MC 1 天 |

不带单位的纯数字（如 `30`）按 `config.time.default-mode` 解释；也可显式追加 `REAL_TIME` / `MC_TIME`。

示例：
```
/storm setweather HEAVY_RAIN 30m REAL_TIME   # 现实 30 分钟暴雨
/storm setweather HEAVY_RAIN 6h MC_TIME       # MC 6 小时暴雨
/storm setweather CLEAR 1mcday MC_TIME        # MC 一天晴
/storm path addpoint typhoon-001 -500 200 10m REAL_TIME
```

---

## 指令

主指令 `/islandstorm`，别名 `/storm`、`/weatherplus`、`/isweather`。

### 基础
```
/storm help
/storm reload
/storm status
/storm setweather <类型> [时长] [REAL_TIME|MC_TIME]
/storm setwind <风速km/h> <风向 N/NE/E/SE/S/SW/W/NW>
/storm forecast                       # 查看排期
/storm forecast hourly                # 查看小时预报
/storm forecast add <类型> <时长> [模式]   # 追加排期
/storm forecast clear                 # 清空排期
/storm html [preview|forecast|all]    # 生成 HTML 卡
/storm bypass <玩家>                   # 切换玩家是否受风影响
/storm debug
```

### 区域
```
/storm region list
/storm region create <名称> <世界> <minX> <minZ> <maxX> <maxZ> <天气类型>
/storm region delete <名称>
/storm region setweather <名称> <天气类型>
/storm region setwind <名称> <风速> <风向>
/storm region damage <名称> <true|false> <等级0-5>
```

### 风暴路径
```
/storm path list
/storm path create <id> <TYPHOON|EXTREME_STORM> <世界> <半径>
/storm path addpoint <id> <x> <z> <到达时间> [REAL_TIME|MC_TIME]
/storm path start <id>
/storm path stop <id>
/storm path delete <id>
```

### 网页用户
```
/storm webuser list
/storm webuser create <用户名> <密码>
/storm webuser passwd <用户名> <新密码>
/storm webuser delete <用户名>
/storm webuser grant <用户名> <权限节点>
/storm webuser revoke <用户名> <权限节点>
```

所有子指令均带 TabComplete 与错误提示。

---

## Bukkit 权限节点

| 节点 | 说明 | 默认 |
|---|---|---|
| `islandstorm.admin` | 全部权限 | op |
| `islandstorm.reload` | 重载插件 | op |
| `islandstorm.status` | 查看天气状态 | 所有人 |
| `islandstorm.setweather` | 更改天气 / 排期 | op |
| `islandstorm.setwind` | 更改风速风向 | op |
| `islandstorm.forecast` | 查看预报 | 所有人 |
| `islandstorm.html` | 生成 HTML 卡 | op |
| `islandstorm.bypass` | 绕过风影响 | op |
| `islandstorm.debug` | 查看调试信息 | op |
| `islandstorm.region` | 管理区域天气 | op |
| `islandstorm.path` | 管理风暴路径 | op |
| `islandstorm.webuser` | 管理网页用户 | op |

---

## 网页控制台

- 配置见 `config.yml` 的 `web` 段：默认 `0.0.0.0:8765`，可开关、改地址端口、改令牌过期时间。
- 前端自动释放到 `plugins/IslandStorm/web/`。
- 打开 `http://<服务器IP>:8765/` → 登录 → 控制台。
- 功能：查看/设置全局天气与风、小时预报、区域增删改（含地图画框）、风暴路径查看与地图建点、生成 HTML、用户管理、查看在线人数/TPS/世界列表/2D 地图。

> ⚠️ **安全提醒**：默认监听 `0.0.0.0` 会暴露到公网。请务必配置防火墙或反向代理（如仅内网/加 HTTPS 反代），并第一时间修改默认 `admin` 密码。

### 密码与令牌机制

- 密码使用 **JDK 原生 PBKDF2WithHmacSHA256** 哈希存储（格式 `PBKDF2$sha256$<迭代>$<盐>$<哈希>`），**绝不明文落盘**。
- 登录成功后发放随机令牌（仅存内存，**服务器重启即失效**），过期时间由 `web.token-expire-minutes` 控制。
- 所有 API（除 `/api/login`）都要求有效令牌，且按权限节点校验。

### 如何生成密码 / 创建用户

无需手动算哈希——用游戏内或后台指令即可，插件会自动哈希：

```
/storm webuser create director 我的密码
/storm webuser grant director weather.set
/storm webuser passwd director 新密码
```

首次启动若 `web-users.yml` 为空，会自动生成 `admin` 并在控制台打印一次性随机临时密码。

### 网页权限节点

```
weather.view  weather.set  wind.view  wind.set  forecast.view
region.view   region.create region.edit region.delete
storm.path.view storm.path.edit blockdamage.view blockdamage.edit
html.generate server.status user.manage map.view
```
持有 `*` 表示拥有全部网页权限。

### REST API（均返回 JSON，需 `Authorization: Bearer <token>`）

```
POST /api/login          POST /api/logout         GET  /api/status
GET  /api/weather        POST /api/weather/set    POST /api/wind/set
GET  /api/forecast       GET  /api/forecast/hourly
GET  /api/regions        POST /api/regions/create POST /api/regions/update  POST /api/regions/delete
GET  /api/worlds         GET  /api/map?world=world
GET  /api/storm/path     POST /api/storm/path/set
POST /api/html/generate  POST /api/html/generate/hourly  POST /api/html/generate/all
GET  /api/users          POST /api/users/create   POST /api/users/password  POST /api/users/delete
```

---

## HTML 天气卡

- `plugins/IslandStorm/html/weather-preview.html` —— 赛事风当前天气预览卡。
- `plugins/IslandStorm/html/hourly-forecast.html` —— 央视风未来 24 小时逐小时预报卡（同时显示现实时间与 MC 时间）。
- 两个文件均自包含内联 CSS、不依赖任何 CDN，16:9 横向布局，带自动刷新，**直接作为 OBS 浏览器源**即可。
- 生成方式：`/storm html preview` / `/storm html forecast` / `/storm html all`，或网页控制台按钮，或天气变化时自动生成（`html.auto-generate-on-weather-change`）。

---

## 方块破坏（默认关闭）

仅 TYPHOON / EXTREME_STORM 默认允许；区域与风暴路径可独立开关。开启需在 `config.yml` 设 `block-damage.enabled: true`。

- 性能安全：定时分批，仅在「处于破坏区域内的玩家」周围抽查少量方块，不做大范围扫描。
- 可配置：每轮检查数、概率（按等级 1-5）、Y 范围、模式（AIR / DROP / FALLING_BLOCK）、黑名单、白名单。
- 默认硬保护：箱子、潜影盒、床、命令方块、刷怪笼、屏障、结构方块、末影箱、基岩等。
- 破坏前触发 `StormBlockDamageEvent`，其他插件可监听取消。
- 检测到 WorldGuard 时预留保护 Hook（第一版为占位，不强依赖）。

---

## 配置文件

`config.yml`（主配置）、`messages.yml`（文案）、`regions.yml`（区域）、`storm-paths.yml`（风暴路径）、`web-users.yml`（网页用户）。
首次启动自动释放，修改后 `/storm reload` 生效（重载不会重复启动任务）。

---

## 代码结构

```
io.lbynb.islandstorm
├── IslandStormPlugin
├── command/StormCommand
├── config/ConfigManager
├── weather/   (WeatherType, WeatherState, WeatherManager, ForecastEntry, DangerLevel, VanillaSyncMode)
├── wind/      (WindDirection, WindState, WindManager)
├── time/      (TimeMode, ParsedDuration, GameTimeUtil)
├── forecast/  (ForecastManager, HourlyForecastEntry, HourlyForecastManager)
├── region/    (WeatherRegion, RegionManager)
├── storm/     (StormPath, StormPathPoint, StormPathManager)
├── damage/    (BlockDamageManager, BlockDamageLevel, BlockDamageMode, StormBlockDamageEvent)
├── map/       (MapProvider, VanillaMapProvider, BlueMapProviderPlaceholder, DynmapProviderPlaceholder, MapData)
├── html/      (HtmlWeatherCardGenerator)
├── web/       (WebServerManager, WebAuthManager, WebUser, WebPermission, PasswordHasher, SessionToken,
│               StaticFileHandler, api/*)
├── task/      (WeatherCycleTask, WindEffectTask, StormMovementTask, BlockDamageTask)
└── util/      (MessageUtil)
```
