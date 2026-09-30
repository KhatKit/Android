# ImageToolbox 迁移清单

核对源：<https://github.com/T8RIN/ImageToolbox/tree/693eb0222eaf631d753f31b7bbb058e95a5aa8cf>
（本次下载的上游提交）。本清单覆盖该提交 `feature/` 下的全部模块，**不是已全部实现的声明**。
现有实现为按相同功能设计的 SDK 图像处理，不宣称算法、格式和 UI 与上游等价。

| 上游模块 | 当前状态 / 云端工具 |
|---|---|
| ai-tools | 待迁移：模型、推理引擎与任务适配；模型和 SO 均须按需下载 |
| apng-tools | 待迁移：动画编解码 |
| app-logs | 应用自身日志 UI，不是图像工具 |
| archive-tools | 待迁移：归档格式适配 |
| ascii-art | 部分：image_analyze/ascii |
| audio-cover-extractor | 待迁移 |
| base64-tools | 待迁移为独立文件工具 |
| batch-rename | 待迁移，需覆盖/冲突保护 |
| checksum-tools | 待迁移为独立文件工具 |
| cipher | 待迁移，不以自制密码算法代替上游 |
| code-preview | 待迁移：代码排版与字体资源 |
| collage-maker | 部分：image_compose/grid/collage |
| color-library | 待迁移：颜色数据集及其许可 |
| color-tools | 部分：image_analyze/palette/average |
| compare | 1.1.0 新增：RGB MAE/MSE/PSNR、变化计数、绝对差异图；不含 SSIM/交互比较 |
| compression-lab | 部分：已有 JPEG 质量压缩；无压缩实验室全套算法 |
| crop | 部分：image_resize_crop；无任意遮罩交互 |
| curves | 部分：三段亮度近似，不是上游曲线编辑器 |
| delete-exif | 部分：重编码删除元数据，不是无损删除 |
| document-scanner | 待迁移：扫描算法与透视矫正 |
| draw | 待迁移：参数化绘制和交互画布需分开设计 |
| duplicate-finder | 待迁移 |
| easter-egg | 应用彩蛋，不是图像工具 |
| edit-exif | 待迁移：当前只有 EXIF 读取 |
| erase-background | 待迁移：分割引擎和模型 |
| filters | 部分：已有滤镜/预设；1.1.0 新增 .cube LUT 与内存滤镜链；非 500+ 全量 |
| format-conversion | 部分：PNG/JPEG/WebP；其它编码器待迁移 |
| fractal-generation | 待迁移 |
| gif-tools | 待迁移：动画编解码 |
| gradient-maker | 待迁移 |
| help | 上游帮助 UI，不是图像工具 |
| image-cutting | 部分：单区域批量 crop；多区域切割待迁移 |
| image-preview | 上游预览 UI，宿主结果展示替代；交互对齐待做 |
| image-splitting | 1.1.0 新增：网格/水平/垂直切片；不是上游全部交互选项 |
| image-stacking | 部分：水平/垂直堆叠 |
| image-stitch | 已迁移为 `image_stitch`：水平/垂直拼接、统一尺寸、间距和背景色；复杂 OpenCV 对齐/渐隐边缘仍未迁移 |
| jxl-tools | 待迁移：JXL 原生编解码 |
| libraries-info | 上游依赖信息 UI；迁入库仍需附 LICENSE/NOTICE |
| library-details | 同上 |
| limits-resize | 部分：fit/fill；全部尺寸约束待对齐 |
| load-net-image | 待迁移：独立下载脚本需网络白名单 |
| main | 上游导航 UI，不复制进宿主 |
| markup-layers | 待迁移：图层/贴纸/文字交互 |
| media-picker | 使用宿主文件选择；不是迁入上游 UI |
| mesh-gradients | 待迁移 |
| multi-frame-fusion | 待迁移 |
| noise-generation | 部分：给已有图增加 grain/noise；无独立纹理生成 |
| palette-pdf | 待迁移 |
| palette-tools | 部分：主色量化/取色 |
| pdf-tools | 部分：已有位图 PDF 读写/重排等；不保留矢量文字 |
| photomosaic | 待迁移 |
| pick-color | 部分：image_analyze/pick/color_at |
| quick-tiles | Android 快捷磁贴 UI，不是图像工具 |
| recognize-text | 宿主有 OCR 能力，但上游语言/模型流程尚未迁移 |
| resize-convert | 部分：缩放、PNG/JPEG/WebP |
| root | 上游应用容器，不是 root 权限工具 |
| scan-qr-code | 待迁移为独立依赖；不因宿主有识别能力就宣称完整迁移 |
| settings | 宿主设置替代，不复制上游设置 UI |
| shader-studio | 待迁移：着色器运行环境与参数契约 |
| single-edit | 部分：已有参数化编辑，不含上游综合编辑 UI |
| svg-maker | 待迁移：栅格描摹算法 |
| texture-generation | 待迁移 |
| usage-statistics | 上游统计 UI，不是图像工具 |
| wallpapers-export | 待迁移 |
| watermarking | 部分：文字/图片水印 |
| webp-tools | 部分：静态 WebP；动画待迁移 |
| weight-resize | 待迁移：按目标文件大小搜索编码参数 |

## 依赖边界

- Lua、参数 schema、依赖版本/hash/signature 放云端卡片包；实现不加到 app/khatkit 的 implementation。
- 1.1.0 仍仅用 Android SDK，由独立模块构建 DEX jar；宿主只保留已有 bridge 契约。
- 当前加载器的 nativeLibrarySearchPath 为 null，打包任务也只打 DEX，不自动包含 AAR 的 SO/模型/资源。
  后续接入 ONNX、GPU/原生滤镜、GIF/APNG/JXL 等前，必须实现 ABI 选择、依赖打包、签名校验后的
  原生库/资源提取与加载；仅在 Gradle 添加 implementation 会漏包，不能这样发布。
- 任何直接移入上游源码、模型或第三方依赖都要保留对应 LICENSE/NOTICE，并逐项检查分发许可。
- 待迁移项不生成可调用占位卡片，不把未支持操作放入 AI schema。
