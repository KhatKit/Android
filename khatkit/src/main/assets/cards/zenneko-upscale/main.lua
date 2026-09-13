-- 极客猫 图像放大。POST JSON：image（data URL）/ scale。
-- 计费：成功扣 ¥0.02（失败不扣费）；接口返回远程 imageUrl。
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

local image = args.image
local scale = args.scale
if not image or image == "" then
  local v = ui.form("图像放大", {
    { type = "file_picker", id = "image", label = "选择图片" },
    { type = "number", id = "scale", label = "放大倍数", default = args.scale or 2 }
  })
  if not v then return { cancelled = true } end
  image, scale = v.image, v.scale
end
if not image or image == "" then return { error = "缺少图片 image" } end
scale = tonumber(scale) or 2
if scale < 1 then scale = 1 end

if string.sub(image, 1, 5) ~= "data:" then
  local encoded = tool.readBase64(image)
  if type(encoded) == "table" and encoded.__error then return { error = tostring(encoded.__error) } end
  image = encoded
end

ui.progress(0.2, "上传并放大中…")
local body = '{"image":"' .. image .. '","scale":' .. tostring(math.floor(scale)) .. "}"
local key = api_key()
local res = tool.httpPost("https://zenneko.top/api/upscale.php", body, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

local image_url = string.match(res, '"imageUrl"%s*:%s*"([^"]*)"') or ""
ui.progress(1, "完成")
return {
  image_url = image_url,
  scale = scale,
  message = "结果图片链接已返回，可直接打开或下载",
  response = res
}
