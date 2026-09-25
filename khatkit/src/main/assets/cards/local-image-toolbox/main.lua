-- 本地图像工具箱：所有处理在设备端由 imageToolbox bridge 完成（不联网、不上传）。
-- 参数 params 支持 JSON 字符串（{"width":800,"keep_aspect":true}）或键值对（width=800,quality=80）。
-- 结果默认写到源文件同目录 <名字>_<操作>.<扩展名>；显式 output 可指定输出路径。
-- compress 复用 tool.compressImage（质量压缩）；images_to_pdf 用 files 数组按序成页。

local OP_NAMES = {
  "resize", "crop", "rotate", "flip", "grayscale", "blur", "sharpen", "pixelate",
  "brightness_contrast", "saturation", "hue", "auto_contrast", "invert", "sepia",
  "watermark", "border", "round_corners", "format_convert", "compress", "strip_metadata",
  "images_to_pdf", "pdf_to_images", "pdf_page_count",
}

local function trim(text)
  return (string.gsub(tostring(text or ""), "^%s*(.-)%s*$", "%1"))
end

-- ---------- 轻量 JSON 解析（参数需要真实取值来派发 bridge 调用） ----------

local function skip_ws(text, index)
  while index <= #text do
    local char = string.sub(text, index, index)
    if char == " " or char == "\t" or char == "\n" or char == "\r" then
      index = index + 1
    else
      break
    end
  end
  return index
end

local parse_value

local function parse_string(text, index)
  index = index + 1
  local buffer = {}
  while index <= #text do
    local char = string.sub(text, index, index)
    if char == '"' then
      return table.concat(buffer), index + 1
    elseif char == "\\" then
      local next_char = string.sub(text, index + 1, index + 1)
      if next_char == "n" then
        table.insert(buffer, "\n")
      elseif next_char == "t" then
        table.insert(buffer, "\t")
      elseif next_char == "r" then
        table.insert(buffer, "\r")
      elseif next_char == "b" then
        table.insert(buffer, "\b")
      elseif next_char == "f" then
        table.insert(buffer, "\f")
      elseif next_char == "u" then
        local code = tonumber(string.sub(text, index + 2, index + 5), 16)
        if code ~= nil then table.insert(buffer, utf8.char(code)) end
        index = index + 4
      else
        table.insert(buffer, next_char)
      end
      index = index + 2
    else
      table.insert(buffer, char)
      index = index + 1
    end
  end
  return table.concat(buffer), index
end

local function parse_number(text, index)
  local cursor = index
  while cursor <= #text do
    local char = string.sub(text, cursor, cursor)
    if string.find(char, "[%d%.eE%+%-]") then
      cursor = cursor + 1
    else
      break
    end
  end
  return tonumber(string.sub(text, index, cursor - 1)), cursor
end

