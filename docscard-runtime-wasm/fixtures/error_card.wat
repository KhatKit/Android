;; fixtures/error_card.wat —— 错误码 + kk_last_error 卡片：验证 ABI v1 错误通路。
;;
;; kk_handle 返回负值 -7（= 错误码 7），kk_last_error() 返回 `(ptr << 32) | len`
;; 指向 {"error":"bad_event"}。引擎必须映射成
;; RuntimeError::CardError { code: 7, export: "kk_handle", detail: Some(该 JSON) }，
;; 而不是 panic 或静默返回空串。
(module
  (import "kk" "host_call" (func $host_call (param i32 i32 i32) (result i64)))
  (import "kk" "log" (func $log (param i32 i32 i32)))
  (import "kk" "rand" (func $rand (param i32 i32) (result i32)))
  (import "kk" "now_ms" (func $now_ms (result i64)))
  (import "kk" "abort" (func $abort (param i32)))

  (memory (export "memory") 2)
  (data (i32.const 16) "{\"error\":\"bad_event\"}")

  ;; bump 分配器：给静态 JSON 数据留出低地址，堆从 1024 起。
  (global $heap (mut i32) (i32.const 1024))

  (func (export "kk_alloc") (param $size i32) (result i32)
    (local $ptr i32)
    (local.set $ptr (global.get $heap))
    (if (i32.gt_u (i32.add (i32.add (local.get $ptr) (local.get $size)) (i32.const 8))
                   (i32.const 130000))
      (then (return (i32.const 0))))
    (global.set $heap (i32.add (i32.add (global.get $heap) (local.get $size)) (i32.const 8)))
    (local.get $ptr))

  (func (export "kk_free") (param i32) (param i32))
  (func (export "kk_init") (param i32) (param i32) (result i32) (i32.const 0))

  (func (export "kk_shutdown") (result i32) (i32.const 0))

  ;; kk_handle 返回负值 -(7)：卡片自报错误码 7，细节在 kk_last_error() 里。
  (func (export "kk_handle") (param $ptr i32) (param $len i32) (result i64)
    (i64.const -7))

  ;; 错误 JSON：{"error":"bad_event"}（21 字节，位于偏移 16）。
  (func (export "kk_last_error") (result i64)
    (i64.or (i64.shl (i64.const 16) (i64.const 32)) (i64.const 21)))
)
