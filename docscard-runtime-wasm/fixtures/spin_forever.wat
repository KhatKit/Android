;; fixtures/spin_forever.wat —— 死循环卡片：验证 limits.timeout_ms（wasmi fuel）中断。
;;
;; kk_handle 进入无限循环；引擎应在 fuel 耗尽时返回 RuntimeError::Timeout，
;; 而不是挂死宿主线程。测试用 manifest 把 timeout_ms 设成 20ms。
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
  (func (export "kk_init") (param i32) (param i32) (result i32) (i32.const 0))

  (func (export "kk_handle") (param $ptr i32) (param $len i32) (result i64)
    (loop $spin (br $spin))
    (i64.const 0))

  (func (export "kk_shutdown") (result i32) (i32.const 0))

  (func (export "kk_last_error") (result i64) (i64.const 0))
)
