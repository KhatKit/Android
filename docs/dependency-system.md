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
        "version": "1.2.0",
        "sha256": "cdeb93b9...（64 位小写十六进制）",
        "signature": "MEUCIQ...（产物字节的 ECDSA P-256 分离签名，DER base64）",
        "url": "/api/dependencies/imageToolbox/1.2.0"
      },
      {
        "name": "imageToolbox",
        "version": "1.1.0",
        "sha256": "77aa01...",
        "signature": "MEUCIQ...（备用版本签名）"
      }
    ]
  }
}
```

- `name` / `version` 必填；`sha256` 必填且必须是 64 位小写十六进制（客户端与 Hub 校验都会拦截）。
- `signature` 必填（客户端加载前强制校验；Hub 上传/种子时会自动回填，缺失时卡片审核只给 warning）。
- **同名依赖允许声明多个版本**（按顺序 = 回滚清单）：首选版本被 Hub 撤销（410）或下架（404）时，
  宿主自动尝试下一个候选版本；每个候选都仍要过 sha256 + 签名校验，不会放宽安全边界。
- `url` 可选，**必须是相对 Hub 的路径（以 `/` 开头）**，用于自定义 CDN 前缀；留空时客户端使用
  `<hub>/api/dependencies/<name>/<version>`。绝对 URL（第三方镜像）会被拒绝。
- 兼容性：旧卡片不带 `requires.dependencies`，解析与校验完全不受影响；旧字段
  `requires.plugins` 仅在服务端市场读 manifest 时作为回退兼容，客户端只认新字段。

## 3. 下载 / 校验 / 加载流程

```
运行卡片（AI / 用户 / 事件）
  └─ CardExecutor.ensureDependencies(CardManifest)
       ├─ 按 name 分组，取清单中第一个候选版本（被撤销/下架的候选自动回退到下一个）
       ├─ 命中内存缓存（name+version+sha256）→ 直接复用
       ├─ filesDir/dependencies/<name>/<version>.jar 已存在
       │      → 校验 sha256 + ECDSA 签名
       │      ├─ 任一不匹配：删除缓存，重新下载（防止被替换的旧产物）
       │      └─ 匹配：复用只读产物，不重写（已加载版本不重复落盘）
       ├─ 在线核对服务端 /api/dependencies/pubkey 指纹（best-effort；不一致 → 拒绝加载）
       ├─ 下载（只从用户配置的 Hub；带看板进度「正在下载依赖包：…」）
       ├─ sha256 校验失败 → 中止执行，中文错误
       ├─ ECDSA 签名校验（固定公钥）：失败 → 拒绝落盘加载，中文错误
       ├─ 落盘：write + fsync + 原子改名 → 落盘后复核 sha256 + 签名 → setReadOnly
       │      并复核 canWrite()==false；父目录收紧为「仅属主可读、不可写」
       │      （Android 14+ 动态代码加载 W^X 要求，否则 DexClassLoader 被系统拦截）
       └─ DexClassLoader(只读 jar, optimizedDir=codeCacheDir/dependency-dex/<name>/<sha前12位>,
              null, appClassLoader)
            ├─ 校验 jar 含 classes.dex
            ├─ 读 META-INF/khatkit-dependency.properties 的 entry（缺失用约定类名）
            ├─ 实例化入口类（Context / 无参构造器）
            └─ 包成 DependencyBridgeWrapper → BridgeRegistry.registerDynamic(name, bridge)
