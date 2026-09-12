// @khatkit/std 内置库（JS 子集）
function joinPath() {
  var parts = Array.prototype.slice.call(arguments);
  return parts.join('/').replace(/\/+/g, '/');
}

function formatSize(bytes) {
  var units = ['B', 'KB', 'MB', 'GB', 'TB'];
  var n = Number(bytes) || 0;
  var i = 0;
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024;
    i++;
  }
  return n.toFixed(1) + units[i];
}

module.exports = {
  joinPath: joinPath,
  formatSize: formatSize,
};
