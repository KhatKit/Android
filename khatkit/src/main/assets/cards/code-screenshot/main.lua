-- 代码截图生成工具 (纯 Lua + HTML/CSS)
-- 生成 HTML 页面并转换为截图

-- 项目预设配置
local PRESETS = {
  khatkit = {
    theme = "monokai",
    bg = "rgb(40, 44, 52)",
    fontSize = 13,
    lineNumbers = false
  },
  kodeheap = {
    theme = "dracula",
    bg = "rgb(40, 42, 54)",
    fontSize = 13,
    lineNumbers = true
  },
  presentation = {
    theme = "github-dark",
    bg = "rgb(13, 17, 23)",
    fontSize = 16,
    lineNumbers = false
  },
  docs = {
    theme = "solarized-light",
    bg = "rgb(253, 246, 227)",
    fontSize = 12,
    lineNumbers = true
  }
}

-- 主题配色
local THEMES = {
  monokai = {
    bg = "#272822",
    text = "#f8f8f2",
    keyword = "#f92672",
    string = "#e6db74",
    comment = "#75715e",
    number = "#ae81ff",
    function_name = "#a6e22e"
  },
  dracula = {
    bg = "#282a36",
    text = "#f8f8f2",
    keyword = "#ff79c6",
    string = "#f1fa8c",
    comment = "#6272a4",
    number = "#bd93f9",
    function_name = "#50fa7b"
  },
  ["github-dark"] = {
    bg = "#0d1117",
    text = "#c9d1d9",
    keyword = "#ff7b72",
    string = "#a5d6ff",
    comment = "#8b949e",
    number = "#79c0ff",
    function_name = "#d2a8ff"
  },
  ["solarized-light"] = {
    bg = "#fdf6e3",
    text = "#657b83",
    keyword = "#859900",
    string = "#2aa198",
    comment = "#93a1a1",
    number = "#d33682",
    function_name = "#b58900"
  }
}