```

关键实现：

| 组件 | 位置 | 说明 |
|---|---|---|
| `CardManifest.DependencyReq` | `card-validator` | `name/version/url?/sha256/signature`，纯 JVM 供卡片仓库 CI 复用 |
| `PinnedDependencyKey` | `khatkit/…/dependency/PinnedDependencyKey.kt` | 构建时固定的签名公钥（base64 DER）与指纹 |
| `DependencySignature` | `khatkit/…/dependency/DependencySignature.kt` | ECDSA P-256 / SHA256withECDSA 验签（纯 JVM） |
| `DependencyFallback` | `khatkit/…/dependency/DependencyFallback.kt` | 同名多版本分组、410/404 错误分类（可回滚） |
| `DependencyManager` | `khatkit/…/dependency/DependencyManager.kt` | 下载、sha256 + 签名校验、落盘、DexClassLoader 加载、实例缓存 |
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
| `DEPENDENCY_SIGNATURE_MISSING` | manifest 里的依赖缺少 signature（客户端拒绝加载，提示更新卡片） |
| `DEPENDENCY_SIGNATURE_INVALID` | 签名不是合法 base64，或验签失败（产物/签名被篡改，拒绝落盘加载） |
| `DEPENDENCY_PUBKEY_INVALID` | App 内置固定公钥非法（构建配置错误，拒绝一切依赖） |
| `DEPENDENCY_PUBKEY_MISMATCH` | 服务端 `/api/dependencies/pubkey` 指纹与内置固定指纹不一致（疑似中间人或密钥轮换） |
| `DEPENDENCY_REVOKED` | 服务端返回 410：该版本已被撤销；同名有备用版本时自动回退 |
| `DEPENDENCY_VERSION_UNAVAILABLE` | 服务端返回 404：版本不存在/已下架；同名有备用版本时自动回退 |
| `DEPENDENCY_HUB_UNCONFIGURED` | 未配置 Hub 地址 |
| `DEPENDENCY_URL_INVALID` | url 不是 Hub 相对路径 |
| `DEPENDENCY_DOWNLOAD_FAILED` | 离线 / Hub 不可达 / HTTP 非 2xx |
| `DEPENDENCY_HASH_MISMATCH` | 下载产物 sha256 与声明不一致（拒绝落盘加载） |
| `DEPENDENCY_ARTIFACT_INVALID` | jar 缺少 classes.dex |
| `DEPENDENCY_STORAGE_FAILED` | 应用私有目录不可写 |
| `DEPENDENCY_READONLY_FAILED` | 产物无法置为只读（Android 14+ 要求代码文件只读） |
| `DEPENDENCY_DEX_DIR_UNWRITABLE` | dex 优化目录（codeCacheDir）不可写 |
| `DEPENDENCY_LOAD_BLOCKED` | 加载被系统拦截（Android 14+ W^X 动态代码加载限制）；已有内存副本时自动复用 |
| `DEPENDENCY_LOAD_FAILED` | DexClassLoader / 入口类 / 构造器失败 |
| `DEPENDENCY_UNAVAILABLE` | 宿主不支持依赖包运行时 |

## 4. 能力协商与 Hub 索引

- `BridgeRegistry.availableBridges()` 始终包含 `dependency`，表示设备支持按需加载 dex 依赖包；
  加载后的依赖名（如 `imageToolbox`）也会出现在能力集合里。
- Hub 侧 `KhatKitHubService.supportsCapability` 与客户端 `HubFilters.byCapability` 规则一致：
  卡片声明了 `requires.dependencies` 且设备具备 `dependency` 能力时，对应依赖 bridge 视为可按需获取，
  卡片对 AI 可见；真正运行前才下载依赖包。服务端同时接受旧客户端上报的 `plugin` 能力字符串。
- 索引 JSON 字段为 `dependencies`（与 manifest 一致）。

## 5. 信任模型（签名 / 撤销 / 回滚）

### 5.1 信任锚：App 内置固定公钥

- 服务端持有 ECDSA P-256 私钥（`KHATKIT_SIGNING_KEY` 环境变量，PKCS#8 base64；未配置时首次启动
  自动生成并持久化到 `uploads/khatkit-keys/signing.pk8`），公钥与指纹见 `GET /api/dependencies/pubkey`。
- App 在**构建时固定**公钥与指纹（`PinnedDependencyKey`），不信任运行时从服务端拉取的公钥；
  运行时拉取 `pubkey` 只用于**核对**（指纹不一致 → 直接拒绝加载，`DEPENDENCY_PUBKEY_MISMATCH`）。
- 仓库内置的固定公钥是 **KServer 开发环境**密钥（指纹 `d1e65fea…e75f`，对应
  `uploads/khatkit-keys/` 自动生成的密钥对），仅供联调；**生产发布前必须把
  `PinnedDependencyKey.PUBLIC_KEY_BASE64 / FINGERPRINT_SHA256` 换成生产服务端
  `KHATKIT_SIGNING_KEY` 对应的公钥与指纹，并保证部署时设置同一私钥**，否则 App 会拒绝加载。
- 更换发布密钥必须走 App 发版；旧 App 会拒绝新密钥签名的依赖，这是有意为之。

### 5.2 双校验：sha256 + ECDSA

1. `manifest.requires.dependencies[].sha256`：防传输损坏 / CDN 缓存错误 / 产物被替换；
2. `signature`（ECDSA P-256 / SHA256withECDSA，覆盖 jar 原始字节）：防**持有服务端存储/发布权限的
   攻击者**投毒——没有私钥就签不出能在客户端通过的产物；
3. 下载后、落盘后、加载前都要复核 sha256 + 签名；缓存产物签名不符会被删除并重新下载。

### 5.3 撤销与回滚

- 产物状态：`active + approved` 才下发；`revoked`（停用）返回 **410**（中文提示），未激活返回 403。
- 管理端 `POST /api/admin/dependencies/{name}/{version}/disable` 立即止血；
  `POST /api/admin/dependencies/{name}/{version}/enable` 可回滚恢复（例如误停用）。
  兼容旧别名 `revoke` / `activate`（语义相同）。
- 卡片清单可声明同名依赖的多个版本（回滚清单）。首选版本 410/404 时，App 自动按清单顺序尝试
  备用版本；签名/哈希失败**不会**回退（避免把安全异常当成可用性问题掩盖）。
- 卡片的依赖版本被撤销且清单里没有备用版本时：卡片运行被拒绝并给出中文提示，引导用户更新卡片
  或到市场安装最新版本；状态会发布到自动化看板。

### 5.4 攻击面：拥有服务器权限的攻击者能/不能做什么

| 攻击者能力 | 结果 |
|---|---|
| 改写 `uploads` 里的 jar 产物 | 无法通过 sha256 + 签名（无签名身份）；App 拒绝加载 |
| 用自己的密钥重签篡改后的产物 | 客户端固定公钥验签失败；卡片的 sha256 也需同步改写（卡片包 hash 变化会被发现） |
| 提供恶意 `/api/dependencies/pubkey` | 指纹与内置固定值不一致 → 拒绝加载；即使不核对，签名校验仍以内置公钥为准 |
| 撤销/删除产物（拒绝服务） | 无法投毒，只能导致依赖不可用；同名备用版本自动回退，否则明确报错 |
| 伪造卡片 manifest 指向恶意版本 | manifest 来自服务端且卡片包 hash 校验；即使篡改，恶意产物仍无有效签名 |
| 轮换服务端密钥 | 旧 App 拒绝加载新产物（设计如此），需发版更新固定公钥 |

### 5.5 其他边界

- 只信配置的 Hub：`url` 只允许相对路径，不支持任意镜像/裸 IP。
- 私有目录 + 只读：产物只写 `filesDir/dependencies/<name>/<version>.jar`，绝不从共享存储加载；
  落盘 fsync 后 `setReadOnly()` 并复核 `canWrite()==false`（Android 14+ W^X 要求）。
- 格式约束：jar 必须含 `classes.dex`；入口类名来自产物内 `khatkit-dependency.properties` 或约定。
- 最小暴露：依赖只通过 `DependencyBridgeWrapper`/`KhatKitDependency` 的公有方法暴露给脚本。
- Hub 人工审核 + 服务端 `KhatKitCardValidator` 依赖规则是第一道闸门；签名与固定公钥是第二道闸门。

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
# 返回 {"name":"imageToolbox","version":"1.0.0","sha256":"...","signature":"MEUCIQ..."}

# 3) 校验下发
curl -o /tmp/dependency.jar https://<hub>/api/dependencies/imageToolbox/1.0.0
sha256sum /tmp/dependency.jar
curl -s https://<hub>/api/dependencies/pubkey   # 公钥 + 指纹，与 App 内置固定值核对

# 4) 应急停用 / 启用（旧别名 revoke / activate 等价）
curl -X POST -H "Authorization: Bearer <ADMIN_JWT>" \
     https://<hub>/api/admin/dependencies/imageToolbox/1.0.0/disable
curl -X POST -H "Authorization: Bearer <ADMIN_JWT>" \
     https://<hub>/api/admin/dependencies/imageToolbox/1.0.0/enable
```

