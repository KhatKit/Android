#!/usr/bin/env python3
"""C1 文档统计口径的**可复算**脚本：把 `docs/eval/c1-group-chat.md` 里那批手抄数字
逐个重算出来，并**交叉验证**。零设备、零 gradle、只读。

为什么需要这个脚本
------------------
`docs/eval/c1-group-chat.md` 里有五组统计数字：`:app:testDebugUnitTest` 的类数/例数、
「C1 相关 JVM 测试类台账」的类数/例数、lint 的 error/warning/hint、仪器 `@Test` 总数、
以及台账锚点区间的 commit 数。**它们全都是人手抄进文档的**，而这条流水线已经因为
抄错而返工过至少五次（97/758 与 98/771 错位一档、`GroupTurnCoordinatorTest` +5 与 +6、
`PrimaryBottomSheet` 62 与 64、androidTest 61 与 64）。本脚本把这五组数字各自的
**独立来源**固化下来，让「文档里的数」与「实际来源算出的数」可以逐项对账。

⚠️ **本脚本取代 `docs/eval/c1-group-chat.md` 里那条旧的「复算脚本」，而旧脚本已登记为
不可信**（见该文档「C1 相关 JVM 测试类台账」小节）。旧脚本的致命缺陷不是算错，而是
**会骗人**：

1. 它用 `if b:` / `elif re.fullmatch(r'\\d+', c[1])` 两条互斥分支抓例数。表里凡是用
   第三种写法（既非纯数字、也非纯数字粗体）的行会被**整行静默跳过**，实测只剩
   `rows 14 / declared sum 172`。而它的输出里 `mismatch vs XML: []` **看起来仍然像
   「已核对过」**——一个只报 14 行的核对脚本，会给人「核对通过」的错觉。
2. 它**没有任何「反空跑」自检**。如果表里的行数掉到 0，它会打印
   `rows 0 / declared sum 0 / mismatch vs XML: []`——**这与「40 行全部核对通过」
   在字面上完全无法区分**。

本脚本针对第 2 条做了硬约束，见下节「反空跑自检」。

五个口径与它们的独立来源
------------------------
| # | 数字 | 独立来源 | 备注 |
|---|---|---|---|
| 1 | app JVM 单测 类数/例数/failures/errors/skipped | `app/build/test-results/testDebugUnitTest/TEST-*.xml` 的 `testsuite` 属性 | ⚠️ 该目录**会被过滤跑覆盖**，见「陈旧 XML」一节 |
| 2 | app JVM 单测 类数/例数（第二来源） | `app/src/test/**/*.kt` 里 `@Test` 出现次数 | 与 ① 互为交叉验证；**不需要跑 gradle** |
| 3 | C1 台账 类数/例数 | 本文「C1 相关 JVM 测试类台账」表格 × **②的逐类计数** | 表格是人工维护的，数值由独立来源裁判 |
| 4 | lint error/warning/hint | `*/build/reports/lint-results-debug.xml` 的 `severity` 属性 | ⚠️ **不能读 SARIF 的 `level`**：实测 590 条里 390 条 `level` 是空的 |
| 5 | 仪器 `@Test` 总数 | `app/src/androidTest/**/*.kt`（区分 tracked / 含 untracked） | |
| 6 | 台账锚点区间 commit 数 | `git log --oneline d45ebd10~1..1b0e04a9 \\| wc -l` | **只读打印，永不写回文档** |

⚠️ **口径 ④ 为什么必须读 XML 而不是 SARIF**：SARIF 的 `level` 字段在本仓库**大面积为空**
（app 模块 590 条结果里 390 条 `level` 缺失），按 `level` 统计会把 390 条算成「未知」。
XML 的 `severity` 属性是完整的，且两边的**总数一致**（590 = 590），所以总数可以交叉
验证、严重度分布只能信 XML。本脚本两个都读，并断言总数相等。

反空跑自检（本脚本的核心防线）
------------------------------
「反空跑」= 脚本扫出 0 行 / 0 个文件 / 0 条结果时，**必须报红并以非零码退出**，
绝不允许静默输出「通过」。这不是假想需求：本流水线在源码护栏上**连续踩过两次**
类似的坑——① 一处 `content` 尾随 lambda 的大括号写在了右括号**外**，导致整段源码
扫描被静默跳过；② 一条负向后行断言漏掉了全限定名，同样静默跳过。两次的共同特征都是
**「扫到 0 条」被当成了「没问题」**。所以本脚本的每一个口径都满足：

1. **非零下限**：扫到 0 个文件 / 0 行 / 0 条 issue 一律 `FAIL`。
2. **可疑下限**：扫到的东西**非零但明显偏少**也 `FAIL`，而不只拦 0。实测就撞上过：
   `app/build/test-results/testDebugUnitTest/` 里只剩 **1 个** XML（因为最后一次跑
   是带 `--tests` 过滤的），总量非零却是彻底误导的——只报「1 类 / 5 例」而不报红，
   就是下一个会骗人的工具。
3. **交叉验证**：口径 ① 与 ② 互校、④ 的 XML 与 SARIF 互校。两者不一致即 `FAIL`，
   因为「不一致」意味着至少有一个来源已经陈旧。
4. **`--self-test`**：把每个探测器喂上**人造的坏输入**（空目录、只含 1 个 XML 的目录、
   0 行的表、带裸 `|` 的表），断言它们**确实报红**。这让「探测器是活的」成为可验证的
   事实，而不是声明。`--self-test` 自身也参与退出码。

用法
----
```
python3 tools/verification/c1_doc_stats.py                 # 全部口径
python3 tools/verification/c1_doc_stats.py --only unit     # 只算单测口径
python3 tools/verification/c1_doc_stats.py --self-test     # 只跑探测器自检
python3 tools/verification/c1_doc_stats.py --check-doc     # 额外把文档里写的数与实算对账
```

退出码：0 = 全部口径通过；1 = 有 `FAIL`（含自检失败）；2 = 参数错误。
"""

