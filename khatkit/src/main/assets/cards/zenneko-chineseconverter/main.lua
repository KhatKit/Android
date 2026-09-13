-- 极客猫 简繁转换（免费）。POST JSON：{ data, type }（type: s2t / t2s）。
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
  if string.find(res, '"success"%s*:%s*false') then
    return (string.match(res, '"error"%s*:%s*"([^"]*)"')
      or string.match(res, '"message"%s*:%s*"([^"]*)"')
      or "接口返回失败") .. "（如为鉴权问题请运行 zenneko-set-key）"
  end
  if string.find(res, '"status"%s*:%s*"error"') then
    return (string.match(res, '"message"%s*:%s*"([^"]*)"')
      or "接口返回失败") .. "（如为鉴权问题请运行 zenneko-set-key）"
  end
  local code = tonumber(string.match(res, '"code"%s*:%s*(%d+)') or "")
  if code and code >= 400 then
    return (string.match(res, '"msg"%s*:%s*"([^"]*)"')
      or string.match(res, '"error"%s*:%s*"([^"]*)"')
      or ("接口错误码 " .. code)) .. "（如为鉴权问题请运行 zenneko-set-key）"
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

local data = args.data
local t = args.type
if not data or data == "" then
  local v = ui.form("简繁转换", {
    { type = "input", id = "data", label = "待转换文本", default = args.data or "" },
    { type = "select", id = "type", label = "方向", options = { "s2t", "t2s" }, default = args.type or "s2t" }
  })
  if not v then return { cancelled = true } end
  data, t = v.data, v.type
end
if not data or data == "" then return { error = "缺少待转换文本 data" } end
t = t or "s2t"
if t == "1" then t = "s2t" elseif t == "2" then t = "t2s" end
if t ~= "s2t" and t ~= "t2s" then t = "s2t" end

local key = api_key()
local body = '{"data":"' .. json_escape(data) .. '","type":"' .. t .. '"}'
local res = tool.httpPost("https://zenneko.top/api/chineseconverter.php", body, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

local result = string.match(res, '"result"%s*:%s*"(.*)"%s*,%s*"billing"')
if result then
  result = string.gsub(result, '\\"', '"')
  result = string.gsub(result, '\\n', "\n")
  result = string.gsub(result, '\\\\', "\\")
end
return { result = result or "", direction = t, response = res }
