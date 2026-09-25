# 原生插件系统（Plugin System）

> 目标：把「图像工具箱」这类原生能力**从 APK 编译产物中剥离**。实现放在独立 Gradle 模块，
> 打成 dex 插件 jar 发布到 Hub；卡片脚本声明 `requires.plugins`，宿主在运行前与卡片一起
> 下载、校验 sha256、用 `DexClassLoader` 加载成动态 bridge。应用侧只保留接口与派发器。

## 1. 产物格式

插件产物是一个普通 jar（扩展名可 `.jar`，服务端不打包 AAR）：

```
image-toolbox-1.0.0.jar
├── classes.dex                        # 必须存在（唯一要求的 dex 入口）
└── META-INF/khatkit-plugin.properties # 元数据（UTF-8，key=value）
    name=imageToolbox
    version=1.0.0
    entry=heizige.kk.khatkit.plugins.imageToolbox.ImageToolboxPlugin
```

- `classes.dex`：D8 产物。引用宿主类（如 `heizige.kk.khatkit.bridge.ImageToolboxBridge`）
  与 Kotlin 标准库时**不打进插件**，运行时由 App classloader 作为 `DexClassLoader` 的 parent 提供。
- `entry`：入口类全名。缺省约定 `heizige.kk.khatkit.plugins.<name>.<Name>Plugin`
  （`<name>` 原样、`<Name>` 首字母大写）。入口类构造器支持 `(android.content.Context)` 或
  无参；有 Context 构造器时宿主注入 `applicationContext`。
- `name` 同时是注册进 `BridgeRegistry` 的 bridge 名（如 `imageToolbox`），
  也是脚本里的调用名。命名规则：小写字母开头，后接字母/数字/下划线，允许 camelCase。

## 2. 卡片 manifest：`requires.plugins`

```json
{
  "name": "image_resize_crop",
  "version": "1.0.0",
  "engine": "lua",
  "entry": { "lua": "main.lua" },
  "requires": {
    "bridges": ["tool", "ui", "imageToolbox"],
    "plugins": [
      {
        "name": "imageToolbox",
        "version": "1.0.0",
        "sha256": "cdeb93b9...（64 位小写十六进制）",
        "url": "/api/plugins/imageToolbox/1.0.0"
      }
    ]
  }
}
```

- `name` / `version` 必填；`sha256` 必填且必须是 64 位小写十六进制（客户端与 Hub 校验都会拦截）。
- `url` 可选，**必须是相对 Hub 的路径（以 `/` 开头）**，用于自定义 CDN 前缀；留空时客户端使用
  `<hub>/api/plugins/<name>/<version>`。绝对 URL（第三方镜像）会被拒绝。
- 兼容性：旧卡片不带 `requires.plugins`，解析与校验完全不受影响。

## 3. 下载 / 校验 / 加载流程

```
运行卡片（AI / 用户 / 事件）
  └─ CardExecutor.ensurePlugins(CardManifest)
       ├─ 命中内存缓存（name+version+sha256）→ 直接复用
       ├─ filesDir/plugins/<name>/<version>.jar 已存在 → 校验 sha256
       │      └─ 不匹配：删除缓存，重新下载（防止被替换的旧产物）
       ├─ 下载（只从用户配置的 Hub；带看板进度「正在下载插件：…」）
       ├─ sha256 校验失败 → 中止执行，中文错误
       └─ DexClassLoader(jar, optimizedDir=codeCacheDir/plugin-dex/<name>/<sha前12位>,
              null, appClassLoader)
            ├─ 校验 jar 含 classes.dex
            ├─ 读 META-INF/khatkit-plugin.properties 的 entry（缺失用约定类名）
            ├─ 实例化入口类（Context / 无参构造器）
            └─ 包成 PluginBridgeWrapper → BridgeRegistry.registerDynamic(name, bridge)
```

关键实现：

| 组件 | 位置 | 说明 |
|---|---|---|
| `CardManifest.PluginReq` | `card-validator` | `name/version/url?/sha256`，纯 JVM 供卡片仓库 CI 复用 |
| `PluginManager` | `khatkit/…/plugin/PluginManager.kt` | 下载、sha256 校验、落盘、DexClassLoader 加载、实例缓存 |
| `PluginBridgeWrapper` | `khatkit/…/plugin/PluginBridgeWrapper.kt` | 动态 bridge：类型化方法 + 通用 `call`/`availableMethods` |
| `KhatKitPlugin` | `khatkit/…/plugin/KhatKitPlugin.kt` | 通用派发 SPI（`call(method, argsJson)`） |
| `BridgeRegistry.registerDynamic` | `khatkit/…/bridge/BridgeRegistry.kt` | 运行期注册 bridge；`availableBridges()` 随之上报能力 |
| `CardExecutor.ensurePlugins` | `khatkit/…/exec/CardExecutor.kt` | 执行前确保插件就绪，失败返回中文 `EngineResult.Err` |

脚本调用方式（二者等价，优先类型化）：

```lua
local out = imageToolbox.resize(path, 800, 0, true)      -- 类型化（插件实现 ImageToolboxBridge 时可用）
local out2 = imageToolbox.call("resize", '["' .. path .. '",800,0,true]')  -- 通用派发
local methods = imageToolbox.availableMethods()          -- JSON 数组字符串
```

### 中文错误

