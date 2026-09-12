// 共享库示例：从内置层 require @khatkit/std。
const std = require('@khatkit/std');

let path = args.path;
if (!path) {
  const form = ui.form("文件信息", [
    { type: "file_picker", id: "path", label: "选择文件" }
  ]);
  if (!form) return { cancelled: true };
  path = form.path;
}
if (!path) return { error: "没有选择文件" };

const content = tool.readText(path);
return {
  path: std.joinPath("files", path),
  size: std.formatSize(content.length),
  preview: content.length > 200 ? content.substring(0, 200) : content,
};
