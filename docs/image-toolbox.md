# 图像工具箱（本地处理，云端市场分发）

参考 ImageToolbox（T8RIN/ImageToolbox）的常用静态图处理，**全部在设备本地完成**：

- **实现不随 APK 编译**：代码在独立模块 `image-toolbox-dependency`，由 D8 打成含
  `classes.dex` 的 jar（`image-toolbox-1.0.0.jar`）发布到 Hub；运行时随卡片下载，
  校验 sha256 后由 `DexClassLoader` 加载成动态 bridge `imageToolbox`。
  纯 Android SDK（`Bitmap` / `Canvas` / `Paint` / `ColorMatrix` / `Matrix`，PDF 用 `PdfDocument` / `PdfRenderer`），
  不依赖 `javax.imageio`、无网络调用。
- 应用侧只保留 bridge 接口 `heizige.kk.khatkit.bridge.ImageToolboxBridge`（编译期契约）
  与动态派发包装器 `DependencyBridgeWrapper`；原 `bridge/impl/ImageToolboxBridgeImpl.kt` 已删除。
- Lua 卡片从云端卡片 Hub（市场）下载，不打包进 APK。Hub 索引（KServer 种子）按功能拆成 6 张免费卡片，
  声明 `requires.bridges: ["tool", "ui", "imageToolbox"]`、`requires.dependencies: [{ name: "imageToolbox", version: "1.0.0", sha256: ... }]`、
  网络白名单为空、AI 与用户均可触发：

  | 市场卡片 | 包含操作 |
  |---|---|
  | `image_resize_crop` | resize / crop / rotate / flip / crop_aspect / fit / fill / smart_fit / rotate_auto_crop / resample |
  | `image_enhance` | 既有增强 10 项 + 31 种预设 + 20 种参数化滤镜 + 21 种效果 |
  | `image_finish` | watermark / border / round_corners / format_convert / compress / strip_metadata / outline / opacity / drop_shadow / border_double / border_shadow / tint_transparent / duotone |
  | `image_compose` | grid / collage / stack_h / stack_v / side_by_side / blend / watermark_image |
  | `image_analyze` | info / histogram / dominant / palette / pick / color_at / average / exif / ascii |
  | `image_pdf` | images_to_pdf / pdf_to_images / pdf_page_count / rotate / reorder / extract / delete / nup / compress / merge |

- 能力协商：只要设备支持依赖包运行时（capability `dependency`），声明 `requires.dependencies` 的卡片即可见；
  首次运行前宿主自动下载依赖包（离线/校验失败会给出中文错误），详见 [dependency-system.md](dependency-system.md)。
- 图片不出设备、结果免费，不需要 Hub 令牌；`/sdcard` 等共享存储路径仍需「所有文件访问」授权。

```
AI / 用户 ──▶ image_* 卡片（Hub 下载的 Lua） ──▶ imageToolbox（Hub 下载的 dex 依赖） ──▶ Bitmap/Canvas 本地处理 ──▶ 新文件 / JSON
```

---

## 1. 本地操作清单

### 1.1 既有类型化方法（向后兼容）

