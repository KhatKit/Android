-- 极客猫 移除对象。POST JSON：image / mask（均为 data URL）。
-- 计费：成功扣 ¥0.02（失败不扣费）；结果返回 base64 图片，保存为 webp。
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

local function to_data_url(value, field)
  if not value or value == "" then return nil, "缺少" .. field end
  if string.sub(value, 1, 5) == "data:" then return value, nil end
  local encoded = tool.readBase64(value)
  if type(encoded) == "table" and encoded.__error then return nil, tostring(encoded.__error) end
  return encoded, nil
end

local image = args.image
local mask = args.mask
if not image or image == "" or not mask or mask == "" then
  local v = ui.form("移除对象", {
    { type = "file_picker", id = "image", label = "原图" },
    { type = "file_picker", id = "mask", label = "遮罩图（白色为移除区域）" }
  })
  if not v then return { cancelled = true } end
  image, mask = v.image, v.mask
end

local image_data, image_err = to_data_url(image, "原图 image")
if image_err then return { error = image_err } end
local mask_data, mask_err = to_data_url(mask, "遮罩图 mask")
if mask_err then return { error = mask_err } end

ui.progress(0.2, "上传并处理中…")
local body = '{"image":"' .. image_data .. '","mask":"' .. mask_data .. '"}'
local key = api_key()
local res = tool.httpPost("https://zenneko.top/api/removeobject.php", body, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

local b64 = string.match(res, '"(data:image/[^"]*)"') or string.match(res, '"image"%s*:%s*"([A-Za-z0-9+/=]+)"')
if not b64 then return { error = "未在响应中找到图片数据", response = res } end
local output = "/sdcard/Download/zenneko_removeobject_" .. tostring(os.time()) .. "_" .. tostring(math.random(1000, 9999)) .. ".webp"
tool.saveBase64(b64, output)
ui.progress(1, "已保存")
return { output = output }
