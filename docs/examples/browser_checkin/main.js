function decoded(value) {
  const result = typeof value === "string" ? JSON.parse(value) : value;
  if (!result || result.error || result.__error) {
    throw new Error(result && (result.error || result.__error) || "Empty browser response");
  }
  if (result.snapshot && result.snapshot.error) throw new Error(result.snapshot.error);
  return result;
}
for (const key of ["url", "button_selector", "result_selector", "success_text"]) {
  if (typeof args[key] !== "string" || !args[key].trim()) throw new Error("Missing " + key);
}
let id;
try {
  const opened = decoded(browser.open(args.url));
  id = opened.id;
  if (typeof id !== "string" || !id) throw new Error("Missing browser session");
  decoded(browser.act(id, "wait", {selector: args.button_selector, timeout_ms: 10000}));
  decoded(browser.act(id, "click", {selector: args.button_selector}));
  const verified = decoded(browser.act(id, "wait", {
    selector: args.result_selector, text: args.success_text, timeout_ms: 10000
  }));
  return {success: true, url: verified.url, verified_text: args.success_text};
} finally {
  if (id) browser.close(id);
}
