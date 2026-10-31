;; fixtures/undeclared_capability.wat —— 未声明能力调用卡片：验证能力白名单。
;;
;; kk_handle 调 kk.host_call(cap = 1, event_ptr, event_len)：
;;   * 返回负值（HOST_ERR_CAPABILITY_DENIED = 1）  -> 回 {"denied":true}
;;   * 返回 (ptr<<32|len) 的成功应答              -> 回 {"ok":true}
;; 测试用两个 manifest 分别覆盖"拒绝"与"放行"两条路径（cap 索引 = 声明列表下标）。
(module
  (import "kk" "host_call" (func $host_call (param i32 i32 i32) (result i64)))
  (import "kk" "log" (func $log (param i32 i32 i32)))
  (import "kk" "rand" (func $rand (param i32 i32) (result i32)))
  (import "kk" "now_ms" (func $now_ms (result i64)))
  (import "kk" "abort" (func $abort (param i32)))

  (memory (export "memory") 2)
  (data (i32.const 16) "{\"denied\":true}")
  (data (i32.const 64) "{\"ok\":true}")

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

  (func (export "kk_last_error") (result i64) (i64.const 0))

  ;; 请求 cap 索引 1（manifest.capabilities 的下标）。
  ;; manifest 只声明 1 个能力时索引 1 未声明 -> 必须被拒绝；
  ;; manifest 声明 2 个能力时索引 1 = 第 2 个能力 -> 允许。
  (func (export "kk_handle") (param $ptr i32) (param $len i32) (result i64)
    (if (result i64)
      (i64.lt_s
        (call $host_call (i32.const 1) (local.get $ptr) (local.get $len))
        (i64.const 0))
      (then (i64.or (i64.shl (i64.const 16) (i64.const 32)) (i64.const 15)))
      (else (i64.or (i64.shl (i64.const 64) (i64.const 32)) (i64.const 11)))))
)