-- 简单的语法高亮（支持常见语言）
local function highlightCode(code, language, theme)
  local colors = THEMES[theme] or THEMES.monokai

  -- 关键字模式
  local keywords = {
    kotlin = {"fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "return", "import", "package"},
    java = {"public", "private", "protected", "class", "interface", "if", "else", "for", "while", "return", "import", "package", "static", "void"},
    python = {"def", "class", "if", "else", "elif", "for", "while", "return", "import", "from", "as", "try", "except"},
    javascript = {"function", "const", "let", "var", "if", "else", "for", "while", "return", "import", "export", "class"},
    go = {"func", "var", "const", "if", "else", "for", "return", "import", "package", "type", "struct"}
  }

  local lang_keywords = keywords[language] or keywords.kotlin

  -- HTML 转义
  code = code:gsub("&", "&amp;"):gsub("<", "&lt;"):gsub(">", "&gt;")

  -- 高亮注释
  code = code:gsub("(//[^\n]*)", '<span style="color:' .. colors.comment .. '">%1</span>')
  code = code:gsub("(%/%*.-\*%/)", '<span style="color:' .. colors.comment .. '">%1</span>')
  code = code:gsub("(#[^\n]*)", '<span style="color:' .. colors.comment .. '">%1</span>')

  -- 高亮字符串
  code = code:gsub('(".-")', '<span style="color:' .. colors.string .. '">%1</span>')
  code = code:gsub("('.-')", '<span style="color:' .. colors.string .. '">%1</span>')

  -- 高亮关键字
  for _, kw in ipairs(lang_keywords) do
    code = code:gsub("(%f[%w])" .. kw .. "(%f[%W])", '<span style="color:' .. colors.keyword .. ';font-weight:bold">%1' .. kw .. '%2</span>')
  end

  -- 高亮数字
  code = code:gsub("(%d+)", '<span style="color:' .. colors.number .. '">%1</span>')

  return code
end

-- 生成 HTML
local function generateHTML(code, config)
  local theme_colors = THEMES[config.theme] or THEMES.monokai
  local highlighted = highlightCode(code, config.language, config.theme)

  -- 行号
  local lines = {}
  local line_num = 1
  for line in (highlighted .. "\n"):gmatch("([^\n]*)\n") do
    if config.lineNumbers then
      table.insert(lines, string.format('<div class="line"><span class="line-number">%d</span><span class="code-line">%s</span></div>', line_num, line))
    else
      table.insert(lines, '<div class="code-line">' .. line .. '</div>')
    end
    line_num = line_num + 1
  end

  local html = string.format([[
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      background: %s;
      padding: 60px;
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    }
    .container {
      background: %s;
      border-radius: 10px;
      padding: 48px 32px;
      box-shadow: 0 20px 68px rgba(0,0,0,0.55);
      position: relative;
    }
    .window-controls {
      position: absolute;
      top: 15px;
      left: 32px;
      display: flex;
      gap: 8px;
    }
    .window-button {
      width: 12px;
      height: 12px;
      border-radius: 50%%;
    }
    .window-button.red { background: #ff5f56; }
    .window-button.yellow { background: #ffbd2e; }
    .window-button.green { background: #27c93f; }
    .code-container {
      margin-top: %s;
      color: %s;
      font-size: %dpx;
      line-height: 1.6;
      overflow-x: auto;
    }
    .line {
      display: flex;
    }
    .line-number {
      color: #666;
      padding-right: 20px;
      text-align: right;
      min-width: 40px;
      user-select: none;
    }
    .code-line {
      white-space: pre;
    }
  </style>
</head>
<body>
  <div class="container">
    %s
    <div class="code-container">
      %s
    </div>
  </div>
</body>
</html>
]],
    config.bg,
    theme_colors.bg,
    config.windowControls and "20px" or "0",
    theme_colors.text,
    config.fontSize,
    config.windowControls and '<div class="window-controls"><div class="window-button red"></div><div class="window-button yellow"></div><div class="window-button green"></div></div>' or '',
    table.concat(lines, "\n")
  )

  return html
end

-- 主逻辑
local code = args.code
local language = args.language or "auto"
local preset = args.preset
local output = args.output

-- 检测语言
if language == "auto" then
  if code:find("fun%s+%w+%(") or code:find("val%s+") or code:find("var%s+") then
    language = "kotlin"
  elseif code:find("def%s+%w+%(") or code:find("import%s+") then
    language = "python"
  elseif code:find("function%s+") or code:find("const%s+") or code:find("let%s+") then
    language = "javascript"
  elseif code:find("func%s+") or code:find("package%s+main") then
    language = "go"
  elseif code:find("public%s+class") or code:find("void%s+") then
    language = "java"
  else
    language = "kotlin"
  end
end

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

-- 构建配置
local config = {
  theme = args.theme,
  bg = args.bg,
  fontSize = args.fontSize,
  lineNumbers = args.lineNumbers,
  windowControls = args.windowControls,
  language = language
}

-- 应用预设
if preset and PRESETS[preset] then
  local p = PRESETS[preset]
  config.theme = config.theme or p.theme
  config.bg = config.bg or p.bg
  config.fontSize = config.fontSize or p.fontSize
  if config.lineNumbers == nil then config.lineNumbers = p.lineNumbers end
end

-- 默认值
config.theme = config.theme or "monokai"
config.bg = config.bg or "rgb(40, 44, 52)"
config.fontSize = config.fontSize or 14
if config.lineNumbers == nil then config.lineNumbers = false end
if config.windowControls == nil then config.windowControls = true end

-- 生成输出路径
if not output or output == "" then
  local timestamp = os.date("%Y%m%d_%H%M%S")
  output = "/sdcard/Download/code_screenshot_" .. timestamp .. ".html"
end

-- 生成 HTML
local html = generateHTML(code, config)

-- 保存 HTML
tool.writeText(output, html)

-- 返回结果（用户可以用浏览器打开并截图，或使用其他工具转换）
return {
  success = true,
  output = output,
  theme = config.theme,
  language = language,
  message = "代码截图 HTML 已生成: " .. output .. "\n\n提示：\n1. 在浏览器中打开此文件\n2. 使用浏览器截图功能或开发者工具截图\n3. 或使用 Android 截图分享"
}
