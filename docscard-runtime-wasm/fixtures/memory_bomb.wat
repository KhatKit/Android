;; fixtures/memory_bomb.wat —— 声明超限初始内存的卡片：验证 limits.memory_mb 在实例化阶段生效。
;;
;; `(memory 64)` = 64 页 = 4 MiB。manifest.limits.memory_mb = 1 时宿主 limiter 只允许
;; 16 页，实例化必须失败（RuntimeError::Instantiate），不能先把 4 MiB 分配出来再拦。
(module
  (import "kk" "host_call" (func $host_call (param i32 i32 i32) (result i64)))
  (import "kk" "log" (func $log (param i32 i32 i32)))
  (import "kk" "rand" (func $rand (param i32 i32) (result i32)))
  (import "kk" "now_ms" (func $now_ms (result i64)))
  (import "kk" "abort" (func $abort (param i32)))

  (memory (export "memory") 64)

  (func (export "kk_alloc") (param $size i32) (result i32) (i32.const 1024))
  (func (export "kk_free") (param i32) (param i32))
  (func (export "kk_init") (param i32) (param i32) (result i32) (i32.const 0))
  (func (export "kk_handle") (param i32) (param i32) (result i64) (i64.const 0))
  (func (export "kk_shutdown") (result i32) (i32.const 0))
  (func (export "kk_last_error") (result i64) (i64.const 0))
)
