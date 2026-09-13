-- 执行 vFlow 工作流（同步等待结果）。token 由 vflow_list_workflows 首次配置。
local base = store.kvGet("base_url", "http://127.0.0.1:8080/api/v1")
local token = store.secretGet("token")
if not token or token == "" then
  return { error = "未配置 vFlow：请先运行 vflow_list_workflows 填地址和 Token" }
end

local id = args.workflow_id
local name = args.name

if (not id or id == "") and name and name ~= "" then
  local list = tool.httpGet(base .. "/workflows", {
    Authorization = "Bearer " .. token
  })
  local lowered = string.lower(name)
  local matched = nil
  for entry in string.gmatch(list, '{[^{}]*}') do
    local entryName = string.match(entry, '"name"%s*:%s*"([^"]*)"')
    local entryId = string.match(entry, '"id"%s*:%s*"([^"]*)"')
    if entryName and entryId and string.find(string.lower(entryName), lowered, 1, true) then
      matched = entryId
      break
    end
  end
  if not matched then
    return { error = "找不到工作流：" .. name }
  end
  id = matched
end

if not id or id == "" then
  return { error = "缺少 workflow_id 或 name" }
end

local body = '{"async":false,"timeout":120'
if args.variables and args.variables ~= "" then
  body = body .. ',"inputVariables":' .. args.variables
end
body = body .. '}'

local res = tool.httpPost(base .. "/workflows/" .. id .. "/execute", body, {
  Authorization = "Bearer " .. token,
  ["Content-Type"] = "application/json"
})
return { workflow_id = id, response = res }
