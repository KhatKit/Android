-- 列出本机 vFlow 的工作流；首次运行让用户填 API 地址与 Token（Token 存 Keystore）。
local base = store.kvGet("base_url", "http://127.0.0.1:8080/api/v1")
local token = store.secretGet("token")
if not token or token == "" or args.reset == true then
  local v = ui.form("连接 vFlow", {
    { type = "input", id = "base", label = "vFlow API 地址", default = base },
    { type = "input", id = "token", label = "访问 Token" }
  })
  if not v then return { cancelled = true } end
  base = v.base
  token = v.token
  if not base or base == "" then return { error = "缺少 API 地址" } end
  if not token or token == "" then return { error = "缺少 Token" } end
  store.kvSet("base_url", base)
  store.secretSet("token", token)
end

local res = tool.httpGet(base .. "/workflows", {
  Authorization = "Bearer " .. token
})
return { response = res }
