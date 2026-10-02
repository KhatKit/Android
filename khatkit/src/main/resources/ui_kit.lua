-- Restricted UI DSL. Constructors produce data only; the host validates again.
do
  local bridge, api = ui, {}
  local layouts = { Column=true, Row=true, Box=true, Card=true, Section=true }
  local fields = {
    TextField="string", NumberField="number", Switch="boolean", Checkbox="boolean",
    Slider="number", Select="string", RadioGroup="string", FilePicker="paths", DirPicker="string",
  }
  local schemas = {
    Column={spacing="number",hAlign="start|center|end",scrollable="boolean",modifier="modifier"},
    Row={spacing="number",vAlign="start|center|end|space_between",modifier="modifier"},
    Box={hAlign="start|center|end",vAlign="start|center|end",modifier="modifier"},
    Spacer={height="number",width="number"}, Divider={},
    Card={title="string",modifier="modifier"}, Section={title="string",modifier="modifier"},
    Text={text="string",style="title|subtitle|body|label|code",tone="default|muted|primary|error",bold="boolean",modifier="modifier"},
    Markdown={text="string",modifier="modifier"},
    Image={src="string",contentDescription="string",modifier="modifier"},
    Badge={text="string",tone="default|primary|success|warning|error"},
    ProgressBar={ratio="number",label="string"},
    TextField={placeholder="string",multiline="boolean",lines="number"},
    NumberField={min="number",max="number",step="number"},
    Switch={}, Checkbox={}, Slider={min="number",max="number",step="number"},
    Select={options="strings"}, RadioGroup={options="strings"},
    FilePicker={filter="image|video|audio|document|any",multiple="boolean"}, DirPicker={},
    Button={label="string",action="string",tone="primary|default|danger",disabled="boolean"},
  }
  for name, kind in pairs(fields) do
    local s = schemas[name]
    s.id, s.label, s.value, s.hint, s.required = "string","string",kind,"string","boolean"
  end
  local function fail(where, why) error("ui." .. where .. ": " .. why, 3) end
  local function number(v) return type(v)=="number" and v==v and math.abs(v)<math.huge end
  local function array(t, where)
    if type(t)~="table" then fail(where,"expected array") end
    local n=0
    for k in pairs(t) do
      if type(k)~="number" or k<1 or k%1~=0 then fail(where,"expected dense array") end
      n=n+1
    end
    for i=1,n do if t[i]==nil then fail(where,"sparse array") end end
    return n
  end
  local noargs={fillMaxSize=true,fillMaxWidth=true,fillMaxHeight=true,scrollable=true}
  local dimensions={width=true,height=true,size=true,weight=true}
  local function checkop(op)
    local name, args
    if type(op)=="string" then name,args=op,{}
    elseif type(op)=="table" then
      array(op,"Modifier")
      name,args=op[1],{}
      for i=2,#op do args[#args+1]=op[i] end
    else fail("Modifier","invalid operation") end
    local n=#args
    if noargs[name] then
      if n~=0 then fail("Modifier."..name,"expected no arguments") end
    elseif dimensions[name] or name=="padding" then
      if (name=="padding" and n~=1 and n~=2 and n~=4) or (name~="padding" and n~=1) then
        fail("Modifier."..name,"invalid argument count")
      end
      for _,v in ipairs(args) do
        if not number(v) or v<0 or (name=="weight" and v==0) then fail("Modifier."..name,"invalid dimension") end
      end
    elseif name=="visible" then
      if n~=1 or type(args[1])~="boolean" then fail("Modifier.visible","expected boolean") end
    elseif name=="clip" then
      if (n~=1 and n~=2) or (args[1]~="rounded" and args[1]~="circle")
        or (n==2 and (not number(args[2]) or args[2]<0)) then fail("Modifier.clip","invalid shape/radius") end
    else fail("Modifier."..tostring(name),"unknown operation") end
  end
  local function modifier(ops)
    return setmetatable({__mod=ops}, {__index=function(_, name)
      if not noargs[name] and not dimensions[name] and name~="padding" and name~="visible" and name~="clip" then
        fail("Modifier."..tostring(name),"unknown operation")
      end
      return function(...)
        local args=table.pack(...)
        local op={name}
        for i=1,args.n do
          if args[i]==nil then fail("Modifier."..name,"nil argument") end
          op[#op+1]=args[i]
        end
        checkop(op)
        local copy={}
        for i,v in ipairs(ops) do copy[i]=v end
        copy[#copy+1]=#op==1 and name or op
        return modifier(copy)
      end
    end, __metatable=false})
  end
  local function checkvalue(v, kind, where)
    if kind=="modifier" then
      if type(v)~="table" then fail(where,"expected Modifier") end
      for k in pairs(v) do if k~="__mod" then fail(where,"invalid Modifier property") end end
      array(v.__mod,where)
      if #v.__mod>64 then fail(where,"too many Modifier operations") end
      for _,op in ipairs(v.__mod) do checkop(op) end
    elseif kind=="strings" or (kind=="paths" and type(v)=="table") then
      array(v,where)
      for _,s in ipairs(v) do if type(s)~="string" then fail(where,"expected string array") end end
    elseif kind=="number" then
      if not number(v) then fail(where,"expected finite number") end
    elseif kind:find("|",1,true) then
      if type(v)~="string" or not ("|"..kind.."|"):find("|"..v.."|",1,true) then fail(where,"invalid token") end
    elseif type(v)~=(kind=="paths" and "string" or kind) then fail(where,"expected "..kind) end
  end
  local function validate(root)
    local ids, visiting, count = {},{},0
    local function visit(node, depth)
      if type(node)~="table" then fail("node","expected node") end
      local name=node.__ui
      if type(name)~="string" or not schemas[name] then fail(tostring(name),"unknown component") end
      if depth>32 then fail(name,"tree depth exceeds 32") end
      count=count+1
      if count>512 then fail(name,"tree node count exceeds 512") end
      if visiting[node] then fail(name,"cyclic tree") end
      visiting[node]=true
      for k in pairs(node) do
        if k~="__ui" and k~="props" and k~="children" then fail(name.."."..tostring(k),"unknown node property") end
      end
      if type(node.props)~="table" then fail(name..".props","expected table") end
      for k,v in pairs(node.props) do
        local kind=schemas[name][k]
        if not kind then fail(name.."."..tostring(k),"unknown property") end
        checkvalue(v,kind,name.."."..k)
      end
      local p=node.props
      if fields[name] then
        if type(p.id)~="string" or not p.id:find("%S") then fail(name..".id","required") end
        if ids[p.id] then fail(name..".id","duplicate "..p.id) end
        ids[p.id]=true
      end
      if name=="ProgressBar" and p.ratio and (p.ratio<0 or p.ratio>1) then fail(name..".ratio","expected 0..1") end
      for _,key in ipairs({"spacing","width","height","lines"}) do
        if p[key] and (p[key]<0 or (key=="lines" and (p[key]<1 or p[key]%1~=0))) then fail(name.."."..key,"invalid size") end
      end
      if p.min and p.max and p.min>=p.max then fail(name..".max","must exceed min") end
      if p.step and p.step<=0 then fail(name..".step","must be positive") end
      if name=="Section" and not p.title then fail(name..".title","required") end
      if name=="Image" and (not p.src or p.src=="" or
        (p.src:sub(1,1)~="/" and p.src:sub(1,10)~="content://")) then
        fail(name..".src","expected local absolute path or content URI")
      end
      if name=="Button" and (not p.action or p.action=="") then fail(name..".action","required") end
      local n=array(node.children,name..".children")
      if not layouts[name] and n>0 then fail(name..".children","leaf cannot contain children") end
      for _,child in ipairs(node.children) do visit(child,depth+1) end
      visiting[node]=nil
    end
    visit(root,1)
    return root
  end
  for name in pairs(schemas) do
    api[name]=function(t)
      if type(t)~="table" then fail(name,"expected table") end
      local props, children={},{}
      local n=0
      for k,v in pairs(t) do
        if type(k)=="number" then
          if k<1 or k%1~=0 then fail(name..".children","invalid index") end
          n=n+1
        elseif type(k)=="string" then props[k]=v
        else fail(name,"invalid key") end
      end
      for i=1,n do
        local v=t[i]
        if type(v)=="string" then v={__ui="Text",props={text=v},children={}} end
        if v==nil then fail(name..".children","sparse array") end
        children[i]=v
      end
      return validate({__ui=name,props=props,children=children})
    end
  end
  api.Modifier=modifier({})
  -- Keep bridge functions behind a proxy so ordinary assignment cannot replace them.
  if bridge then
    for name,fn in pairs(bridge) do api[name]=fn end
    for _,name in ipairs({"form","screen","show"}) do
      local fn=bridge[name]
      if fn then
        api[name]=function(...)
          local args=table.pack(...)
          local root=args[name=="show" and 1 or 2]
          if type(root)=="table" and root.__ui~=nil then validate(root) end
          return fn(table.unpack(args,1,args.n))
        end
      end
    end
  end
  ui=setmetatable({}, {
    __index=function(_,name)
      if api[name]==nil then fail(tostring(name),"unknown component or method") end
      return api[name]
    end,
    __newindex=function(_,name) fail(tostring(name),"read-only API") end,
    __metatable=false,
  })
end
