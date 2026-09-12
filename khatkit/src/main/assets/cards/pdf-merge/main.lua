-- 合并多个 PDF 为一个。
-- 厚宿主、薄脚本：脚本只负责下单，真正的合并由宿主 tool.mergePdf 完成。
-- 只能调 card.json 里声明过的 bridge（tool / ui）。

local v = ui.form("合并 PDF", {
  { type = "file_picker", id = "paths", label = "选择 PDF", multiple = true, filter = ".pdf" }
})
if not v then return { cancelled = true } end

local paths = v.paths or args.paths
if not paths or #paths == 0 then return { error = "没有选择文件" } end

ui.progress(0, "开始合并…")
local output = tool.mergePdf(paths, "/sdcard/Download/merged.pdf")
ui.progress(1, "完成")

return { output = output, count = #paths }
