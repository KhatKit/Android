package heizige.kk.khatkit.app.core.data.browser

import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BrowserScriptsTest {
    @Test
    fun `readiness requires visible target and matching current text`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>("""
                globalThis.found=false;globalThis.visible=true;globalThis.label="Loading";
                globalThis.getComputedStyle=()=>({visibility:visible?"visible":"hidden"});
                globalThis.target={get innerText(){return label},getClientRects:()=>[{}]};
                globalThis.document={body:target,querySelector:()=>found?target:null};
            """)
            assertEquals("""{"ready":false}""", evaluate<String>(BrowserScripts.ready("#result", "Done")))
            evaluate<Any?>("found=true;label='Done'")
            assertEquals("""{"ready":true}""", evaluate<String>(BrowserScripts.ready("#result", "Done")))
            evaluate<Any?>("visible=false")
            assertEquals("""{"ready":false}""", evaluate<String>(BrowserScripts.ready("#result", "Done")))
        }
    }

    private val stableFixture = """
        globalThis.clicked=-1;
        globalThis.TextEncoder=class {encode(s){return {length:unescape(encodeURIComponent(s)).length}}};
        globalThis.getComputedStyle=()=>({visibility:"visible"});
        globalThis.makeElement=i=>({
            tagName:"BUTTON",innerText:"item "+i,id:"item-"+i,isConnected:true,
            getClientRects:()=>[{}],getAttribute:()=>null,matches:()=>true,
            getBoundingClientRect:()=>({x:0,y:i*40,width:100,height:40}),
            click:()=>{clicked=i}
        });
        globalThis.elements=Array.from({length:12},(_,i)=>makeElement(i));
        globalThis.document={querySelectorAll:()=>elements};
    """

    @Test
    fun `navigation invalidates old references even when the new document has identical elements`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>(stableFixture)
            val before = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot)).jsonObject
            val oldRef = before.getValue("elements").jsonArray[0].jsonObject.getValue("ref").jsonPrimitive.content
            evaluate<Any?>("document={querySelectorAll:()=>elements}")
            val after = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot)).jsonObject
            assertNotEquals(before["document_id"], after["document_id"])
            assertTrue(evaluate<String>(BrowserScripts.click(oldRef)).contains("Stale element reference"))
            assertEquals(-1, evaluate<Int>("clicked"))
            val newRef = after.getValue("elements").jsonArray[0].jsonObject.getValue("ref").jsonPrimitive.content
            assertEquals("""{"clicked":true}""", evaluate<String>(BrowserScripts.click(newRef)))
        }
    }

    @Test
    fun `delta reconstructs additions edits removals and reordered elements`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>(stableFixture)
            val before = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot)).jsonObject
            val baseline = before.getValue("snapshot_id").jsonPrimitive.content
            evaluate<Any?>("""
                elements[0].innerText="Updated 😀";
                elements[1].isConnected=false;
                elements.splice(1,1);
                elements.push(makeElement(99));
                elements.reverse();
            """)
            val deltaText = evaluate<String>(BrowserScripts.snapshot(null, 0, baseline))
            val delta = Json.parseToJsonElement(deltaText).jsonObject
            assertEquals("delta", delta["mode"]?.jsonPrimitive?.content)
            assertEquals(1, delta.getValue("added").jsonArray.size)
            assertEquals(1, delta.getValue("changed").jsonArray.size)
            assertEquals(1, delta.getValue("removed").jsonArray.size)
            val reconstructed = before.getValue("elements").jsonArray.associateBy {
                it.jsonObject.getValue("ref").jsonPrimitive.content
            }.toMutableMap()
            delta.getValue("removed").jsonArray.forEach { reconstructed.remove(it.jsonPrimitive.content) }
            (delta.getValue("added").jsonArray + delta.getValue("changed").jsonArray).forEach {
                reconstructed[it.jsonObject.getValue("ref").jsonPrimitive.content] = it
            }
            val ordered = delta.getValue("order").jsonArray.map { reconstructed.getValue(it.jsonPrimitive.content) }
            val full = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot)).jsonObject
            assertEquals(full.getValue("elements").jsonArray.toList(), ordered)
            assertTrue(deltaText.toByteArray().size < full.toString().toByteArray().size)
        }
    }

    @Test
    fun `unknown evicted and differently scoped baselines fall back to full snapshots`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>(stableFixture)
            val first = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot)).jsonObject
            val baseline = first.getValue("snapshot_id").jsonPrimitive.content
            val differentPage = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot(null, 3, baseline))).jsonObject
            assertEquals("full", differentPage["mode"]?.jsonPrimitive?.content)
            assertEquals("scope_changed", differentPage["reset_reason"]?.jsonPrimitive?.content)
            repeat(4) { evaluate<String>(BrowserScripts.snapshot) }
            val evicted = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot(null, 0, baseline))).jsonObject
            assertEquals("unknown_baseline", evicted["reset_reason"]?.jsonPrimitive?.content)
            assertEquals(4, evaluate<Int>("document[Symbol.for('khatkit.browser.references')].snapshots.size"))
        }
    }

    @Test
    fun `selectors and typed text remain data and dispatch input events`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>("""
                globalThis.events=[];
                globalThis.target={value:"",focus(){},dispatchEvent(e){events.push(e.type)}};
                globalThis.document={querySelector(s){globalThis.selector=s;return target}};
                globalThis.Event=class {constructor(type){this.type=type}};
            """)
            val selector = """input[name="x');globalThis.injected=true;//"]"""
            val text = "中文😀\n\";globalThis.injected=true;//"
            assertEquals("""{"typed":true}""", evaluate<String>(BrowserScripts.type(selector, text)))
            assertEquals(text, evaluate<String>("target.value"))
            assertEquals(selector, evaluate<String>("selector"))
            assertEquals("undefined", evaluate<String>("typeof injected"))
            assertEquals("""["input","change"]""", evaluate<String>("JSON.stringify(events)"))
        }
    }

    @Test
    fun `missing and malformed selectors return errors without clicking`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>("globalThis.document={querySelector(){return null}}")
            assertTrue(Json.parseToJsonElement(evaluate<String>(BrowserScripts.click("#missing"))).jsonObject.containsKey("error"))
            evaluate<Any?>("document.querySelector=()=>{throw new Error('invalid selector')}")
            assertEquals("invalid selector",
                Json.parseToJsonElement(evaluate<String>(BrowserScripts.click("["))).jsonObject["error"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `snapshot is live and bounded below twenty kilobytes for large unicode pages`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>("""
                globalThis.label="商品😀".repeat(60);
                globalThis.TextEncoder=class {encode(s){return {length:unescape(encodeURIComponent(s)).length}}};
                globalThis.getComputedStyle=()=>({visibility:"visible"});
                globalThis.document={querySelectorAll:()=>Array.from({length:500},(_,i)=>({
                    tagName:"BUTTON",innerText:label,id:"buy-"+i,
                    getClientRects:()=>[{}],getAttribute:()=>null,matches:()=>true,
                    getBoundingClientRect:()=>({x:0,y:i*40,width:100,height:40})
                }))};
            """)
            val before = evaluate<String>(BrowserScripts.snapshot)
            assertTrue(before.toByteArray(Charsets.UTF_8).size < 20_000)
            val first = Json.parseToJsonElement(before).jsonObject
            assertTrue(first.getValue("truncated").jsonPrimitive.boolean)
            assertTrue(first.getValue("elements").jsonArray.isNotEmpty())
            evaluate<Any?>("label='Changed after click'")
            val after = evaluate<String>(BrowserScripts.snapshot)
            assertTrue(after.contains("Changed after click"))
            assertFalse(after.contains("商品"))
        }
    }

    @Test
    fun `scroll hover and Enter dispatch expected actions and respect cancellation`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>("""
                globalThis.events=[];globalThis.submits=0;globalThis.allow=true;
                globalThis.window=globalThis;globalThis.scrollX=0;globalThis.scrollY=0;
                globalThis.scrollBy=(x,y)=>{scrollX+=x;scrollY+=y};
                globalThis.MouseEvent=globalThis.KeyboardEvent=class {
                    constructor(type,options){this.type=type;this.key=options?.key}
                };
                globalThis.document={querySelector:()=>({
                    tagName:"INPUT",focus(){},form:{requestSubmit(){submits++}},
                    dispatchEvent(e){events.push(e.type);return allow}
                })};
            """)
            assertEquals("""{"x":4,"y":-250}""", evaluate<String>(BrowserScripts.scroll(4, -250)))
            assertEquals("""{"hovered":true}""", evaluate<String>(BrowserScripts.hover("#target")))
            evaluate<String>(BrowserScripts.pressKey("#target", "Enter"))
            assertEquals(1, evaluate<Int>("submits"))
            evaluate<Any?>("allow=false")
            evaluate<String>(BrowserScripts.pressKey("#target", "Enter"))
            assertEquals(1, evaluate<Int>("submits"))
            assertEquals("""["mouseover","mouseenter","keydown","keyup","keydown","keyup"]""",
                evaluate<String>("JSON.stringify(events)"))
        }
    }

    @Test
    fun `snapshot pagination has stable actionable references and rejects detached elements`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>("""
                globalThis.clicked=-1;
                globalThis.TextEncoder=class {encode(s){return {length:unescape(encodeURIComponent(s)).length}}};
                globalThis.getComputedStyle=()=>({visibility:"visible"});
                globalThis.elements=Array.from({length:150},(_,i)=>({
                    tagName:"BUTTON",innerText:"button "+i,id:"",isConnected:true,
                    getClientRects:()=>[{}],getAttribute:()=>null,matches:()=>true,
                    getBoundingClientRect:()=>({x:0,y:i*40,width:100,height:40}),
                    click:()=>{clicked=i}
                }));
                globalThis.document={querySelectorAll:()=>elements};
            """)
            val first = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot)).jsonObject
            val ref = first["elements"]!!.jsonArray.first().jsonObject["ref"]!!.jsonPrimitive.content
            val next = first["next_offset"]!!.jsonPrimitive.int
            assertTrue(next > 0)
            val second = Json.parseToJsonElement(evaluate<String>(BrowserScripts.snapshot(null, next))).jsonObject
            assertEquals("button $next", second["elements"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content)
            assertEquals("""{"clicked":true}""", evaluate<String>(BrowserScripts.click(ref)))
            assertEquals(0, evaluate<Int>("clicked"))
            evaluate<Any?>("elements[0].isConnected=false")
            assertTrue(evaluate<String>(BrowserScripts.click(ref)).contains("Stale element reference"))
        }
    }
}
