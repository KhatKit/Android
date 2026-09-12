-- 按文字点击屏幕控件。失败时返回可点击候选文字，方便 AI 纠正后再试。
local text = args.text
local exact = args.exact == true
if not text or text == "" then
  local v = ui.form("无障碍点击", {
    { type = "input", id = "text", label = "要点击的文字" },
    { type = "switch", id = "exact", label = "精确匹配" }
  })
  if not v then return { cancelled = true } end
  text = v.text or args.text
  exact = v.exact == true
end
if not text or text == "" then return { error = "缺少文字" } end

local pkg = accessibility.currentPackage()
local ok = accessibility.click({ text = text, exact = exact })
if ok then
  return { clicked = true, text = text, package = pkg }
end

local nodes = accessibility.findNodes({ clickable = true })
local candidates = {}
local limit = math.min(#nodes, 20)
for i = 1, limit do
  local node = nodes[i]
  local label = node.text
  if not label or label == "" then label = node.desc end
  if label and label ~= "" then table.insert(candidates, label) end
end

return { clicked = false, text = text, package = pkg, candidates = candidates }
