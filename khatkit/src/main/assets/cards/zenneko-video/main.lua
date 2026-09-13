-- 极客猫 视频聚合搜索。GET 查询参数：action / keyword / site / page / url / vid / cid / dmid / text / time / color / type / size。
-- 文档写的 video_search.php 已不存在，线上实际为 video_aggregate.php（2026-09 验证）。
-- 仅 action=play 成功解析出播放链接扣 ¥0.02（失败不扣费）。
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

local function urlencode(s)
  return (string.gsub(s or "", "([^%w%-%._~])", function(c)
    return string.format("%%%02X", string.byte(c))
  end))
end

local action = args.action
if not action or action == "" then
  local v = ui.form("视频聚合搜索", {
    { type = "select", id = "action", label = "操作", options = { "search", "episodes", "play", "danmaku" }, default = "search" },
    { type = "input", id = "keyword", label = "关键词（search）", default = args.keyword or "" },
    { type = "input", id = "url", label = "播放页 URL（episodes/play）", default = args.url or "" }
  })
  if not v then return { cancelled = true } end
  action = v.action
  if args.keyword == nil or args.keyword == "" then args.keyword = v.keyword end
  if args.url == nil or args.url == "" then args.url = v.url end
end
if not action or action == "" then return { error = "缺少 action" } end

local query = "action=" .. urlencode(action)
local function add(name, value)
  if value ~= nil and tostring(value) ~= "" then
    query = query .. "&" .. name .. "=" .. urlencode(tostring(value))
  end
end
add("keyword", args.keyword)
add("site", args.site)
add("page", args.page)
add("url", args.url)
add("vid", args.vid)
add("cid", args.cid)
add("dmid", args.dmid)
add("text", args.text)
add("time", args.time)
add("color", args.color)
add("type", args.type)
add("size", args.size)

local key = api_key()
local res = tool.httpGet("https://zenneko.top/api/video_aggregate.php?" .. query, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

local code = tonumber(string.match(res, '"code"%s*:%s*(%d+)') or "200") or 200
return { action = action, code = code, response = res }
