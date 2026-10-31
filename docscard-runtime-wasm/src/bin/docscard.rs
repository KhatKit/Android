//! `docscard` CLI：本地跑一张 WASM 卡片，或跑一个 .kkcard 包。
//!
//! `docscard run --card card.wasm --manifest manifest.json --event '{"type":"tick"}'
//! `docscard run --package my-card.kkcard --event '{}'
//!
//! 依次调用 `kk_init` / `kk_handle` / `kk_shutdown`，把结果 JSON 打到 stdout；
//! 任何失败（包非法、manifest 非法、缺 export、超时、能力被拒等）都以非 0 退出码结束。

use std::process::ExitCode;

use docscard_runtime_wasm::{
    CardHost, CardPackage, CardRuntime, DefaultHost, HostError, Manifest, MockHost,
    NullUiPresenter, ScriptedUiPresenter, UiPresenter, UiRenderHost, UiRequest,
};

const USAGE: &str = r#"usage:
  docscard run --card <card.wasm> --manifest <manifest.json> [--event <json|file>] [--verbose]
               [--mock-host <mock.json>] [--repeat <n>]
  docscard run --package <card.kkcard> [--event <json|file>] [--verbose] [--sha256 <hex>]

options:
  --card <card.wasm>   直接跑裸 wasm 卡片（需要同时给 --manifest）
  --manifest <json>    manifest.json 路径（--card 模式必需）
  --package <kkcard>   .kkcard 包（zip）：内含 manifest.json + card.wasm + assets/**
  --sha256 <hex>       校验 .kkcard 整包 sha256（可选，仅 --package 模式）
  --event <json|file>  事件 JSON（内联 JSON 或 .json 文件路径），默认 {}
  --mock-host <file>   用预置应答宿主代替 DefaultHost（键 = host_call 的 method）：
                       {"host.locale": "zh-CN", "ui.form": {"text": "确定"}}
                       {"__raw": "..."} 表示该方法返回值本身是 JSON 字符串；
                       无 __default 时未知方法返回 HOST_ERR_HOST_FAILURE。
  --ui <file|null>    启用 UI 宿主（ui:render）：file = 按顺序应答的 JSON 数组，
                       null = 一律取消（form/screen 返回 null）。启用后 ui.* 由 Rust
                       UiRenderHost 校验节点树（组件/属性白名单、深度<=32、节点<=512、
                       重复 id）并交给应答脚本，其余能力仍走 --mock-host。
  --repeat <n>         同一事件连跑 n 次（验证事件间复用不泄漏/不越界）
  --verbose            把卡片的 kk.log 输出到 stderr
  -h, --help           打印本帮助"#;

fn main() -> ExitCode {
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(err) => {
            eprintln!("docscard: {err}");
            ExitCode::from(1)
        }
    }
}

fn run() -> Result<(), String> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let Some(subcommand) = args.first() else {
        return Err(format!("missing subcommand\n{USAGE}"));
    };
    match subcommand.as_str() {
        "run" => cmd_run(&args[1..]),
        "help" | "--help" | "-h" => {
            println!("{USAGE}");
            Ok(())
        }
        other => Err(format!("unknown subcommand `{other}`\n{USAGE}")),
    }
}

enum Source {
    /// 裸 card.wasm + manifest.json。
    Card { wasm: String, manifest: String },
    /// .kkcard 包（zip）。
    Package {
        path: String,
        sha256: Option<String>,
    },
}

struct RunArgs {
    source: Source,
    event: String,
    verbose: bool,
    /// `--mock-host` 指定的预置应答宿主文件。
    mock_host: Option<String>,
    /// `--ui`：启用 UI 宿主（文件为应答脚本，"null" 表示一律取消）。
    ui: Option<String>,
    /// `--repeat`：同一事件连跑次数（默认 1）。
    repeat: u32,
}

fn parse_run_args(args: &[String]) -> Result<RunArgs, String> {
    let mut card = None;
    let mut manifest = None;
    let mut package = None;
    let mut sha256 = None;
    let mut event = String::from("{}");
    let mut verbose = false;
    let mut mock_host = None;
    let mut ui = None;
    let mut repeat: u32 = 1;
    let mut index = 0;
    while index < args.len() {
        let arg = args[index].as_str();
        match arg {
            "--card" => card = Some(value_of(args, &mut index, "--card")?),
            "--manifest" => manifest = Some(value_of(args, &mut index, "--manifest")?),
            "--package" => package = Some(value_of(args, &mut index, "--package")?),
            "--sha256" => sha256 = Some(value_of(args, &mut index, "--sha256")?),
            "--event" => event = value_of(args, &mut index, "--event")?,
            "--mock-host" => mock_host = Some(value_of(args, &mut index, "--mock-host")?),
            "--ui" => ui = Some(value_of(args, &mut index, "--ui")?),
            "--repeat" => {
                let raw = value_of(args, &mut index, "--repeat")?;
                repeat = raw
                    .parse::<u32>()
                    .map_err(|err| format!("--repeat expects a positive integer: {err}"))?;
                if repeat == 0 {
                    return Err("--repeat must be >= 1".to_string());
                }
            }
            "--verbose" | "-v" => {
                verbose = true;
                index += 1;
            }
            "help" | "--help" | "-h" => {
                println!("{USAGE}");
                std::process::exit(0);
            }
            other => return Err(format!("unknown option `{other}`\n{USAGE}")),
        }
    }

    let source = match (card, package) {
        (Some(_), Some(_)) => {
            return Err(format!("--card and --package are mutually exclusive\n{USAGE}"));
        }
        (None, None) => {
            return Err(format!("one of --card or --package is required\n{USAGE}"));
        }
        (Some(wasm), None) => {
            let manifest = manifest.ok_or_else(|| format!("--card requires --manifest\n{USAGE}"))?;
            if sha256.is_some() {
                return Err(format!("--sha256 only applies to --package\n{USAGE}"));
            }
            Source::Card { wasm, manifest }
        }
        (None, Some(path)) => {
            if manifest.is_some() {
                return Err(format!(
                    "--manifest must not be given with --package (the manifest comes from the package)\n{USAGE}"
                ));
            }
            Source::Package { path, sha256 }
        }
    };

    if mock_host.is_some() && matches!(source, Source::Package { .. }) {
        return Err(format!("--mock-host only applies to --card mode\n{USAGE}"));
    }

    Ok(RunArgs {
        source,
        event,
        verbose,
        mock_host,
        ui,
        repeat,
    })
}

fn value_of(args: &[String], index: &mut usize, flag: &str) -> Result<String, String> {
    let value = args
        .get(*index + 1)
        .cloned()
        .ok_or_else(|| format!("{flag} requires a value"))?;
    *index += 2;
    Ok(value)
}

fn cmd_run(args: &[String]) -> Result<(), String> {
    let parsed = parse_run_args(args)?;
    let event_json = load_event(&parsed.event)?;
    match parsed.source {
        Source::Card { wasm, manifest } => {
            let host = build_cli_host(parsed.mock_host.as_deref(), parsed.ui.as_deref())?;
            run_card(&wasm, &manifest, &event_json, parsed.verbose, parsed.repeat, host)
        }
        Source::Package { path, sha256 } => {
            run_package(&path, sha256.as_deref(), &event_json, parsed.verbose)
        }
    }
}

/// CLI 组合宿主：ui.* 走 UiRenderHost（校验 + 应答脚本），其余能力走 --mock-host。
///
/// 两者都不给时等价于 DefaultHost（host_call 一律拒绝），这也是既有行为。
struct CliHost {
    ui: Option<UiRenderHost<Box<dyn UiPresenter>>>,
    mock: Option<MockHost>,
}

impl CliHost {
    /// 本次运行发生的 UI 交互（verbose 打印/断言用）。
    fn ui_requests(&self) -> &[UiRequest] {
        match self.ui.as_ref() {
            Some(ui) => ui.requests(),
            None => &[],
        }
    }
}
impl CardHost for CliHost {
    fn host_call(&mut self, cap: &str, request_json: &str) -> Result<String, HostError> {
        let method = serde_json::from_str::<serde_json::Value>(request_json)
            .ok()
            .and_then(|value| {
                value
                    .get("method")
                    .and_then(|method| method.as_str())
                    .map(str::to_string)
            });
        let is_ui = method
            .as_deref()
            .map(|method| method.starts_with("ui."))
            .unwrap_or(false);
        if is_ui {
            return match self.ui.as_mut() {
                Some(ui) => ui.host_call(cap, request_json),
                None => Err(HostError::Denied(format!(
                    "{cap}: UI 宿主未启用（加 --ui <file|null> 提供 ui.* 应答）"
                ))),
            };
        }
        match self.mock.as_mut() {
            Some(mock) => mock.host_call(cap, request_json),
            None => Err(HostError::Denied(format!(
                "{cap}: 未提供宿主实现（用 --mock-host/--ui 指定应答）"
            ))),
        }
    }

    fn log(&mut self, level: i32, message: &str) {
        if let Some(mock) = self.mock.as_mut() {
            mock.log(level, message);
        }
    }
}

fn build_cli_host(mock_host: Option<&str>, ui: Option<&str>) -> Result<CliHost, String> {
    let mock = match mock_host {
        Some(path) => Some(MockHost::from_file(path)?),
        None => None,
    };
    let ui = match ui {
        Some("null") | Some("none") => Some(UiRenderHost::new(
            Box::new(NullUiPresenter) as Box<dyn UiPresenter>
        )),
        Some(path) => {
            let text = std::fs::read_to_string(path)
                .map_err(|err| format!("cannot read ui replies {path}: {err}"))?;
            let presenter = ScriptedUiPresenter::from_json(&text)?;
            Some(UiRenderHost::new(
                Box::new(presenter) as Box<dyn UiPresenter>
            ))
        }
        None => None,
    };
    Ok(CliHost { ui, mock })
}

fn run_card(
    card: &str,
    manifest: &str,
    event_json: &str,
    verbose: bool,
    repeat: u32,
    host: CliHost,
) -> Result<(), String> {
    let wasm = std::fs::read(card).map_err(|err| format!("cannot read card {card}: {err}"))?;
    let manifest_json = std::fs::read_to_string(manifest)
        .map_err(|err| format!("cannot read manifest {manifest}: {err}"))?;

    let manifest_value = Manifest::parse(&manifest_json).map_err(|err| err.to_string())?;
    let mut runtime = CardRuntime::load(&wasm, &manifest_value, host)
        .map_err(|err| format!("[{}] {err}", err.kind()))?;

    runtime
        .init(&manifest_json)
        .map_err(|err| format!("[{}] {err}", err.kind()))?;
    let mut result = String::new();
    for _ in 0..repeat.max(1) {
        result = runtime
            .handle(event_json)
            .map_err(|err| format!("[{}] {err}", err.kind()))?;
    }
    runtime
        .shutdown()
        .map_err(|err| format!("[{}] {err}", err.kind()))?;

    println!("{result}");
    if verbose {
        if repeat > 1 {
            eprintln!("[repeat] handled {repeat} events");
        }
        for log in runtime.logs() {
            eprintln!("[kk.log {}] {}", log.level, log.message);
        }
        if let Some(failure) = runtime.last_host_error() {
            eprintln!(
                "[kk.host_call] code={} cap={} {}",
                failure.code, failure.capability, failure.message
            );
        }
        for request in runtime.host().ui_requests() {
            let title = request.title.clone().unwrap_or_default();
            if let Some(tree) = &request.tree {
                eprintln!(
                    "[ui] {} title={:?} nodes={} fields={:?}",
                    request.method,
                    title,
                    request.node_count(),
                    request.field_ids()
                );
                if std::env::var("DOCSCARD_PRINT_TREE").is_ok() {
                    eprintln!("{}", tree);
                }
            } else {
                eprintln!("[ui] {} args={:?}", request.method, request.args);
            }
        }
    }
    Ok(())
}


fn run_package(
    path: &str,
    expected_sha256: Option<&str>,
    event_json: &str,
    verbose: bool,
) -> Result<(), String> {
    let package = CardPackage::open_path(path)
        .map_err(|err| format!("[package:{}] {err}", err.kind()))?;
    if let Some(expected) = expected_sha256 {
        package
            .verify_sha256(expected)
            .map_err(|err| format!("[package:{}] {err}", err.kind()))?;
    }

    let mut runtime = package
        .into_runtime(DefaultHost::new())
        .map_err(|err| format!("[package:{}] {err}", err.kind()))?;
    runtime
        .init(&package.manifest_json)
        .map_err(|err| format!("[{}] {err}", err.kind()))?;
    let result = runtime
        .handle(event_json)
        .map_err(|err| format!("[{}] {err}", err.kind()))?;
    runtime
        .shutdown()
        .map_err(|err| format!("[{}] {err}", err.kind()))?;

    println!("{result}");
    if verbose {
        eprintln!(
            "[package] {} assets extracted, sha256={}",
            package.assets.len(),
            package.archive_sha256
        );
        print_verbose(&runtime);
    }
    Ok(())
}

fn print_verbose<H: docscard_runtime_wasm::CardHost>(runtime: &CardRuntime<H>) {
    for log in runtime.logs() {
        eprintln!("[kk.log {}] {}", log.level, log.message);
    }
    if let Some(failure) = runtime.last_host_error() {
        eprintln!(
            "[kk.host_call] code={} cap={} {}",
            failure.code, failure.capability, failure.message
        );
    }
}

/// `--event` 既接受内联 JSON，也接受 .json 文件路径。
fn load_event(raw: &str) -> Result<String, String> {
    let trimmed = raw.trim();
    if trimmed.starts_with('{') || trimmed.starts_with('[') {
        return Ok(trimmed.to_string());
    }
    match std::fs::read_to_string(trimmed) {
        Ok(text) => Ok(text.trim().to_string()),
        Err(err) => Err(format!(
            "--event is neither inline JSON nor a readable file ({err})"
        )),
    }
}
