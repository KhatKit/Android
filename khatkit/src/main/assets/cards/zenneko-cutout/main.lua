-- 极客猫 智能抠图。POST JSON：image（data URL）/ model。
-- 计费：成功扣 ¥0.02（失败不扣费）；结果保存为透明 PNG。
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
local model = args.model
if not image or image == "" then
  local v = ui.form("智能抠图", {
    { type = "file_picker", id = "image", label = "选择图片" },
    { type = "select", id = "model", label = "模型", options = { "sharp", "fur" }, default = args.model or "sharp" }
  })
  if not v then return { cancelled = true } end
  image, model = v.image, v.model
end
if not image or image == "" then return { error = "缺少图片 image" } end
if not model or model == "" then model = "sharp" end
if model ~= "sharp" and model ~= "fur" then model = "sharp" end

if string.sub(image, 1, 5) ~= "data:" then
  local encoded = tool.readBase64(image)
  if type(encoded) == "table" and encoded.__error then return { error = tostring(encoded.__error) } end
  image = encoded
end

ui.progress(0.2, "上传并抠图中…")
local body = '{"image":"' .. image .. '","model":"' .. model .. '"}'
local key = api_key()
local res = tool.httpPost("https://zenneko.top/api/cutout.php", body, {
  ["Authorization"] = "Bearer " .. key,
  ["X-Open-Key"] = key
})
local err = check(res)
if err then return { error = err } end

local b64 = string.match(res, '"(data:image/[^"]*)"') or string.match(res, '"image"%s*:%s*"([A-Za-z0-9+/=]+)"')
if not b64 then return { error = "未在响应中找到图片数据", response = res } end
local output = "/sdcard/Download/zenneko_cutout_" .. tostring(os.time()) .. "_" .. tostring(math.random(1000, 9999)) .. ".png"
tool.saveBase64(b64, output)
ui.progress(1, "已保存")
return { output = output, model = model }
