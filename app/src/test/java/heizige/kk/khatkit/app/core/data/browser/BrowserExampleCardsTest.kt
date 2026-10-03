package heizige.kk.khatkit.app.core.data.browser

import com.dokar.quickjs.quickJs
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.CardValidator
import heizige.kk.khatkit.card.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BrowserExampleCardsTest {
    private fun directory(name: String) = File("../../../KhatKitCards/$name")
    private fun source(name: String) = directory(name).resolve("main.js").readText()

    private val host = """
        globalThis.opened=[];globalThis.closed=[];globalThis.calls=[];globalThis.failWait=false;
        globalThis.prices=["CNY 30.00","CNY 9.90","CNY 18.50"];
        globalThis.browser={
          open(url){const id="tab-"+opened.length;opened.push(url);return JSON.stringify({id,url})},
          act(id,action,params){
            calls.push({id,action,params});
            if(action==="wait" && failWait) return {__error:"Timeout"};
            if(action==="read_element") return JSON.stringify({text:prices[Number(id.slice(4))]});
            return JSON.stringify({url:opened[Number(id.slice(4))],snapshot:{elements:[]}});
          },
          close(id){closed.push(id);return "Closed"}
        };
    """

    @Test
    fun `example manifests and source declarations pass validator`() {
        for (name in listOf("browser_checkin", "browser_compare_prices")) {
            val manifest = Json.decodeFromString<CardManifest>(directory(name).resolve("card.json").readText())
            val errors = CardValidator.validate(manifest, mapOf("main.js" to source(name)))
                .filter { it.severity == Severity.ERROR }
            assertTrue(errors.toString(), errors.isEmpty())
        }
    }

    @Test
    fun `checkin verifies result and releases tab on success and failure`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>(host)
            evaluate<Any?>("""globalThis.args={url:"https://browser-fixture.test/checkin",
                button_selector:"#checkin",result_selector:"#result",success_text:"签到成功"}""")
            val script = source("browser_checkin")
            val result = Json.parseToJsonElement(evaluate<String>("JSON.stringify((()=>{$script})())")).jsonObject
            assertTrue(result.getValue("success").jsonPrimitive.boolean)
            assertEquals("""["wait","click","wait"]""", evaluate<String>("JSON.stringify(calls.map(c=>c.action))"))
            assertEquals("签到成功", evaluate<String>("calls[2].params.text"))
            assertEquals("""["tab-0"]""", evaluate<String>("JSON.stringify(closed)"))
            evaluate<Any?>("failWait=true")
            assertEquals("Timeout", evaluate<String>("(()=>{try{(()=>{$script})();return 'unexpected success'}catch(e){return e.message}})()"))
            assertEquals("""["tab-0","tab-1"]""", evaluate<String>("JSON.stringify(closed)"))
        }
    }

    @Test
    fun `price comparison preserves sources sorts prices and closes every tab after failure`() = runBlocking {
        quickJs(Dispatchers.Default) {
            evaluate<Any?>(host)
            evaluate<Any?>("""globalThis.args={urls:["https://browser-fixture.test/a",
                "https://browser-fixture.test/b","https://browser-fixture.test/c"],price_selector:"#price",currency:"CNY"}""")
            val script = source("browser_compare_prices")
            val result = Json.parseToJsonElement(evaluate<String>("JSON.stringify((()=>{$script})())")).jsonObject
            assertEquals("https://browser-fixture.test/b", result.getValue("cheapest").jsonObject["url"]?.jsonPrimitive?.content)
            assertEquals("CNY 9.90", result.getValue("cheapest").jsonObject["raw_text"]?.jsonPrimitive?.content)
            assertEquals(3, evaluate<Int>("closed.length"))
            evaluate<Any?>("opened=[];closed=[];prices[1]='USD 1.00'")
            assertTrue(evaluate<String>("(()=>{try{(()=>{$script})();return 'unexpected success'}catch(e){return e.message}})()").contains("Currency mismatch"))
            assertEquals(3, evaluate<Int>("closed.length"))
        }
    }
}
