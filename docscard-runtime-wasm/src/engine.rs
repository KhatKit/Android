//! WASM 卡片宿主运行时（基于 wasmi 0.36）。
//!
//! ## ABI v1 内存约定（冻结，改动需同时改 abi.rs 与 Kotlin 侧）
//!
//! `text
//! 宿主 -> guest：ptr = kk_alloc(len); memory[ptr..ptr+len] = event_json; call handle(ptr, len)
//! guest -> 宿主：结果 = (ptr << 32) | len;  宿主读 memory[ptr..ptr+len] = result_json; kk_free(ptr, len)
//! 错误码 != 0（kk_init/kk_shutdown 返回值，或 kk_handle 返回 0）-> 读 kk_last_error() 的 JSON
//! `
//!
//! * 宿主只通过**线性内存 + JSON** 与卡片交换数据，不传对象、不共享句柄。
//! * `kk_alloc` / `kk_free` 由卡片实现，宿主必须成对使用；结果缓冲区读完立即 free。
//! * `kk_last_error` 的缓冲区归卡片所有，宿主只读不 free。
//! * `kk.host_call` 返回负值 `-(code)` 表示失败，正值 `(ptr << 32) | len` 表示应答 JSON。
//!
//! ## 限额
//!
//! * `limits.memory_mb` -> wasmi `StoreLimits`（最大页数；memory.grow 超限直接失败）。
//! * `limits.timeout_ms` -> wasmi fuel（每次调用前重置），耗尽映射为 `RuntimeError::Timeout`。
//! * `limits.output_bytes` -> 宿主回传内容 + 事件结果 + 事件输入的字节上限。
//! * `manifest.capabilities` -> `kk.host_call` 的 cap 索引只能落在声明列表内，否则拒绝。

use wasmi::core::TrapCode;
use wasmi::{
    Caller, Config, Engine, Error as WasmiError, Extern, Func, Linker, Memory, Module,
    ResourceLimiter, Store, StoreLimits, StoreLimitsBuilder, Val,
};

use crate::abi;
use crate::error::RuntimeError;
use crate::host::CardHost;
use crate::manifest::{Capability, Manifest, ManifestSpec};

/// 1 ms 超时折算的 wasmi fuel 数。fuel 是确定性度量，不依赖墙钟。
pub const FUEL_PER_MS: u64 = 10_000;

/// `kk.log` 单条消息的最大字节数（超出截断）。
pub const MAX_LOG_MESSAGE_BYTES: usize = 4096;
/// 保留的日志条数上限（防止卡片刷屏耗尽宿内存）。
pub const MAX_LOG_ENTRIES: usize = 1024;

/// 一条卡片日志（`kk.log`）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LogRecord {
    pub level: i32,
    pub message: String,
}

/// `kk.host_call` 最近一次失败记录（能力拒绝、宿主失败、应答超长等）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HostCallFailure {
    /// ABI v1 的 `HOST_ERR_*` 码。
    pub code: i32,
    /// 卡片请求的能力字符串；cap 索引越界时为 `cap#<index>`。
    pub capability: String,
    pub message: String,
}

/// wasmi `Store` 的用户数据：宿主实现 + 限额 + 审计状态。
pub struct HostState<H: CardHost> {
    host: H,
    capabilities: Vec<Capability>,
    limits: StoreLimits,
    max_output_bytes: usize,
    last_host_error: Option<HostCallFailure>,
    host_call_failures: u32,
    logs: Vec<LogRecord>,
    /// 卡片调用 `kk.abort(code)` 时记录，随后中断执行。
    aborted: Option<i32>,
    rng_state: u64,
}

impl<H: CardHost> HostState<H> {
    pub(crate) fn new(
        host: H,
        capabilities: Vec<Capability>,
        limits: StoreLimits,
        max_output_bytes: usize,
    ) -> Self {
        let seed = (abi::now_ms() as u64) ^ 0x9E37_79B9_7F4A_7C15;
        Self {
            host,
            capabilities,
            limits,
            max_output_bytes,
            last_host_error: None,
            host_call_failures: 0,
            logs: Vec::new(),
            aborted: None,
            rng_state: seed | 1,
        }
    }