| code | 场景 |
|---|---|
| `PLUGIN_SHA256_INVALID` | manifest 里的 sha256 不是 64 位十六进制 |
| `PLUGIN_HUB_UNCONFIGURED` | 未配置 Hub 地址 |
| `PLUGIN_URL_INVALID` | url 不是 Hub 相对路径 |
| `PLUGIN_DOWNLOAD_FAILED` | 离线 / Hub 不可达 / HTTP 非 2xx |
| `PLUGIN_HASH_MISMATCH` | 下载产物 sha256 与声明不一致（拒绝落盘加载） |
| `PLUGIN_ARTIFACT_INVALID` | jar 缺少 classes.dex |
| `PLUGIN_LOAD_FAILED` | DexClassLoader / 入口类 / 构造器失败 |
| `PLUGIN_UNAVAILABLE` | 宿主不支持插件运行时 |

## 4. 能力协商与 Hub 索引

- `BridgeRegistry.availableBridges()` 始终包含 `plugin`，表示设备支持按需加载 dex 插件；
  加载后的插件名（如 `imageToolbox`）也会出现在能力集合里。
- Hub 侧 `KhatKitHubService.supportsCapability` 与客户端 `HubFilters.byCapability` 规则一致：
  卡片声明了 `requires.plugins` 且设备具备 `plugin` 能力时，对应插件 bridge 视为可按需获取，
  卡片对 AI 可见；真正运行前才下载插件。

## 5. 安全边界

1. **sha256 固定**：`manifest.requires.plugins[].sha256` 是唯一信任锚；下载后、加载前必须校验，
   不匹配直接拒绝，不做“宽松放行”。
2. **只信配置的 Hub**：`url` 只允许相对路径，解析到用户配置的 Hub 根；不支持任意镜像/裸 IP。
3. **私有目录**：产物只写 `filesDir/plugins/<name>/<version>.jar`，绝不从 `/sdcard` 等共享存储加载。
4. **格式约束**：jar 必须含 `classes.dex`；入口类名来自产物内 `khatkit-plugin.properties` 或约定。
5. **最小暴露**：插件通过动态 bridge 暴露的方法即 `PluginBridgeWrapper`/`KhatKitPlugin` 上的公有方法；
   入口类被实例化后只能通过 bridge 调用，不共享应用进程内的任意对象。
6. 插件本身是原生代码执行权限：Hub 审核（与服务端 `KhatKitCardValidator` 的插件规则一致）仍是最后一道闸门。

## 6. 构建与发布 imageToolbox 插件

```bash
# 1) 构建插件产物（D8 转换 + 写入 properties）
./gradlew :image-toolbox-plugin:buildPluginDex
# 产物：image-toolbox-plugin/build/plugin/image-toolbox-1.0.0.jar
# 控制台会打印字节数与 sha256

# 2) 上传到 Hub（需 admin/developer JWT）
curl -H "Authorization: Bearer <JWT>" \
     -F name=imageToolbox -F version=1.0.0 \
     -F file=@image-toolbox-plugin/build/plugin/image-toolbox-1.0.0.jar \
     https://<hub>/api/admin/plugins
# 返回 {"name":"imageToolbox","version":"1.0.0","sha256":"..."}

# 3) 校验下发
curl -o /tmp/plugin.jar https://<hub>/api/plugins/imageToolbox/1.0.0
sha256sum /tmp/plugin.jar
```

服务端行为（KodeHeadServer）：

- `GET /api/plugins/{name}/{version}`：公开下发产物；不存在返回 404 中文提示。
- `POST /api/admin/plugins`：multipart `name`/`version`/`file`；校验 jar 内含 classes.dex，
  落盘 `uploads/khatkit-plugins/<name>/<version>.jar`，并**自动回填**所有依赖该插件的卡片
  `requires.plugins[].sha256`，重建卡片包 hash（保证索引 hash 与下载包一致）。
- `GET /api/admin/plugins`：列出已上传产物及 sha256。
- 种子（`image_resize_crop` 等 4 张卡片）在启动时用 `KhatKitPluginStore.ref()` 计算
  `requires.plugins[].sha256`；**若产物尚未上传则 sha256 为空**，此时卡片的 `requires.bridges`
  仍含 `imageToolbox`，客户端会在能力协商/运行前给出明确错误。发布顺序应为：
  先 `buildPluginDex` + 上传产物，再让客户端拉取卡片包；或上传后调用一次卡片更新。
- `GET /api/cards` 列表与 `/api/khatkit/index.json` 索引均带 `plugins`，App 据此在运行前下载。

Gradle 任务细节：`buildPluginDex` 依赖 `:image-toolbox-plugin:assembleDebug`，从 AAR 取
`classes.jar` 交给 D8（`$ANDROID_HOME/build-tools/*/d8 --min-api 26`），jar 条目时间戳固定为 0，
保证同源码重复构建产物字节一致（sha256 稳定）。

## 7. 已知边界 / TODO

- 插件卸载/版本回收：当前只保留 `filesDir/plugins` 下按 name/version 的 jar，无自动清理策略。
  后续可按「最近 1 个版本 + 30 天」清理。
- 插件间依赖、权限声明（如 MANAGE_EXTERNAL_STORAGE 申请入口）由宿主统一提供，插件不单独声明。
- 非 imageToolbox 插件若只实现 `KhatKitPlugin`，脚本只能用 `call/availableMethods`；
  需要类型化 API 时在宿主侧新增接口并由插件实现（与本文件所述 imageToolbox 相同）。
