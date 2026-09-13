-- 设置极客猫开放平台 API Key。
-- 保存到 zenneko 卡片共享的 zenneko_key，并用免费快递查询接口验证密钥是否有效。

local DEFAULT_KEY = "sk-c33978821b7d87ce4ef96c38022c86a13846abda180434b2"

local key = args.key
if not key or key == "" then
  local last = store.secretGet("zenneko_key")
  if not last or last == "" then last = store.sharedRead("zenneko_key") end
  if not last or last == "" then last = DEFAULT_KEY end
  local v = ui.form("设置极客猫 API Key", {
    { type = "input", id = "key", label = "API Key（sk- 开头）", default = last }
  })
  if not v then return { cancelled = true } end
  key = v.key
end

key = string.gsub(key or "", "%s", "")
if key == "" or string.sub(key, 1, 3) ~= "sk-" then
  return { error = "API Key 格式不正确（应以 sk- 开头）" }
end

ui.progress(0.3, "验证密钥…")
local res = tool.httpGet("https://zenneko.top/api/kuaidi.php?nu=TEST123456", {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
if type(res) == "table" and res.__error then return { error = tostring(res.__error) } end
if not res or res == "" then return { error = "验证失败：接口无响应" } end
if string.find(res, "Invalid open API key", 1, true) or string.find(res, "Invalid API key", 1, true)
  or string.find(res, '"code"%s*:%s*401') then
  return { error = "API Key 无效，请检查后重试" }
end

store.secretSet("zenneko_key", key)
store.sharedWrite("zenneko_key", key)
ui.progress(1, "已保存")
return {
  saved = true,
  key_tail = string.sub(key, -6),
  message = "密钥已保存（zenneko_key），所有 zenneko 卡片共用"
}