from __future__ import annotations

import argparse
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

DOC_REL = "docs/eval/c1-group-chat.md"
LEDGER_HEADING = "### C1 相关 JVM 测试类台账"
ANCHOR_RANGE = "d45ebd10~1..1b0e04a9"
ANCHOR_SHA = "1b0e04a9"

OK, WARN, FAIL = "OK", "WARN", "FAIL"


@dataclass
class Check:
    """一条口径的结果。`status` 为 `FAIL` 时脚本退出码非零。"""

    group: str
    name: str
    source: str
    value: str
    status: str = OK
    note: str = ""


@dataclass
class Report:
    checks: list[Check] = field(default_factory=list)

    def add(self, group: str, name: str, source: str, value: str,
            status: str = OK, note: str = "") -> Check:
        c = Check(group, name, source, value, status, note)
        self.checks.append(c)
        return c

    @property
    def failed(self) -> list[Check]:
        return [c for c in self.checks if c.status == FAIL]

    @property
    def warned(self) -> list[Check]:
        return [c for c in self.checks if c.status == WARN]


def repo_root() -> Path:
    """脚本在 `tools/verification/`，仓库根是上溯三层。"""
    return Path(__file__).resolve().parents[2]


# --------------------------------------------------------------------------
# GFM 表格切分：`|` 是分隔符，`\|` 是字面量竖线（**代码段内也必须转义**）。
# --------------------------------------------------------------------------
def split_row(line: str) -> list[str]:
    """按 GFM 表格规则切一行。`\\|` 被还原成字面量 `|`。

    ⚠️ 用朴素的 `line.split('|')` 会把 `\\|` 也切开，于是「已修好」的表格依旧被数成
    多列——这正是 C1-05 那一行当初骗过状态列审计的机制。
    """
    s = line.strip()
    if not s.startswith("|"):
        raise ValueError(f"不是表格行: {line[:40]!r}")
    s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    out: list[str] = []
    cur = ""
    i = 0
    while i < len(s):
        ch = s[i]
        if ch == "\\" and i + 1 < len(s) and s[i + 1] == "|":
            cur += "|"
            i += 2
            continue
        if ch == "|":
            out.append(cur)
            cur = ""
            i += 1
            continue
        cur += ch
        i += 1
    out.append(cur)
    return out


def is_table_row(line: str) -> bool:
    s = line.strip()
    return len(s) > 1 and s.startswith("|") and s.endswith("|")


def audit_tables(text: str, label: str) -> tuple[int, int, list[str]]:
    """扫全文所有表格块，返回 (块数, 列数不符的行数, 人类可读的问题列表)。

    每一块以**第一行**为表头基准；分隔行不参与比对但也必须列数相同。
    """
    lines = text.split("\n")
    blocks: list[tuple[int, int]] = []
    start = None
    for idx, line in enumerate(lines, 1):
        if is_table_row(line):
            if start is None:
                start = idx
        else:
            if start is not None:
                blocks.append((start, idx - 1))
                start = None
    if start is not None:
        blocks.append((start, len(lines)))

    problems: list[str] = []
    checked_rows = 0
    for begin, end in blocks:
        header_n = len(split_row(lines[begin - 1]))
        for ln in range(begin, end + 1):
            n = len(split_row(lines[ln - 1]))
            checked_rows += 1
            if n != header_n:
                problems.append(
                    f"{label}:{ln} 列数 {n} != 表头 {header_n}（块 {begin}-{end}）"
                )
    return len(blocks), checked_rows, problems


