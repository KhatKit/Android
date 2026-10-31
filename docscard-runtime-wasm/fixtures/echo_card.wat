;; fixtures/echo_card.wat —— 最小可用的 ABI v1 卡片：原样回显事件 JSON。
;;
;; 导出全部六个 ABI v1 export：kk_init / kk_handle / kk_shutdown /
;; kk_alloc / kk_free / kk_last_error。kk_init 会把宿主传入的 manifest.json
;; 原文通过 kk.log(level=3) 打出来，用于验证 log 通路。
;;
;; 由 tests/integration.rs 用 `wat` crate 编译后交给引擎执行；也可用 wat2wasm 直接编译：
;;   wat2wasm fixtures/echo_card.wat -o card.wasm
(module
  (import "kk" "host_call" (func $host_call (param i32 i32 i32) (result i64)))
  (import "kk" "log" (func $log (param i32 i32 i32)))
  (import "kk" "rand" (func $rand (param i32 i32) (result i32)))
  (import "kk" "now_ms" (func $now_ms (result i64)))
  (import "kk" "abort" (func $abort (param i32)))

  (memory (export "memory") 2)


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
  (func (export "kk_init") (param $ptr i32) (param $len i32) (result i32)
    ;; 把宿主传入的 manifest.json 原文写到 kk.log（level=3），便于测试断言日志通路。
    (call $log (i32.const 3) (local.get $ptr) (local.get $len))
    (i32.const 0))

  (func (export "kk_handle") (param $ptr i32) (param $len i32) (result i64)
    ;; 原样回显：把宿主写入的 (ptr, len) 打包成 ABI v1 的 i64 结果。
    (i64.or
      (i64.shl (i64.extend_i32_u (local.get $ptr)) (i64.const 32))
      (i64.extend_i32_u (local.get $len))))

  (func (export "kk_shutdown") (result i32)
    (i32.const 0))

  (func (export "kk_last_error") (result i64)
    ;; 0 = 没有错误详情。
    (i64.const 0))
)
