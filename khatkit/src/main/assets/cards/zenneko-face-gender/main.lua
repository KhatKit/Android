-- 极客猫 人脸性别转换。multipart/form-data：file_data（文件）+ gender。
-- 计费：成功扣 ¥0.02（失败不扣费）；结果为 data.imageBase64，保存为 jpg。
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
local gender = args.gender
if not image or image == "" then
  local v = ui.form("人脸性别转换", {
    { type = "file_picker", id = "image", label = "选择人像图片" },
    { type = "select", id = "gender", label = "目标性别（0/1）", options = { "0", "1" }, default = args.gender or "0" }
  })
  if not v then return { cancelled = true } end
  image, gender = v.image, v.gender
end
if not image or image == "" then return { error = "缺少人像图片 image" } end
gender = tostring(gender or "0")
if gender ~= "0" and gender ~= "1" then gender = "0" end

ui.progress(0.2, "上传并生成中…")
local key = api_key()
local res = tool.httpMultipart(
  "https://zenneko.top/api/face_gender.php",
  { gender = gender },
  "file_data",
  image,
  { ["Authorization"] = "Bearer " .. key, ["X-Open-Key"] = key },
  true
)
local err = check(res)
if err then return { error = err } end

if type(res) == "string" and string.sub(res, 1, 8) == "/sdcard/" then
  return { output = res, gender = gender }
end

local b64 = string.match(res, '"imageBase64"%s*:%s*"([^"]*)"') or string.match(res, '"(data:image/[^"]*)"')
if not b64 then
  if string.find(res, "404 Not Found", 1, true) then
    return { error = "接口不可用：face_gender.php 当前返回 404（文档与实际服务不一致），请稍后再试", response = res }
  end
  return { error = "未在响应中找到图片数据", response = res }
end
local output = "/sdcard/Download/zenneko_face_gender_" .. tostring(os.time()) .. "_" .. tostring(math.random(1000, 9999)) .. ".jpg"
tool.saveBase64(b64, output)
ui.progress(1, "已保存")
return { output = output, gender = gender }