    /// 注入的宿主实现。
    pub fn host(&self) -> &H {
        &self.host
    }

    /// 卡片通过 `kk.log` 写的日志。
    pub fn logs(&self) -> &[LogRecord] {
        &self.logs
    }

    /// 最近一次 `kk.host_call` 失败。
    pub fn last_host_error(&self) -> Option<&HostCallFailure> {
        self.last_host_error.as_ref()
    }

    /// `kk.host_call` 失败次数（含能力拒绝）。
    pub fn host_call_failures(&self) -> u32 {
        self.host_call_failures
    }

    /// manifest 声明的能力白名单。
    pub fn capabilities(&self) -> &[Capability] {
        &self.capabilities
    }

    fn record_failure(&mut self, code: i32, capability: String, message: String) {
        self.host_call_failures = self.host_call_failures.saturating_add(1);
        self.last_host_error = Some(HostCallFailure {
            code,
            capability,
            message,
        });
    }

    /// xorshift64*：确定性 PRNG，供 `kk.rand` 使用。
    fn fill_random(&mut self, out: &mut [u8]) {
        for chunk in out.chunks_mut(8) {
            let mut x = self.rng_state;
            x ^= x >> 12;
            x ^= x << 25;
            x ^= x >> 27;
            self.rng_state = x;
            let value = x.wrapping_mul(0x2545_F491_4F6C_DD1D).to_le_bytes();
            let n = chunk.len();
            chunk.copy_from_slice(&value[..n]);
        }
    }
}

fn guest_memory<H: CardHost>(caller: &Caller<'_, HostState<H>>) -> Option<Memory> {
    caller
        .get_export(abi::MEMORY_EXPORT)
        .and_then(Extern::into_memory)
}

/// 从 guest 线性内存读一段字节；越界/无内存时返回 ABI 错误码。
fn read_guest_bytes<H: CardHost>(
    caller: &Caller<'_, HostState<H>>,
    ptr: i32,
    len: i32,
) -> Result<Vec<u8>, i32> {
    if ptr < 0 || len < 0 {
        return Err(abi::HOST_ERR_POINTER_OOB);
    }
    let memory = guest_memory(caller).ok_or(abi::HOST_ERR_POINTER_OOB)?;
    let data = memory.data(caller);
    let start = ptr as usize;
    let end = start
        .checked_add(len as usize)
        .ok_or(abi::HOST_ERR_POINTER_OOB)?;
    if end > data.len() {
        return Err(abi::HOST_ERR_POINTER_OOB);
    }
    Ok(data[start..end].to_vec())
}

/// 向 guest 线性内存写一段字节；越界/无内存时返回 ABI 错误码。
fn write_guest_bytes<H: CardHost>(
    caller: &mut Caller<'_, HostState<H>>,
    ptr: u32,
    bytes: &[u8],
) -> Result<(), i32> {
    let memory = guest_memory(caller).ok_or(abi::HOST_ERR_POINTER_OOB)?;
    let data = memory.data_mut(&mut *caller);
    let start = ptr as usize;
    let end = start
        .checked_add(bytes.len())
        .ok_or(abi::HOST_ERR_POINTER_OOB)?;
    if end > data.len() {
        return Err(abi::HOST_ERR_POINTER_OOB);
    }
    data[start..end].copy_from_slice(bytes);
    Ok(())
}

/// 在宿主函数内部调用 guest 的 `kk_alloc`（wasmi 支持嵌套调用）。
fn guest_alloc<H: CardHost>(caller: &mut Caller<'_, HostState<H>>, size: u32) -> Option<u32> {
    let func = caller
        .get_export(abi::EXPORT_ALLOC)
        .and_then(Extern::into_func)?;
    let mut results = [Val::I32(0)];
    func.call(&mut *caller, &[Val::I32(size as i32)], &mut results)
        .ok()?;
    match results[0] {
        Val::I32(ptr) if ptr > 0 => Some(ptr as u32),
        _ => None,
    }
}