def parse_ledger(text: str) -> tuple[list[tuple[str, int]], str]:
    """解析「C1 相关 JVM 测试类台账」表。返回 (行列表, 出错原因)。

    ⚠️ **与文档里那条旧脚本的差别**：旧脚本用两条互斥正则抓例数，抓不到的行
    **静默跳过**；这里抓不到就**报错**（`errors` 非空），由调用方决定是否 `FAIL`。
    """
    lines = text.split("\n")
    try:
        begin = next(i for i, l in enumerate(lines) if l.startswith(LEDGER_HEADING))
    except StopIteration:
        return [], f"找不到标题 {LEDGER_HEADING!r}"

    rows: list[tuple[str, int]] = []
    errors: list[str] = []
    seen: set[str] = set()
    for offset, line in enumerate(lines[begin + 1:], start=begin + 2):
        if line.startswith("### "):
            break
        if not is_table_row(line):
            continue
        cells = split_row(line)
        if len(cells) < 2:
            errors.append(f"{LEDGER_HEADING} 附近第 {offset} 行列数 {len(cells)} 过少")
            continue
        m = re.fullmatch(r"\*{0,2}`([A-Za-z0-9_]+)`\*{0,2}", cells[0].strip())
        if not m or m.group(1) == "测试类":
            continue
        cls = m.group(1)
        if cls in seen:
            errors.append(f"{LEDGER_HEADING}: 类 {cls} 重复出现")
        seen.add(cls)
        # 例数列：兼容「裸数字」与「**数字**<br>**数字**（注）」两种在用写法。
        bold = re.findall(r"\*\*(\d+)\*\*", cells[1])
        if bold:
            rows.append((cls, int(bold[-1])))
        elif re.fullmatch(r"\d+", cells[1].strip()):
            rows.append((cls, int(cells[1].strip())))
        else:
            errors.append(
                f"{LEDGER_HEADING}: 类 {cls} 的例数格无法解析: {cells[1][:60]!r}"
            )
    return rows, "; ".join(errors)


def count_test_annotations(files: list[str]) -> tuple[int, int]:
    """返回 (文件数, `@Test` 出现次数)。⚠️ 数的是**出现次数**，不是行数——
    一行里写两个 `@Test` 也要算两条。"""
    n_files = 0
    n_ann = 0
    for path in files:
        n_files += 1
        with open(path, encoding="utf-8") as fh:
            n_ann += fh.read().count("@Test")
    return n_files, n_ann


def git_lines(root: Path, *args: str) -> tuple[int, str]:
    """跑一条 git 命令，返回 (输出行数, 原始输出)。失败时行数为 -1。"""
    proc = subprocess.run(
        ["git", *args], cwd=root, capture_output=True, text=True, check=False
    )
    if proc.returncode != 0:
        return -1, proc.stderr.strip()
    lines = [l for l in proc.stdout.split("\n") if l.strip()]
    return len(lines), proc.stdout


