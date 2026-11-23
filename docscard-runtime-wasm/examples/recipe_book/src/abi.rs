//! ABI v1: the host fills a guest allocation for the reply; the host frees the exported result.
use serde_json::{json, Value};
use std::{ptr, slice};

#[link(wasm_import_module = "kk")]
extern "C" {
    fn host_call(cap: i32, ptr: *const u8, len: usize) -> i64;
}

#[no_mangle]
pub extern "C" fn kk_alloc(len: usize) -> *mut u8 {
    if !(1..=262144).contains(&len) {
        return ptr::null_mut();
    }
    Box::into_raw(vec![0u8; len].into_boxed_slice()) as *mut u8
}

#[no_mangle]
pub unsafe extern "C" fn kk_free(ptr: *mut u8, len: usize) {
    if !ptr.is_null() {
        drop(Box::from_raw(ptr::slice_from_raw_parts_mut(ptr, len)));
    }
}

fn present(request: Value) -> Result<Value, String> {
    let bytes = request.to_string();
    let reply = unsafe { host_call(0, bytes.as_ptr(), bytes.len()) };
    if reply <= 0 {
        return Err(format!("无法显示菜谱 ({reply})"));
    }
    let ptr = (reply >> 32) as *mut u8;
    let len = reply as u32 as usize;
    let bytes = unsafe { Box::from_raw(ptr::slice_from_raw_parts_mut(ptr, len)) };
    let value: Value = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
    if let Some(reason) = value["__error"].as_str() {
        return Err(reason.into());
    }
    Ok(value)
}

#[no_mangle]
pub unsafe extern "C" fn kk_handle(ptr: *const u8, len: usize) -> i64 {
    let value = serde_json::from_slice(slice::from_raw_parts(ptr, len))
        .map_err(|e| e.to_string())
        .and_then(|event| super::run(event, present))
        .unwrap_or_else(|reason| json!({"__error":reason}));
    let out = value.to_string().into_bytes().into_boxed_slice();
    let len = out.len() as i64;
    ((Box::into_raw(out) as *mut u8 as i64) << 32) | len
}
