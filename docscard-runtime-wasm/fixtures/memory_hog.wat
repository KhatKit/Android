;; fixtures/memory_hog.wat —— 狂分配卡片：验证 limits.memory_mb（wasmi StoreLimits 页上限）。
;;
;; kk_handle 反复 memory.grow(1)，直到 host limiter 拒绝（返回 -1）。
;; 然后按最终页数返回静态 JSON：
;;   memory.size() == 16  -> {"grow":"capped"}    （manifest.limits.memory_mb = 1 -> 16 页）
;;   其它                 -> {"grow":"uncapped"}
;; 这个卡片把自己的行为写成可断言的结果，测试不需要读 guest 内存。
(module
  (import "kk" "host_call" (func $host_call (param i32 i32 i32) (result i64)))
  (import "kk" "log" (func $log (param i32 i32 i32)))
  (import "kk" "rand" (func $rand (param i32 i32) (result i32)))
  (import "kk" "now_ms" (func $now_ms (result i64)))
  (import "kk" "abort" (func $abort (param i32)))

  (memory (export "memory") 2)
  (data (i32.const 16) "{\"grow\":\"capped\"}")
  (data (i32.const 64) "{\"grow\":\"uncapped\"}")

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

  ;; 狂分配内存：每次 memory.grow(1) 直到失败（-1）或到 wasm 32 位上限 65536 页。
  ;; 正常被 limits.memory_mb 拦住时，页数会恰好停在 memory_mb * 16。
  (func (export "kk_handle") (param $ptr i32) (param $len i32) (result i64)
    (local $prev i32)
    (block $done
      (loop $grow
        (br_if $done (i32.ge_u (memory.size) (i32.const 65536)))
        (local.set $prev (memory.grow (i32.const 1)))
        (br_if $done (i32.eq (local.get $prev) (i32.const -1)))
        (br $grow)))
    (if (result i64) (i32.eq (memory.size) (i32.const 16))
      (then (i64.or (i64.shl (i64.const 16) (i64.const 32)) (i64.const 17)))
      (else (i64.or (i64.shl (i64.const 64) (i64.const 32)) (i64.const 19)))))
)