# --------------------------------------------------------------------------
# 口径 1 + 2：app JVM 单测
# --------------------------------------------------------------------------
def section_unit(root: Path, rep: Report) -> None:
    xml_glob = "app/build/test-results/testDebugUnitTest/TEST-*.xml"
    paths = sorted(glob.glob(str(root / xml_glob)))
    xml_classes = 0

    # ---- 口径 1：XML ----
    if not paths:
        rep.add("unit", "单测 XML 结果", xml_glob, "0 个文件", FAIL,
                "目录为空或从未跑过——这是「反空跑」必须拦下的情形")
    else:
        xml_classes = len(paths)
        tests = failures = errors = skipped = 0
        broken: list[str] = []
        for p in paths:
            try:
                suite = ET.parse(p).getroot()
            except ET.ParseError as exc:
                broken.append(f"{os.path.basename(p)}: {exc}")
                continue
            tests += int(suite.get("tests") or 0)
            failures += int(suite.get("failures") or 0)
            errors += int(suite.get("errors") or 0)
            skipped += int(suite.get("skipped") or 0)
        if broken:
            rep.add("unit", "单测 XML 解析", xml_glob, f"{len(broken)} 个文件损坏", FAIL,
                    broken[0])
        else:
            # ⚠️ 非零但偏少也要拦：实测该目录只剩 1 个 XML（最后一次是过滤跑）。
            stale = xml_classes < 20
            rep.add("unit", "单测 XML 类数/例数", xml_glob,
                    f"{xml_classes} 类 / {tests} 例", FAIL if stale else OK,
                    "⚠️ 少于 20 个 XML，**几乎可以断定是 --tests 过滤跑留下的子集**，"
                    "不可当作全量套件数字引用；请用口径 2 或重跑 "
                    "`./gradlew --offline :app:testDebugUnitTest --rerun`"
                    if stale else "")
            rep.add("unit", "单测 XML failures/errors/skipped", xml_glob,
                    f"{failures} / {errors} / {skipped}",
                    FAIL if (failures or errors) else OK,
                    "XML 报告里存在失败/错误" if (failures or errors) else "")

    # ---- 口径 2：源码 @Test（不需要 gradle，永远可算）----
    src_glob = str(root / "app/src/test/**/*.kt")
    src_files = sorted(glob.glob(src_glob, recursive=True))
    src_classes, src_cases = count_test_annotations(src_files)
    if src_classes == 0:
        rep.add("unit", "单测源码 @Test（类）", "app/src/test/**/*.kt", "0 个文件", FAIL,
                "反空跑：源码树扫不到任何文件")
    else:
        rep.add("unit", "单测源码 @Test（类）", "app/src/test/**/*.kt",
                f"{src_classes} 个含 @Test 的文件", OK)
        rep.add("unit", "单测源码 @Test（例）", "app/src/test/**/*.kt",
                f"{src_cases} 例", OK,
                "口径：数 `@Test` 出现次数；含 @Ignore/参数化，故与 XML 可能差几条")

    # ---- 交叉验证 ① × ② ----
    # ⚠️ 两个来源都在、且 XML 不是被过滤过的子集时，才有意义地比较。
    if xml_classes >= 20 and src_classes:
        diff = abs(src_classes - xml_classes)
        tol = max(5, int(src_classes * 0.05))
        rep.add("unit", "交叉验证 XML×源码（类数）", "①②",
                f"XML {xml_classes} 类 / 源码 {src_classes} 类（差 {diff}，容差 {tol}）",
                FAIL if diff > tol else OK,
                "两个来源差超过容差，至少有一个已陈旧" if diff > tol else "一致")
    elif xml_classes:
        rep.add("unit", "交叉验证 XML×源码（类数）", "①②",
                f"跳过：XML 只有 {xml_classes} 个文件（已判陈旧）", WARN,
                "要真正交叉验证请重跑 `:app:testDebugUnitTest --rerun`")
    else:
        rep.add("unit", "交叉验证 XML×源码（类数）", "①②", "跳过：无 XML", WARN, "")


# --------------------------------------------------------------------------
# 口径 3：C1 台账
# --------------------------------------------------------------------------
def section_ledger(root: Path, rep: Report, doc_text: str) -> list[tuple[str, int]]:
    rows, errors = parse_ledger(doc_text)
    if not rows:
        rep.add("ledger", "台账行数", DOC_REL, "0 行", FAIL,
                f"反空跑：解析不到任何行。{errors}")
        return rows
    if errors:
        rep.add("ledger", "台账解析", DOC_REL, f"{len(rows)} 行 + {len(errors)} 处异常",
                FAIL, errors[:400])
    else:
        rep.add("ledger", "台账行数", DOC_REL, f"{len(rows)} 行", OK)

    # 独立来源：按类名在 app/src/test 里数 @Test，逐行核对。
    counts: dict[str, int] = {}
    for path in glob.glob(str(root / "app/src/test/**/*.kt"), recursive=True):
        cls = os.path.basename(path)[:-3]
        with open(path, encoding="utf-8") as fh:
            counts[cls] = fh.read().count("@Test")

    declared_sum = sum(n for _, n in rows)
    rep.add("ledger", "台账声明例数合计", DOC_REL, f"{declared_sum} 例", OK)

    missing: list[str] = []
    differ: list[tuple[str, int, int]] = []
    for cls, declared in rows:
        got = counts.get(cls)
        if got is None:
            missing.append(cls)
        elif got != declared:
            differ.append((cls, declared, got))
    # ⚠️ 这是本节唯一真正的「核对」，它证明「表里声明的例数 == 源码里的 @Test 数」。
    # ⚠️ 「missing 非空」同样是 FAIL：文档点名了一个不存在的类，比数字错更严重。
    status = OK if (not missing and not differ) else FAIL
    detail = []
    if missing:
        detail.append(f"{len(missing)} 个类在 app/src/test 里找不到: {missing[:8]}")
    if differ:
        detail.append(
            f"{len(differ)} 行与源码不符: " +
            ", ".join(f"{c} 声明{d}/源码{g}" for c, d, g in differ[:8])
        )
    rep.add("ledger", "台账逐行核对（声明 vs 源码 @Test）", "DOC_REL × app/src/test",
            f"{len(rows)} 行全部相等" if status == OK else
            f"{len(missing)} 缺失 / {len(differ)} 不符", status, "；".join(detail))
    return rows


