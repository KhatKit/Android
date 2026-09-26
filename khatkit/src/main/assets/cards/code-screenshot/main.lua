-- 代码截图生成工具
-- 从云端下载 Python 脚本并执行

local SCRIPT_URL = "https://raw.githubusercontent.com/heizige/KhatKit/main/scripts/code-screenshot.py"
local SCRIPT_PATH = "/data/local/tmp/code-screenshot.py"

-- 项目预设配置
local PRESETS = {
  khatkit = {
    theme = "monokai",
    bg = "rgba(40, 44, 52, 1)",
    fontSize = 13,
    lineNumbers = false
  },
  kodeheap = {
    theme = "dracula",
    bg = "rgba(40, 42, 54, 1)",
    fontSize = 13,
    lineNumbers = true
  },
  presentation = {
    theme = "github-dark",
    bg = "rgba(13, 17, 23, 1)",
    fontSize = 16,
    lineNumbers = false
  },
  docs = {
    theme = "solarized-light",
    bg = "rgba(253, 246, 227, 1)",
    fontSize = 12,
    lineNumbers = true
  }
}

-- 下载脚本
local function downloadScript()
  if fs.exists(SCRIPT_PATH) then
    return true
  end

  local result = process.exec({
    cmd = "curl",
    args = {"-fsSL", "-o", SCRIPT_PATH, SCRIPT_URL},
    timeout = 30000
  })

  if result.exitCode ~= 0 then
    return false, "下载脚本失败: " .. (result.stderr or "")
  end

  -- 添加执行权限
  process.exec({cmd = "chmod", args = {"+x", SCRIPT_PATH}})

  return true
end

-- 检查 Python 依赖
local function checkDependencies()
  local check = process.exec({
    cmd = "python3",
    args = {"-c", "import PIL; import pygments"}
  })

  if check.exitCode ~= 0 then
    return false, "缺少 Python 依赖。请在 Termux 中运行:\npkg install python python-pillow\npip install pygments"
  end

  return true
end

-- 主逻辑
local code = args.code
local language = args.language or "auto"
local preset = args.preset
local output = args.output

-- 如果没有提供代码，弹出表单
if not code or code == "" then
  local v = ui.form("代码截图", {
    { type = "textarea", id = "code", label = "代码内容", rows = 10 },
    { type = "select", id = "preset", label = "预设配置",
      options = {
        { value = "khatkit", label = "KhatKit (Monokai)" },
        { value = "kodeheap", label = "KodeHeap (Dracula)" },
        { value = "presentation", label = "演示 (大字号)" },
        { value = "docs", label = "文档 (亮色)" },
        { value = "custom", label = "自定义" }
      }
    },
    { type = "select", id = "language", label = "语言",
      options = {
        { value = "auto", label = "自动检测" },
        { value = "kotlin", label = "Kotlin" },
        { value = "java", label = "Java" },
        { value = "python", label = "Python" },
        { value = "javascript", label = "JavaScript" },
        { value = "go", label = "Go" }
      }
    },
    { type = "switch", id = "lineNumbers", label = "显示行号" },
    { type = "input", id = "output", label = "输出路径（可选）" }
  })
  if not v then return { cancelled = true } end
  code = v.code
  preset = v.preset ~= "custom" and v.preset or nil
  language = v.language or "auto"
  output = v.output
end

if not code or code == "" then
  return { error = "代码内容不能为空" }
end

-- 下载脚本
local ok, err = downloadScript()
if not ok then
  return { error = err }
end

-- 检查依赖
ok, err = checkDependencies()
if not ok then
  return { error = err }
end

-- 构建参数
local py_args = {SCRIPT_PATH, "-o"}

-- 生成输出路径
if not output or output == "" then
  local timestamp = os.date("%Y%m%d_%H%M%S")
  output = "/sdcard/Download/code_screenshot_" .. timestamp .. ".png"
end
table.insert(py_args, output)

-- 应用预设
if preset and PRESETS[preset] then
  table.insert(py_args, "--preset")
  table.insert(py_args, preset)
else
  -- 自定义参数
  local theme = args.theme or "monokai"
  local bg = args.bg or "rgba(40, 44, 52, 1)"
  local fontSize = args.fontSize or 14
  local lineNumbers = args.lineNumbers

  table.insert(py_args, "--theme")
  table.insert(py_args, theme)
  table.insert(py_args, "--bg")
  table.insert(py_args, bg)
  table.insert(py_args, "--font-size")
  table.insert(py_args, tostring(fontSize))

  if lineNumbers then
    table.insert(py_args, "--line-numbers")
  end
end

-- 语言
if language and language ~= "auto" then
  table.insert(py_args, "-l")
  table.insert(py_args, language)
end

-- 窗口控制和阴影
if args.windowControls == false then
  table.insert(py_args, "--no-window-controls")
end
if args.shadow == false then
  table.insert(py_args, "--no-shadow")
end

-- 缩放
local scale = args.scale or 2
table.insert(py_args, "--scale")
table.insert(py_args, tostring(scale))

-- 执行 Python 脚本
local result = process.exec({
  cmd = "python3",
  args = py_args,
  stdin = code,
  timeout = 60000
})

if result.exitCode ~= 0 then
  return {
    error = "生成截图失败: " .. (result.stderr or result.stdout or "未知错误")
  }
end

return {
  success = true,
  output = output,
  message = "代码截图已生成: " .. output
}
