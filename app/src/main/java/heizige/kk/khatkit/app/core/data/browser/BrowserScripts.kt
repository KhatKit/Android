package heizige.kk.khatkit.app.core.data.browser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Arguments are JSON values; selectors/text are never interpolated as executable source. */
internal object BrowserScripts {
    private fun call(body: String, vararg arguments: String): String =
        """(() => { try {
            const key=Symbol.for("khatkit.browser.references");
            const refs=document[key] || (document[key]={
              documentId:globalThis.crypto?.randomUUID?.() || Date.now().toString(36)+Math.random().toString(36).slice(2),
              ids:new WeakMap(),elements:new Map(),next:1,snapshots:new Map(),revision:0
            });
            const resolve=selector=>{
              if(selector.startsWith("@b")) {
                const e=refs.elements.get(selector);
                if(!e || !e.isConnected) throw Error("Stale element reference; take another snapshot");
                return e;
              }
              return document.querySelector(selector);
            };
            return JSON.stringify(($body)(...${JsonArray(arguments.map(::JsonPrimitive))})); }
            catch(e) { return JSON.stringify({error:String(e.message || e)}); } })()"""

    fun click(selector: String) = call("""selector => {
        const e = resolve(selector);
        if (!e) throw Error("No matching element");
        if (e.disabled) throw Error("Element is disabled");
        e.click(); return {clicked:true};
    }""", selector)

    fun type(selector: String, text: String) = call("""(selector, value) => {
        const e = resolve(selector);
        if (!e) throw Error("No matching element");
        if (e.disabled || e.readOnly) throw Error("Element is not editable");
        e.focus();
        if (e.isContentEditable) e.textContent = value;
        else {
          if (!("value" in e) || e.type === "file") throw Error("Element is not a text input");
          let prototype = Object.getPrototypeOf(e), setter;
          while (prototype && !setter) {
            setter = Object.getOwnPropertyDescriptor(prototype, "value")?.set;
            prototype = Object.getPrototypeOf(prototype);
          }
          if (setter) setter.call(e,value); else e.value=value;
        }
        e.dispatchEvent(new Event("input",{bubbles:true}));
        e.dispatchEvent(new Event("change",{bubbles:true}));
        return {typed:true};
    }""", selector, text)

    fun scroll(x: Int, y: Int) = call("""(x,y) => {
        window.scrollBy(Number(x),Number(y)); return {x:scrollX,y:scrollY};
    }""", x.toString(), y.toString())

    fun hover(selector: String) = call("""selector => {
        const e=resolve(selector); if(!e) throw Error("No matching element");
        e.dispatchEvent(new MouseEvent("mouseover",{bubbles:true}));
        e.dispatchEvent(new MouseEvent("mouseenter")); return {hovered:true};
    }""", selector)

    fun pressKey(selector: String, key: String) = call("""(selector,key) => {
        const e=resolve(selector); if(!e) throw Error("No matching element");
        e.focus();
        const allowed=e.dispatchEvent(new KeyboardEvent("keydown",{key,bubbles:true,cancelable:true}));
        if(allowed && key==="Enter" && e.tagName==="INPUT" && e.form) e.form.requestSubmit();
        e.dispatchEvent(new KeyboardEvent("keyup",{key,bubbles:true}));
        return {dispatched:true,trusted:false};
    }""", selector, key)

    val links = call("""() => Array.from(document.querySelectorAll("a[href]"))
        .filter(a=>a.getClientRects().length>0).slice(0,100)
        .map(a=>({text:(a.innerText||"").slice(0,120),url:a.href.slice(0,1024)}))""")

    val text = call("""() => ({text:(document.body?.innerText||"").slice(0,12000)})""")
    fun text(selector: String) = call("""selector => {
        const e=resolve(selector); if(!e) throw Error("No matching element");
        return {text:(e.innerText||"").slice(0,12000)};
    }""", selector)

