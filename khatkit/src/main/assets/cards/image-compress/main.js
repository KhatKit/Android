// 批量压缩图片。JS 示例：与 Lua 卡片共用同一套 bridge API。
const v = ui.form("压缩图片", [
  { type: "file_picker", id: "files", label: "图片", multiple: true, filter: "image/*" },
  { type: "slider", id: "quality", label: "质量", min: 10, max: 100, default: 80 }
]);
if (!v) return { cancelled: true };

const files = (v.files && v.files.length) ? v.files : (args.files || []);
if (!files.length) return { error: "没有选择文件" };

const quality = Math.round(v.quality || args.quality || 80);
const outputs = [];
for (let i = 0; i < files.length; i++) {
  ui.progress((i + 1) / files.length, `已处理 ${i + 1}/${files.length}`);
  outputs.push(tool.compressImage(files[i], quality));
}
return { count: outputs.length, outputs };
