// SPDX-License-Identifier: AGPL-3.0-or-later
// Import only the explicitly pinned protocol allowlist. Never copy upstream UI/locales.
// Usage: node tools/import-upstream.cjs /path/to/YunX [--check]
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { parseStrings, buildResources, generate } = require("./generate-locales.cjs");
const manifest = require("./upstream-manifest.json");
const target = path.resolve(__dirname, "..");
const digest = bytes => crypto.createHash("sha256").update(bytes).digest("hex");
function prepare(source) {
  // Validate every translation before considering any writes.
  const locales = buildResources();
  const license = fs.readFileSync(path.join(source, "LICENSE"));
  if (digest(license) !== manifest.licenseSha256) throw Error("Upstream LICENSE differs from pinned " + manifest.revision);
  if (!fs.readFileSync(path.join(target, "LICENSE")).equals(license)) throw Error("Local LICENSE differs; preserve it and review manually");
  const copies = [], refs = new Set();
  for (const [relative, expected] of Object.entries(manifest.files)) {
    if (path.isAbsolute(relative) || relative.split("/").includes("..") || !relative.endsWith(".kt")) throw Error("Unsafe manifest path: " + relative);
    const bytes = fs.readFileSync(path.join(source, manifest.sourceRoot, relative));
    if (digest(bytes) !== expected) throw Error("Source differs from pinned " + manifest.revision + ": " + relative);
    const dest = path.join(target, "src/main/java/com/yunx/app", relative);
    if (fs.existsSync(dest) && !fs.readFileSync(dest).equals(bytes)) throw Error("Refusing to overwrite local protocol changes: " + relative);
    for (const match of bytes.toString("utf8").matchAll(/R\.string\.(\w+)/g)) refs.add(match[1]);
    copies.push({ dest, bytes });
  }
  const upstream = new Map();
  const res = path.join(source, "app/src/main/res/values");
  for (const file of fs.readdirSync(res).filter(name => name.endsWith(".xml")).sort()) {
    const xml = fs.readFileSync(path.join(res, file), "utf8");
    if (!/<string\s/.test(xml)) continue;
    for (const [key, entry] of parseStrings(xml, file)) {
      if (upstream.has(key)) throw Error("Duplicate upstream resource: " + key);
      upstream.set(key, entry);
    }
  }
  const missing = [...refs].filter(key => !locales.source.has(key));
  if (missing.length) throw Error("Missing local Chinese source / English translation for upstream keys: " + missing.join(", "));
  for (const [key, entry] of locales.source) {
    const original = upstream.get(key);
    if (!original) throw Error("Missing pinned upstream resource: " + key);
    if (original.body !== entry.body || original.attrs !== entry.attrs) throw Error("Chinese source changed; review both locale maps: " + key);
  }
  return { copies, locales, refs };
}
function main() {
  const args = process.argv.slice(2);
  if (args.length < 1 || args.length > 2 || (args.length === 2 && args[1] !== "--check") || args[0].startsWith("--")) throw Error("Usage: node tools/import-upstream.cjs /path/to/YunX [--check]");
  const plan = prepare(path.resolve(args[0]));
  const check = args[1] === "--check";
  if (!check) {
    for (const { dest, bytes } of plan.copies) {
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.writeFileSync(dest, bytes);
    }
  }
  generate(check, plan.locales);
  console.log((check ? "Verified " : "Imported ") + plan.copies.length + " pinned protocol files at " + manifest.revision + "; " + plan.locales.source.size + " upstream + " + Object.keys(plan.locales.host).length + " host translations per locale");
}
if (require.main === module) {
  try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { prepare };