/// 注册 ABI v1 的五个 import。
///
/// 这是**唯一**的 import 注册入口；`CardRuntime::load` 与单测都复用它。
pub fn link_abi<H: CardHost>(linker: &mut Linker<HostState<H>>) -> Result<(), RuntimeError> {
    linker
        .func_wrap(
            abi::IMPORT_MODULE,
            abi::IMPORT_HOST_CALL,
            |mut caller: Caller<'_, HostState<H>>, cap: i32, req_ptr: i32, req_len: i32| -> Result<i64, WasmiError> {
                // 1. 读请求 JSON（越界一律拒绝，不静默放行）。
                let request = match read_guest_bytes(&caller, req_ptr, req_len) {
                    Ok(bytes) => match String::from_utf8(bytes) {
                        Ok(text) => text,
                        Err(_) => {
                            caller.data_mut().record_failure(
                                abi::HOST_ERR_INVALID_REQUEST,
                                format!("cap#{cap}"),
                                "request JSON is not valid UTF-8".to_string(),
                            );
                            return Ok(abi::encode_error(abi::HOST_ERR_INVALID_REQUEST));
                        }
                    },
                    Err(code) => {
                        caller.data_mut().record_failure(
                            code,
                            format!("cap#{cap}"),
                            "request JSON points outside guest memory".to_string(),
                        );
                        return Ok(abi::encode_error(code));
                    }
                };
                let request_limit = caller.data().max_output_bytes;
                if request.len() > request_limit {
                    caller.data_mut().record_failure(
                        abi::HOST_ERR_INVALID_REQUEST,
                        format!("cap#{cap}"),
                        format!("request JSON is {} bytes, limit is {request_limit}", request.len()),
                    );
                    return Ok(abi::encode_error(abi::HOST_ERR_INVALID_REQUEST));
                }

                // 2. 能力白名单：cap 是 manifest.capabilities 的下标，越界即未声明 -> 拒绝。
                let capability = match caller.data().capabilities.get(cap as usize) {
                    Some(capability) => capability.clone(),
                    None => {
                        caller.data_mut().record_failure(
                            abi::HOST_ERR_CAPABILITY_DENIED,
                            format!("cap#{cap}"),
                            "capability is not declared in manifest.capabilities".to_string(),
                        );
                        return Ok(abi::encode_error(abi::HOST_ERR_CAPABILITY_DENIED));
                    }
                };
                let capability_name = capability.as_str();

                // 3. 交给宿主实现。
                let reply = match caller
                    .data_mut()
                    .host
                    .host_call(&capability_name, &request)
                {
                    Ok(reply) => reply,
                    Err(err) => {
                        let code = err.code();
                        caller.data_mut().record_failure(
                            code,
                            capability_name.clone(),
                            err.to_string(),
                        );
                        return Ok(abi::encode_error(code));
                    }
                };

                // 4. output_bytes 上限同样约束宿主回传内容。
                let limit = caller.data().max_output_bytes;
                if reply.len() > limit {
                    caller.data_mut().record_failure(
                        abi::HOST_ERR_OUTPUT_TOO_LARGE,
                        capability_name,
                        format!("host reply is {} bytes, limit is {limit}", reply.len()),
                    );
                    return Ok(abi::encode_error(abi::HOST_ERR_OUTPUT_TOO_LARGE));
                }

                // 5. 用 kk_alloc 在 guest 堆上分配应答缓冲区并写入。
                let reply_len = reply.len() as u32;
                let ptr = match guest_alloc(&mut caller, reply_len) {
                    Some(ptr) => ptr,
                    None => {
                        caller.data_mut().record_failure(
                            abi::HOST_ERR_HOST_FAILURE,
                            capability_name,
                            "kk_alloc failed while writing host reply".to_string(),
                        );
                        return Ok(abi::encode_error(abi::HOST_ERR_HOST_FAILURE));
                    }
                };
                if let Err(code) = write_guest_bytes(&mut caller, ptr, reply.as_bytes()) {
                    caller.data_mut().record_failure(
                        code,
                        capability_name,
                        "host reply points outside guest memory".to_string(),
                    );
                    return Ok(abi::encode_error(code));
                }
                Ok(abi::pack(ptr, reply_len))
            },
        )
        .map_err(|err| RuntimeError::Link(err.to_string()))?;

    linker
        .func_wrap(
            abi::IMPORT_MODULE,
            abi::IMPORT_LOG,
            |mut caller: Caller<'_, HostState<H>>, level: i32, ptr: i32, len: i32| {
                let raw = match read_guest_bytes(&caller, ptr, len) {
                    Ok(bytes) => String::from_utf8_lossy(&bytes).into_owned(),
                    Err(_) => "<invalid kk.log buffer>".to_string(),
                };
                let mut message = raw;
                if message.len() > MAX_LOG_MESSAGE_BYTES {
                    let mut boundary = MAX_LOG_MESSAGE_BYTES;
                    while boundary > 0 && !message.is_char_boundary(boundary) {
                        boundary -= 1;
                    }
                    message.truncate(boundary);
                }
                let state = caller.data_mut();
                state.host.log(level, &message);
                if state.logs.len() < MAX_LOG_ENTRIES {
                    state.logs.push(LogRecord { level, message });
                }
            },
        )
        .map_err(|err| RuntimeError::Link(err.to_string()))?;

    linker
        .func_wrap(
            abi::IMPORT_MODULE,
            abi::IMPORT_RAND,
            |mut caller: Caller<'_, HostState<H>>, ptr: i32, len: i32| -> i32 {
                if len <= 0 {
                    return 0;
                }
                let mut bytes = vec![0u8; len as usize];
                caller.data_mut().fill_random(&mut bytes);
                match write_guest_bytes(&mut caller, ptr as u32, &bytes) {
                    Ok(()) => 0,
                    Err(code) => code,
                }
            },
        )
        .map_err(|err| RuntimeError::Link(err.to_string()))?;

    linker
        .func_wrap(
            abi::IMPORT_MODULE,
            abi::IMPORT_NOW_MS,
            |_caller: Caller<'_, HostState<H>>| -> i64 { abi::now_ms() },
        )
        .map_err(|err| RuntimeError::Link(err.to_string()))?;

    linker
        .func_wrap(
            abi::IMPORT_MODULE,
            abi::IMPORT_ABORT,
            |mut caller: Caller<'_, HostState<H>>, code: i32| -> Result<(), WasmiError> {
                caller.data_mut().aborted = Some(code);
                // 立刻中断 guest 执行；调用方在 classify_error 里映射成 RuntimeError::Aborted。
                Err(WasmiError::new("kk.abort called by card"))
            },
        )
        .map_err(|err| RuntimeError::Link(err.to_string()))?;

    Ok(())
}

