// Mechanical import of the pinned YunX protocol implementation.
// Usage: node tools/import-upstream.cjs /path/to/YunX
const fs = require("node:fs");
const path = require("node:path");
const source = path.resolve(process.argv[2]);
const target = path.resolve(__dirname, "..");
const src = path.join(source, "app/src/main/kotlin/com/yunx/app");
const dst = path.join(target, "src/main/java/com/yunx/app");
function copy(relative) {
  const from = path.join(src, relative), to = path.join(dst, relative);
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
}
function folder(relative, filter = () => true) {
  for (const entry of fs.readdirSync(path.join(src, relative), { withFileTypes: true })) {
    if (entry.isFile() && filter(entry.name)) copy(path.join(relative, entry.name));
  }
}
folder("data/network", name => !["HttpClients.kt", "DiagnosticNetworkInterceptor.kt", "GitHubTokenStore.kt"].includes(name));
folder("data/network/model");
folder("data/network/video");
folder("data/repository", name => name.endsWith("ResolveRepository.kt") && name !== "ILanzouResolveRepository.kt");
for (const name of ["UploadIo", "UploadSource", "UploadSigning"]) copy(`data/upload/${name}.kt`);
for (const name of ["XunleiAccountEntity", "MultiAccountEntity"]) copy(`data/db/${name}.kt`);
copy("data/download/DownloadPathPolicy.kt");
for (const name of ["HlsDownloader", "HlsRequestPolicy", "DashMuxer"]) copy(`data/download/${name}.kt`);
copy("util/LogRedactor.kt");
copy("ui/login/DiagnosticWebViewClient.kt");
// Keep only string resources used by the copied parser, never upstream screens.
const refs = new Set();
function scan(dir) {
  for (const item of fs.readdirSync(dir, {withFileTypes: true})) {
    const p = path.join(dir, item.name);
    if (item.isDirectory()) scan(p);
    else if (p.endsWith(".kt")) for (const m of fs.readFileSync(p, "utf8").matchAll(/R\.string\.(\w+)/g)) refs.add(m[1]);
  }
}
scan(dst);
const strings = [];
for (const file of fs.readdirSync(path.join(source, "app/src/main/res/values"))) {
  if (!file.endsWith(".xml")) continue;
  const xml = fs.readFileSync(path.join(source, "app/src/main/res/values", file), "utf8");
  for (const m of xml.matchAll(/<string\s+name="([^"]+)"[^>]*>[\s\S]*?<\/string>/g)) {
    if (refs.has(m[1])) { strings.push(m[0]); refs.delete(m[1]); }
  }
}
if (refs.size) throw Error(`Missing upstream strings: ${[...refs].join(", ")}`);
const res = path.join(target, "src/main/res/values");
fs.mkdirSync(res, {recursive: true});
fs.writeFileSync(path.join(res, "yunx_strings.xml"), `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n${strings.join("\n")}\n</resources>\n`);
fs.copyFileSync(path.join(source, "LICENSE"), path.join(target, "LICENSE"));
