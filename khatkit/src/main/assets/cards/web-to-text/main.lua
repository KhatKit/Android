-- 抓取网页 -> 去掉 HTML 标签 -> 保存纯文本。
-- 只做确定性的事：网络与写盘都由宿主 tool bridge 完成。

local url = args.url
local output = args.output
if not url or url == "" then
  local last_url = store.kvGet("last_url", "")
  local v = ui.form("网页转文本", {
    { type = "input", id = "url", label = "网页地址", default = last_url },
    { type = "input", id = "output", label = "保存路径（留空自动）" }
  })
  if not v then return { cancelled = true } end
  url = v.url or args.url
  output = v.output or args.output
end
if not url or url == "" then return { error = "缺少网页地址" } end
if not output or output == "" then
  local name = string.match(url, "([^/?#]+)[/?#]*$") or "page"
  name = string.gsub(name, "[^%w%-%._]", "_")
  if name == "" then name = "page" end
  output = "/sdcard/Download/khatkit-web-" .. name .. ".txt"
end

ui.progress(0, "抓取中…")
local body = tool.httpGet(url, {})
if not body or body == "" then return { error = "抓取失败或内容为空" } end

local text = string.gsub(body, "<[^>]*>", " ")
text = string.gsub(text, "&nbsp;", " ")
text = string.gsub(text, "&amp;", "&")
text = string.gsub(text, "&lt;", "<")
text = string.gsub(text, "&gt;", ">")
text = string.gsub(text, "%s+", " ")
text = string.gsub(text, "^%s*(.-)%s*$", "%1")

ui.progress(1, "保存中…")
tool.writeText(output, text)
store.kvSet("last_url", url)

return { output = output, chars = #text, preview = string.sub(text, 1, 200) }
