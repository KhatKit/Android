# YunX parsing integration

Pinned upstream: CYQawa/YunX `2381a52960cf6e49c998e79630df9cd97007130d`.
License: AGPL-3.0-or-later; upstream file headers and LICENSE retained.

`tools/import-upstream.cjs` records and reproduces the imported protocol files.
The custom `AppStrings`, `DiagnosticLog`, and `HttpClients` adapters avoid the
upstream application, account database, global downloader, and diagnostic logs.
The upstream namespace is retained solely to preserve resource references.

`YunxParserManager` exposes detect/open/list/resolve/close. Each card invocation
gets a separate manager, max 8 sessions, 30-minute expiry, 20 results per page.
Credentials come from the card's encrypted host store; no credential is returned
in file listings or logged. Upstream temporary-transfer paths require an explicit
allow_transfer flag. Retained temporary directories are explained in the card.

Ten cloud providers and all seven upstream video/image parsers are wired.
GitHub exposes public source archives and release assets. It is not a complete
GitHub file-tree browser. Imported upload API methods are not exposed by the bridge.

HLS and DASH helpers are called by KhatKit's download queue through the optional
MediaDownloadHandler interface. Task state, pause/cancel, notifications and
persistence remain in KhatKit. HLS restarts its segments on resume; DASH restarts
both tracks. Standard files retain normal Range resume.

Tests: `./gradlew :yunx-parser:testDebugUnitTest`; device `YunxDeviceTest`.
Card tests live in KhatKitCards/moonbit/scripts/test-yunx-v3.cjs.


## Locale resources and repeatable imports

The upstream protocol remains pinned to the revision above. The explicit
[import manifest](tools/upstream-manifest.json) records 77 protocol files and their
SHA-256 hashes, taken from the official archive for that commit. The namespace,
license, resource names, provider IDs and wire fields remain unchanged. The importer
verifies the retained license rather than replacing it. First-party adapters and
`YunxParserManager` are outside the protocol allowlist.

The tracked localization inputs are separate from generated Android resources:

- [upstream-zh.xml](tools/localization/upstream-zh.xml): the original 442 Chinese
  resource definitions, preserved without translating or renaming their keys.
- [en.json](tools/localization/en.json): 442 complete English translations, written
  directly from those definitions; there is no machine-translation API dependency.
- [host.json](tools/localization/host.json): 24 first-party host messages as
  `[English, Simplified Chinese]` pairs. These cover errors, share titles, download
  notices and the GitHub source-code label; protocol values are not translated.

Generate `src/main/res/values{,-zh}/yunx{,_host}_strings.xml` with:

```sh
node yunx-parser/tools/generate-locales.cjs
node yunx-parser/tools/generate-locales.cjs --check
python3 yunx-parser/tools/check-resource-xml.py
node yunx-parser/tools/verify-locales.cjs
```

Run these commands from the repository root. English is the default locale;
Simplified Chinese uses `values-zh`. Edit the inputs, then regenerate both locales.
Do not edit generated XML directly. The generator checks exact key coverage,
format arguments (including repeated/indexed arguments) and rich-text markup;
it also escapes XML/Android quotes and preserves leading/trailing spaces.

To reproduce an import, provide an extracted archive or checkout containing the
pinned files. No network operation is performed by the importer:

```sh
node yunx-parser/tools/import-upstream.cjs /path/to/pinned/YunX --check
node yunx-parser/tools/import-upstream.cjs /path/to/pinned/YunX
node yunx-parser/tools/verify-locales.cjs /path/to/pinned/YunX
```

All file hashes, the license, resource references and both locale maps are checked
before any write. Existing local protocol changes cause an explicit failure;
they are never overwritten. Reimporting regenerates locales from the local maps,
so upstream Chinese cannot replace the English default. An unknown upstream key,
missing translation, changed Chinese definition or mismatched parameter also
fails before copying protocol files or writing resource output. A deliberate
upstream update requires reviewing the revision/allowlist/hashes and updating the
Chinese source and English map together; changing a hash cannot bypass coverage.

Static verification on 2026-11-28: 442 original + 24 host keys in each locale;
original Chinese preserved; 466 referenced keys resolve; XML/format/markup checks
pass. The pinned-import suite passed 12 checks, including two identical reimports,
missing translation, format mismatch, generated-output drift, local WIP protection,
wrong source hash and a newly referenced key after a simulated repin. The suite
uses temporary copies and never modifies the supplied upstream tree. No Gradle,
APK, device operation or external translation service is part of these checks.