服务端行为（KodeHeadServer）：

- `GET /api/dependencies/pubkey`：公开签名公钥（X.509 DER base64）与 sha256 指纹；
  同时带 `X-KhatKit-Pubkey-Sha256` 响应头。
- `GET /api/dependencies/{name}/{version}`：公开下发产物；不存在返回 404，
  已撤销返回 **410**（中文提示），未激活返回 403。仅 `active + approved` 版本可下载。
- `GET /api/plugins/{name}/{version}`：**旧路径兼容别名**，下发同一文件并带 `Deprecation: true`
  与 `Link: </api/dependencies/...>; rel="successor-version"` 响应头；新客户端请使用新路径。
- `POST /api/admin/dependencies`：multipart `name`/`version`/`file`；校验 jar 内含 classes.dex，
  落盘 `uploads/khatkit-dependencies/<name>/<version>.jar`，用服务端私钥**签名**并置为
  `active + approved`（旧目录 `uploads/khatkit-plugins` 首次启动时自动整体改名迁移，产物不丢），
  同时**自动回填**所有依赖该依赖包的卡片 `requires.dependencies[].sha256 + signature`，
  重建卡片包 hash（保证索引 hash 与下载包一致）。
  启动时会检查所有历史产物：签名缺失或与当前公钥不符（密钥轮换）会自动重签。