# --------------------------------------------------------------------------
# 口径 4：lint
# --------------------------------------------------------------------------
def lint_from_xml(path: Path) -> Counter:
    root = ET.parse(path).getroot()
    return Counter(i.get("severity") for i in root.findall("issue"))


def lint_from_sarif(path: Path) -> tuple[int, Counter]:
    import json

    data = json.loads(path.read_text(encoding="utf-8"))
    levels: Counter = Counter()
    total = 0
    for run in data.get("runs", []):
        for result in run.get("results", []):
            total += 1
            levels[result.get("level")] += 1
    return total, levels


def section_lint(root: Path, rep: Report) -> None:
    app_xml = root / "app/build/reports/lint-results-debug.xml"
    if not app_xml.is_file():
        rep.add("lint", "app lint XML", "app/build/reports/lint-results-debug.xml",
                "文件不存在", FAIL, "反空跑：先跑 `./gradlew --offline lintDebug`")
    else:
        sev = lint_from_xml(app_xml)
        total = sum(sev.values())
        if total == 0:
            rep.add("lint", "app lint 严重度分布", "…lint-results-debug.xml",
                    "0 条 issue", FAIL, "反空跑：XML 存在但一条 issue 都没有")
        else:
            rep.add("lint", "app lint 严重度分布", "…lint-results-debug.xml",
                    f"error {sev.get('Error', 0)} / warning {sev.get('Warning', 0)} / "
                    f"hint {sev.get('Hint', 0)}（合计 {total}）",
                    FAIL if sev.get("Error", 0) else OK,
                    "存在 lint error" if sev.get("Error", 0) else "")

        sarif = root / "app/build/reports/lint-results-debug.sarif"
        if sarif.is_file():
            s_total, s_levels = lint_from_sarif(sarif)
            missing = s_levels.get(None, 0)
            rep.add("lint", "app lint SARIF 交叉验证", "…lint-results-debug.sarif",
                    f"总数 {s_total}（XML {total}）", OK if s_total == total else FAIL,
                    f"⚠️ SARIF 的 `level` 有 {missing} 条为空，所以严重度分布**只能读 XML**")
        else:
            rep.add("lint", "app lint SARIF 交叉验证", "…sarif", "文件不存在", WARN,
                    "只拿到 XML 一个来源，失去交叉验证")

    # 全模块
    xmls = sorted(root.glob("*/build/reports/lint-results-debug.xml"))
    if not xmls:
        rep.add("lint", "全模块 lint", "*/build/reports/lint-results-debug.xml",
                "0 个模块", FAIL, "反空跑：一个模块的报告都没有")
    else:
        tot: Counter = Counter()
        for p in xmls:
            tot += lint_from_xml(p)
        n = sum(tot.values())
        rep.add("lint", "全模块 lint 严重度分布", f"{len(xmls)} 个模块的 …-debug.xml",
                f"error {tot.get('Error', 0)} / warning {tot.get('Warning', 0)} / "
                f"hint {tot.get('Hint', 0)}（合计 {n}）",
                FAIL if (n == 0 or tot.get("Error", 0)) else OK,
                "反空跑：合计为 0" if n == 0 else "")


# --------------------------------------------------------------------------
# 口径 5：仪器 @Test
# --------------------------------------------------------------------------
def section_android_test(root: Path, rep: Report) -> None:
    pattern = str(root / "app/src/androidTest/**/*.kt")
    all_files = sorted(glob.glob(pattern, recursive=True))
    untracked = [p for p in all_files if not _is_tracked(root, p)]
    tracked = [p for p in all_files if p not in set(untracked)]

    if not all_files:
        rep.add("androidtest", "仪器 @Test", "app/src/androidTest/**/*.kt",
                "0 个文件", FAIL, "反空跑：仪器测试源码树扫不到文件")
        return
    t_files, t_ann = count_test_annotations(tracked)
    a_files, a_ann = count_test_annotations(all_files)
    rep.add("androidtest", "仪器 @Test（仅 tracked）", "git ls-files + grep",
            f"{t_files} 个文件 / {t_ann} 例", OK)
    rep.add("androidtest", "仪器 @Test（含 untracked）", "find + grep",
            f"{a_files} 个文件 / {a_ann} 例", OK,
            f"untracked: {[os.path.basename(p) for p in untracked] or '无'}")
    # 两个口径必须自洽：含 untracked ≥ 仅 tracked，且差值 == untracked 文件的注解数。
    if untracked:
        _, u_ann = count_test_annotations(untracked)
        ok = (a_ann - t_ann) == u_ann
        rep.add("androidtest", "仪器 @Test 两口径自洽", "③④",
                f"{a_ann} - {t_ann} = {a_ann - t_ann}（untracked {u_ann}）",
                OK if ok else FAIL,
                "" if ok else "两个口径不自洽")


