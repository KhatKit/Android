-- @khatkit/std 内置库（Lua 子集）
local M = {}

function M.join_path(...)
  local parts = { ... }
  local joined = table.concat(parts, "/")
  return (joined:gsub("//+", "/"))
end

function M.file_size(bytes)
  local units = { "B", "KB", "MB", "GB", "TB" }
  local n = tonumber(bytes) or 0
  local index = 1
  while n >= 1024 and index < #units do
    n = n / 1024
    index = index + 1
  end
  return string.format("%.1f%s", n, units[index])
end

return M