/// 一个已经实例化、可以按事件调用的卡片。
///
/// 生命周期：`load` -> `init` -> (`handle`)* -> `shutdown`。
pub struct CardRuntime<H: CardHost> {
    store: Store<HostState<H>>,
    memory: Memory,
    alloc: Func,
    free: Func,
    handle: Func,
    init: Option<Func>,
    shutdown: Option<Func>,
    last_error: Option<Func>,
    timeout_ms: u64,
    output_bytes: usize,
    fuel_budget: u64,
    card_name: String,
}

impl<H: CardHost> std::fmt::Debug for CardRuntime<H> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CardRuntime")
            .field("card_name", &self.card_name)
            .field("timeout_ms", &self.timeout_ms)
            .field("output_bytes", &self.output_bytes)
            .finish_non_exhaustive()
    }
}

impl<H: CardHost> CardRuntime<H> {
    /// 解析 manifest + 校验 + 实例化卡片。
    pub fn from_manifest_json(
        wasm: &[u8],
        manifest_json: &str,
        host: H,
    ) -> Result<Self, RuntimeError> {
        let manifest = Manifest::parse(manifest_json)?;
        Self::load(wasm, &manifest, host)
    }

    /// 用一个已解析的 manifest 实例化卡片（内部会调用 `Manifest::validate`）。
    pub fn load(wasm: &[u8], manifest: &Manifest, host: H) -> Result<Self, RuntimeError> {
        let spec: ManifestSpec = manifest.validate()?;
        let limits = spec.limits;

        let mut config = Config::default();
        config.consume_fuel(true);
        // wasm SIMD（v128）：MoonBit 的 wasm1 后端在 core 的字符串/JSON 路径里
        // 无条件发射 SIMD 指令，默认关闭会让所有 MoonBit 卡片在 ModuleLoad 阶段失败
        // （"SIMD support is not enabled"）。SIMD 是确定性指令集，不影响 fuel 计量。
        config.wasm_simd(true);
        let engine = Engine::new(&config);
        let module = Module::new(&engine, wasm)
            .map_err(|err| RuntimeError::ModuleLoad(err.to_string()))?;

        let store_limits = StoreLimitsBuilder::new()
            .memory_size(limits.memory_bytes())
            .memories(1)
            .tables(4)
            .table_elements(4096)
            .instances(1)
            .build();
        let state = HostState::new(
            host,
            spec.capabilities.clone(),
            store_limits,
            limits.output_bytes,
        );
        let fuel_budget = limits.timeout_ms.saturating_mul(FUEL_PER_MS);
        let mut store: Store<HostState<H>> = Store::new(&engine, state);
        store.limiter(
            |state: &mut HostState<H>| -> &mut dyn ResourceLimiter { &mut state.limits },
        );
        // start 函数（MoonBit 卡片在这里 grow 内存并初始化堆）也算一次执行，
        // 必须预置 fuel，否则 consume_fuel 下任何指令都会立刻 OutOfFuel。
        store
            .set_fuel(fuel_budget)
            .map_err(|err| RuntimeError::Instantiate(err.to_string()))?;

        let mut linker: Linker<HostState<H>> = Linker::new(&engine);
        link_abi(&mut linker)?;

        let instance = linker
            .instantiate(&mut store, &module)
            .map_err(|err| RuntimeError::Instantiate(err.to_string()))?
            .start(&mut store)
            .map_err(|err| RuntimeError::Instantiate(err.to_string()))?;

        let memory = instance
            .get_memory(&store, abi::MEMORY_EXPORT)
            .ok_or(RuntimeError::MissingExport {
                name: abi::MEMORY_EXPORT,
            })?;
        let alloc = instance
            .get_func(&store, abi::EXPORT_ALLOC)
            .ok_or(RuntimeError::MissingExport {
                name: abi::EXPORT_ALLOC,
            })?;
        let free = instance
            .get_func(&store, abi::EXPORT_FREE)
            .ok_or(RuntimeError::MissingExport {
                name: abi::EXPORT_FREE,
            })?;
        let handle = instance
            .get_func(&store, abi::EXPORT_HANDLE)
            .ok_or(RuntimeError::MissingExport {
                name: abi::EXPORT_HANDLE,
            })?;
        let init = instance.get_func(&store, abi::EXPORT_INIT);
        let shutdown = instance.get_func(&store, abi::EXPORT_SHUTDOWN);
        let last_error = instance.get_func(&store, abi::EXPORT_LAST_ERROR);

        Ok(Self {
            store,
            memory,
            alloc,
            free,
            handle,
            init,
            shutdown,
            last_error,
            timeout_ms: limits.timeout_ms,
            output_bytes: limits.output_bytes,
            fuel_budget,
            card_name: manifest
                .name
                .clone()
                .unwrap_or_else(|| "unnamed-card".to_string()),
        })
    }

