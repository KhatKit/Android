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