| 卡片 op | bridge 方法 | 中文 | 关键参数 | 缺省输出 |
|---|---|---|---|---|
| `resize` | `imageToolbox.resize(path, width, height, keepAspect)` | 缩放 | `width/height/keep_aspect` | `<名>_resized.<源格式>` |
| `crop` | `imageToolbox.crop(path, x, y, width, height)` | 裁剪 | `x/y/width/height` | `<名>_cropped.<源格式>` |
| `rotate` | `imageToolbox.rotate(path, degrees)` | 任意角度旋转（自动扩画布） | `degrees` | `<名>_rotated.<源格式>` |
| `flip` | `imageToolbox.flip(path, horizontal)` | 翻转 | `horizontal`（或 `direction`） | `<名>_flipped.<源格式>` |
| `grayscale` | `imageToolbox.grayscale(path)` | 灰度 | - | `<名>_grayscale.<源格式>` |
| `blur` | `imageToolbox.blur(path, radius)` | 方框模糊（三次分离卷积） | `radius` 像素 | `<名>_blurred.<源格式>` |
| `sharpen` | `imageToolbox.sharpen(path, amount)` | 3×3 锐化卷积 | `amount` 0.1–10 | `<名>_sharpened.<源格式>` |
| `pixelate` | `imageToolbox.pixelate(path, blockSize)` | 像素化 | `block`/`block_size` ≥2 | `<名>_pixelated.<源格式>` |
| `brightness_contrast` | `imageToolbox.brightnessContrast(path, brightness, contrast)` | 亮度/对比度 | 两者 -100..100 | `<名>_brightness_contrast.<源格式>` |
| `saturation` | `imageToolbox.saturation(path, factor)` | 饱和度 | 0=灰度 1=原图 | `<名>_saturated.<源格式>` |
| `hue` | `imageToolbox.hue(path, degrees)` | 色相旋转 | `degrees` | `<名>_hue.<源格式>` |
| `auto_contrast` | `imageToolbox.autoContrast(path)` | 自动对比度（0.5% 截断拉伸） | - | `<名>_auto_contrast.<源格式>` |
| `invert` | `imageToolbox.invert(path)` | 反色 | - | `<名>_inverted.<源格式>` |
| `sepia` | `imageToolbox.sepia(path)` | 复古棕褐 | - | `<名>_sepia.<源格式>` |
| `watermark` | `imageToolbox.watermark(path, text, position, alpha, textSize, colorHex)` | 文字水印 | `text/position/alpha(0-255)/size/color` | `<名>_watermarked.<源格式>` |
| `border` | `imageToolbox.border(path, width, colorHex)` | 纯色边框 | `width/color` | `<名>_bordered.<源格式>` |
| `round_corners` | `imageToolbox.roundCorners(path, radius)` | 圆角 | `radius` | `<名>_rounded.png` |
| `format_convert` | `imageToolbox.convert(path, format, quality)` | 格式转换 | `format` png/jpg/webp | `<名>_converted.<format>` |
| `compress` | `tool.compressImage(path, quality)`（复用，不重复实现） | 质量压缩 | `quality` 1–100 | `<名>_compressed.jpg` |
| `strip_metadata` | `imageToolbox.stripMetadata(path)` | 清除元数据 | - | `<名>_stripped.<源格式>` |
| `images_to_pdf` | `imageToolbox.imagesToPdf(paths, output)` | 图片转 PDF（A4 居中，按序成页） | `files` 路径数组 | `<首图名>_images.pdf` |
| `pdf_to_images` | `imageToolbox.pdfToImages(path, outputDir)` | PDF 逐页转 PNG（2× 渲染） | `output_dir`/`output` | `<PDF名>_pageN.png` |
| `pdf_page_count` | `imageToolbox.pdfPageCount(path)` | PDF 页数 | - | 不落盘，返回数字 |

### 1.2 单图扩展：`process(path, op, paramsJson)`

所有扩展单图操作都走同一入口，参数用 JSON 对象传递（卡片 Lua 自动把 `params` 编码后回传）。
参数缺省写在 `paramsJson` 里即可，常见键：`width/height/ratio/color/threshold/amount/strength/padding/output/quality`。

**滤镜预设（31 种，ColorMatrix 组合）**

`warm`（暖色）、`cool`（冷色）、`vintage`（复古）、`retro`、`film`（胶片）、`polaroid`（宝丽来）、
`lomo`、`kodak`、`fuji`、`gotham`、`cyberpunk`、`coffee`、`golden_hour`、`pink_dream`、`purple_mist`、
`sunrise`、`autumn`、`winter`、`ocean`、`neon`、`noir`（黑白）、`pastel`、`fade`、`bleach_bypass`、
`candlelight`、`old_tv`、`night_vision`（夜视）、`pop_art`、`celluloid`、`fall_colors`、`greenish`。

**参数化滤镜（20 种）**

| op | 说明 | 关键参数 |
|---|---|---|
| `gamma` | 伽马校正 | `gamma`（默认 2.2） |
| `levels` | 色阶 | `in_black/in_white/gamma/out_black/out_white` |
| `curves` | 曲线近似（阴影/中间调/高光） | `shadows/midtones/highlights`（-100..100） |
| `exposure` | 曝光补偿 | `ev`（-4..4） |
| `temperature` | 色温（暖/冷） | `amount` 或 `temperature`（-100..100） |
| `tint` | 色调（绿/品红） | `amount`（-100..100） |
| `vibrance` | 自然饱和度 | `amount` |
| `threshold` | 阈值二值化（可选软边） | `threshold/soft` |
| `posterize` | 色调分离 | `levels`（2..64） |
| `solarize` | 日光反转 | `threshold` |
| `color_balance` | RGB 通道增益 | `red/green/blue`（%） |
| `monochrome` | 单色调 | `color/strength` |
| `black_white` / `bw` | 黑白（通道权重） | `red/blue/contrast` |
| `false_color` | 伪彩映射 | `preset`：thermal/rainbow/fire/ice/ocean/neon |
| `duotone` | 双色调 | `dark/light`（或 `shadow/highlight`） |
| `highlights_shadows` | 高光/阴影恢复 | `shadows/highlights` |
| `haze` / `dehaze` | 雾化 / 去雾 | `amount` |
| `equalize` | 直方图均衡 | - |
| `replace_color` | 颜色替换 | `from/to/tolerance` |

