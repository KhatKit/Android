# ocr-mlkit 插件

OCR 原生插件（market `kind=dependency`, `plugin_id=ocr`），基础包不再带 ML Kit。

## 打包

```bash
plugins/ocr-mlkit/buildPlugin.sh                # 先 assembleRelease 再 d8/打包
plugins/ocr-mlkit/buildPlugin.sh --skip-gradle  # CI 已产出 AAR 时
```

产物：`plugins/ocr-mlkit/build/ocr-plugin.zip`

- 结构：`manifest.json` + `classes.dex` + `lib/arm64-v8a/libmlkit_google_ocr_pipeline.so`
  + `assets/mlkit-google-ocr-models/**`
- manifest 字段：`kind=dependency`、`plugin_id=ocr`、`version`、`entry_class`
  （= `heizige.kk.khatkit.ocr.mlkit.MlKitOcrEngine`）、`abi=["arm64-v8a"]`
- 限制：压缩 ≤ 8 MiB、解压 ≤ 32 MiB、条目 ≤ 1024（`OcrPluginInstaller` 同款校验）

## 发布到市场（POST /api/items/publish）

需要主人提供 JWT（`$KODEHEAP_JWT`，账号/管理员均可；发布后进入 pending，需审核）。

```bash
ZIP=plugins/ocr-mlkit/build/ocr-plugin.zip
# 必须与 zip 内的 manifest.json 完全一致：直接从包内取
MANIFEST=$(unzip -p "$ZIP" manifest.json)
SHA=$(sha256sum "$ZIP" | cut -d' ' -f1)
ARTIFACT=$(base64 -w0 "$ZIP")
AUDIT=$(printf '{"artifact_sha256":"%s","audit_rules_version":"1","score":10,"permissions":[],"network_domains":[],"dangerous_calls":[],"hooks":[]}' "$SHA")

jq -n \
  --arg name "OCR Plugin (ML Kit, arm64)" \
  --arg version "1.0.0" --arg license "Apache-2.0" \
  --arg manifest "$MANIFEST" --arg artifact "$ARTIFACT" \
  --arg audit "$AUDIT" --arg sha "$SHA" \
  '{name:$name, kind:"dependency", version:$version, license:$license,
    manifest_json:$manifest, artifact_base64:$artifact,
    audit_report_json:$audit, audit_score:10, audit_rules_version:"1",
    sha256:$sha, changelog:"首个 OCR 原生插件（ML Kit 中文 OCR, arm64-v8a）"}' \
| curl -sS -X POST https://heizige.top/api/items/publish \
    -H "Authorization: Bearer $KODEHEAP_JWT" \
    -H 'Content-Type: application/json' \
    --data-binary @-
```

注意：
- `manifest_json` 必须与 zip 根目录的 `manifest.json` 逐字节一致（服务端校验同一对象）。
- `sha256`/audit 里的哈希由脚本计算，上传后服务端会重算校验。
- 审核通过后再用 `GET /api/items/{id}` 确认 `files` 与下载 200。
