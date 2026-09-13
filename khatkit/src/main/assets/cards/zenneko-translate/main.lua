-- 极客猫 AI 翻译（免费）。POST JSON，响应为 SSE：data: {"type":"content","content":"..."}
local DEFAULT_KEY = "sk-c33978821b7d87ce4ef96c38022c86a13846abda180434b2"

local function api_key()
  local key = store.sharedRead("zenneko_key")
  if not key or key == "" then key = store.secretGet("zenneko_key") end
  if not key or key == "" then key = DEFAULT_KEY end
  return key
end

local function check(res)
  if type(res) == "table" and res.__error then return tostring(res.__error) end
  if not res or res == "" then return "接口无响应或返回为空" end
  if string.find(res, "Invalid open API key", 1, true) or string.find(res, "Invalid API key", 1, true)
    or string.find(res, '"code"%s*:%s*401') then
    return "API Key 无效或未授权，请运行 zenneko-set-key 更新密钥"
  end
  if string.find(res, "Insufficient balance", 1, true) or string.find(res, '"code"%s*:%s*402') then
    return "账户余额不足，请在极客猫开放平台充值"
  end
  if string.find(res, '"status"%s*:%s*"error"') then
    return (string.match(res, '"message"%s*:%s*"([^"]*)"')
      or "接口返回失败") .. "（如为鉴权问题请运行 zenneko-set-key）"
  end
  if string.find(res, '"success"%s*:%s*false') then
    return (string.match(res, '"error"%s*:%s*"([^"]*)"')
      or "接口返回失败") .. "（如为鉴权问题请运行 zenneko-set-key）"
  end
  if string.find(res, '"error"%s*:%s*"') then
    return string.match(res, '"error"%s*:%s*"([^"]*)"') or "接口返回错误"
  end
  return nil
end

local function json_escape(s)
  s = string.gsub(s or "", "\\", "\\\\")
  s = string.gsub(s, '"', '\\"')
  s = string.gsub(s, "\n", "\\n")
  s = string.gsub(s, "\r", "\\r")
  s = string.gsub(s, "\t", "\\t")
  return s
end

local function utf8_char(cp)
  if cp < 0x80 then return string.char(cp) end
  if cp < 0x800 then return string.char(0xC0 + math.floor(cp / 64), 0x80 + cp % 64) end
  return string.char(0xE0 + math.floor(cp / 4096), 0x80 + math.floor(cp / 64) % 64, 0x80 + cp % 64)
end

local function unescape(s)
  s = string.gsub(s, '\\u(%x%x%x%x)', function(hex) return utf8_char(tonumber(hex, 16)) end)
  s = string.gsub(s, '\\n', "\n")
  s = string.gsub(s, '\\"', '"')
  s = string.gsub(s, '\\\\', "\\")
  return s
end

local text = args.originText
local target = args.targetLang
if not text or text == "" then
  local v = ui.form("AI 翻译", {
    { type = "input", id = "originText", label = "待翻译文本", default = args.originText or "" },
    { type = "input", id = "targetLang", label = "目标语言（如 zh-CN / en）", default = args.targetLang or "en" }
  })
  if not v then return { cancelled = true } end
  text, target = v.originText, v.targetLang
end
if not text or text == "" then return { error = "缺少待翻译文本 originText" } end
if not target or target == "" then target = "en" end

local body = '{"originText":"' .. json_escape(text) .. '","targetLang":"' .. json_escape(target) .. '"'
if args.sourceLang and args.sourceLang ~= "" then
  body = body .. ',"sourceLang":"' .. json_escape(args.sourceLang) .. '"'
end
if args.aiEngineName and args.aiEngineName ~= "" then
  body = body .. ',"aiEngineName":"' .. json_escape(args.aiEngineName) .. '"'
end
body = body .. "}"

local key = api_key()
local res = tool.httpPost("https://zenneko.top/api/translate.php", body, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

local parts = {}
for content in string.gmatch(res, '"content"%s*:%s*"([^"]*)"') do
  parts[#parts + 1] = unescape(content)
end
if #parts == 0 then
  -- 线上 SSE 实际返回 {"text":"..."} 片段（文档示例为 content）
  for piece in string.gmatch(res, '"text"%s*:%s*"([^"]*)"') do
    parts[#parts + 1] = unescape(piece)
  end
end
local result = table.concat(parts)
if result == "" then result = unescape(res) end
return { result = result, target_lang = target }