**效果（21 种）**

| op | 说明 | 关键参数 |
|---|---|---|
| `vignette` | 暗角 | `strength/radius/softness` |
| `grain` / `noise` | 胶片颗粒/噪点 | `amount/mono/seed` |
| `emboss` | 浮雕 | `strength` |
| `edge` / `sobel` | Sobel 边缘检测 | `strength` |
| `laplacian` | 拉普拉斯边缘 | `strength` |
| `sketch` | 素描近似 | `radius` |
| `oil` / `kuwahara` | 油画 / Kuwahara 近似 | `radius` |
| `watercolor` | 水彩近似 | `radius/levels` |
| `halftone` | 半色调网点 | `cell` |
| `crosshatch` | 交叉阴影 | `threshold/spacing` |
| `glitch` | 故障风 | `intensity/channel_shift/density/seed` |
| `pixel_sort` | 像素排序 | `threshold/reverse` |
| `dither` | 有序抖动（Floyd–Steinberg） | `levels` |
| `denoise` / `reduce_noise` | 中值降噪 | `radius` |
| `unsharp` | USM 锐化 | `amount/radius` |
| `glow` | 辉光 | `threshold/radius/amount` |
| `motion_blur` | 运动模糊 | `angle/length` |

**几何与版式（14 种）**

| op | 说明 | 关键参数 |
|---|---|---|
| `crop_aspect` | 按比例裁剪 | `ratio`（"1:1"/"16:9"）、`anchor` |
| `fit` | 等比缩放进目标画布并补背景 | `width/height/background/anchor` |
| `smart_fit` | 同上，背景自动取四角均值 | 同上 |
| `fill` | 等比铺满后居中裁剪 | `width/height` |
| `rotate_auto_crop` | 任意角度旋转后自动裁掉黑边 | `degrees/ratio` |
| `resample` | 重采样 | `width/height/method`：nearest/bilinear |
| `outline` | 沿不透明内容描边 | `width/color` |
| `opacity` / `alpha` | 透明度 | `opacity`（0..100） |
| `drop_shadow` | 投影 | `offset_x/offset_y/radius/color` |
| `border_double` | 双层边框 | `width/inner_width/color/inner_color` |
| `border_shadow` | 阴影渐隐边框 | `width/color/strength` |
| `tint_transparent` | 给透明区域着色 | `color/threshold` |
| `crop_to_content` | 按背景色裁剪到内容 | `threshold/padding/background` |

### 1.3 多图合成：`compose(op, inputsJson, paramsJson)`

`inputsJson` 为输入路径 JSON 数组（卡片把 `image` + `files` 合并后传入），返回输出路径。

| op | 说明 | 关键参数 |
|---|---|---|
| `grid` / `collage` | 拼图网格（cover 填格） | `cols/gap/cell_width/cell_height/background` |
| `stack_h` / `side_by_side` | 水平堆叠 | `height/gap/background` |
| `stack_v` | 垂直堆叠 | `width/gap/background` |
| `blend` | 双图混合（12 种模式） | `mode`：normal/multiply/screen/overlay/soft_light/hard_light/add/subtract/difference/darken/lighten/color_dodge/color_burn；`opacity` |
| `watermark_image` | 图片水印 | `position/opacity/scale/margin` |

### 1.4 只读分析：`analyze(path, query, paramsJson)`

返回 JSON 字符串（不写文件；卡片会把结果放进 `outputs`）。

| query | 说明 | 关键参数 |
|---|---|---|
| `info` | 尺寸/百万像素/宽高比/格式/体积/是否有透明通道 | - |
| `histogram` | R/G/B/亮度各 256 桶直方图 | - |
| `dominant` / `palette` | 主色板（量化统计） | `count`（默认 8）、`quantize` |
| `pick` / `color_at` | 坐标取色 | `x/y` |
| `average` | 平均色 | - |
| `exif` | EXIF（相机/型号/曝光/光圈/ISO/焦距/GPS/方向） | - |
| `ascii` | ASCII 字符画 | `width`、`charset`、`invert` |

