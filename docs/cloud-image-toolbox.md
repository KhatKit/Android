# 云端图像工具箱（cloud-image-toolbox）

把 ImageToolbox（T8RIN/ImageToolbox）中**不依赖 ML / 原生库**的常用静态图处理做成云端脚本能力：

- 服务端：KodeHeadServer 提供 `/api/image/*`（纯 JVM：`java.awt` + `javax.imageio`，无新增依赖）
- 客户端：KhatKit 内置 Lua 卡片 `cloud_image_toolbox`，通过 `tool.httpMultipart` 上传图片并保存结果

```
KhatKit 卡片 ──multipart(image, op, params)──▶ POST /api/image/process ──▶ ImageToolboxService ──▶ 图片字节
      ▲                                          │
      └────────── 结果落盘 /sdcard/Download ◀────┘  计费：每张 1 次工具调用额度
```

---

## 1. 覆盖矩阵（ImageToolbox 功能 → 服务端能力）

### 1.1 已实现（20 个操作，纯 JDK 可离线运行）

| 操作名 | 中文 | 对应 ImageToolbox 功能 | 关键参数 |
|---|---|---|---|
| `resize` | 缩放 | Image Resizing（部分算法） | `scale/width/height/keep_aspect/method` |
| `crop` | 裁剪 | Cropping（规则矩形） | `width/height/x/y` |
| `rotate` | 旋转 | Rotating（任意角度，自动扩画布） | `degrees/background` |
| `flip` | 翻转 | Flipping | `direction` |
| `grayscale` | 灰度 | Grayscale 滤镜 | `method` |
| `blur` | 模糊 | Box / Gaussian Blur | `type/radius` |
| `sharpen` | 锐化 | Sharpen / Unsharp | `amount/radius` |
| `pixelate` | 像素化 | Pixelation | `block` |
| `brightness_contrast` | 亮度对比度 | Brightness / Contrast | `brightness/contrast` |
| `saturation` | 饱和度 | Saturation | `amount` |
| `hue` | 色相 | Hue | `degrees` |
| `auto_contrast` | 自动对比度 | Equalize / Auto levels | `clip_percent` |
| `invert` | 反色 | Color Inversion | - |
| `sepia` | 复古棕褐 | Sepia Tone | - |
| `watermark` | 文字水印 | Watermarking（重复文字的子集） | `text/position/size/opacity/color/margin/shadow` |
| `border` | 边框 | Border Frame（纯色） | `width/color` |
| `round_corners` | 圆角 | Crop with shape mask（Rounded Corners） | `radius/background` |
| `format_convert` | 格式转换 | Format Conversion（PNG/JPEG/BMP/GIF） | `format` |
| `compress` | 质量压缩 | Image Shrinking / Quality compressing | `quality` |
| `strip_metadata` | 清除元数据 | EXIF deleting（重编码即丢弃） | - |

> 所有操作都会重编码，EXIF/ICC 元数据自然不会保留（`strip_metadata` 为显式语义）。
> 动图只处理首帧；WebP 读写取决于服务器运行环境是否带 ImageIO WebP 插件（标准 JDK 不带，会返回不支持的格式）。

### 1.2 暂不在服务端提供（需要 ML / 原生库 / 大量额外依赖）

| ImageToolbox 能力 | 原因 | 可能的落地方式 |
|---|---|---|
| 背景移除、AI 放大/去噪/上色/增强、人像/深度 | 需要 ONNX/MlKit 等模型 | 独立 AI 网关或 GPU 服务 |
| OCR（Tesseract/PaddleOCR）、文档扫描 | 原生 OCR 引擎 | 独立 OCR 服务 |
| 绘图/擦除/标记（Pen、Spot Healing、形状） | 交互式画布 | 客户端本地能力（卡片无法承载画布） |
| PDF 工具（合并/拆分/水印/压缩/OCR） | 需 PDFBox 等依赖 | 已有本地卡片 `pdf-merge`；服务端可后续加 PDFBox |
| 二维码 / 条形码（13 种格式） | 需 ZXing | 加入 ZXing 依赖后可作为新 op |
| 动图转码（GIF/APNG/WebP/JXL/AVIF） | 需动画编解码器 | 专项转码服务 |
| 500+ 高级滤镜、3D LUT、Shader Studio | 逐像素重滤镜/着色器 | 按需挑选实现为服务端 op |
| 拼图/马赛克拼贴/缝合/叠图/切图 | 复杂组合布局 | 后续 op（纯 JVM 可做部分） |
| 调色板/直方图/色调曲线/波形图 | 色彩分析工具集 | 后续只读分析 op |
| EXIF 编辑（写入经纬度、时间等） | ImageIO 不解析 EXIF | 引入 `metadata-extractor` 后支持 |
| 代码截图、纹理/分形生成、压缩包工具 | 与图像处理正交 | 不作为图像 op |

---

## 2. 服务端 API

实现位置：`KodeHeadServer`