_tracked_cache: set[str] | None = None


def _is_tracked(root: Path, path: str) -> bool:
    global _tracked_cache
    if _tracked_cache is None:
        n, out = git_lines(
            root, "ls-files", "--", ":(glob)app/src/androidTest/**/*.kt"
        )
        _tracked_cache = {l.strip() for l in out.split("\n") if l.strip()} if n >= 0 else set()
    rel = os.path.relpath(path, root)
    return rel in _tracked_cache


# --------------------------------------------------------------------------
# 口径 6：git 台账锚点
# --------------------------------------------------------------------------
def section_git(root: Path, rep: Report) -> None:
    n, out = git_lines(root, "log", "--oneline", ANCHOR_RANGE)
    if n < 0:
        rep.add("git", "台账锚点区间 commit 数", f"git log {ANCHOR_RANGE}", "失败", FAIL,
                out[:200])
    else:
        # ⚠️ 这个 78 是**锚定值**，只读打印；本脚本绝不把它写回文档。
        rep.add("git", "台账锚点区间 commit 数", f"git log {ANCHOR_RANGE}",
                f"{n} 个 commit", OK if n > 0 else FAIL,
                "⚠️ 锚定在固定 SHA，永不重算" if n > 0 else "反空跑：区间为空")
    m, out2 = git_lines(root, "log", "--oneline", "--merges", ANCHOR_RANGE)
    if m >= 0:
        rep.add("git", "锚点区间内 merge 数", f"git log --merges {ANCHOR_RANGE}",
                f"{m} 个", OK if m == 0 else WARN,
                "区间内出现 merge，计数口径需另行说明" if m else "")
    a, _ = git_lines(root, "log", "--oneline", f"{ANCHOR_SHA}..HEAD")
    rep.add("git", "锚点之后 commit 数（只读）", f"git log {ANCHOR_SHA}..HEAD",
            f"{a} 个" if a >= 0 else "失败", OK if a > 0 else FAIL)


# --------------------------------------------------------------------------
# 口径 7：表格列数（Task 1 的复算）
# --------------------------------------------------------------------------
def section_tables(root: Path, rep: Report, doc_text: str, label: str | None = None) -> None:
    label = label or DOC_REL
    blocks, rows, problems = audit_tables(doc_text, label)
    if blocks == 0:
        rep.add("tables", "表格列数一致性", label, "0 个表格块", FAIL,
                "反空跑：一个表格块都没扫到")
        return
    rep.add("tables", "表格列数一致性", label,
            f"{blocks} 个表格块 / {rows} 行，列数不符 {len(problems)} 行",
            FAIL if problems else OK,
            "；".join(problems[:10]))


