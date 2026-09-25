-- 云端图像工具箱：KodeHead Hub /api/image/process（multipart）单图与批量处理。
-- 令牌来源：args.token > store.secretGet("hub_token") > 共享区 hub_token > 首次运行弹窗输入并加密保存。
local DEFAULT_HUB = "https://heizige.top"
local MAX_BATCH = 20

local OP_NAMES = {
  "resize", "crop", "rotate", "flip", "grayscale", "blur", "sharpen", "pixelate",
  "brightness_contrast", "saturation", "hue", "auto_contrast", "invert", "sepia",
  "watermark", "border", "round_corners", "format_convert", "compress", "strip_metadata",
}

local KNOWN_PARAMS = {
  "width", "height", "scale", "keep_aspect", "method", "x", "y", "degrees", "angle",
  "background", "direction", "radius", "amount", "block", "brightness", "contrast",
  "type", "text", "position", "opacity", "color", "margin", "shadow", "clip_percent",
  "quality", "format",
}

local function trim(s)
  return (string.gsub(tostring(s or ""), "^%s*(.-)%s*$", "%1"))
end

local function json_escape(s)
  s = string.gsub(s, "\\", "\\\\")
  s = string.gsub(s, '"', '\\"')
  s = string.gsub(s, "\r", "\\r")
  s = string.gsub(s, "\n", "\\n")
  s = string.gsub(s, "\t", "\\t")
  return s
end

