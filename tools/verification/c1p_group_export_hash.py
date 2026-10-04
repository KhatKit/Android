#!/usr/bin/env python3
"""C1-09「Tavern/QR 往返」导出哈希证据的**跨 JVM** 校验脚本（零设备）。

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
5. 打印规整表格：`variant  bytes  sha256`。

不做什么
--------
- **不联网、不 clone 上游**：SillyTavern 的结构对照由 Kotlin 测试
  `C1pGroupExportHashTest.the file structure is what sillytavern group chats read and
  private keys stay namespaced` 做，结论基于已核实并登记在
  `docs/beyond-operit-open-source-references.md` 的 `release` @ `06bde939` 源码行为。
  脚本只负责哈希这一层。
- **不复刻导出逻辑**：Python 侧只读测试打印的值 + 重算落盘文件的哈希，不自己拼 JSON。
- 不证明酒馆真机能打开（那要真机/桌面端）、不证明真实 IO 分发、不证明扫码链路。

产物落在 `app/build/c1p-group-export-hash/`（构建目录，不进仓库源码树）。
"""
from __future__ import annotations

import hashlib
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


def main() -> int:
    print("仓库根：%s" % ROOT)
    print("测试类：%s" % TEST_CLASS)
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

    print()
    print("=" * 100)
    for message in notes:
        print("备注：%s" % message)
    if failures:
        print()
        print("失败 %d 项：" % len(failures))
        for message in failures:
            print("  - %s" % message)
        return 1
    print()
    print("结论：%d 个变体在**同一 JVM 内两次调用**与**跨两次独立 JVM 运行**下 SHA-256 全部一致；"
          % len(first))
    print("      落盘文件的 hashlib 复算值与 JVM 打印值全部一致。")
    print("      这份证据**不**证明酒馆真机能打开该文件、**不**证明真实 IO 分发、**不**证明扫码链路。")
    return 0


if __name__ == "__main__":
    sys.exit(main())