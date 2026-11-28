// SPDX-License-Identifier: AGPL-3.0-or-later
// Offline checks, plus pinned-import integration when an upstream path is supplied.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const crypto = require("node:crypto");
const { spawnSync } = require("node:child_process");
const { buildResources, generate, validatePair } = require("./generate-locales.cjs");
const root = path.resolve(__dirname, "..");
const hash = b => crypto.createHash("sha256").update(b).digest("hex");
const resources = buildResources();
generate(true, resources);
validatePair("rich", '<b>File %1$s</b>', '<b>文件 %1$s</b>');
assert.throws(() => validatePair("rich", 'File %1$s', '<b>文件 %1$s</b>'), /markup mismatch/);
assert.throws(() => validatePair("format", 'File %1$d', '文件 %1$s'), /parameter mismatch/);
assert.throws(() => validatePair("untranslated", '文件', '文件'), /Untranslated Chinese/);
assert.throws(() => validatePair("placeholder", 'TODO', '文件'), /Placeholder translation/);
let cases = 5;
if (process.argv[2]) {
  const upstream = path.resolve(process.argv[2]);
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), "khatkit-yunx-locales-"));
  function fixture(label) {
    const dir = path.join(temp, label);
    for (const p of ["tools", "src", "LICENSE"]) fs.cpSync(path.join(root, p), path.join(dir, p), { recursive: true });
    return dir;
  }
  function run(dir, args, expected = 0, pattern) {
    const p = spawnSync(process.execPath, args, { cwd: dir, encoding: "utf8" });
    assert.equal(p.status, expected, p.stdout + p.stderr);
    if (pattern) assert.match(p.stderr, pattern);
  }
  function outputHashes(dir) {
    return Object.keys(resources.outputs).map(p => hash(fs.readFileSync(path.join(dir, p))));
  }
  function editMap(dir, action) {
    const p = path.join(dir, "tools/localization/en.json");
    const map = JSON.parse(fs.readFileSync(p, "utf8")); action(map);
    fs.writeFileSync(p, JSON.stringify(map));
  }
  const roundtrip = fixture("roundtrip");
  const originalHashes = outputHashes(roundtrip);
  for (let i = 0; i < 2; i++) run(roundtrip, ["tools/import-upstream.cjs", upstream]);
  assert.deepEqual(outputHashes(roundtrip), originalHashes);
  run(roundtrip, ["tools/import-upstream.cjs", upstream, "--check"]);
  cases++;
  const missing = fixture("missing");
  editMap(missing, map => delete map.e1_with_errno);
  run(missing, ["tools/import-upstream.cjs", upstream], 1, /missing=.*e1_with_errno/);
  assert.deepEqual(outputHashes(missing), originalHashes);
  cases++;
  const mismatch = fixture("format");
  editMap(mismatch, map => { map.e1_with_errno = 'Wrong %1$d'; });
  run(mismatch, ["tools/import-upstream.cjs", upstream], 1, /Format parameter mismatch/);
  assert.deepEqual(outputHashes(mismatch), originalHashes);
  cases++;
  const stale = fixture("stale");
  fs.appendFileSync(path.join(stale, "src/main/res/values/yunx_strings.xml"), "\n");
  run(stale, ["tools/generate-locales.cjs", "--check"], 1, /Generated resource differs/);
  cases++;
  const wip = fixture("wip");
  const manifest = JSON.parse(fs.readFileSync(path.join(wip, "tools/upstream-manifest.json"), "utf8"));
  const relative = Object.keys(manifest.files)[0];
  const local = path.join(wip, "src/main/java/com/yunx/app", relative);
  fs.appendFileSync(local, "\n// local WIP\n");
  const wipHash = hash(fs.readFileSync(local));
  run(wip, ["tools/import-upstream.cjs", upstream], 1, /Refusing to overwrite local protocol changes/);
  assert.equal(hash(fs.readFileSync(local)), wipHash);
  assert.deepEqual(outputHashes(wip), originalHashes);
  cases++;
  const changed = fixture("changed");
  const source = path.join(temp, "upstream");
  fs.mkdirSync(source, { recursive: true });
  fs.copyFileSync(path.join(upstream, "LICENSE"), path.join(source, "LICENSE"));
  fs.cpSync(path.join(upstream, "app/src/main/res/values"), path.join(source, "app/src/main/res/values"), { recursive: true });
  for (const relative of Object.keys(manifest.files)) {
    const dest = path.join(source, manifest.sourceRoot, relative);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(path.join(upstream, manifest.sourceRoot, relative), dest);
  }
  const protocol = path.join(source, manifest.sourceRoot, relative);
  fs.appendFileSync(protocol, "\n// R.string.test_untranslated_upstream_key\n");
  run(changed, ["tools/import-upstream.cjs", source], 1, /Source differs from pinned/);
  assert.deepEqual(outputHashes(changed), originalHashes);
  cases++;
  // Simulate a deliberate repin: even valid new source hashes cannot bypass translation coverage.
  manifest.files[relative] = hash(fs.readFileSync(protocol));
  fs.writeFileSync(path.join(changed, "tools/upstream-manifest.json"), JSON.stringify(manifest));
  fs.copyFileSync(protocol, path.join(changed, "src/main/java/com/yunx/app", relative));
  run(changed, ["tools/import-upstream.cjs", source], 1, /Missing local Chinese source.*test_untranslated_upstream_key/);
  assert.deepEqual(outputHashes(changed), originalHashes);
  cases++;
  console.log("Pinned import fixtures: " + temp);
}
console.log(cases + " checks passed; " + resources.source.size + " upstream + " + Object.keys(resources.host).length + " host keys per locale");