local function parse_array(text, index)
  local array = {}
  index = skip_ws(text, index + 1)
  if string.sub(text, index, index) == "]" then return array, index + 1 end
  while true do
    local value
    value, index = parse_value(text, index)
    array[#array + 1] = value
    index = skip_ws(text, index)
    local char = string.sub(text, index, index)
    if char == "," then
      index = skip_ws(text, index + 1)
    elseif char == "]" then
      return array, index + 1
    else
      return array, index
    end
  end
end

local function parse_object(text, index)
  local object = {}
  index = skip_ws(text, index + 1)
  if string.sub(text, index, index) == "}" then return object, index + 1 end
  while true do
    local key
    index = skip_ws(text, index)
    key, index = parse_string(text, index)
    index = skip_ws(text, index)
    if string.sub(text, index, index) == ":" then index = skip_ws(text, index + 1) end
    local value
    value, index = parse_value(text, index)
    if key ~= nil then object[key] = value end
    index = skip_ws(text, index)
    local char = string.sub(text, index, index)
    if char == "," then
      index = skip_ws(text, index + 1)
    elseif char == "}" then
      return object, index + 1
    else
      return object, index
    end
  end
end

parse_value = function(text, index)
  index = skip_ws(text, index)
  local char = string.sub(text, index, index)
  if char == "{" then return parse_object(text, index) end
  if char == "[" then return parse_array(text, index) end
  if char == '"' then return parse_string(text, index) end
  if string.sub(text, index, index + 3) == "true" then return true, index + 4 end
  if string.sub(text, index, index + 4) == "false" then return false, index + 5 end
  if string.sub(text, index, index + 3) == "null" then return nil, index + 4 end
  return parse_number(text, index)
end

local function json_decode(text)
  local ok, value, index = pcall(parse_value, text, 1)
  if not ok or type(value) ~= "table" then return nil end
  index = skip_ws(text, index or 1)
  if index <= #text then return nil end
  return value
end

local function parse_params(raw)
  if type(raw) == "table" then return raw end
  if type(raw) ~= "string" then return {} end
  local text = trim(raw)
  if text == "" then return {} end
  if string.sub(text, 1, 1) == "{" then
    local parsed = json_decode(text)
    if type(parsed) ~= "table" then return nil end
    return parsed
  end
  local object = {}
  for pair in string.gmatch(text, "[^,;&]+") do
    local key, value = string.match(pair, "^%s*([%w_]+)%s*[=:]%s*(.-)%s*$")
    if key ~= nil then
      local number = tonumber(value)
      if number ~= nil then
        object[key] = number
      elseif value == "true" or value == "false" then
        object[key] = (value == "true")
      else
        object[key] = value
      end
    end
  end
  return object
end

-- ---------- 参数取值助手 ----------

local function num(value, default)
  if type(value) == "number" then return value end
  local parsed = tonumber(value)
  if parsed == nil then return default end
  return parsed
end

local function int(value, default)
  local parsed = num(value, nil)
  if parsed == nil then return default end
  return math.floor(parsed)
end

local function bool(value, default)
  if type(value) == "boolean" then return value end
  if type(value) == "string" then
    if value == "true" or value == "1" then return true end
    if value == "false" or value == "0" then return false end
  end
  if type(value) == "number" then return value ~= 0 end
  return default
end

local function str(value, default)
  if type(value) == "string" then
    local text = trim(value)
    if text ~= "" then return text end
  elseif type(value) == "number" then
    return tostring(value)
  end
  return default
end

-- bridge 调用统一返回：成功=路径/路径表/数字，失败={__error=中文说明}，这里归一成 (值, 错误)。
local function unwrap(result)
  if type(result) == "table" and result.__error ~= nil then
    return nil, tostring(result.__error)
  end
  if result == nil then
    return nil, "操作未返回结果"
  end
  return result, nil
end

-- ---------- 操作派发：显式 if/elseif（反射按方法名调用） ----------

local function run_op(op, path, p)
  if op == "resize" then
    return imageToolbox.resize(path, int(p.width, 0), int(p.height, 0), bool(p.keep_aspect, true))
  elseif op == "crop" then
    return imageToolbox.crop(path, int(p.x, 0), int(p.y, 0), int(p.width, 0), int(p.height, 0))
  elseif op == "rotate" then
    return imageToolbox.rotate(path, num(p.degrees, num(p.angle, 90)))
  elseif op == "flip" then
    local horizontal = true
    if p.horizontal ~= nil then
      horizontal = bool(p.horizontal, true)
    elseif p.direction ~= nil then
      horizontal = (str(p.direction, "horizontal") ~= "vertical")
    end
    return imageToolbox.flip(path, horizontal)
  elseif op == "grayscale" then
    return imageToolbox.grayscale(path)
  elseif op == "blur" then
    return imageToolbox.blur(path, int(p.radius, 8))
  elseif op == "sharpen" then
    return imageToolbox.sharpen(path, num(p.amount, 1))
  elseif op == "pixelate" then
    return imageToolbox.pixelate(path, int(p.block, int(p.block_size, int(p.size, 12))))
  elseif op == "brightness_contrast" then
    return imageToolbox.brightnessContrast(path, int(p.brightness, 0), int(p.contrast, 0))
  elseif op == "saturation" then
    return imageToolbox.saturation(path, num(p.factor, num(p.amount, 1)))
  elseif op == "hue" then
    return imageToolbox.hue(path, num(p.degrees, 0))
  elseif op == "auto_contrast" then
    return imageToolbox.autoContrast(path)
  elseif op == "invert" then
    return imageToolbox.invert(path)
  elseif op == "sepia" then
    return imageToolbox.sepia(path)
  elseif op == "watermark" then
    local alpha = int(p.alpha, -1)
    if alpha < 0 then
      local opacity = num(p.opacity, 0.6)
      if opacity <= 1 then opacity = opacity * 255 end
      alpha = math.floor(opacity)
    end
    local size = int(p.size, int(p.text_size, 0))
    local color = str(p.color, str(p.color_hex, "#FFFFFF"))
    return imageToolbox.watermark(path, str(p.text, ""), str(p.position, "bottom-right"), alpha, size, color)
  elseif op == "border" then
    return imageToolbox.border(path, int(p.width, 16), str(p.color, str(p.color_hex, "#FFFFFF")))
  elseif op == "round_corners" then
    return imageToolbox.roundCorners(path, int(p.radius, 48))
  elseif op == "format_convert" or op == "convert" then
    return imageToolbox.convert(path, str(p.format, "png"), int(p.quality, 90))
  elseif op == "compress" then
    return tool.compressImage(path, int(p.quality, 85))
  elseif op == "strip_metadata" then
    return imageToolbox.stripMetadata(path)
  elseif op == "pdf_to_images" then
    return imageToolbox.pdfToImages(path, str(p.output_dir, str(p.output, "")))
  elseif op == "pdf_page_count" then
    return imageToolbox.pdfPageCount(path)
  end
  return { __error = "不支持的操作：" .. tostring(op) }
end

-- ---------- 主流程 ----------

local op = str(args.op, "resize")
local params = parse_params(args.params)
if params == nil then
  return { error = "params JSON 解析失败：" .. tostring(args.params) }
end

local paths = {}
local function add_path(value)
  if type(value) ~= "string" then return end
  for item in string.gmatch(value, "[^,\n;]+") do
    local path = trim(item)
    if path ~= "" then paths[#paths + 1] = path end
  end
end

add_path(str(args.image, str(params.image, "")))
local raw_files = args.files
if raw_files == nil then raw_files = params.files end
if type(raw_files) == "table" then
  for _, item in ipairs(raw_files) do add_path(item) end
else
  add_path(raw_files)
end

if #paths == 0 then
  local values = ui.form("本地图像工具箱", {
    { type = "file_picker", id = "image", label = "图片/PDF 路径（多张用换行或逗号分隔）" },
    { type = "select", id = "op", label = "操作", options = OP_NAMES, default = op },
    { type = "input", id = "params", label = "参数（JSON 或 width=800,quality=80）", default = "{}" },
    { type = "input", id = "output", label = "输出路径（留空写到源文件旁）" },
  })
  if not values then return { cancelled = true } end
  op = str(values.op, op)
  local form_params = parse_params(values.params)
  if form_params ~= nil then params = form_params end
  add_path(values.image)
  local form_output = str(values.output, "")
  if form_output ~= "" then params.output = form_output end
end

if #paths == 0 then return { error = "缺少文件：请填写 image/files 参数" } end

local explicit_output = str(args.output, str(params.output, ""))
if op == "pdf_to_images" and explicit_output ~= "" then
  params.output_dir = explicit_output
end

if op == "images_to_pdf" then
  ui.progress(0.2, "正在生成 PDF…")
  local ok, result = pcall(imageToolbox.imagesToPdf, paths, explicit_output)
  if not ok then return { error = "生成 PDF 失败：" .. tostring(result) } end
  local output, failure = unwrap(result)
  if failure ~= nil then return { error = "生成 PDF 失败：" .. failure } end
  ui.progress(1, "完成")
  return {
    op = op,
    count = #paths,
    output = output,
    outputs = { output },
    local_only = true,
    message = "已在本地生成 PDF：" .. tostring(output),
  }
end

local outputs = {}
local errors = {}
for index, path in ipairs(paths) do
  ui.progress((index - 0.5) / #paths, "本地处理 " .. tostring(index) .. "/" .. tostring(#paths) .. "（" .. op .. "）")
  local ok, result = pcall(run_op, op, path, params)
  if not ok then
    errors[#errors + 1] = tostring(path) .. "：" .. tostring(result)
  else
    local value, failure = unwrap(result)
    if failure ~= nil then
      errors[#errors + 1] = tostring(path) .. "：" .. failure
    else
      outputs[#outputs + 1] = value
    end
  end
end
ui.progress(1, "完成")

local summary = {
  op = op,
  count = #paths,
  ok_count = #outputs,
  outputs = outputs,
  params = params,
  local_only = true,
  message = "已在本机处理 " .. tostring(#outputs) .. "/" .. tostring(#paths) .. " 个文件，结果与源文件同目录",
}
if #outputs == 1 then summary.output = outputs[1] end
if #errors > 0 then summary.error = table.concat(errors, "；") end
if #outputs == 0 then return { error = summary.error or "处理失败：没有产生输出" } end
return summary
