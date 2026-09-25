# 原生依赖包系统（Dependency System）

> 目标：把「图像工具箱」这类原生能力**从 APK 编译产物中剥离**。实现放在独立 Gradle 模块，
> 打成 dex 依赖 jar 发布到 Hub；卡片脚本声明 `requires.dependencies`，宿主在运行前与卡片一起
> 下载、校验 sha256、用 `DexClassLoader` 加载成动态 bridge。应用侧只保留接口与派发器。
>
> 命名说明：本体系只叫「依赖包 / dependency」。「插件 / plugin」这个名字留给未来的**前端插件市场**
> （可交互 UI 扩展），二者不是一回事。

## 1. 产物格式

依赖产物是一个普通 jar（扩展名可 `.jar`，服务端不打包 AAR）：

```
image-toolbox-1.0.0.jar
├── classes.dex                            # 必须存在（唯一要求的 dex 入口）
└── META-INF/khatkit-dependency.properties # 元数据（UTF-8，key=value）
    name=imageToolbox
    version=1.0.0
    entry=heizige.kk.khatkit.dependencies.imageToolbox.ImageToolboxDependency
```

- `classes.dex`：D8 产物。引用宿主类（如 `heizige.kk.khatkit.bridge.ImageToolboxBridge`）
  与 Kotlin 标准库时**不打进依赖产物**，运行时由 App classloader 作为 `DexClassLoader` 的 parent 提供。
- `entry`：入口类全名。缺省约定 `heizige.kk.khatkit.dependencies.<name>.<Name>Dependency`
  （`<name>` 原样、`<Name>` 首字母大写）。入口类构造器支持 `(android.content.Context)` 或
  无参；有 Context 构造器时宿主注入 `applicationContext`。
- `name` 同时是注册进 `BridgeRegistry` 的 bridge 名（如 `imageToolbox`），
  也是脚本里的调用名。命名规则：小写字母开头，后接字母/数字/下划线，允许 camelCase。

## 2. 卡片 manifest：`requires.dependencies`

```json
{
  "name": "image_resize_crop",
  "version": "1.0.0",
  "engine": "lua",
  "entry": { "lua": "main.lua" },
  "requires": {
    "bridges": ["tool", "ui", "imageToolbox"],
    "dependencies": [
      {
        "name": "imageToolbox",
        "version": "1.0.0",
        "sha256": "cdeb93b9...（64 位小写十六进制）",
        "url": "/api/dependencies/imageToolbox/1.0.0"
      }
    ]
  }
}
```

- `name` / `version` 必填；`sha256` 必填且必须是 64 位小写十六进制（客户端与 Hub 校验都会拦截）。
- `url` 可选，**必须是相对 Hub 的路径（以 `/` 开头）**，用于自定义 CDN 前缀；留空时客户端使用
  `<hub>/api/dependencies/<name>/<version>`。绝对 URL（第三方镜像）会被拒绝。
- 兼容性：旧卡片不带 `requires.dependencies`，解析与校验完全不受影响；旧字段
  `requires.plugins` 仅在服务端市场读 manifest 时作为回退兼容，客户端只认新字段。

## 3. 下载 / 校验 / 加载流程

```
运行卡片（AI / 用户 / 事件）
  └─ CardExecutor.ensureDependencies(CardManifest)
       ├─ 命中内存缓存（name+version+sha256）→ 直接复用
       ├─ filesDir/dependencies/<name>/<version>.jar 已存在 → 校验 sha256
       │      └─ 不匹配：删除缓存，重新下载（防止被替换的旧产物）
       ├─ 下载（只从用户配置的 Hub；带看板进度「正在下载依赖包：…」）
       ├─ sha256 校验失败 → 中止执行，中文错误
       └─ DexClassLoader(jar, optimizedDir=codeCacheDir/dependency-dex/<name>/<sha前12位>,
              null, appClassLoader)
            ├─ 校验 jar 含 classes.dex
            ├─ 读 META-INF/khatkit-dependency.properties 的 entry（缺失用约定类名）
            ├─ 实例化入口类（Context / 无参构造器）
            └─ 包成 DependencyBridgeWrapper → BridgeRegistry.registerDynamic(name, bridge)
```