    /// manifest 里的卡片名（日志/CLI 用）。
    pub fn card_name(&self) -> &str {
        &self.card_name
    }

    /// 注入的宿主实现。
    pub fn host(&self) -> &H {
        self.store.data().host()
    }

    /// 卡片通过 `kk.log` 写的日志。
    pub fn logs(&self) -> &[LogRecord] {
        self.store.data().logs()
    }

    /// 最近一次 `kk.host_call` 失败（能力拒绝等）。
    pub fn last_host_error(&self) -> Option<&HostCallFailure> {
        self.store.data().last_host_error()
    }

    /// `kk.host_call` 失败次数（含能力拒绝）。
    pub fn host_call_failures(&self) -> u32 {
        self.store.data().host_call_failures()
    }

    /// manifest 声明的能力白名单。
    pub fn capabilities(&self) -> &[Capability] {
        self.store.data().capabilities()
    }

    /// 调 `kk_init(manifest_ptr, len)`。卡片没有导出 `kk_init` 时视为无操作。
    pub fn init(&mut self, manifest_json: &str) -> Result<(), RuntimeError> {
        let Some(func) = self.init else {
            return Ok(());
        };
        let (ptr, len) = self.write_input(manifest_json)?;
        let mut results = [Val::I32(0)];
        let outcome = self.call_export(
            &func,
            abi::EXPORT_INIT,
            &[Val::I32(ptr as i32), Val::I32(len as i32)],
            &mut results,
        );
        self.free_input(ptr, len);
        outcome?;
        let code = results[0].i32().unwrap_or(0);
        if code != 0 {
            let detail = self.last_error_json();
            return Err(RuntimeError::CardError {
                code,
                export: abi::EXPORT_INIT,
                detail,
            });
        }
        Ok(())
    }

