#!/usr/bin/env python3
"""C1-09「Tavern/QR 往返」导出哈希证据的**跨 JVM + golden** 校验脚本（零设备）。

背景
----
契约 `docs/beyond-orit-client-changes.md` 要求每个验收用例保存「导出哈希」。
`docs/eval/c1-group-chat.md` 的 C1-09 行因此一直写「无证据」，理由是
「需要真机导出才能算哈希」。那个理由站不住脚：`TavernChatCodec.exportGroupJsonl` /
`TavernChatCodec.exportGroup` / `GroupChat.encodeQr` 都是**纯函数**，输入只有
`List<MessageNode>` + `GroupConfig` + `List<RoleCardMeta>` + 几个字符串，
不碰数据库、不碰 Context、不碰设备。

于是这里连起**两个独立 JVM 运行**（两次 `gradlew :app:testDebugUnitTest --rerun`，
每次都是全新的测试 JVM），把两次各自打印出的哈希拿来比。这一条是单次运行
**测不出来**的，也是最容易含糊过去的一处——所以必须显式跑两遍。

做什么
------
1. 跑第 1 次测试 JVM，抓下 `C1P-HASH` 行；把 XML 结果复制到 `/tmp` 存档。
2. 跑第 2 次测试 JVM，同样抓 `C1P-HASH` 行。
3. 逐个变体比对两次的 SHA-256 与字节数。任何一项不同即**失败**并把两个值原样打印。
4. 用 `hashlib` **独立**再算一遍落在 `app/build/c1p-group-export-hash/` 里的文件，
   与 JVM 里算出来的值对照——这是「打印的哈希确实对应磁盘上那份字节」的交叉验证，
   而不是自己给自己作证。
5. **与 golden 清单严格比对**（见下节「golden 清单」）。
6. 打印规整表格：`variant  bytes  sha256`。

golden 清单（长期护栏）
----------------------
**为什么必须有。** 只比「两次运行之间一致」是个**真缺口**：如果哪天有人悄悄改了
`exportGroupJsonl` 的输出格式——多写一个键、少写一个 `create_date`、换了个换行符、
调了字段顺序——两次运行**照样完全一致**，测试照样全绿，导出文件却已经不兼容了。
「确定性」和「格式没变」是两件完全不同的事，前者不能推出后者。所以期望值必须**写进
仓库**，比对也必须是默认行为。

**放在哪、为什么。** `tools/verification/c1p_group_export_hash.golden.json`，与本脚本
同目录、必须提交：

1. 它唯一的消费者就是本脚本，同目录放让这对文件一起被看到、一起被 review；
   `tools/verification/` 已经是入库的验证工具目录（`c1d_migration_30_31_replay.py`、
   `c1p_migration_31_32_replay.py` 都在这）。
2. **必须入库**——护栏的前提是期望值在仓库里，否则谁都能重算一遍把旧值冲掉。
3. **刻意不放** `app/build/c1p-group-export-hash/`：那是测试产物目录、`clean` 一下就
   没了，而且那个目录的 KDoc 已经写明「绝不落进仓库源码树」。把期望值和实测产物放
   一起，等于让护栏自己给自己作证。

**怎么读。** 严格 `json.loads`，再逐条校 schema：只允许 `_comment` / `variants` /
`qr_payloads` 三个顶层键；每条必须有 `bytes`（非负整数）与 `sha256`（64 位小写
十六进制）。**解析失败或 schema 不符一律硬失败并原样打印错误**，绝不允许「读不出来
就当没有 golden、悄悄只做跨 JVM 比对」——那正好是护栏要防的静默失效。

**默认严格比对。** 任何一个变体的 `bytes` 或 `sha256` 与清单不符即退出码 1，并且
逐个变体打印「变了哪个 / golden 旧值 / 实测新值」。新增或删除变体同样报红
（清单里有但这次没跑到、这次跑到但清单里没有，各算一条失败），否则「顺手加一个
变体」就能绕过比对。

**怎么更新（必须是显式动作）。**

```
python3 tools/verification/c1p_group_export_hash.py --update-golden
git diff tools/verification/c1p_group_export_hash.golden.json   # 必须人工 review
```

流程上仍然要跑完两次独立 JVM：清单只允许从**跨 JVM 已确认一致**且**落盘文件
hashlib 已对上**的运行里写出来，否则会把非确定性一次性固化成「期望值」。所以更新
模式下前 4 项检查一旦失败就直接退出、**拒绝写盘**。更新只改本脚本一个参数触发的
写盘动作，没有任何自动接受路径；CI 里也**不许**跑 `--update-golden`
（`.github/workflows/` 的三个工作流本来就不调这个脚本，它至今是纯手工命令）。

**改了导出格式的人请照这个顺序做**：先跑一次默认模式**看它报红并确认报红的变体正是
你改到的那些**，再 `--update-golden`，再 `git diff` 那个 golden 文件——每一条变化都
该能对上「我这次改的是哪个键 / 哪处顺序」，对不上就别提交。golden 文件里也写了同样
的一段话，就地提醒。

不做什么
--------
- **不联网、不 clone 上游**：SillyTavern 的结构对照由 Kotlin 测试
  `C1pGroupExportHashTest.the file structure is what sillytavern group chats read and
  private keys stay namespaced` 做，结论基于已核实并登记在
  `docs/beyond-orit-open-source-references.md` 的 `release` @ `06bde939` 源码行为。
  脚本只负责哈希这一层。
- **不复刻导出逻辑**：Python 侧只读测试打印的值 + 重算落盘文件的哈希，不自己拼 JSON。
- 不证明酒馆真机能打开该文件（那要真机/桌面端）、不证明真实 IO 分发、不证明扫码链路。
- **不把「golden 全绿」读成「C1-09 通过」**：契约 `:206` 点名的四类证据里，
  「酒馆本体打开」、viewer 可见消息 ID、模型调用序列、token 计数仍然零份，
  导出文件本身也没经过真实 IO 分发。`docs/eval/c1-group-chat.md` 的 C1-09 状态列
  因此**必须仍是 `unverified`**。

产物落在 `app/build/c1p-group-export-hash/`（构建目录，不进仓库源码树）；
golden 清单落在 `tools/verification/`（**入库**，是护栏本身）。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
TEST_CLASS = "heizige.kk.khatkit.app.core.data.ai.tavern.C1pGroupExportHashTest"
ARTIFACT_DIR = ROOT / "app/build/c1p-group-export-hash"
RESULT_XML = ROOT / ("app/build/test-results/testDebugUnitTest/TEST-%s.xml" % TEST_CLASS)
ARCHIVE_DIR = pathlib.Path("/tmp/opencode/c1p-group-export-hash")

# golden 清单：与本脚本同目录、**必须入库**。理由见模块 docstring 的「golden 清单」。
GOLDEN_REL = "tools/verification/c1p_group_export_hash.golden.json"
GOLDEN_PATH = ROOT / GOLDEN_REL
GOLDEN_TOP_KEYS = ("_comment", "variants", "qr_payloads")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

# golden 文件里的 `_comment`：更新流程就地写在那里，改护栏的人不用去翻脚本。
# `--update-golden` 写盘时会原样带上这段，所以它是**唯一**的真相来源，不会两处漂移。
GOLDEN_COMMENT = [
    "C1-09「Tavern/QR 往返」导出字节的 golden 清单——长期护栏，不是随手快照。",
    "由 `python3 tools/verification/c1p_group_export_hash.py` 默认（严格）比对；",
    "不一致即报红并逐个变体打印「旧值 / 新值」。",
    "【改了导出格式怎么办】先确认是有意的格式变更，再显式跑",
    "  python3 tools/verification/c1p_group_export_hash.py --update-golden",
    "然后 **人工 review `git diff tools/verification/c1p_group_export_hash.golden.json`**：",
    "每一行都该能对上「我这次到底改了什么键/什么顺序」。review 不通过就不要提交。",
    "更新动作绝不会自动发生：脚本默认严格比对，只有显式传 --update-golden 才写盘，",
    "且跨两次独立 JVM 不一致时拒绝写入。",
    "variants 来自 C1pGroupExportHashTest 的 9 个 fixture 变体（exportGroupJsonl /",
    "exportGroup / encodeQr 三条出口，含 2 个 QR 载荷变体）；qr_payloads 来自同类的",
    "qr payload sha256 用例，按 mode 命名（pipeline / vote）。",
    "两组刻意在 QR 载荷上重叠：qr_payloads.pipeline 与 variants.qr_payload_pipeline",
    "是同一份字节（encodeQr(config, cards)），分开登记是因为它们由两条独立用例、",
    "两条独立的 println 打出来，任何一边漂移都要看得见。",
]

# `--rerun` 只挂紧邻其前的那个 task，所以 task 名必须是最后那个。
GRADLE_TASK = ":app:testDebugUnitTest"
GRADLE_TIMEOUT = 3600
LOCK_RETRY_SECONDS = 150
LOCK_RETRIES = 3

# 撞锁的典型特征：Gradle 抢不到文件锁 / daemon 被别的构建占着。
LOCK_PATTERN = re.compile(
    r"(Timeout waiting to lock|waiting to lock|File lock request timed out"
    r"|Could not (acquire|obtain) (the )?lock|daemon disappeared|Timeout waiting"
    r"|Unable to (acquire|obtain))",
    re.I,
)

HASH_LINE = re.compile(r"^C1P-HASH\s+(\S+)\s+(\d+)\s+([0-9a-f]{64})\s*$")
QR_LINE = re.compile(r"^C1P-QR\s+(\S+)\s+(\d+)\s+([0-9a-f]{64})\s*$")

failures: list[str] = []
notes: list[str] = []


def check(ok: bool, message: str) -> bool:
    if not ok:
        failures.append(message)
    return ok


def note(message: str) -> None:
    notes.append(message)


def run_gradle_once(run_label: str) -> bool:
    """跑一次独立的测试 JVM。撞锁按 150 秒重试，最多 LOCK_RETRIES 次。"""
    cmd = [
        "./gradlew", "--offline", GRADLE_TASK, "--rerun",
        "--tests", TEST_CLASS,
    ]
    for attempt in range(1, LOCK_RETRIES + 1):
        print("[%s] 尝试 %d/%d：%s" % (run_label, attempt, LOCK_RETRIES, " ".join(cmd)), flush=True)
        proc = subprocess.run(
            cmd, cwd=ROOT, capture_output=True, text=True, timeout=GRADLE_TIMEOUT,
        )
        out = (proc.stdout or "") + (proc.stderr or "")
        if proc.returncode == 0:
            print("[%s] 退出码 0" % run_label, flush=True)
            return True
        if LOCK_PATTERN.search(out) and attempt < LOCK_RETRIES:
            note("[%s] 第 %d 次撞上 Gradle 文件锁，%d 秒后重试" % (run_label, attempt, LOCK_RETRY_SECONDS))
            time.sleep(LOCK_RETRY_SECONDS)
            continue
        print(out[-6000:], file=sys.stderr)
        check(False, "[%s] gradle 退出码 %d（最后 %d 行见 stderr）" % (run_label, proc.returncode, 0))
        return False
    return False


def parse_hashes(xml: pathlib.Path) -> tuple[dict[str, tuple[int, str]], dict[str, tuple[int, str]]]:
    """从 JUnit XML 的 system-out 里抠出测试打印的 `C1P-HASH` / `C1P-QR` 行。

    刻意不解析 XML 结构去反推哈希——那些行是测试自己 println 出来的，
    读的就是**它打印的那个字符串**。
    """
    if not xml.exists():
        check(False, "找不到测试结果 XML：%s" % xml)
        return {}, {}
    text = xml.read_text(encoding="utf-8", errors="replace")
    match = re.search(r"<system-out>(.*?)</system-out>", text, re.S)
    if match is None:
        check(False, "测试结果 XML 里没有 system-out，拿不到打印的哈希：%s" % xml)
        return {}, {}
    variants: dict[str, tuple[int, str]] = {}
    qr: dict[str, tuple[int, str]] = {}
    for raw in match.group(1).splitlines():
        line = raw.strip()
        hit = HASH_LINE.match(line)
        if hit:
            variants[hit.group(1)] = (int(hit.group(2)), hit.group(3))
            continue
        hit = QR_LINE.match(line)
        if hit:
            qr[hit.group(1)] = (int(hit.group(2)), hit.group(3))
    return variants, qr


def sha256_of(path: pathlib.Path) -> tuple[int, str]:
    data = path.read_bytes()
    return len(data), hashlib.sha256(data).hexdigest()


# ---------------- golden 清单 ----------------


def load_golden(path: pathlib.Path) -> dict[str, dict[str, tuple[int, str]]] | None:
    """读并严格校验 golden 清单。

    返回 `{"variants": {...}, "qr_payloads": {...}}`，其中每个值是 `(bytes, sha256)`。
    任何解析或 schema 问题都先记成失败再返回 `None`——**绝不**降级成「没有 golden、
    只做跨 JVM 比对」，那个静默失效正是这道护栏要防的事。
    """
    if not path.is_file():
        check(False, "找不到 golden 清单：%s" % path)
        return None
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        check(False, "golden 清单不是合法 JSON：%s（%s）" % (path, error))
        return None
    if not isinstance(raw, dict):
        check(False, "golden 清单顶层必须是 JSON 对象，实际是 %s" % type(raw).__name__)
        return None

    unknown = [key for key in raw if key not in GOLDEN_TOP_KEYS]
    if unknown:
        check(False, "golden 清单有未定义的顶层键 %s（只允许 %s）" % (sorted(unknown), list(GOLDEN_TOP_KEYS)))
        return None

    parsed: dict[str, dict[str, tuple[int, str]]] = {}
    ok = True
    for section in ("variants", "qr_payloads"):
        if section not in raw:
            check(False, "golden 清单缺少顶层键 `%s`" % section)
            ok = False
            continue
        entries = raw[section]
        if not isinstance(entries, dict) or not entries:
            check(False, "golden 清单的 `%s` 必须是非空 JSON 对象" % section)
            ok = False
            continue
        bucket: dict[str, tuple[int, str]] = {}
        for name, value in entries.items():
            if not isinstance(value, dict) or set(value) != {"bytes", "sha256"}:
                check(False, "golden %s.%s 的字段必须恰好是 {bytes, sha256}，实际是 %s"
                      % (section, name, sorted(value) if isinstance(value, dict) else type(value).__name__))
                ok = False
                continue
            size, digest = value["bytes"], value["sha256"]
            # bool 是 int 的子类，显式排除，否则 True 会被当成 1 字节通过。
            if isinstance(size, bool) or not isinstance(size, int) or size < 0:
                check(False, "golden %s.%s 的 bytes 必须是非负整数，实际是 %r" % (section, name, size))
                ok = False
                continue
            if not isinstance(digest, str) or SHA256_RE.match(digest) is None:
                check(False, "golden %s.%s 的 sha256 必须是 64 位小写十六进制，实际是 %r"
                      % (section, name, digest))
                ok = False
                continue
            bucket[name] = (size, digest)
        parsed[section] = bucket
    return parsed if ok else None


def describe(value: tuple[int, str]) -> str:
    return "bytes=%d sha256=%s" % value


def compare_golden(
    label: str,
    golden: dict[str, tuple[int, str]],
    observed: dict[str, tuple[int, str]],
    where: str,
) -> None:
    """逐变体比对实测值与 golden 值，任何一项不符即记一条失败（含旧值与新值）。"""
    # 变体集合先比：只改「加了一个变体 / 删了一个变体」也必须报红。
    for name in sorted(set(golden) - set(observed)):
        check(False, "golden 里有变体但这次没跑到：%s.%s = %s" % (where, name, describe(golden[name])))
    for name in sorted(set(observed) - set(golden)):
        check(False, "这次跑到变体但 golden 里没有：%s.%s = %s" % (where, name, describe(observed[name])))
    for name in sorted(set(golden) & set(observed)):
        want, got = golden[name], observed[name]
        if want == got:
            continue
        which = []
        if want[0] != got[0]:
            which.append("字节数 %d -> %d" % (want[0], got[0]))
        if want[1] != got[1]:
            which.append("SHA-256 %s -> %s" % (want[1], got[1]))
        check(
            False,
            "导出字节变了 %s.%s：%s（golden 旧值 %s / 实测新值 %s）"
            % (where, name, "，".join(which), describe(want), describe(got)),
        )
    note("[%s] golden %s 比对完成：%d 个变体" % (label, where, len(observed)))


def golden_payload(
    variants: dict[str, tuple[int, str]],
    qr: dict[str, tuple[int, str]],
) -> dict[str, object]:
    """把实测值序列化成 golden 文件的 JSON 结构（键排序，读写无关）。"""

    def section(source: dict[str, tuple[int, str]]) -> dict[str, dict[str, object]]:
        return {name: {"bytes": value[0], "sha256": value[1]} for name, value in sorted(source.items())}

    return {
        "_comment": GOLDEN_COMMENT,
        "variants": section(variants),
        "qr_payloads": section(qr),
    }


def write_golden(
    path: pathlib.Path,
    previous: dict[str, dict[str, tuple[int, str]]] | None,
    variants: dict[str, tuple[int, str]],
    qr: dict[str, tuple[int, str]],
) -> None:
    """写出新的 golden，并把每处变化打印成可 review 的「旧值 -> 新值」清单。"""
    print()
    print("-" * 100)
    print("--update-golden：本次不比对旧值，改为写出新清单（**必须人工 review git diff**）")
    print("-" * 100)
    if previous is None:
        print("golden 清单此前不存在或不可解析：这是**首次生成**，请当作新文件 review。")
    else:
        for where, old, new in (
            ("variants", previous.get("variants", {}), variants),
            ("qr_payloads", previous.get("qr_payloads", {}), qr),
        ):
            for name in sorted(set(old) - set(new)):
                print("  %-46s 被删除（原 %s）" % ("%s.%s" % (where, name), describe(old[name])))
            for name in sorted(set(new) - set(old)):
                print("  %-46s 新增（%s）" % ("%s.%s" % (where, name), describe(new[name])))
            for name in sorted(set(old) & set(new)):
                if old[name] == new[name]:
                    continue
                print("  %-46s 变了：%s  ->  %s" % ("%s.%s" % (where, name), describe(old[name]), describe(new[name])))
            unchanged = sum(1 for name in set(old) & set(new) if old[name] == new[name])
            print("  %-46s 未变 %d 项" % (where, unchanged))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(golden_payload(variants, qr), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print()
    print("已写入 %s（%d 字节）。下一步必须做的事：" % (path, path.stat().st_size))
    print("  1) git diff %s" % GOLDEN_REL)
    print("  2) 逐行确认每处变化都对应你这次有意改的键 / 顺序；对不上就别提交。")
    print("  3) 跑一次默认模式（不加 --update-golden）确认全绿。")
    print("  ⚠️ 不要在 CI 里跑这个参数：更新必须是一次显式、人工 review 的动作。")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="c1p_group_export_hash.py",
        description=(
            "C1-09 群聊导出字节的确定性 + golden 校验（零设备）。"
            "默认严格比对 golden 清单；--update-golden 改为写出新清单（显式动作，须人工 review）。"
        ),
    )
    parser.add_argument(
        "--update-golden",
        action="store_true",
        help="把这次实测值写进 golden 清单（跨两次 JVM 不一致时拒绝写）。只在有意改导出格式时用。",
    )
    parser.add_argument(
        "--golden",
        default=None,
        help="golden 清单路径覆盖（默认 %s）。自测/影子仓库比对时用。" % GOLDEN_REL,
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    golden_path = pathlib.Path(args.golden).resolve() if args.golden else GOLDEN_PATH
    print("仓库根：%s" % ROOT)
    print("测试类：%s" % TEST_CLASS)
    print("golden 清单：%s" % golden_path)
    print("模式：%s" % ("--update-golden（写盘，须人工 review diff）" if args.update_golden else "严格比对（默认）"))
    print()

    runs: list[tuple[str, dict[str, tuple[int, str]], dict[str, tuple[int, str]]]] = []
    for index in (1, 2):
        label = "run%d" % index
        if not run_gradle_once(label):
            return 1
        variants, qr = parse_hashes(RESULT_XML)
        if not check(bool(variants), "[%s] 没抓到任何 C1P-HASH 行" % label):
            return 1
        ARCHIVE_DIR.mkdir(parents=True, exist_ok=True)
        archive = ARCHIVE_DIR / ("%s-%s.xml" % (label, RESULT_XML.name))
        shutil.copy2(RESULT_XML, archive)
        note("[%s] 结果 XML 存档：%s" % (label, archive))
        runs.append((label, variants, qr))

    (label1, first, qr1), (label2, second, qr2) = runs

    # ---- 变体集合一致 ----
    check(
        set(first) == set(second),
        "两次运行的变体集合不同：%s vs %s" % (sorted(first), sorted(second)),
    )

    print()
    print("=" * 100)
    print("C1-09 群聊导出哈希（两次独立 JVM 运行）")
    print("=" * 100)
    print("%-46s %8s  %s" % ("variant", "bytes", "sha256"))
    print("-" * 100)

    for name in sorted(first):
        bytes1, sha1 = first[name]
        bytes2, sha2 = second.get(name, (-1, "<缺失>"))
        same = bytes1 == bytes2 and sha1 == sha2
        check(same, "跨 JVM 不一致 %s：run1 %d %s / run2 %d %s" % (name, bytes1, sha1, bytes2, sha2))
        print(
            "%-46s %8s  %s  %s"
            % (name, bytes1 if same else "%d/%d" % (bytes1, bytes2), sha1, "一致" if same else "**不一致**")
        )

    print()
    print("-" * 100)
    print("QR 载荷（GroupChat.encodeQr，同样两次独立 JVM）")
    print("-" * 100)
    for name in sorted(qr1):
        bytes1, sha1 = qr1[name]
        bytes2, sha2 = qr2.get(name, (-1, "<缺失>"))
        same = bytes1 == bytes2 and sha1 == sha2
        check(same, "QR 跨 JVM 不一致 %s：run1 %d %s / run2 %d %s" % (name, bytes1, sha1, bytes2, sha2))
        print(
            "%-46s %8s  %s  %s"
            % (name, bytes1 if same else "%d/%d" % (bytes1, bytes2), sha1, "一致" if same else "**不一致**")
        )

    # ---- 交叉验证：JVM 里算的哈希 == Python 独立算的落盘文件哈希 ----
    print()
    print("-" * 100)
    print("交叉验证：Python 用 hashlib 独立重算 app/build/c1p-group-export-hash/ 里的落盘文件")
    print("-" * 100)
    if not check(ARTIFACT_DIR.is_dir(), "落盘目录不存在：%s" % ARTIFACT_DIR):
        return 1
    for name in sorted(first):
        artifact = ARTIFACT_DIR / ("%s.txt" % name)
        if not check(artifact.is_file(), "落盘文件缺失：%s" % artifact):
            continue
        size, digest = sha256_of(artifact)
        reported_bytes, reported_sha = first[name]
        ok = size == reported_bytes and digest == reported_sha
        check(
            ok,
            "落盘文件与 JVM 打印值不一致 %s：磁盘 %d %s / JVM %d %s"
            % (name, size, digest, reported_bytes, reported_sha),
        )
        print(
            "%-46s %8d  %s  %s"
            % (name, size, digest, "与 JVM 一致" if ok else "**与 JVM 不一致**")
        )

    # ---- golden 清单比对（或写盘）。放在最后：前 4 项先过，才有资格动 golden ----
    golden = load_golden(golden_path)
    if args.update_golden:
        # 只允许从「跨 JVM 一致 + 落盘文件已对上」的运行里写：否则会把非确定性
        # 一次性固化成期望值，那正好抵消这道护栏的意义。
        if failures:
            print()
            print("=" * 100)
            print("--update-golden 拒绝写盘：本次运行本身有 %d 项失败（跨 JVM 不一致或落盘对不上）。" % len(failures))
            for message in failures:
                print("  - %s" % message)
            print("先修好上面这些再谈更新 golden。")
            return 1
        write_golden(golden_path, golden, first, qr1)
        print()
        print("=" * 100)
        for message in notes:
            print("备注：%s" % message)
        print()
        print("结论：已按本次实测重写 golden 清单（跨两次 JVM 一致、落盘文件已交叉复算）。")
        print("      **必须先 review `git diff %s` 再提交**；这不是一次验收通过。" % GOLDEN_REL)
        return 0

    if golden is None:
        print()
        print("=" * 100)
        for message in notes:
            print("备注：%s" % message)
        print()
        print("失败 %d 项（golden 清单缺失或不可解析，本轮的确定性证据无法升级为格式护栏）：" % len(failures))
        for message in failures:
            print("  - %s" % message)
        print()
        print("要首次生成清单请显式跑：python3 tools/verification/c1p_group_export_hash.py --update-golden")
        return 1

    print()
    print("-" * 100)
    print("golden 清单比对（variants：exportGroupJsonl / exportGroup / encodeQr 的 9 个变体）")
    print("-" * 100)
    compare_golden(label1, golden["variants"], first, "variants")
    for name in sorted(first):
        want = golden["variants"].get(name)
        got = first[name]
        print(
            "%-46s %s  %s"
            % (name, "一致" if want == got else "**变了**", describe(got))
        )
    print()
    print("-" * 100)
    print("golden 清单比对（qr_payloads：encodeQr 载荷，按 mode）")
    print("-" * 100)
    compare_golden(label1, golden["qr_payloads"], qr1, "qr_payloads")
    for name in sorted(qr1):
        want = golden["qr_payloads"].get(name)
        got = qr1[name]
        print(
            "%-46s %s  %s"
            % (name, "一致" if want == got else "**变了**", describe(got))
        )

    print()
    print("=" * 100)
    for message in notes:
        print("备注：%s" % message)
    if failures:
        print()
        print("失败 %d 项：" % len(failures))
        for message in failures:
            print("  - %s" % message)
        print()
        print("⚠️ 导出字节与 golden 清单不符。若这是**有意**的导出格式变更：")
        print("   1) 确认报红的变体正好是你改到的那些；")
        print("   2) python3 tools/verification/c1p_group_export_hash.py --update-golden")
        print("   3) 人工 review `git diff %s`；对不上就别提交。" % GOLDEN_REL)
        print("   若这是**无意**的漂移：改回生产代码，别动清单。")
        return 1
    print()
    print("结论：%d 个变体 + %d 个 QR 载荷在**同一 JVM 内两次调用**、**跨两次独立 JVM 运行**"
          % (len(first), len(qr1)))
    print("      与 **golden 清单**下 SHA-256 / 字节数全部一致；落盘文件的 hashlib 复算值")
    print("      与 JVM 打印值全部一致。")
    print("      这份证据**不**证明酒馆真机能打开该文件、**不**证明真实 IO 分发、**不**证明扫码链路，")
    print("      也**不**使 `docs/eval/c1-group-chat.md` 的 C1-09 通过——契约 `:206` 点名的")
    print("      「酒馆本体打开」、viewer 可见消息 ID、模型调用序列、token 计数仍零份。")
    return 0


if __name__ == "__main__":
    sys.exit(main())