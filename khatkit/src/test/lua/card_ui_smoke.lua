-- Execute real card entrypoints with the real DSL and deterministic bridge fixtures.
-- This checks UI/return flow, not Android image processing or live F-Droid services.
local cards=(os.getenv("KHATKIT_CARDS_DIR") or "../../KhatKitCards"):gsub("/$", "").."/"
local prelude="khatkit/src/main/resources/ui_kit.lua"
local function install(form,screen)
  ui={form=form,screen=screen,show=function(root) assert(root.__ui) end,progress=function() end}
  dofile(prelude)
end
local function equal(a,b)
  if type(a)~=type(b) then return false end
  if type(a)~="table" then return a==b end
  for k,v in pairs(a) do if not equal(v,b[k]) then return false end end
  for k in pairs(b) do if a[k]==nil then return false end end
  return true
end
local function fields(root,result)
  result=result or {}
  if root.props.id then result[root.props.id]=root.props end
  for _,child in ipairs(root.children) do fields(child,result) end
  return result
end
local images={"resize_crop","enhance","finish","compose","compare","split","lut","pdf","analyze","workflow"}
for _,suffix in ipairs(images) do
  local name="image_"..suffix
  local file=cards..name.."/main.lua"
  args={}
  local options
  install(function(_,root)
    assert(root.__ui=="Column")
    local f=fields(root)
    assert(f.image.multiple and f.image.filter=="any" and f.params.value=="{}")
    options=f.op.options
    return nil
  end)
  assert(dofile(file).cancelled)
  local progress, calls
  imageToolbox=setmetatable({}, {__index=function(_,method)
    return function(...)
      calls[#calls+1]={method,...}
      if method=="analyze" or method=="compose" then return '{"result":"fixture","outputs":["/tmp/result.png"]}' end
      return "/tmp/result.png"
    end
  end})
  tool={copyPath=function(...) calls[#calls+1]={"copyPath",...} end}
  for _,op in ipairs(options) do
    local function run(useForm)
      calls,progress={},0
      args=useForm and {} or {image="/tmp/input.png",op=op,params="{}"}
      -- Use a fresh proxy with a progress recorder.
      ui=nil
      ui={form=function(_,root)
        assert(useForm and fields(root).op)
        return {image="/tmp/input.png",op=op,params="{}",output=""}
      end,progress=function(ratio) assert(ratio>=0 and ratio<=1); progress=progress+1 end}
      dofile(prelude)
      local result=dofile(file)
      return result,calls,progress
    end
    local direct,dc,dp=run(false)
    local submitted,sc,sp=run(true)
    assert(equal(direct,submitted) and equal(dc,sc) and dp==sp,name..": form/args mismatch "..op)
  end
  print(name..": cancel + "..#options.." operations, form/args results and bridge calls match")
end
args={}
install(function() return nil end)
assert(dofile(cards.."a11y-click-text/main.lua").cancelled)
accessibility={currentPackage=function() return "fixture" end,click=function(v) assert(v.text=="OK"); return true end}
args={}
install(function(_,root) assert(fields(root).text.required); return {text="OK",exact=true} end)
assert(dofile(cards.."a11y-click-text/main.lua").clicked)
print("a11y-click-text: submit/cancel passed")

local index='{"packages":{"org.example.app":{"metadata":{"name":"Example","summary":"demo","description":"details","license":"MIT"},"versions":{"1":{"manifest":{"versionName":"1.0"},"file":{"name":"example.apk"}}}}}}'
tool={httpGet=function() return index end}
download={start=function(request) assert(request.url:find("example.apk",1,true)); return "fixture-task" end}
for _,action in ipairs({"popular","search","details","download"}) do
  args={action=action,query="Example",package_id="org.example.app"}
  install(function() error("unexpected form") end,function(_,root)
    assert(root.__ui=="Column")
    return {event="download",values={package_id="org.example.app"}}
  end)
  assert(dofile(cards.."fdroid_market/main.lua").task_id=="fixture-task",action)
end
args={}
install(function() return nil end)
assert(dofile(cards.."fdroid_market/main.lua").cancelled)
args={action="search",query="no match"}
install(function() error("unexpected form") end)
assert(dofile(cards.."fdroid_market/main.lua").count==0)
args={action="popular"}
install(function() error("unexpected form") end,function() return {event="done",values={}} end)
assert(dofile(cards.."fdroid_market/main.lua").count==1)
args={action="details",package_id="org.example.app"}
install(function() error("unexpected form") end,function() return nil end)
assert(dofile(cards.."fdroid_market/main.lua").cancelled)
print("fdroid_market: four actions + result download + cancel/done/empty passed")
args={}
assert(dofile(cards.."music_key/main.lua").error)
musicKey={call=function() return "/tmp/music.mp3" end}
args={path="/tmp/music.ncm"}
assert(dofile(cards.."music_key/main.lua").output=="/tmp/music.mp3")
args={}
assert(dofile(cards.."yunx_parallel_download/main.lua").error)
local ticks, updates=0,0
web={savedCookie=function() return "fixture-cookie" end}
download={
  start=function(task) assert(task.headers.Cookie=="fixture-cookie" and task.threads==8); return "fixture" end,
  status=function()
    ticks=ticks+1
    return ticks==1 and {state="running",progress=0.5} or {state="done",file="/tmp/fixture"}
  end,
}
ui={progress=function() updates=updates+1 end}
dofile(prelude)
args={url="https://fixture.invalid/file"}
assert(dofile(cards.."yunx_parallel_download/main.lua").state=="done" and updates==2)
print("music_key / yunx_parallel_download: unchanged entrypoint, result and progress regression passed")
