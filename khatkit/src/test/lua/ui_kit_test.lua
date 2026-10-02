local prelude = "khatkit/src/main/resources/ui_kit.lua"
local calls=0
ui={form=function(_,root) calls=calls+1; return root end}
dofile(prelude)
local Column, Text, TextField = ui.Column, ui.Text, ui.TextField
local function rejects(part, fn)
  local ok,err=pcall(fn)
  assert(not ok and tostring(err):find(part,1,true), tostring(err))
end
local chain=ui.Modifier.fillMaxWidth()
local root=Column{"hello", TextField{id="name",value=""}, modifier=chain.padding(4,8)}
assert(root.children[1].__ui=="Text" and root.children[1].props.text=="hello")
assert(#chain.__mod==1 and #root.props.modifier.__mod==2)
assert(ui.form("test",root)==root and calls==1)
rejects("Text.color",function() Text{color="#fff"} end)
rejects("Text.tone",function() Text{tone="danger"} end)
rejects("Image.src",function() ui.Image{src="https://example.com/image.png"} end)
rejects("NoSuch",function() ui.NoSuch{} end)
rejects("TextField.id",function() Column{TextField{id="x"},TextField{id="x"}} end)
rejects("Modifier.padding",function() ui.Modifier.padding(1,2,3) end)
rejects("Modifier.width",function() ui.Modifier.width("16dp") end)
rejects("Modifier.alpha",function() ui.Modifier.alpha(0.5) end)
rejects("Modifier.visible",function() ui.Modifier.visible(nil) end)
rejects("read-only",function() ui.form=function() end end)
rejects("sparse",function() Column{[2]=Text{text="x"}} end)
rejects("leaf",function() Text{Text{text="x"}} end)
local deep=Text{}
for i=1,31 do deep=Column{deep} end
rejects("depth",function() Column{deep} end)
local nodes={}
for i=1,511 do nodes[i]=Text{} end
Column(nodes)
nodes[512]=Text{}
rejects("count",function() Column(nodes) end)
root.props.color="red"
rejects("Column.color",function() ui.form("tampered",root) end)
assert(calls==1)
print("Lua DSL: constructors, bridge proxy, tampering, modifiers and limits passed")
