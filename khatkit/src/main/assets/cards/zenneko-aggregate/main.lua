-- 极客猫 聚合解析。POST JSON：action / channel / url。
-- getChannels 免费；parse 成功扣 ¥0.02（失败不扣费）。
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

local action = args.action
local channel = args.channel
local url = args.url
if not action or action == "" then
  local v = ui.form("聚合解析", {
    { type = "select", id = "action", label = "操作", options = { "getChannels", "parse" }, default = "parse" },
    { type = "input", id = "channel", label = "渠道 ID（如 dwo）", default = args.channel or "" },
    { type = "input", id = "url", label = "分享链接", default = args.url or "" }
  })
  if not v then return { cancelled = true } end
  action, channel, url = v.action, v.channel, v.url
end
if action ~= "getChannels" and action ~= "parse" then
  return { error = "action 必须是 getChannels 或 parse" }
end
if action == "parse" then
  if not channel or channel == "" then return { error = "parse 需要 channel（可先 getChannels 获取）" } end
  if not url or url == "" then return { error = "parse 需要待解析链接 url" } end
end

local body = '{"action":"' .. action .. '"'
if channel and channel ~= "" then body = body .. ',"channel":"' .. json_escape(channel) .. '"' end
if url and url ~= "" then body = body .. ',"url":"' .. json_escape(url) .. '"' end
body = body .. "}"

local key = api_key()
local res = tool.httpPost("https://zenneko.top/api/aggregate_parse.php", body, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

local status = string.match(res, '"status"%s*:%s*"([^"]*)"') or "success"
return { action = action, status = status, response = res }
