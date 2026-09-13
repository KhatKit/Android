-- 极客猫 网盘解析。POST JSON：operation / url / passcode / file_id / parent_id / cookie / access_token / token。
-- 计费：identify / open / list 免费；direct_link 成功扣 ¥0.02。自定义 headers 参数暂不支持。
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

local operation = args.operation
local url = args.url
if not operation or operation == "" or not url or url == "" then
  local v = ui.form("网盘解析", {
    { type = "select", id = "operation", label = "操作", options = { "identify", "list", "open", "direct_link" }, default = args.operation or "list" },
    { type = "input", id = "url", label = "分享链接", default = args.url or "" },
    { type = "input", id = "passcode", label = "提取码（可选）", default = args.passcode or "" }
  })
  if not v then return { cancelled = true } end
  operation, url = v.operation, v.url
end
if not url or url == "" then return { error = "缺少网盘分享链接 url" } end
if operation ~= "identify" and operation ~= "open" and operation ~= "list" and operation ~= "direct_link" then
  return { error = "operation 必须是 identify / open / list / direct_link" }
end
if operation == "direct_link" and (not args.file_id or args.file_id == "") then
  return { error = "direct_link 需要 file_id（来自 list 返回的 items[].id）" }
end

local function add(name, value)
  if value and value ~= "" then
    return ',"' .. name .. '":"' .. json_escape(value) .. '"'
  end
  return ""
end

local body = '{"operation":"' .. operation .. '"'
  .. ',"url":"' .. json_escape(url) .. '"'
  .. add("passcode", args.passcode)
  .. add("file_id", args.file_id)
  .. add("parent_id", args.parent_id)
  .. add("cookie", args.cookie)
  .. add("access_token", args.access_token)
  .. add("token", args.token)
  .. "}"

local key = api_key()
local res = tool.httpPost("https://zenneko.top/api/pan.php", body, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

local status = string.match(res, '"status"%s*:%s*"([^"]*)"') or "success"
local provider = string.match(res, '"provider"%s*:%s*"([^"]*)"') or ""
local direct = ""
if operation == "direct_link" then
  direct = string.match(res, '"url"%s*:%s*"([^"]*)"') or ""
end
return {
  operation = operation,
  status = status,
  provider = provider,
  direct_url = direct,
  response = res
}