# --------------------------------------------------------------------------
# --self-test：证明每个探测器确实会报红
# --------------------------------------------------------------------------
def self_test(root: Path) -> int:
    """把探测器喂上人造坏输入，断言它们**确实** FAIL。

    ⚠️ 这一节存在的理由：源码护栏上连续两次「扫到 0 条 → 当成没问题」。一个从不
    报红的探测器和一个有效探测器在输出上无法区分，所以必须**当场证明它会红**。
    """
    print("=== self-test：探测器必须能在人造坏输入上报红 ===")
    failures: list[str] = []

    def expect(label: str, cond: bool, detail: str = "") -> None:
        print(f"  [{'PASS' if cond else 'FAIL'}] {label}{(' — ' + detail) if detail else ''}")
        if not cond:
            failures.append(label)

    # 1) 空 XML 目录必须报红
    rep = Report()
    empty = root / "app/build/test-results/testDebugUnitTest"
    saved = empty.is_dir()
    real = sorted(glob.glob(str(empty / "TEST-*.xml"))) if saved else []
    try:
        import tempfile

        with tempfile.TemporaryDirectory() as td:
            root2 = Path(td) / "repo"
            (root2 / "app/build/test-results/testDebugUnitTest").mkdir(parents=True)
            (root2 / "app/src/test").mkdir(parents=True)
            rep = Report()
            section_unit(root2, rep)
            xml_fail = any(c.status == FAIL and "XML" in c.name for c in rep.checks)
            src_fail = any(c.status == FAIL and "源码" in c.name for c in rep.checks)
        expect("空 XML 目录 → 口径 1 报红", xml_fail)
        expect("空源码树 → 口径 2 报红", src_fail)
    finally:
        pass

    # 2) 只含 1 个 XML 的目录必须报红（陈旧/过滤跑）
    import tempfile

    with tempfile.TemporaryDirectory() as td:
        root3 = Path(td) / "repo"
        d = root3 / "app/build/test-results/testDebugUnitTest"
        d.mkdir(parents=True)
        (d / "TEST-x.xml").write_text(
            '<testsuite name="x" tests="5" failures="0" errors="0" skipped="0"/>',
            encoding="utf-8",
        )
        (root3 / "app/src/test").mkdir(parents=True)
        (root3 / "app/src/test/OnlyOne.kt").write_text(
            "class OnlyOne { @Test fun a() = Unit }\n", encoding="utf-8"
        )
        rep = Report()
        section_unit(root3, rep)
        stale = [c for c in rep.checks if c.status == FAIL and "类数" in c.name]
        expect("只有 1 个 XML → 判为陈旧并报红", bool(stale),
               stale[0].note[:70] if stale else "没有报红")

    # 3) 0 行的台账必须报红
    rep = Report()
    section_ledger(root, rep, "# 没有台账标题的空文档\n")
    led = [c for c in rep.checks if c.status == FAIL]
    expect("台账 0 行 → 报红", bool(led), led[0].note[:70] if led else "没有报红")

    # 4) 例数格无法解析的行必须报错，而不是静默跳过（旧脚本的致命缺陷）
    bad_doc = (
        LEDGER_HEADING + "\n\n"
        "| 测试类 | 例数 | 钉住什么 |\n|---|---:|---|\n"
        "| `FooTest` | 12 | ok |\n"
        "| `BarTest` | 见 XML | 解析不了 |\n"
    )
    rep = Report()
    section_ledger(root, rep, bad_doc)
    parse_fail = [c for c in rep.checks if c.status == FAIL and "解析" in c.name]
    expect("例数格无法解析 → 报红（旧脚本会静默跳过）", bool(parse_fail))

    # 5) 表格里的裸 `|` 必须被抓出来
    blocks, rows, problems = audit_tables(
        "| a | b |\n|---|---|\n| 1 | 2 |\n| `x | y` | 3 |\n", "t"
    )
    expect("表格裸 `|` → 被抓出", len(problems) == 1, str(problems))
    blocks2, _, problems2 = audit_tables("| a | b |\n|---|---|\n| 1 | 2 |\n", "t")
    expect("表格列数一致 → 不误报", problems2 == [])
    blocks3, _, problems3 = audit_tables("没有表格\n", "t")
    expect("0 个表格块 → 交由调用方判红", blocks3 == 0 and problems3 == [])

    # 6) `split_row` 必须把 `\|` 还原成字面量竖线且不误切。
    #    ⚠️ `split_row` **刻意不 strip** 单元格（GFM 里首尾空格本来就无意义，
    #    但保留原样更忠实；调用方各自 strip），所以这里比对时要 strip。
    cells = split_row("| `a\\|b` | c |")
    expect("split_row 还原 \\| 且列数正确",
           len(cells) == 2 and cells[0].strip() == "`a|b`", str(cells))
    naive = "| `a\\|b` | c |".strip().strip("|").split("|")
    expect("朴素 split('|') 确实会误切（文档里那条旧脚本的坑）",
           len(naive) == 3, str(naive))

    # 7) 0 条 issue 的 lint XML 必须报红
    with tempfile.TemporaryDirectory() as td:
        root4 = Path(td) / "repo"
        p = root4 / "app/build/reports"
        p.mkdir(parents=True)
        (p / "lint-results-debug.xml").write_text(
            '<?xml version="1.0"?><issues format="6" by="lint 8.0.0"></issues>',
            encoding="utf-8",
        )
        rep = Report()
        section_lint(root4, rep)
        lint_fail = any(c.status == FAIL for c in rep.checks)
        expect("0 条 lint issue → 报红", lint_fail)

    print(f"=== self-test 结果：{len(failures)} 处失败 ===")
    for f in failures:
        print(f"  FAILED: {f}")
    return 1 if failures else 0