关键实现：

| 组件 | 位置 | 说明 |
|---|---|---|
| `CardManifest.DependencyReq` | `card-validator` | `name/version/url?/sha256`，纯 JVM 供卡片仓库 CI 复用 |
| `DependencyManager` | `khatkit/…/dependency/DependencyManager.kt` | 下载、sha256 校验、落盘、DexClassLoader 加载、实例缓存 |
| `DependencyBridgeWrapper` | `khatkit/…/dependency/DependencyBridgeWrapper.kt` | 动态 bridge：类型化方法 + 通用 `call`/`availableMethods` |
| `KhatKitDependency` | `khatkit/…/dependency/KhatKitDependency.kt` | 通用派发 SPI（`call(method, argsJson)`） |
| `BridgeRegistry.registerDynamic` | `khatkit/…/bridge/BridgeRegistry.kt` | 运行期注册 bridge；`availableBridges()` 随之上报能力 |
| `CardExecutor.ensureDependencies` | `khatkit/…/exec/CardExecutor.kt` | 执行前确保依赖包就绪，失败返回中文 `EngineResult.Err` |

脚本调用方式（二者等价，优先类型化）：

```lua
local out = imageToolbox.resize(path, 800, 0, true)      -- 类型化（依赖实现 ImageToolboxBridge 时可用）
local out2 = imageToolbox.call("resize", '["' .. path .. '",800,0,true]')  -- 通用派发
local methods = imageToolbox.availableMethods()          -- JSON 数组字符串
```

### 中文错误

| code | 场景 |
|---|---|
| `DEPENDENCY_SHA256_INVALID` | manifest 里的 sha256 不是 64 位十六进制 |
| `DEPENDENCY_HUB_UNCONFIGURED` | 未配置 Hub 地址 |
| `DEPENDENCY_URL_INVALID` | url 不是 Hub 相对路径 |
| `DEPENDENCY_DOWNLOAD_FAILED` | 离线 / Hub 不可达 / HTTP 非 2xx |
| `DEPENDENCY_HASH_MISMATCH` | 下载产物 sha256 与声明不一致（拒绝落盘加载） |
| `DEPENDENCY_ARTIFACT_INVALID` | jar 缺少 classes.dex |
| `DEPENDENCY_LOAD_FAILED` | DexClassLoader / 入口类 / 构造器失败 |
| `DEPENDENCY_UNAVAILABLE` | 宿主不支持依赖包运行时 |

## 4. 能力协商与 Hub 索引

- `BridgeRegistry.availableBridges()` 始终包含 `dependency`，表示设备支持按需加载 dex 依赖包；
  加载后的依赖名（如 `imageToolbox`）也会出现在能力集合里。
- Hub 侧 `KhatKitHubService.supportsCapability` 与客户端 `HubFilters.byCapability` 规则一致：
  卡片声明了 `requires.dependencies` 且设备具备 `dependency` 能力时，对应依赖 bridge 视为可按需获取，
  卡片对 AI 可见；真正运行前才下载依赖包。服务端同时接受旧客户端上报的 `plugin` 能力字符串。
- 索引 JSON 字段为 `dependencies`（与 manifest 一致）。

## 5. 安全边界

1. **sha256 固定**：`manifest.requires.dependencies[].sha256` 是唯一信任锚；下载后、加载前必须校验，
   不匹配直接拒绝，不做“宽松放行”。
2. **只信配置的 Hub**：`url` 只允许相对路径，解析到用户配置的 Hub 根；不支持任意镜像/裸 IP。
3. **私有目录**：产物只写 `filesDir/dependencies/<name>/<version>.jar`，绝不从 `/sdcard` 等共享存储加载。
4. **格式约束**：jar 必须含 `classes.dex`；入口类名来自产物内 `khatkit-dependency.properties` 或约定。
5. **最小暴露**：依赖通过动态 bridge 暴露的方法即 `DependencyBridgeWrapper`/`KhatKitDependency` 上的公有方法；
   入口类被实例化后只能通过 bridge 调用，不共享应用进程内的任意对象。
