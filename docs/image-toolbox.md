# 图像工具箱（本地）

参考 ImageToolbox（T8RIN/ImageToolbox）的常用静态图处理，**全部在设备本地完成**：

- 宿主 bridge：`imageToolbox`（`heizige.kk.khatkit.bridge.ImageToolboxBridge` + `bridge/impl/ImageToolboxBridgeImpl.kt`），
  纯 Android SDK（`Bitmap` / `Canvas` / `Paint` / `ColorMatrix` / `Matrix`，PDF 用 `PdfDocument` / `PdfRenderer`），不依赖 `javax.imageio`、无网络调用。
- 内置 Lua 卡片：`khatkit/src/main/assets/cards/local-image-toolbox/`（manifest `local_image_toolbox`），
  声明 `requires.bridges: ["tool", "ui", "imageToolbox"]`，网络白名单为空，AI 与用户均可触发。
- 图片不出设备、结果免费，不需要 Hub 令牌；`/sdcard` 等共享存储路径仍需「所有文件访问」授权。

```
AI / 用户 ──▶ local_image_toolbox (Lua) ──▶ imageToolbox bridge ──▶ Bitmap/Canvas 本地处理 ──▶ 新文件
```

---

## 1. 本地操作清单

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

行为约定：

- 所有操作解码后先按 EXIF 方向转正，再处理并重编码；输出不写回 EXIF/ICC，因此重编码即丢元数据。
- 输出缺省写到**源文件同目录** `<名字>_<操作>.<扩展名>`；显式传 `output`（PDF 转换）/ `output_dir`（PDF 转图）可改路径。
- 失败抛中文错误，引擎编码为 `{"__error":"..."}`；卡片汇总 `error` 字段并返回 `outputs` 数组。
- 批量：`image` 里用换行/逗号分隔，或传 `files` 数组；`images_to_pdf` 用 `files` 数组。
- 参数 `params` 支持 JSON 字符串（`{"width":800,"keep_aspect":true}`）或键值对（`width=800,quality=80`）。
- 图片路径缺失时卡片弹 `ui.form` 让用户填写路径、操作与参数。

AI 调用示例：

```json
{ "op": "resize", "image": "/sdcard/DCIM/cat.jpg", "params": "{\"width\":1080,\"keep_aspect\":true}" }
{ "op": "watermark", "image": "/sdcard/DCIM/cat.jpg", "params": "{\"text\":\"@heizige\",\"position\":\"bottom_right\",\"opacity\":0.6}" }
{ "op": "images_to_pdf", "files": ["/sdcard/a.png", "/sdcard/b.png"], "output": "/sdcard/Download/album.pdf" }
{ "op": "pdf_to_images", "image": "/sdcard/Download/album.pdf", "params": "{\"output_dir\":\"/sdcard/Download/pages\"}" }
```

### 1.1 仍然不在范围内的能力

| ImageToolbox 能力 | 原因 |
|---|---|
| AI 背景移除、AI 放大/去噪/上色、人像/深度 | 需要 ONNX/MlKit 等模型，超出纯 SDK 图像处理 |
| 交互式绘图/擦除/标记（Pen、Spot Healing、形状） | 需要画布交互，卡片脚本无法承载 |
| GIF/APNG/WebP 动画、视频转码与剪辑 | 需要动画/视频编解码器，不在静态图工具范围 |
| OCR、二维码/条码、500+ 滤镜、拼图/3D LUT | 需要 ML Kit / ZXing / 着色器等额外依赖；OCR 另有 `tool.ocrText` |