    /// 调 `kk_handle(event_ptr, len)` 并返回结果 JSON。
    pub fn handle(&mut self, event_json: &str) -> Result<String, RuntimeError> {
        let (ptr, len) = self.write_input(event_json)?;
        let handle = self.handle;
        let mut results = [Val::I64(0)];
        let outcome = self.call_export(
            &handle,
            abi::EXPORT_HANDLE,
            &[Val::I32(ptr as i32), Val::I32(len as i32)],
            &mut results,
        );
        self.free_input(ptr, len);
        outcome?;
        let packed = results[0].i64().unwrap_or(0);
        if packed == 0 {
            // ABI v1：0 表示失败，细节在 kk_last_error() 的 JSON 里。
            return Err(RuntimeError::CardError {
                code: 1,
                export: abi::EXPORT_HANDLE,
                detail: self.last_error_json(),
            });
        }
        if let Some(code) = abi::decode_error(packed) {
            // 负值 = 卡片自报错误码（与 kk.host_call 的失败编码一致）。
            return Err(RuntimeError::CardError {
                code,
                export: abi::EXPORT_HANDLE,
                detail: self.last_error_json(),
            });
        }
        let result = self.read_result(packed)?;
        match serde_json::from_str::<serde_json::Value>(&result) {
            Ok(_) => Ok(result),
            Err(err) => Err(RuntimeError::InvalidResult {
                export: abi::EXPORT_HANDLE,
                detail: format!("result is not valid JSON: {err}"),
            }),
        }
    }

    /// 调 `kk_shutdown()`。卡片没有导出时视为无操作。
    pub fn shutdown(&mut self) -> Result<(), RuntimeError> {
        let Some(func) = self.shutdown else {
            return Ok(());
        };
        let mut results = [Val::I32(0)];
        self.call_export(&func, abi::EXPORT_SHUTDOWN, &[], &mut results)?;
        let code = results[0].i32().unwrap_or(0);
        if code != 0 {
            let detail = self.last_error_json();
            return Err(RuntimeError::CardError {
                code,
                export: abi::EXPORT_SHUTDOWN,
                detail,
            });
        }
        Ok(())
    }

    /// 读 `kk_last_error()` 的 JSON（卡片未导出时返回 `None`）。
    pub fn last_error_json(&mut self) -> Option<String> {
        let func = self.last_error?;
        let mut results = [Val::I64(0)];
        if self
            .call_export(&func, abi::EXPORT_LAST_ERROR, &[], &mut results)
            .is_err()
        {
            return None;
        }
        let packed = results[0].i64().unwrap_or(0);
        if packed == 0 {
            return None;
        }
        let (ptr, len) = abi::unpack(packed);
        if len == 0 {
            return None;
        }
        let memory = self.memory;
        let data = memory.data(&self.store);
        let start = ptr as usize;
        let end = start.checked_add(len as usize)?;
        if end > data.len() {
            return None;
        }
        String::from_utf8(data[start..end].to_vec()).ok()
    }