# --------------------------------------------------------------------------
# --check-doc：文档里写的数 vs 实算
# --------------------------------------------------------------------------
def check_doc(root: Path, rep: Report, doc_text: str, ledger_rows) -> None:
    """把文档里**声称**的数字与本脚本**独立算出**的数字对账。"""
    claims = re.findall(r"(\d+)\s*类\s*/\s*(\d+)\s*例", doc_text)
    declared = Counter(claims)
    print("=== --check-doc：文档声称的「N 类 / M 例」出现次数 ===")
    for (n_classes, n_cases), times in sorted(
        declared.items(), key=lambda kv: -kv[1]
    )[:12]:
        print(f"  {n_classes:>4} 类 / {n_cases:>4} 例   × {times}")
    print(f"  （共 {len(declared)} 种不同写法、合计 {sum(declared.values())} 处）")

    # 台账声明值 vs 源码逐行核对值
    rows, errors = parse_ledger(doc_text)
    if not rows:
        rep.add("check-doc", "台账声明 vs 实算", DOC_REL, "台账 0 行", FAIL, errors)
        return
    counts: dict[str, int] = {}
    for path in glob.glob(str(root / "app/src/test/**/*.kt"), recursive=True):
        cls = os.path.basename(path)[:-3]
        with open(path, encoding="utf-8") as fh:
            counts[cls] = fh.read().count("@Test")
    bad = [
        (c, d, counts.get(c))
        for c, d in rows
        if counts.get(c) != d
    ]
    rep.add("check-doc", "台账声明 vs 源码实算", "DOC_REL × app/src/test",
            f"{len(rows)} 行 / {sum(n for _, n in rows)} 例 "
            + ("全部相等" if not bad else f"{len(bad)} 行不符"),
            FAIL if bad else OK,
            ", ".join(f"{c}: 声明{d}/实算{g}" for c, d, g in bad[:10]))


def print_report(rep: Report) -> None:
    print()
    print("=" * 100)
    print(f"{'组':<11}{'状态':<6}{'口径':<34}{'值'}")
    print("-" * 100)
    for c in rep.checks:
        print(f"{c.group:<11}{c.status:<6}{c.name:<34}{c.value}")
        if c.source:
            print(f"{'':<11}{'':<6}  来源: {c.source}")
        if c.note:
            print(f"{'':<11}{'':<6}  ⚠️ {c.note}")
    print("-" * 100)
    n_ok = sum(1 for c in rep.checks if c.status == OK)
    print(f"合计 {len(rep.checks)} 条：OK {n_ok} / WARN {len(rep.warned)} / FAIL {len(rep.failed)}")
    if rep.failed:
        print("\n❌ 失败项（退出码非零）：")
        for c in rep.failed:
            print(f"  - [{c.group}] {c.name}: {c.value}")
            if c.note:
                print(f"      {c.note}")
    if rep.warned:
        print("\n⚠️ 警告项：")
        for c in rep.warned:
            print(f"  - [{c.group}] {c.name}: {c.value}")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="C1 文档统计口径复算（零设备、只读、不跑 gradle）",
    )
    p.add_argument(
        "--only",
        action="append",
        choices=["unit", "ledger", "lint", "androidtest", "git", "tables"],
        help="只跑指定口径（可重复）；默认全跑",
    )
    p.add_argument("--self-test", action="store_true",
                   help="只跑探测器自检：证明每个探测器在坏输入上确实报红")
    p.add_argument("--check-doc", action="store_true",
                   help="额外把文档里声称的数字与实算结果对账")
    p.add_argument("--doc", default=None,
                   help=f"文档路径（默认 {DOC_REL}）")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = repo_root()

    if args.self_test:
        return self_test(root)

    doc_path = Path(args.doc) if args.doc else root / DOC_REL
    if not doc_path.is_file():
        print(f"❌ 找不到文档 {doc_path}", file=sys.stderr)
        return 2
    doc_text = doc_path.read_text(encoding="utf-8")

    want = set(args.only) if args.only else {"unit", "ledger", "lint",
                                             "androidtest", "git", "tables"}
    rep = Report()
    ledger_rows: list[tuple[str, int]] = []
    if "unit" in want:
        section_unit(root, rep)
    if "ledger" in want:
        ledger_rows = section_ledger(root, rep, doc_text)
    if "lint" in want:
        section_lint(root, rep)
    if "androidtest" in want:
        section_android_test(root, rep)
    if "git" in want:
        section_git(root, rep)
    if "tables" in want:
        section_tables(root, rep, doc_text, doc_path.name)
    if args.check_doc:
        check_doc(root, rep, doc_text, ledger_rows)

    if not rep.checks:
        # 反空跑的最后一道：一条口径都没产出，绝不能安静地退出 0。
        print("❌ 一条口径都没产出（--only 全部落空？）——拒绝报告「通过」", file=sys.stderr)
        return 1

    print_report(rep)
    return 1 if rep.failed else 0


if __name__ == "__main__":
    sys.exit(main())
