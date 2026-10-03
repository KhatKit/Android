function decoded(value) {
  const result = typeof value === "string" ? JSON.parse(value) : value;
  if (!result || result.error || result.__error) {
    throw new Error(result && (result.error || result.__error) || "Empty browser response");
  }
  if (result.snapshot && result.snapshot.error) throw new Error(result.snapshot.error);
  return result;
}
if (!Array.isArray(args.urls) || args.urls.length < 2 || args.urls.length > 3 ||
    args.urls.some(url => typeof url !== "string" || !url.trim())) throw new Error("Provide two or three URLs");
if (typeof args.price_selector !== "string" || !args.price_selector.trim()) throw new Error("Missing price_selector");
if (typeof args.currency !== "string" || !args.currency.trim()) throw new Error("Missing currency");
const tabs = [];
try {
  for (const url of args.urls) {
    const opened = decoded(browser.open(url));
    if (typeof opened.id !== "string" || !opened.id) throw new Error("Missing browser session");
    tabs.push({id: opened.id, url});
  }
  const offers = tabs.map(tab => {
    const page = decoded(browser.act(tab.id, "wait", {selector: args.price_selector, timeout_ms: 10000}));
    const selected = decoded(browser.act(tab.id, "read_element", {selector: args.price_selector}));
    const raw = selected.text.trim();
    if (!raw.startsWith(args.currency + " ")) throw new Error("Currency mismatch at " + tab.url);
    const amount = raw.slice(args.currency.length + 1).trim();
    if (!/^(0|[1-9][0-9]*)(\.[0-9]{1,2})?$/.test(amount)) throw new Error("Ambiguous price at " + tab.url);
    const price = Number(amount);
    if (!Number.isFinite(price) || price <= 0) throw new Error("Invalid price at " + tab.url);
    return {url: page.url || tab.url, raw_text: raw, currency: args.currency, price};
  }).sort((a, b) => a.price - b.price);
  return {offers, cheapest: offers[0], includes_shipping: false};
} finally {
  for (const tab of tabs) {
    try { browser.close(tab.id); } catch (_) { /* Executor also releases all run-owned tabs. */ }
  }
}