    fun ready(selector: String?, text: String?) = call("""(selector,text) => {
        const e=selector ? resolve(selector) : document.body;
        const visible=!!e && (!selector || (e.getClientRects().length>0 && getComputedStyle(e).visibility!=="hidden"));
        return {ready:visible && (!text || (e.innerText||"").includes(text))};
    }""", selector.orEmpty(), text.orEmpty())

    // Bound bytes on serialized UTF-8, not UTF-16 chars.
    val snapshot get() = snapshot(null, 0)

    fun snapshot(selector: String?, offset: Int, since: String? = null): String {
        require(offset in 0..100_000) { "offset must be between 0 and 100000" }
        return call("""(selector,offsetText,since) => {
        const offset=Number(offsetText);
        const scope=JSON.stringify([selector,offset]);
        const baseline=since ? refs.snapshots.get(since) : null;
        const snapshotId=refs.documentId+"-"+(++refs.revision);
        const result={mode:"full",document_id:refs.documentId,snapshot_id:snapshotId,
          selector:selector||null,elements:[],truncated:false,offset,next_offset:null};
        if(since && !baseline) result.reset_reason="unknown_baseline";
        else if(baseline && baseline.scope!==scope) result.reset_reason="scope_changed";
        const bytes=s=>new TextEncoder().encode(s).length;
        const root=selector ? resolve(selector) : document;
        if(!root) throw Error("No matching snapshot root");
        for(const [ref,e] of refs.elements) { if(!e.isConnected) refs.elements.delete(ref); }
        let seen=0,index=0;
        for(const e of root.querySelectorAll("a,button,input,textarea,select,[role],h1,h2,h3,p,li,label")) {
          if(index++<offset) continue;
          if(++seen>3000) { result.truncated=true;result.next_offset=index-1;break; }
          if(!e.getClientRects().length || getComputedStyle(e).visibility==="hidden") continue;
          const r=e.getBoundingClientRect();
          let ref=refs.ids.get(e);
          if(!ref) {ref="@b"+refs.documentId+":"+(refs.next++);refs.ids.set(e,ref);}
          refs.elements.set(ref,e);
          while(refs.elements.size>3000) refs.elements.delete(refs.elements.keys().next().value);
          const item={ref,tag:e.tagName.toLowerCase(),text:(e.innerText||e.getAttribute("aria-label")||e.getAttribute("placeholder")||"").slice(0,160),
            role:(e.getAttribute("role")||"").slice(0,80),id:(e.id||"").slice(0,160),name:(e.getAttribute("name")||"").slice(0,160),
            type:(e.getAttribute("type")||"").slice(0,80),
            bbox:[Math.round(r.x),Math.round(r.y),Math.round(r.width),Math.round(r.height)],
            interactive:e.matches("a,button,input,textarea,select,[contenteditable=true],[role=button]")};
          result.elements.push(item);
          if(bytes(JSON.stringify(result))>16000) {
            result.elements.pop();result.truncated=true;result.next_offset=index-1;break;
          }
        }
        refs.snapshots.set(snapshotId,{scope,result});
        while(refs.snapshots.size>4) refs.snapshots.delete(refs.snapshots.keys().next().value);
        if(baseline && baseline.scope===scope) {
          const previous=new Map(baseline.result.elements.map(e=>[e.ref,e]));
          const current=new Map(result.elements.map(e=>[e.ref,e]));
          const delta={mode:"delta",document_id:refs.documentId,snapshot_id:snapshotId,
            base_snapshot_id:since,selector:result.selector,offset,next_offset:result.next_offset,
            truncated:result.truncated,added:[],changed:[],removed:[],order:result.elements.map(e=>e.ref)};
          for(const e of result.elements) {
            const before=previous.get(e.ref);
            if(!before) delta.added.push(e);
            else if(JSON.stringify(before)!==JSON.stringify(e)) delta.changed.push(e);
          }
          for(const ref of previous.keys()) { if(!current.has(ref)) delta.removed.push(ref); }
          if(bytes(JSON.stringify(delta))<bytes(JSON.stringify(result))) return delta;
        }
        return result;
    }""", selector.orEmpty(), offset.toString(), since.orEmpty())
    }
}
