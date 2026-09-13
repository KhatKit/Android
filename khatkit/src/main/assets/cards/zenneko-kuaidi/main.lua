-- 极客猫 快递查询（免费）。密钥优先读共享 zenneko_key，其次本卡片密钥，最后默认 Key。
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

local nu = args.nu
if not nu or nu == "" then
  local last = store.kvGet("last_nu", "")
  local v = ui.form("快递查询", {
    { type = "input", id = "nu", label = "快递单号", default = last }
  })
  if not v then return { cancelled = true } end
  nu = v.nu
end
nu = string.gsub(nu or "", "[^%w]", "")
if #nu < 5 then return { error = "快递单号无效（应为 5-32 位数字/字母）" } end

local key = api_key()
local res = tool.httpGet("https://zenneko.top/api/kuaidi.php?nu=" .. nu, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

store.kvSet("last_nu", nu)
return {
  nu = nu,
  company = string.match(res, '"comName"%s*:%s*"([^"]*)"') or "",
  state = string.match(res, '"stateText"%s*:%s*"([^"]*)"') or "",
  response = res
}
