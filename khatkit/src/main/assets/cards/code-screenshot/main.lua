-- 代码截图生成工具
-- 通过 Shizuku 执行 Python 脚本，生成精美的代码截图（carbon.now.sh 风格）

local SCRIPT_URL = "https://raw.githubusercontent.com/KhatKit/tools/main/code-screenshot.py"
local WORK_DIR = "/sdcard/Download/khatkit_code_screenshot"
local SCRIPT_PATH = WORK_DIR .. "/code-screenshot.py"

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

local LANGUAGE_EXT = {
  kotlin = "kt",
  java = "java",
  python = "py",
  javascript = "js",
  go = "go"
}

-- 单引号包裹，防止 shell 注入
local function quote(value)
  return "'" .. tostring(value):gsub("'", "'\\''") .. "'"
end

-- 执行 shell 命令；成功返回 true + stdout，失败返回 false + 错误输出
local function runShell(cmd)
  local out = shizuku.shell(cmd) or ""
  local code = string.match(out, "^%[exit (%d+)%]")
  if code and code ~= "0" then
    return false, out
  end
  if string.match(out, "^%[error%]") or string.match(out, "^%[timeout%]") then
    return false, out
  end
  return true, out
end

-- 下载并写入 Python 脚本（几 KB，每次运行覆盖写入）
local function ensureScript()
  local body = tool.httpGet(SCRIPT_URL, {})
  if type(body) == "table" then
    return false, "下载脚本失败：" .. tostring(body.__error or "未知错误")
  end
  if type(body) ~= "string" or body == "" then
    return false, "下载脚本失败：响应为空"
  end
  local written = tool.writeText(SCRIPT_PATH, body)
  if type(written) == "table" and written.__error then
    return false, "写入脚本失败：" .. tostring(written.__error)
  end
  return true
end

-- 检查并安装依赖
local function checkDependencies()
  local ok = runShell("python3 -c 'import PIL, pygments' 2>&1")
  if ok then
    return true
  end
  local installed = runShell("pip3 install --user -q Pillow pygments 2>&1")
  if not installed then
    return false, "安装依赖失败，请手动运行: pip3 install --user Pillow pygments"
  end
  return true
end

-- 主逻辑
local code = args.code
local language = args.language or "auto"
local theme = args.theme
local bg = args.bg
local fontSize = args.fontSize
local lineNumbers = args.lineNumbers
local shadow = args.shadow
local windowControls = args.windowControls
local scale = args.scale or 2
local output = args.output
local preset = args.preset

-- 如果没有提供代码，弹出表单
if not code or code == "" then
  local v = ui.form("代码截图", {
    { type = "input", id = "code", label = "代码内容" },
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
  }, { fullscreen = true, landscape = true })
  if not v then return { cancelled = true } end
  code = v.code
  preset = v.preset ~= "custom" and v.preset or nil
  language = v.language or "auto"
  lineNumbers = v.lineNumbers == true
  output = v.output
end

if not code or code == "" then
  return { error = "代码内容不能为空" }
end

-- 应用预设
if preset and PRESETS[preset] then
  local p = PRESETS[preset]
  theme = theme or p.theme
  bg = bg or p.bg
  fontSize = fontSize or p.fontSize
  if lineNumbers == nil then lineNumbers = p.lineNumbers end
end

-- 默认值
theme = theme or "monokai"
bg = bg or "rgba(40, 44, 52, 1)"
fontSize = fontSize or 14
if lineNumbers == nil then lineNumbers = false end
if shadow == nil then shadow = true end
if windowControls == nil then windowControls = true end

-- 生成输出路径
local timestamp = os.date("%Y%m%d_%H%M%S")
if not output or output == "" then
  output = WORK_DIR .. "/code_screenshot_" .. timestamp .. ".png"
end

ui.progress(0.05, "准备代码截图")

-- 下载脚本
local ok, err = ensureScript()
if not ok then
  return { error = err }
end

ui.progress(0.2, "检查 Python 依赖")

-- 检查依赖
ok, err = checkDependencies()
if not ok then
  return { error = err, needsSetup = true }
end

-- 写入临时代码文件（按语言给扩展名，便于自动识别）
local ext = LANGUAGE_EXT[language] or "txt"
local codeFile = WORK_DIR .. "/code_temp_" .. timestamp .. "." .. ext
local written = tool.writeText(codeFile, code)
if type(written) == "table" and written.__error then
  return { error = "写入代码文件失败：" .. tostring(written.__error) }
end

-- 构建命令参数
local cmd = "python3 " .. quote(SCRIPT_PATH)
  .. " -i " .. quote(codeFile)
  .. " -o " .. quote(output)
  .. " --theme " .. quote(theme)
  .. " --bg " .. quote(bg)
  .. " --font-size " .. tostring(math.floor(fontSize))
  .. " --language " .. quote(language)
  .. " --scale " .. tostring(math.floor(scale))
if lineNumbers then
  cmd = cmd .. " --line-numbers"
end
if not shadow then
  cmd = cmd .. " --no-shadow"
end
if not windowControls then
  cmd = cmd .. " --no-window-controls"
end

ui.progress(0.35, "生成截图")

-- 执行截图生成
local generated, out = runShell(cmd)
tool.deletePath(codeFile, false)

if not generated then
  return {
    error = "生成截图失败",
    detail = out
  }
end

ui.progress(1, "完成")

return {
  success = true,
  output = output,
  theme = theme,
  language = language,
  message = "代码截图已生成: " .. output .. "\n" .. tostring(out)
}
