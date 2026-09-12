// 后台下载：脚本只下单，下载任务由宿主服务执行，退出脚本也继续。
let url = args.url;
let name = args.name;
if (!url) {
  const v = ui.form("下载文件", [
    { type: "input", id: "url", label: "文件地址" },
    { type: "input", id: "name", label: "保存文件名（可留空）" }
  ]);
  if (!v) return { cancelled: true };
  url = v.url;
  name = v.name;
}
if (!url) return { error: "缺少下载地址" };

const task = { url: url };
if (name) task.name = name;

const id = download.start(task);
ui.progress(0, "已加入下载队列…");

let status = download.status(id, 5);
for (let i = 0; i < 720 && (status.state === "queued" || status.state === "running"); i++) {
  const ratio = Number(status.progress) || 0;
  ui.progress(ratio, "下载中 " + Math.round(ratio * 100) + "%");
  status = download.status(id, 5);
}

if (status.state !== "done") {
  return { error: status.error || ("下载失败: " + status.state), id: id };
}

const count = Number(store.kvGet("download_count", "0")) + 1;
store.kvSet("download_count", String(count));

return { file: status.file, state: status.state, total_downloads: count };