local function json_encode(value)
  local kind = type(value)
  if value == nil then return "null" end
  if kind == "boolean" then return value and "true" or "false" end
  if kind == "number" then
    if value == math.floor(value) and math.abs(value) < 1e15 then return string.format("%d", value) end
    return tostring(value)
  end
  if kind == "string" then return '"' .. json_escape(value) .. '"' end
  if kind == "table" then
    if #value > 0 then
      local parts = {}
      for _, item in ipairs(value) do parts[#parts + 1] = json_encode(item) end
      return "[" .. table.concat(parts, ",") .. "]"
    end
    local parts = {}
    for key, item in pairs(value) do
      if type(key) == "string" then
        parts[#parts + 1] = '"' .. json_escape(key) .. '":' .. json_encode(item)
      end
    end
    return "{" .. table.concat(parts, ",") .. "}"
  end
  return "null"
end

local function normalize_params_string(raw)
  local text = trim(raw)
  if text == "" then return "{}" end
  if string.sub(text, 1, 1) == "{" then return text end
  local object = {}
  for pair in string.gmatch(text, "[^,;&]+") do
    local key, value = string.match(pair, "^%s*([%w_]+)%s*[=:]%s*(.-)%s*$")
    if key then
      local number = tonumber(value)
      if number then
        object[key] = number
      elseif value == "true" or value == "false" then
        object[key] = (value == "true")
      else
        object[key] = value
      end
    end
  end
  return json_encode(object)
end

local function build_params(args, form_value)
  if type(form_value) == "string" and trim(form_value) ~= "" then
    return normalize_params_string(form_value)
  end
  local source = args.params
  if type(source) == "string" then
    local text = trim(source)
    if text ~= "" then return normalize_params_string(text) end
    source = nil
  end
  local object = {}
  if type(source) == "table" then
    for key, value in pairs(source) do object[key] = value end
  end
  for _, key in ipairs(KNOWN_PARAMS) do
    if object[key] == nil and args[key] ~= nil then object[key] = args[key] end
  end
  return json_encode(object)
end

local function api_error(body)
  if type(body) == "table" and body.__error then return tostring(body.__error) end
  if not body or body == "" then return "接口无响应或返回为空" end
  if type(body) ~= "string" then return nil end
  if string.sub(body, 1, 1) == "/" then return nil end
  if string.find(body, "工具调用额度不足", 1, true) then
    return "套餐工具调用额度不足：请在 KhatKit「设置 → 套餐 / 激活」升级或等待额度重置"
  end
  if string.find(body, "无效或已过期", 1, true) then
    return "激活令牌无效或已过期：请重新输入 Hub 激活令牌（设置 → 套餐 / 激活）"
  end
  if string.find(body, "无共享存储访问权限", 1, true) then
    return body
  end
  if string.find(body, '"error"', 1, true) then
    return string.match(body, '"message"%s*:%s*"([^"]*)"') or "服务端返回错误"
  end
  return nil
end

local function resolve_hub(raw)
  local hub = trim(raw)
  if hub == "" then hub = trim(store.kvGet("hub_base", DEFAULT_HUB)) end
  if hub == "" then hub = DEFAULT_HUB end
  return string.gsub(hub, "/+$", "")
end

local function resolve_token(raw)
  local token = trim(raw)
  if token ~= "" then return token end
  token = trim(store.secretGet("hub_token"))
  if token ~= "" then return token end
  token = trim(store.sharedRead("hub_token"))
  if token ~= "" then return token end
  local values = ui.form("设置 Hub 激活令牌", {
    { type = "input", id = "token", label = "激活令牌（设置 → 套餐 / 激活）" },
    { type = "input", id = "hub", label = "Hub 地址", default = DEFAULT_HUB },
  })
  if not values then return nil end
  token = trim(values.token)
  if token == "" then return nil end
  store.secretSet("hub_token", token)
  local hub = trim(values.hub)
  if hub ~= "" then store.kvSet("hub_base", hub) end
  return token
end

local function process_one(hub, token, path, op, params_json, format, quality)
  local fields = {
    op = op,
    params = params_json,
    ["return"] = "binary",
  }
  if format ~= "" then fields.format = format end
  if quality > 0 then fields.quality = tostring(math.floor(quality)) end
  local ok, result = pcall(tool.httpMultipart, hub .. "/api/image/process", fields, "image", path,
    { ["Authorization"] = "Bearer " .. token }, true)
  if not ok then return nil, tostring(result) end
  local err = api_error(result)
  if err then return nil, err end
  if type(result) ~= "string" or string.sub(result, 1, 1) ~= "/" then
    return nil, "未返回图片：服务端响应格式异常"
  end
  return result, nil
end

local image = trim(args.image)
local raw_files = args.files
local op = trim(args.op)
local format = trim(args.format)
local quality = tonumber(args.quality) or 0
local token_arg = args.token
local hub_arg = args.hub
local params_form = nil

local has_files = false
if type(raw_files) == "table" and #raw_files > 0 then
  has_files = true
elseif type(raw_files) == "string" and trim(raw_files) ~= "" then
  has_files = true
end

if image == "" and not has_files then
  local values = ui.form("云端图像工具箱", {
    { type = "file_picker", id = "image", label = "图片路径（单张）" },
    { type = "file_picker", id = "files", label = "批量路径（每行一个，最多 " .. tostring(MAX_BATCH) .. " 张）" },
    { type = "select", id = "op", label = "操作", options = OP_NAMES, default = op ~= "" and op or "resize" },
    { type = "input", id = "params", label = "参数（JSON 或 width=800,quality=80）", default = "{}" },
    { type = "select", id = "format", label = "输出格式", options = { "png", "jpg", "bmp", "gif" }, default = "png" },
    { type = "slider", id = "quality", label = "JPEG 质量", min = 1, max = 100, default = 85 },
    { type = "input", id = "token", label = "激活令牌（留空使用已保存）" },
  })
  if not values then return { cancelled = true } end
  image = trim(values.image)
  raw_files = values.files
  op = trim(values.op)
  params_form = values.params
  format = trim(values.format)
  quality = tonumber(values.quality) or 85
  token_arg = values.token
end

if op == "" then op = "resize" end
if quality <= 0 then quality = 85 end
quality = math.max(1, math.min(100, quality))

local paths = {}
if image ~= "" then table.insert(paths, image) end
if type(raw_files) == "table" then
  for _, item in ipairs(raw_files) do
    local path = trim(item)
    if path ~= "" then table.insert(paths, path) end
  end
elseif type(raw_files) == "string" and trim(raw_files) ~= "" then
  for item in string.gmatch(raw_files, "[^,\n;]+") do
    local path = trim(item)
    if path ~= "" then table.insert(paths, path) end
  end
end

if #paths == 0 then return { error = "缺少图片：请填写 image 或 files 参数" } end
if #paths > MAX_BATCH then
  return { error = "一次最多处理 " .. tostring(MAX_BATCH) .. " 张，当前 " .. tostring(#paths) .. " 张" }
end

local params_json = build_params(args, params_form)
local hub = resolve_hub(hub_arg)
local token = resolve_token(token_arg)
if not token then return { cancelled = true } end

local outputs = {}
local errors = {}
for index, path in ipairs(paths) do
  ui.progress((index - 0.5) / #paths, "云端处理 " .. tostring(index) .. "/" .. tostring(#paths) .. "（" .. op .. "）")
  local output, err = process_one(hub, token, path, op, params_json, format, quality)
  if output then
    table.insert(outputs, output)
  else
    table.insert(errors, tostring(path) .. "：" .. tostring(err))
  end
end
ui.progress(1, "完成")

local result = {
  op = op,
  hub = hub,
  count = #paths,
  ok_count = #outputs,
  outputs = outputs,
  params = params_json,
  message = "已处理 " .. tostring(#outputs) .. "/" .. tostring(#paths) .. " 张，结果保存在下载目录",
  cost = "本次消耗 " .. tostring(#paths) .. " 次工具调用额度",
}
if #outputs == 1 then result.output = outputs[1] end
if #errors > 0 then result.error = table.concat(errors, "；") end
return result