    /// 事件输入长度也受 `output_bytes` 约束（防止宿主被超大事件拖死）。
    fn write_input(&mut self, json: &str) -> Result<(u32, u32), RuntimeError> {
        let bytes = json.as_bytes();
        if bytes.len() > self.output_bytes {
            return Err(RuntimeError::InputTooLarge {
                len: bytes.len(),
                limit: self.output_bytes,
            });
        }
        let len = bytes.len() as u32;
        let ptr = self.alloc_in_guest(len)?;
        let memory = self.memory;
        let data = memory.data_mut(&mut self.store);
        let start = ptr as usize;
        let end = start
            .checked_add(bytes.len())
            .ok_or(RuntimeError::MemoryOutOfBounds {
                ptr,
                len,
                memory_len: data.len(),
            })?;
        if end > data.len() {
            return Err(RuntimeError::MemoryOutOfBounds {
                ptr,
                len,
                memory_len: data.len(),
            });
        }
        data[start..end].copy_from_slice(bytes);
        Ok((ptr, len))
    }

    fn free_input(&mut self, ptr: u32, len: u32) {
        let free = self.free;
        let _ = self.call_export(
            &free,
            abi::EXPORT_FREE,
            &[Val::I32(ptr as i32), Val::I32(len as i32)],
            &mut [],
        );
    }

    fn alloc_in_guest(&mut self, size: u32) -> Result<u32, RuntimeError> {
        let alloc = self.alloc;
        let mut results = [Val::I32(0)];
        self.call_export(
            &alloc,
            abi::EXPORT_ALLOC,
            &[Val::I32(size as i32)],
            &mut results,
        )?;
        match results[0] {
            Val::I32(ptr) if ptr > 0 => Ok(ptr as u32),
            _ => Err(RuntimeError::AllocFailed { size }),
        }
    }

    /// 从 `(ptr << 32) | len` 读结果 JSON，并检查 `output_bytes` 上限。
    fn read_result(&mut self, packed: i64) -> Result<String, RuntimeError> {
        if let Some(code) = abi::decode_error(packed) {
            return Err(RuntimeError::InvalidResult {
                export: abi::EXPORT_HANDLE,
                detail: format!("negative result encoding (host error code {code})"),
            });
        }
        let (ptr, len) = abi::unpack(packed);
        if len as usize > self.output_bytes {
            return Err(RuntimeError::OutputTooLarge {
                len: len as usize,
                limit: self.output_bytes,
            });
        }
        let memory = self.memory;
        let data = memory.data(&self.store);
        let start = ptr as usize;
        let end = start
            .checked_add(len as usize)
            .ok_or(RuntimeError::MemoryOutOfBounds {
                ptr,
                len,
                memory_len: data.len(),
            })?;
        if end > data.len() {
            return Err(RuntimeError::MemoryOutOfBounds {
                ptr,
                len,
                memory_len: data.len(),
            });
        }
        String::from_utf8(data[start..end].to_vec()).map_err(|err| RuntimeError::InvalidResult {
            export: abi::EXPORT_HANDLE,
            detail: format!("result is not valid UTF-8: {err}"),
        })
    }

    /// 每次调用前重置 fuel，保证 `timeout_ms` 对单次调用生效。
    fn call_export(
        &mut self,
        func: &Func,
        name: &'static str,
        inputs: &[Val],
        outputs: &mut [Val],
    ) -> Result<(), RuntimeError> {
        self.store
            .set_fuel(self.fuel_budget)
            .map_err(|err| RuntimeError::Link(err.to_string()))?;
        match func.call(&mut self.store, inputs, outputs) {
            Ok(()) => Ok(()),
            Err(err) => Err(self.classify_error(name, err)),
        }
    }

    fn classify_error(&mut self, name: &'static str, err: WasmiError) -> RuntimeError {
        if let Some(code) = self.store.data_mut().aborted.take() {
            return RuntimeError::Aborted { code };
        }
        let out_of_fuel = matches!(err.as_trap_code(), Some(TrapCode::OutOfFuel))
            || self.store.get_fuel().map(|fuel| fuel == 0).unwrap_or(false);
        if out_of_fuel {
            return RuntimeError::Timeout {
                timeout_ms: self.timeout_ms,
            };
        }
        RuntimeError::Trap {
            export: name,
            message: err.to_string(),
        }
    }
}
