// 导出当前屏幕的可见文字（最多 60 条），给 AI 做无截图场景的界面理解。
const nodes = accessibility.dumpWindow();
const items = [];
for (let i = 0; i < nodes.length && items.length < 60; i++) {
  const node = nodes[i];
  const text = node.text || node.desc || "";
  if (!text) continue;
  items.push({
    text: text,
    clickable: !!node.clickable,
    bounds: node.bounds
  });
}

return {
  package: accessibility.currentPackage(),
  count: items.length,
  nodes: items
};
