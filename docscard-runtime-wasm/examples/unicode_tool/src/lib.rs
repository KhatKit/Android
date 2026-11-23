mod codec;
mod ui;

use serde_json::Value;

pub fn run(
    event: Value,
    screen: impl FnMut(Value) -> Result<Value, String>,
) -> Result<Value, String> {
    if event["type"] != "run" {
        return Err("不支持的卡片事件".into());
    }
    let args = &event["payload"];
    if let Some(input) = args.get("text") {
        return codec::convert(
            args["mode"].as_str().unwrap_or("encode"),
            input.as_str().ok_or("text 必须是字符串")?,
        )
        .map_err(str::to_owned);
    }
    ui::interactive(screen)
}

// Only the ABI adapter handles raw memory; host inputs and returned buffers have different owners.
#[cfg(target_arch = "wasm32")]
mod abi {
    use super::*;
    use serde_json::json;
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

    fn screen(request: Value) -> Result<Value, String> {
        let bytes = request.to_string();
        let reply = unsafe { host_call(0, bytes.as_ptr(), bytes.len()) };
        if reply <= 0 {
            return Err(format!("无法打开卡片界面 ({reply})"));
        }
        let ptr = (reply >> 32) as *mut u8;
        let len = reply as u32 as usize;
        // host_call used our kk_alloc, so this slice can be freed when parsing finishes.
        let buf = unsafe { Box::from_raw(ptr::slice_from_raw_parts_mut(ptr, len)) };
        let value: Value = serde_json::from_slice(&buf).map_err(|e| e.to_string())?;
        if let Some(reason) = value["__error"].as_str() {
            return Err(reason.to_owned());
        }
        Ok(value)
    }

    #[no_mangle]
    pub unsafe extern "C" fn kk_handle(ptr: *const u8, len: usize) -> i64 {
        let result = serde_json::from_slice(slice::from_raw_parts(ptr, len))
            .map_err(|e| e.to_string())
            .and_then(|event| run(event, screen))
            .unwrap_or_else(|reason| json!({"__error": reason}));
        let buf = result.to_string().into_bytes().into_boxed_slice();
        let len = buf.len() as i64;
        ((Box::into_raw(buf) as *mut u8 as i64) << 32) | len
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn headless_parameters_never_open_a_screen() {
        let result = run(
            json!({"type":"run", "payload":{"mode":"decode","text":"U+1F600"}}),
            |_| panic!("unexpected UI"),
        )
        .unwrap();
        assert_eq!(result["text"], "😀");
    }

    #[test]
    fn back_from_result_preserves_form_and_close_finishes() {
        let replies = [
            json!({"event":"submit","values":{"text":"A中😀","mode":"编码"}}),
            json!({"event":"result_close"}),
            json!({"event":"card_close"}),
        ];
        let mut pages = Vec::new();
        let result = run(json!({"type":"run","payload":{}}), |page| {
            pages.push(page);
            Ok(replies[pages.len() - 1].clone())
        })
        .unwrap();
        assert_eq!(result["closed"], true);
        assert_eq!(pages.len(), 3);
        assert_eq!(pages[1]["args"][2]["push"], true);
        assert!(pages[2]["args"][1].to_string().contains("A中😀"));
    }
}
