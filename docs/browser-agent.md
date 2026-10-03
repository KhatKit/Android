# Browser Agent card runtime

The browser is a host capability for KhatKit cards, not a settings page or a
built-in `ChatToolFactory` tool. AI sees an installed card as `khatkit__<card-name>`;
the card receives a run-scoped `browser` bridge. Each tab owns a WebView
and a coroutine mutex; operations on one tab queue while different tabs may load
independently. WebView calls run on the main dispatcher. A failed initial load
destroys its tab; loading/evaluation times out after 30 seconds by default.

Use the ID returned by `browser.open(url)` for `browser.snapshot`, `browser.act`
and `browser.close`. A card can hold several IDs concurrently. The executor owns
those IDs and destroys every remaining tab in `finally`, so another card run
cannot take over an old tab.

History and bookmarks use a dedicated Room database. History keeps the latest 200
unique URLs. Legacy JSON is imported once in a transaction and preserved on disk;
malformed/oversized sources are marked failed without blocking new writes.
Clearing history never reimports the old file. Bookmarks preserve their original
creation time when renamed. Snapshots record URL changes observed after page navigation.

Viewport screenshots are PNG images in app-private storage, returned through the
same image message type as device screenshots. Capture retains at most 40 files;
files older than 24 hours are pruned on the next capture. Detached WebView drawing
passed a nonblank bitmap check on PKG110 / API 36 with local HTML;
screenshots are not full-page images.

The run-scoped `browser` bridge is documented in
[script-api-reference.md](script-api-reference.md#21-browser可编程浏览器).
The executor releases its tabs in `finally`. The card bridge checks approvals,
deadlines, owned session IDs and declared navigation domains.

All page reads and interactions require approval unless the card manifest grants
that exact bridge method.
HTTP, HTTPS and `about:blank` are accepted. Local file/content access is disabled.
WebView uses its default shared cookie store; isolated cookies are not implemented.

The card manifest's immutable `network.allow` checks direct navigation, WebView navigation callbacks,
intercepted resource requests, downloads and page reads/actions. This is not a
complete network sandbox: WebSockets, service workers and subresource redirect
chains are not fully covered by WebView interception.

`browser_snapshot` reads live DOM every time. Elements include tag, text, role,
id/name/type, interactive flag, CSS pixel bbox and a reference such as `@b<document-id>:12`.
Pass either that reference or a CSS selector as `selector` for actions. References
remain stable for retained elements in the same document; detached or evicted
references fail explicitly. Navigation creates a new document, so get a fresh
snapshot after navigating.

The result includes `document_id`, `snapshot_id` and `mode`. Pass `snapshot_id` as
`since` with the same selector/offset to request changes. `mode: delta` contains
`base_snapshot_id`, `added`, `changed`, `removed` and `order`: remove those refs,
upsert added/changed elements, then order by `order`. This applies to one returned
page; removed refs may have moved outside that page. Retain pagination metadata.
`mode: full` replaces the page and is used when it is smaller, the baseline was
evicted, the scope changed or the document navigated. Only four baselines are kept.
Reference IDs include a per-document token, so old references cannot accidentally
address a similarly positioned element after navigation.

Use `browser_wait` for delayed page changes instead of repeatedly opening the page.
Its timeout includes queued time and is capped at 30 seconds. Conditions are CSS/ref
visibility and/or literal text; arbitrary page JavaScript is not exposed.

The snapshot object is bounded near 16KB UTF-8, leaving room for the enclosing
response under ordinary URL/title sizes. For `truncated: true`, request the
reported `next_offset`. Offsets index candidate elements and may advance past
hidden nodes. A snapshot scans at most 3000 candidates after its offset and keeps
at most 3000 element references. The optional root selector supports narrowing
to a particular section. DOM mutations can change pagination indices.

This runtime is still an A1 implementation in progress:

- Synthetic DOM events are not trusted hardware input and do not satisfy all
  browser user-activation checks. Hover dispatch does not emulate CSS `:hover`.
- Detached WebViews use a fixed viewport and may behave differently for
  animation, popups, downloads, permissions, third-party authentication and
  pages requiring an attached view.
- There is intentionally no browser settings UI or direct chat browser tool.
  Authenticated downloads and isolated cookie stores remain outstanding.
  [KhatKitCards](../../../KhatKitCards) contains the AI-facing check-in and price
  comparison cards. Real-site
  acceptance remains outstanding. Six [device tests](browser-device-validation.md)
  now pass on PKG110 / API 36, covering local HTML and Room, not real websites.
- Unit tests execute the generated scripts in QuickJS with DOM fixtures; they
  do not prove real WebView login, rendering or three-tab device concurrency.

Run focused verification:

```sh
./gradlew :app:testDebugUnitTest --tests 'heizige.kk.khatkit.app.core.data.browser.*'
```