| 文件 | 作用 |
|---|---|
| `src/service/ImageToolboxService.kt` | 纯 JVM 图像操作 + 编解码 + 操作目录 |
| `src/routing/ImageToolboxRouting.kt` | `/api/image/*` 路由、multipart/JSON 解析、额度扣减 |
| `src/model/ImageDto.kt` | 目录与响应 DTO |
| `test/ImageToolboxServiceTest.kt` | 10 个纯 JVM 单元测试 |
| `scripts/smoke_image.sh` | 端到端冒烟（目录/单图/批量/计费/错误码） |

### 2.1 `GET /api/image/ops`（公开）

返回操作目录：`ops[]`（含中文说明与参数 schema）、`output_formats`、`max_batch`、`max_input_mb`、`cost` 等。

### 2.2 `POST /api/image/process`

鉴权：`Authorization: Bearer <激活令牌或设备会话密钥>`；每张扣 1 次工具调用额度（不足返回 402）。

multipart 表单：

| 字段 | 说明 |
|---|---|
| `image` | 图片文件（字段名也接受 `file`/`files`/`images`） |
| `op` | 操作名，见目录 |
| `params` | 参数 JSON 字符串，如 `{"width":800}` |
| `format` | 可选输出格式 `png/jpg/bmp/gif`（默认 png；`compress` 默认 jpg） |
| `quality` | 可选 JPEG 质量 1..100 |
| `return` | 可选 `binary`（默认，直接返回图片）或 `json`（返回 base64） |

JSON 请求体（`Content-Type: application/json`）二选一：

```json
{
  "op": "resize",
  "params": { "width": 800 },
  "image_url": "https://example.com/a.jpg",
  "format": "jpg",
  "quality": 85
}
```

也支持 `image_base64`（data URL 或裸 base64）与 `image_urls`（数组，多图时用 batch）。

响应：

- `return=binary` → 图片字节 + `X-Image-Format/Width/Height/Cost` 响应头
- `return=json` / JSON 请求 → `{ op, format, mime, width, height, input_bytes, output_bytes, image_base64, cost }`

### 2.3 `POST /api/image/batch`

multipart 重复字段 `files`（最多 5 张）或 JSON `image_urls`，其余字段同 process；返回：

```json
{ "op": "pixelate", "count": 2, "ok_count": 2,
  "items": [ { "name": "a.png", "ok": true, "image_base64": "data:image/png;base64,..." } ],
  "cost": "2 次工具调用" }
```

单图失败不影响其它项（`ok=false` + `error`）；批量按图片数扣额度。

### 2.4 限制与安全

- 单张 ≤ 32MB、边长 ≤ 12000px、像素 ≤ 40MP；一次处理 ≤ 5 张（batch）
- `image_url` 只允许 http/https，且拒绝回环/内网/链路本地地址（SSRF 防护）
- 处理在 `Dispatchers.Default`，编解码关闭 ImageIO 磁盘缓存

---

## 3. 卡片用法

卡片：`khatkit/src/main/assets/cards/cloud-image-toolbox/`（manifest `cloud_image_toolbox`）

- 触发：AI 工具调用 + 用户手动运行
- 桥：`tool` / `ui` / `store`；网络白名单：`heizige.top`
- 令牌：优先 `args.token`，其次卡片密钥 `store.secretGet("hub_token")`，再次共享区 `hub_token`；
  都没有时会弹窗要求输入并加密保存（Hub 地址保存为 `hub_base`，默认 `https://heizige.top`）
- 结果：由 `tool.httpMultipart` 自动保存到 `/sdcard/Download/zenneko_process_*.png|jpg|...`，
  返回 `{ output, outputs[], ok_count, cost }`

AI 调用示例：

```json
{ "image": "/sdcard/DCIM/cat.jpg", "op": "resize", "params": { "width": 1080 }, "format": "jpg", "quality": 85 }
{ "image": "/sdcard/DCIM/cat.jpg", "op": "watermark", "params": { "text": "@heizige", "position": "bottom_right" } }
{ "files": ["/sdcard/a.png", "/sdcard/b.png"], "op": "grayscale" }
```

用户手动运行：选择图片路径（`file_picker` 为路径输入框，批量可在同一字段用换行/逗号分隔），
选择操作、参数（支持 `{"width":800}` 或 `width=800,quality=80`）、输出格式与质量。

设置令牌：在「设置 → 套餐 / 激活」激活套餐后，把激活令牌粘贴到卡片弹窗即可；
也可先运行一次任意操作触发弹窗。

---

## 4. 如何扩展一个新 op（纯 JVM）

1. `ImageToolboxService.process()` 增加分发分支；
2. 实现 `opXxx(src, params)`（`java.awt`/`javax.imageio`，避免新依赖）；
3. 在 `catalog()` 注册操作名、中文说明与参数 schema（UI/AI 都会读到）；
4. `resolveOutputFormat` 如需新默认格式在此调整；
5. 在 `test/ImageToolboxServiceTest.kt` 增加断言，运行 `./kotlin test`；
6. 更新卡片 `OP_NAMES`（可选）与本文档矩阵；
7. 端到端验证：`./scripts/smoke_image.sh`（需本机 PostgreSQL + 运行中的服务）。

保持不变式：不引入原生依赖、所有输入都做尺寸/大小上限、错误统一抛 `ImageOpException`（路由映射为 400）。