### 1.5 PDF 编辑：`pdfEdit(op, source, paramsJson)`

说明：纯 Android SDK 只能「`PdfRenderer` 光栅化 → `PdfDocument` 重建」，编辑后文字会转成位图且体积随渲染比例变化。

| op | 说明 | 关键参数 |
|---|---|---|
| `rotate` | 所有页面旋转 | `degrees` |
| `reorder` | 页面重排 | `pages`：`"3,1,2"` 或 `[3,1,2]` |
| `extract` | 抽取页 | `pages`：`"1-3,5"` 或 `[1,3,5]` |
| `delete` | 删除页 | `pages`（其余保留） |
| `nup` | 多页合一（2/4/6/9 页每张） | `pages_per_sheet/gap` |
| `compress` | 降采样重渲染压缩 | `scale`（0.1..1）、`grayscale` |
| `merge` | 多 PDF 合并（`source` + `params.files`） | `files` 数组 |

行为约定：

- 所有操作解码后先按 EXIF 方向转正，再处理并重编码；输出不写回 EXIF/ICC，因此重编码即丢元数据。
- 输出缺省写到**源文件同目录** `<名字>_<操作>.<扩展名>`；显式传 `output`（PDF/合成）、`output_dir`（PDF 转图）可改路径。
  透明相关操作（outline/opacity/drop_shadow/border_double 等）默认输出 png。
- 失败抛中文错误，引擎编码为 `{"__error":"..."}`；卡片汇总 `error` 字段并返回 `outputs` 数组。
- 批量：`image` 里用换行/逗号分隔，或传 `files` 数组；合成/PDF 编辑按「多文件一次执行」处理。
- 参数 `params` 支持 JSON 字符串（`{"width":800,"keep_aspect":true}`）或键值对（`width=800,quality=80`）。
- 图片路径缺失时卡片弹 `ui.form` 让用户填写路径、操作与参数。

AI 调用示例：

```json
{ "op": "resize", "image": "/sdcard/DCIM/cat.jpg", "params": "{\"width\":1080,\"keep_aspect\":true}" }
{ "op": "grain", "image": "/sdcard/DCIM/cat.jpg", "params": "{\"amount\":25,\"mono\":true}" }
{ "op": "vintage", "image": "/sdcard/DCIM/cat.jpg", "params": "{}" }
{ "op": "crop_aspect", "image": "/sdcard/DCIM/cat.jpg", "params": "{\"ratio\":\"1:1\",\"anchor\":\"center\"}" }
{ "op": "grid", "files": ["/sdcard/a.png", "/sdcard/b.png", "/sdcard/c.png"], "params": "{\"cols\":2,\"gap\":8}" }
{ "op": "blend", "files": ["/sdcard/a.png", "/sdcard/b.png"], "params": "{\"mode\":\"overlay\",\"opacity\":80}" }
{ "op": "dominant", "image": "/sdcard/DCIM/cat.jpg", "params": "{\"count\":6}" }
{ "op": "nup", "image": "/sdcard/Download/in.pdf", "params": "{\"pages_per_sheet\":4}" }
```

### 1.6 仍然不在范围内的能力

| ImageToolbox 能力 | 原因 |
|---|---|
| AI 背景移除、AI 放大/去噪/上色、人像/深度 | 需要 ONNX/MlKit 等模型，超出纯 SDK 图像处理 |
| 交互式绘图/擦除/标记（Pen、Spot Healing、形状） | 需要画布交互，卡片脚本无法承载 |
| GIF/APNG/WebP 动画、视频转码与剪辑 | 需要动画/视频编解码器，不在静态图工具范围 |
| 3D LUT（.cube）、500+ 复杂滤镜全家桶 | 需要着色器/LUT 文件解析，超出纯 Canvas 像素运算；已用 31 预设 + 41 滤镜/效果近似覆盖常用风格 |
| 二维码/条码生成与识别 | 纯 Android SDK 无 QR 编码器；识别另有 `tool` 能力，生成暂不提供 |
| OCR、文档扫描 | 需要 ML Kit，宿主 App 已用 OCR 卡片实现，不放进依赖包 |
| 调色板 PDF、照片马赛克、Seam Carving 等高级算法 | 算法体量与收益不匹配，暂不实现 |