- `POST /api/admin/dependencies/{name}/{version}/disable`：仅 admin；立即停止下发（410），可回滚。
  （兼容别名 `revoke`）
- `POST /api/admin/dependencies/{name}/{version}/enable`：仅 admin；回滚为 `active + approved`。
  （兼容别名 `activate`）
- `GET /api/admin/dependencies`：列出全部版本及 sha256 / signature / status / approved。
- 种子（`image_resize_crop` 等 6 张卡片）在启动时用 `KhatKitDependencyStore.ref()` 计算
  `requires.dependencies[].sha256 + signature`；**若产物尚未上传则为空**，此时卡片的 `requires.bridges`
  仍含 `imageToolbox`，客户端会在能力协商/运行前给出明确错误。发布顺序应为：
  先 `buildDependencyDex` + 上传产物，再让客户端拉取卡片包；或上传后调用一次卡片更新。
- `GET /api/khatkit/index.json` 索引带 `dependencies`，App 据此在运行前下载。
- 数据库列 `khatkit_cards.plugins` 已重命名为 `khatkit_cards.dependencies`：启动时按列存在性
  自动 `RENAME COLUMN`（若两列并存则先回填数据再删旧列），不会丢历史数据。

Gradle 任务细节：`buildDependencyDex` 依赖 `:image-toolbox-dependency:assembleDebug`，从 AAR 取
`classes.jar` 交给 D8（`$ANDROID_HOME/build-tools/*/d8 --min-api 26`），jar 条目时间戳固定为 0，
保证同源码重复构建产物字节一致（sha256 稳定）。

## 7. 已知边界 / TODO

- 依赖卸载/版本回收：服务端已支持 `disable`（410 停止下发，别名 `revoke`）与 `enable`（回滚，别名 `activate`），
  但客户端 `filesDir/dependencies` 下按 name/version 的 jar 无自动清理策略；
  后续可按「最近 1 个版本 + 30 天」清理（已加载到内存的 revoked 版本需重启进程才彻底失效）。
- 依赖间依赖、权限声明（如 MANAGE_EXTERNAL_STORAGE 申请入口）由宿主统一提供，依赖不单独声明。
- 非 imageToolbox 依赖若只实现 `KhatKitDependency`，脚本只能用 `call/availableMethods`；
  需要类型化 API 时在宿主侧新增接口并由依赖实现（与本文件所述 imageToolbox 相同）。
