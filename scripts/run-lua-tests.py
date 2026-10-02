"""Run a Lua test file with the system Lua 5.4 shared library (no interpreter package needed)."""
import ctypes
import sys

lua = ctypes.CDLL("liblua-5.4.so")
lua.luaL_newstate.restype = ctypes.c_void_p
lua.luaL_openlibs.argtypes = [ctypes.c_void_p]
lua.luaL_loadfilex.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p]
lua.lua_pcallk.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_longlong, ctypes.c_void_p]
lua.lua_tolstring.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p]
lua.lua_tolstring.restype = ctypes.c_char_p
lua.lua_close.argtypes = [ctypes.c_void_p]
state = lua.luaL_newstate()
try:
    lua.luaL_openlibs(state)
    status = lua.luaL_loadfilex(state, sys.argv[1].encode(), None)
    if status == 0:
        status = lua.lua_pcallk(state, 0, -1, 0, 0, None)
    if status != 0:
        print(lua.lua_tolstring(state, -1, None).decode(), file=sys.stderr)
        sys.exit(1)
finally:
    lua.lua_close(state)