6. 依赖本身是原生代码执行权限：Hub 审核（与服务端 `KhatKitCardValidator` 的依赖规则一致）仍是最后一道闸门。

## 6. 构建与发布 imageToolbox 依赖包

```bash
# 1) 构建依赖产物（D8 转换 + 写入 properties）
./gradlew :image-toolbox-dependency:buildDependencyDex
# 产物：image-toolbox-dependency/build/dependency/image-toolbox-1.0.0.jar
# 控制台会打印字节数与 sha256

# 2) 上传到 Hub（需 admin/developer JWT）
curl -H "Authorization: Bearer <JWT>" \
     -F name=imageToolbox -F version=1.0.0 \
     -F file=@image-toolbox-dependency/build/dependency/image-toolbox-1.0.0.jar \
     https://<hub>/api/admin/dependencies
# 返回 {"name":"imageToolbox","version":"1.0.0","sha256":"..."}

# 3) 校验下发
curl -o /tmp/dependency.jar https://<hub>/api/dependencies/imageToolbox/1.0.0
sha256sum /tmp/dependency.jar
```

服务端行为（KodeHeadServer）：

- `GET /api/dependencies/{name}/{version}`：公开下发产物；不存在返回 404 中文提示。
- `GET /api/plugins/{name}/{version}`：**旧路径兼容别名**，下发同一文件并带 `Deprecation: true`
  与 `Link: </api/dependencies/...>; rel="successor-version"` 响应头；新客户端请使用新路径。
- `POST /api/admin/dependencies`：multipart `name`/`version`/`file`；校验 jar 内含 classes.dex，
  落盘 `uploads/khatkit-dependencies/<name>/<version>.jar`（旧目录 `uploads/khatkit-plugins`
  首次启动时自动整体改名迁移，产物不丢），并**自动回填**所有依赖该依赖包的卡片
  `requires.dependencies[].sha256`，重建卡片包 hash（保证索引 hash 与下载包一致）。
- `GET /api/admin/dependencies`：列出已上传产物及 sha256。
- 种子（`image_resize_crop` 等 6 张卡片）在启动时用 `KhatKitDependencyStore.ref()` 计算
  `requires.dependencies[].sha256`；**若产物尚未上传则 sha256 为空**，此时卡片的 `requires.bridges`
  仍含 `imageToolbox`，客户端会在能力协商/运行前给出明确错误。发布顺序应为：
  先 `buildDependencyDex` + 上传产物，再让客户端拉取卡片包；或上传后调用一次卡片更新。
- `GET /api/khatkit/index.json` 索引带 `dependencies`，App 据此在运行前下载。
- 数据库列 `khatkit_cards.plugins` 已重命名为 `khatkit_cards.dependencies`：启动时按列存在性
  自动 `RENAME COLUMN`（若两列并存则先回填数据再删旧列），不会丢历史数据。

Gradle 任务细节：`buildDependencyDex` 依赖 `:image-toolbox-dependency:assembleDebug`，从 AAR 取
`classes.jar` 交给 D8（`$ANDROID_HOME/build-tools/*/d8 --min-api 26`），jar 条目时间戳固定为 0，
保证同源码重复构建产物字节一致（sha256 稳定）。

## 7. 已知边界 / TODO

- 依赖卸载/版本回收：当前只保留 `filesDir/dependencies` 下按 name/version 的 jar，无自动清理策略。
  后续可按「最近 1 个版本 + 30 天」清理。
- 依赖间依赖、权限声明（如 MANAGE_EXTERNAL_STORAGE 申请入口）由宿主统一提供，依赖不单独声明。
- 非 imageToolbox 依赖若只实现 `KhatKitDependency`，脚本只能用 `call/availableMethods`；
  需要类型化 API 时在宿主侧新增接口并由依赖实现（与本文件所述 imageToolbox 相同）。
