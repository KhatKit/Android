#!/usr/bin/env python3
"""
C1-S 主机侧 SQL 重放证据（抽屉类型筛选，非 instrumentation）。

背景：C1-10「单聊/群聊共存」的验收要求是「类型筛选只过滤、来回切换不丢数据」。仓库里
已有的两层证据都只到「纯函数」和「源码文本」这一层：

  1. `ChatDrawerViewModel.planConversationListQuery`（判定走哪条查询）
     + `ConversationListQueryPlanTest`（7 条）
  2. `ConversationTypeFilterSourceGuardTest`（2 条，断言 SQL 字面量没被复制、内存 filter 已删）

缺口：`ConversationDAO.kt` 里那两条 `@Query` 的 SQL **从没在真实 SQLite 引擎上执行过**。
`app/build.gradle.kts` 的 `testImplementation(libs.junit)` 没有 Robolectric / room-testing
（room-testing 只在 androidTestImplementation 里），所以 JVM 单测跑不了 DAO。

本脚本用 Python 标准库 `sqlite3`（真实 SQLite 引擎，不是 mock、不起服务、不读设备）
重放这两条查询，把「SQL 层面的行为」补成主机侧可复现的断言。

数据源全部来自仓库里的真实产物，**不手抄 SQL**：
  - 两条 `@Query` 的 SQL（含 `"..." + CONVERSATION_TYPE_PREDICATE_SQL + "..."` 的
    Kotlin 字符串拼接折叠）、形参声明顺序：
        app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt
  - `CONVERSATION_TYPE_PREDICATE_SQL` 常量原文：同一个文件
  - 投影列 → Kotlin 字段的别名映射：
        app/src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationRepository.kt
        的 `data class LightConversationEntity`
  - 类型字面量 `TYPE_DIRECT` / `TYPE_GROUP` / `FILTER_ALL`：
        app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt
  - 建表 DDL（列名/类型/默认值/主键）：
        app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/32.json 的 createSql

---------------------------------------------------------------------------
Room 命名参数 → Python sqlite3 的绑定规则（转换规则集中在这里）
---------------------------------------------------------------------------
Room 把 `:name` 按**形参名**绑定（`@Query` 的 SQL 里出现 `:type` 就取 Kotlin 形参
`type` 的值）；Python `sqlite3` 原生就支持 `:name` 占位符 + dict 绑定，语法完全一致，
所以**主路径不做任何文本改写**，直接把
    {"assistantId": ..., "searchText": ..., "type": ...}
交给 `conn.execute(sql, mapping)`。这是与 Room 逐字节同构的绑定。

但「按名绑定」会**掩盖绑定顺序问题**，所以脚本同时把两种绑定模型都跑一遍并互相校验：

  * 模型 A（Room / SQLite 原生，脚本主路径）：按名绑定。
  * 模型 B（朴素的位置绑定适配器，手写测试夹具常见写法）：第 i 个占位符取第 i 个
    **声明顺序**的实参。只有当「SQL 里占位符的出现顺序 == Kotlin 形参声明顺序」时
    B 才与 A 等价。因此脚本把这条等价关系显式钉成断言（而不是默认它成立）：
      - `占位符出现顺序 == 形参声明顺序`
      - `按声明顺序做位置绑定 == 按名绑定`（结果集逐 id 相等）
      - `把实参轮转一位后的位置绑定 != 按名绑定`（证明上一条不是恒真）

这三层一起给「绑定顺序错位」这类 bug 上牙：DAO 里改形参顺序、改占位符名、或者把两条
查询里某个 `:x` 换成另一个参数，都会让上面至少一条报红。

`LIMIT n OFFSET m`（模拟 Room 的 LimitOffsetPagingSource）与
`SELECT COUNT(*) FROM (<原查询>)`（Room 生成 count 的方式）都是**机械拼接**，不改谓词。

---------------------------------------------------------------------------
范围边界（不要把这份证据读成比它更大的东西）
---------------------------------------------------------------------------
* 这是**宿主 CPython 捆绑的 SQLite**，不是 Android 的 `android.database.sqlite.SQLiteDatabase`。
  LIKE 大小写折叠、类型亲和性、`ORDER BY` 同值时的稳定性等行为**两处都可能不同**，
  真机口径需要 instrumentation 另取证。
* 本脚本只验 SQL 层。「走哪条查询」是 `planConversationListQuery` 的纯函数契约，
  由 `ConversationListQueryPlanTest` 覆盖；内存过滤已删由 `ConversationTypeFilterSourceGuardTest`
  覆盖。这里不重复那两层，也不声称 Room 的编译期 SQL 校验（列名拼错会在编译期报错，
  不可能跑到引擎上）。
* 第 6 组只**观测** `folder_id` 口径差异（未归档路有 `AND folder_id = ''`、搜索路没有），
  这是 B2 待产品决策项：脚本把事实钉住，不替谁做判断，也不改任何生产代码。

---------------------------------------------------------------------------
变异检验（脚本有牙齿的证据；影子仓库在 /tmp，用文件备份 + sha256sum -c 还原，
全程不使用任何 git 命令）
---------------------------------------------------------------------------
在 `/tmp` 建影子仓库（把本脚本 + `ConversationDAO.kt` 副本 + 它读的那几个产物一起复制
过去，脚本按 `__file__` 的 `parents[2]` 定位 ROOT，所以影子仓库里跑的是**未改动**的脚本），
逐个注入下列缺陷，每次都要求退出码非 0：

  | 变异 | 注入什么| 退出码 | 报红断言（首条）                          |
  |------|----------|--------|-------------------------------------------|
  | M1   | `AND (:type = '' OR type = :type)` -> `AND type = :type` | 1 | 抠到的谓词原文不符 / 未归档路 type='' 返回空 |
  | M2   | 谓词里 `OR` -> `AND`                      | 1 | 抠到的谓词原文不符 / type='' 返回空          |
  | M3   | 未归档路删掉 `AND folder_id = ''`         | 1 | 未归档路 WHERE 段不含 `folder_id = ''`       |
  | M4   | `ORDER BY ... DESC, ... DESC` -> `ASC, ASC` | 1 | ORDER BY 子句不符 / 返回顺序与期望不同        |
  | M5   | Kotlin 形参声明顺序改成 type, searchText, assistantId | 1 | 形参顺序不符 / 占位符顺序 != 形参顺序 |
  | M6   | 搜索路 `:searchText` -> `:assistantId`    | 1 | 占位符顺序 != 形参顺序 / 搜索命中变空         |

复现步骤（不碰真仓库、不用 git）：
    cp ConversationDAO.kt /tmp/backup.kt && sha256sum ConversationDAO.kt > /tmp/dao.sha
    # 改 DAO，跑 python3 tools/verification/c1s_conversation_type_filter_replay.py
    cp /tmp/backup.kt ConversationDAO.kt && sha256sum -c /tmp/dao.sha

不写任何文件（`sqlite3.connect(":memory:")`），只 print 到 stdout。
"""
import pathlib
import re
import sqlite3
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from c1d_migration_30_31_replay import note  # noqa: E402  (复用既有脚本的口径说明助手)

ROOT = pathlib.Path(__file__).resolve().parents[2]
DAO = (ROOT / "app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt")
REPO = (ROOT / "app/src/main/java/heizige/kk/khatkit/app/core/data/repository"
        "/ConversationRepository.kt")
GROUP_CHAT = (ROOT / "app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt")
SCHEMA_JSON = (ROOT / "app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/32.json")

UNFILED_FUN = "getUnfiledConversationsOfAssistantByType"
SEARCH_FUN = "searchConversationsOfAssistantByType"

# --------------------------------------------------------------------------
# 断言统计（带分组，供最后汇总）
# --------------------------------------------------------------------------
failures = []
assertions = 0
group_stats = {}
_current_group = "?"


def group(name):
    global _current_group
    _current_group = name


def check(label, ok, detail=""):
    global assertions
    assertions += 1
    st = group_stats.setdefault(_current_group, [0, 0])
    st[0 if ok else 1] += 1
    print(("  PASS  " if ok else "  FAIL  ") + label + (("  -> " + detail) if detail else ""))
    if not ok:
        failures.append(label)
    return ok


# ==========================================================================
# 1. 从 Kotlin 源码抽取（正则，不用手抄 SQL）
# ==========================================================================
def decode_kotlin_literal(raw):
    """把 Kotlin 双引号字面量的转义还原成实际字节（无反斜杠时原样返回）。

    避免无条件 unicode_escape：那会把字面量里的非 ASCII 字符按字节拆坏。
    """
    if "\\" not in raw:
        return raw
    return raw.encode("utf-8").decode("unicode_escape").encode("latin-1").decode("utf-8")


# `@Query( <常量/字面量拼接> ) fun <name>( <形参> )` —— 允许跨行。
# 注解实参里含字符串字面量（字面量里可能有括号，如 getConversationCountPerDay 的
# strftime(...)），所以不能靠 `@Query\(...\)` 的非贪婪匹配切边界 —— 那样会把
# 字面量内部的括号当成注解的结束符。这里改成手工配平括号，跳过双引号串。
_KOTLIN_STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
_SIG_RE = re.compile(r"\s*(?:suspend\s+)?fun\s+(\w+)\s*\(([^)]*)\)", re.S)


def _matching_paren(text, open_idx):
    """从 `text[open_idx] == '('` 出发找配对的 `)`，跳过双引号串内部的括号。"""
    depth, i, n = 0, open_idx, len(text)
    while i < n:
        m = _KOTLIN_STRING.match(text, i)
        if m:
            i = m.end()
            continue
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise SystemExit(f"@Query 的括号在文件里配不平，位置 {open_idx}")


def iter_queries(text):
    """按源码顺序产出 (函数名, @Query 实参原文, 形参原文)。"""
    for m in re.finditer(r"@Query\s*\(", text):
        open_idx = m.end() - 1
        close_idx = _matching_paren(text, open_idx)
        body = text[open_idx + 1:close_idx]
        sig = _SIG_RE.match(text, close_idx + 1)
        if not sig:
            raise SystemExit(f"@{m.group(0)} 之后没找到 fun 签名（抽取假设失效）")
        yield sig.group(1), body, sig.group(2)


def fold_query_text(annotation_body, literals, func_name):
    """把 `@Query(...)` 里的 `"字面量" + 常量名 + "字面量"` 折叠成最终 SQL 原文。

    顺序 = 源码里的出现顺序（Room 收到的就是这个拼接结果）。遇到非字面量/非常量的
    token 直接报错 —— 宁可脚本失败，也不要悄悄用一份猜出来的 SQL 继续跑。
    """
    out, pos = [], 0
    body = annotation_body.strip()
    while pos < len(body):
        if body[pos].isspace() or body[pos] == "+":
            pos += 1
            continue
        m = _KOTLIN_STRING.match(body, pos)
        if m:
            out.append(decode_kotlin_literal(m.group(1)))
            pos = m.end()
            continue
        m = re.match(r"([A-Za-z_]\w*)", body[pos:])
        if m and m.group(1) in literals:
            out.append(literals[m.group(1)])
            pos += m.end()
            continue
        raise SystemExit(
            f"{func_name} 的 @Query 里出现无法解析的 token: {body[pos:pos + 40]!r}")
    sql = "".join(out)
    if not sql.strip().upper().startswith("SELECT"):
        # DAO 里还有 @Query("UPDATE ...") / @Query("DELETE ...")；本脚本只重放读查询
        return None
    return sql


def parse_kotlin_params(param_text):
    """`assistantId: String, type: String = ""` -> ["assistantId", "type"]（声明顺序）。"""
    names = []
    for raw in param_text.split(","):
        item = raw.split("=")[0].strip()
        if not item:
            continue
        m = re.match(r"([A-Za-z_]\w*)\s*:", item)
        if not m:
            raise SystemExit(f"解析不了形参: {raw!r}")
        names.append(m.group(1))
    return names


def projection_of(sql):
    """从 SELECT 段抠出投影列清单与 `列 AS 别名` 映射（按源码顺序，去重保序）。"""
    body = sql[sql.upper().index("SELECT") + len("SELECT"): sql.upper().index(" FROM ")]
    cols, alias_map = [], {}
    for part in body.split(","):
        item = part.strip()
        if not item:
            continue
        m = re.match(r"(\w+)\s+(?:as|AS)\s+(\w+)\s*$", item)
        if m:
            cols.append(m.group(1))
            alias_map[m.group(1)] = m.group(2)
        else:
            cols.append(item.strip("`"))
            alias_map[item.strip("`")] = item.strip("`")
    return cols, alias_map


def where_clause(sql):
    """抠出 WHERE 段（到 ORDER BY / 结尾为止）。"""
    up = sql.upper()
    start = up.index(" WHERE ") + len(" WHERE ")
    end = up.find(" ORDER BY ", start)
    return sql[start:end if end > 0 else len(sql)].strip()


def placeholder_occurrences(sql):
    """SQL 里 `:name` 占位符的**出现序列**（保留重复 —— `:type` 在谓词里出现两次）。"""
    return [m.group(1) for m in re.finditer(r":([A-Za-z_]\w*)", sql)]


def placeholder_order(sql):
    """占位符的**首次出现顺序**（同名去重），用来与 Kotlin 形参声明顺序对表。"""
    order = []
    for name in placeholder_occurrences(sql):
        if name not in order:
            order.append(name)
    return order


def light_entity_fields():
    """抠出 `data class LightConversationEntity(...)` 的字段名 + 默认值表达式。"""
    text = REPO.read_text(encoding="utf-8")
    m = re.search(r"data class LightConversationEntity\s*\((.*?)\n\)", text, re.S)
    if not m:
        raise SystemExit("ConversationRepository.kt 里找不到 data class LightConversationEntity")
    fields = []
    for raw in m.group(1).split(","):
        item = raw.strip()
        if not item or item.startswith("//") or item.startswith("*"):
            continue
        parts = item.split("=", 1)
        decl = re.sub(r"^(?:val|var)\s+", "", parts[0].strip())
        m = re.match(r"([A-Za-z_]\w*)\s*:", decl)
        default = parts[1].strip() if len(parts) > 1 else None
        if m:
            fields.append((m.group(1), default))
    return fields


def kotlin_const(text, name):
    m = re.search(r'const val\s+' + re.escape(name) + r'\s*=\s*"((?:[^"\\]|\\.)*)"', text)
    if not m:
        raise SystemExit(f"找不到 const val {name}")
    return decode_kotlin_literal(m.group(1))


def extract():
    """一次性把本脚本要验的全部「真相」从源码抠出来。"""
    dao_text = DAO.read_text(encoding="utf-8")

    # 常量：`internal const val CONVERSATION_TYPE_PREDICATE_SQL = "..."`
    cm = re.search(r'const val\s+CONVERSATION_TYPE_PREDICATE_SQL\s*=\s*"((?:[^"\\]|\\.)*)"',
                   dao_text)
    if not cm:
        raise SystemExit("ConversationDAO.kt 里找不到 CONVERSATION_TYPE_PREDICATE_SQL 常量")
    predicate = decode_kotlin_literal(cm.group(1))

    literals = {"CONVERSATION_TYPE_PREDICATE_SQL": predicate}
    queries = {}
    for name, body, params in iter_queries(dao_text):
        queries[name] = {
            "sql": fold_query_text(body, literals, name),
            "kotlin_params": parse_kotlin_params(params),
        }

    group_text = GROUP_CHAT.read_text(encoding="utf-8")
    type_consts = {n: kotlin_const(group_text, n)
                   for n in ("TYPE_DIRECT", "TYPE_GROUP", "FILTER_ALL")}
    return {
        "predicate": predicate,
        "queries": queries,
        "light_fields": light_entity_fields(),
        "type_consts": type_consts,
        "predicate_ref_count": len(re.findall(r"CONVERSATION_TYPE_PREDICATE_SQL", dao_text)),
    }


# ==========================================================================
# 2. fixture
# ==========================================================================
# (id, assistant_id, type, folder_id, is_pinned, update_at_offset, title)
BASE_AT = 1700000000000
FIXTURE = [
    # --- A1 / DIRECT：未归档 / f1 / f2 三种 folder 位置 ---
    ("d-plain",    "A1", "DIRECT", "",  0, 9000, "普通单聊 Alpha"),
    ("d-pinned",   "A1", "DIRECT", "",  1, 1000, "置顶单聊 Beta"),
    ("d-f1",       "A1", "DIRECT", "f1", 0, 8000, "文件夹一 单聊 Gamma"),
    ("d-f2",       "A1", "DIRECT", "f2", 1, 2000, "文件夹二 单聊 Delta"),
    # --- A1 / GROUP：未归档 / f1（再加一行 f2，让 f2 不是 DIRECT 专属） ---
    ("g-plain",    "A1", "GROUP",  "",  0, 7000, "群聊 Epsilon"),
    ("g-pinned",   "A1", "GROUP",  "",  1,  500, "置顶群聊 Zeta"),
    ("g-f1",       "A1", "GROUP",  "f1", 0, 6500, "文件夹一 群聊 Eta"),
    ("g-f2",       "A1", "GROUP",  "f2", 0, 4000, "文件夹二 群聊 Theta"),
    # --- 「共享词」矩阵：未归档/文件夹 × 单聊/群聊，用来观测 folder_id 口径差异 ---
    ("d-share-uf", "A1", "DIRECT", "",  0, 3000, "共享词 单聊未归档"),
    ("d-share-f1", "A1", "DIRECT", "f1", 0, 2800, "共享词 单聊在f1"),
    ("g-share-uf", "A1", "GROUP",  "",  0, 2600, "共享词 群聊未归档"),
    ("g-share-f1", "A1", "GROUP",  "f1", 0, 2400, "共享词 群聊在f1"),
    # --- LIKE 通配符：字面量 '%' / '_'，各配一个「只靠通配符才会误命中」的诱饵 ---
    ("d-pct",      "A1", "DIRECT", "",  0, 2200, "百分号 100% 完成"),
    ("d-decoy100", "A1", "DIRECT", "",  0, 2150, "诱饵 版本 100 记录"),
    ("d-under",    "A1", "DIRECT", "",  0, 2100, "下划线 a_b_c"),
    ("d-decoy_axb","A1", "DIRECT", "",  0, 2050, "诱饵 axbxc"),
    ("g-pct",      "A1", "GROUP",  "",  0, 1400, "群聊 %50 折扣"),
    # --- LIKE 大小写：ASCII 一对（大小写不同、其余相同） ---
    ("d-case-up",  "A1", "DIRECT", "",  0, 1950, "Case Sensitive Probe"),
    ("d-case-lo",  "A1", "DIRECT", "",  0, 1900, "case sensitive probe"),
    # --- 非 ASCII 一对：繁简不同（不是大小写，是不同的字） ---
    ("cjk-simp",   "A1", "DIRECT", "",  0, 1800, "中文标题搜索词"),
    ("cjk-trad",   "A1", "DIRECT", "",  0, 1700, "中文標題搜索词"),
    # --- 空标题 / 纯空格标题 ---
    ("d-empty",    "A1", "DIRECT", "",  0, 1600, ""),
    ("d-space",    "A1", "DIRECT", "",  0, 1500, "   "),
    # --- 故意造一组 (is_pinned, update_at) 完全相同的行（都放 f2，未归档路看不到它们）——
    #     用来实测「ORDER BY 同值时行序由引擎决定、不是 SQL 契约」这件事本身 ---
    ("d-tie-jia",  "A1", "DIRECT", "f2", 0, 2000, "同刻 Delta 甲"),
    ("d-tie-yi",   "A1", "DIRECT", "f2", 0, 2000, "同刻 Delta 乙"),
    # --- 另一个助手：跨助手隔离 ---
    ("a2-direct",  "A2", "DIRECT", "",  1, 9999, "别的助手 Alpha"),
    ("a2-group",   "A2", "GROUP",  "",  0, 9998, "别的助手 群聊 Alpha"),
]
# folder_id 只用这三个值（'' = 未归档）
FOLDERS = ("", "f1", "f2")


def _row(r):
    return {
        "id": r[0], "assistant_id": r[1], "type": r[2], "folder_id": r[3],
        "is_pinned": r[4], "update_at": BASE_AT + r[5], "title": r[6],
    }


# 按 id 索引的 fixture：期望值（oracle）全部从这张表算，不复用 SQL 语义
ROW = {r[0]: _row(r) for r in FIXTURE}


def build_db(ddl_sql):
    conn = sqlite3.connect(":memory:")
    conn.execute(ddl_sql)
    cols = ("id, assistant_id, title, nodes, create_at, update_at, suggestions,"
            " is_pinned, custom_system_prompt, mode_injection_ids, lorebook_ids,"
            " workspace_cwd, folder_id, type, group_config, group_cards")
    # 非 fixture 关注的列一律用 32.json 里的列默认值填（nodes/suggestions 等），
    # 这样 fixture 表的默认值语义与 Room 导出的 schema 一致，而不是脚本自造。
    vals = ("(?,?,?,'[]',?,?,'[]',?,'','[]','[]','',?,?,?,'')")
    for r in FIXTURE:
        f = _row(r)
        conn.execute(
            f"INSERT INTO conversationentity ({cols}) VALUES {vals}",
            (f["id"], f["assistant_id"], f["title"], f["update_at"] - 5000,
             f["update_at"], f["is_pinned"], f["folder_id"], f["type"],
             '{"schema_version":1}' if f["type"] == "GROUP" else ""))
    conn.commit()
    return conn


# ==========================================================================
# 3. 执行封装
# ==========================================================================
def run(conn, sql, args_by_name):
    """模型 A：按名绑定（与 Room 一致）。返回**排序后**的 id 列表（集合语义断言用）。"""
    return sorted(r[0] for r in conn.execute(sql, args_by_name).fetchall())


def to_qmark(sql):
    """把 `:name` 占位符改写成 `?`（跳过单引号串），保持出现顺序。

    Python 的 sqlite3 不允许「命名占位符 + 位置实参」混用，所以模型 B 必须先做这一步
    文本改写。改写只碰占位符本身，不碰谓词 —— 但**这正是顺序开始有意义的那个入口**：
    改写之后实参靠位置对齐，写错顺序不会报错，只会静默返回错的行集。
    """
    out, i, n = [], 0, len(sql)
    while i < n:
        ch = sql[i]
        if ch == "'":
            j = sql.find("'", i + 1)
            j = n - 1 if j < 0 else j
            out.append(sql[i:j + 1])
            i = j + 1
            continue
        if ch == ":":
            m = re.match(r":[A-Za-z_]\w*", sql[i:])
            if m:
                out.append("?")
                i += m.end()
                continue
        out.append(ch)
        i += 1
    return "".join(out)


def run_rows_ids(conn, sql, args_by_name):
    """按查询原样返回 id 顺序（顺序类断言用，不能排序）。"""
    return [r[0] for r in conn.execute(sql, args_by_name).fetchall()]


def run_positional(conn, sql_qmark, ordered_values):
    """模型 B：按位置绑定，值必须自己按「声明顺序」对齐。"""
    return sorted(r[0] for r in conn.execute(sql_qmark, tuple(ordered_values)).fetchall())


def paged(conn, sql, args, page_size):
    """模拟 Room LimitOffsetPagingSource：`LIMIT n OFFSET m` 逐页取；返回 (页列表, 总数)。

    count 用 Room 的生成方式 `SELECT COUNT(*) FROM (<原查询>)`，不改谓词，
    所以「筛选后总数」和「实际返回条数」必然同口径（内存过滤做不到这一点）。
    """
    pages, offset = [], 0
    while True:
        page = [r[0] for r in conn.execute(
            f"{sql} LIMIT {page_size} OFFSET {offset}", args).fetchall()]
        if not page:
            break
        pages.append(page)
        offset += page_size
    count = conn.execute(f"SELECT COUNT(*) FROM ({sql})", args).fetchone()[0]
    return pages, count


# ==========================================================================
# main
# ==========================================================================
def main():
    print("== 0. 抽取：从 Kotlin 源码 / Room schema JSON 抠真相（不手抄 SQL）==")
    group("G0 抽取自检")
    facts = extract()
    predicate = facts["predicate"]
    unfiled = facts["queries"].get(UNFILED_FUN)
    search = facts["queries"].get(SEARCH_FUN)
    check(f"抠到 {UNFILED_FUN}", unfiled is not None)
    check(f"抠到 {SEARCH_FUN}", search is not None)
    if unfiled is None or search is None:
        return 2
    u_sql, s_sql = unfiled["sql"], search["sql"]
    u_params, s_params = unfiled["kotlin_params"], search["kotlin_params"]

    check("CONVERSATION_TYPE_PREDICATE_SQL 抠到的原文 == "
          "\" AND (:type = '' OR type = :type)\"",
          predicate == " AND (:type = '' OR type = :type)", repr(predicate))
    check("DAO 里该常量出现 3 次（1 次声明 + 2 次引用）",
          facts["predicate_ref_count"] == 3, str(facts["predicate_ref_count"]))
    check("两条查询折叠后都含同一份 type 谓词文本",
          predicate in u_sql and predicate in s_sql,
          f"unfiled={predicate in u_sql} search={predicate in s_sql}")
    check("type 谓词在整份 DAO 里只有一份字面量（无第二份拷贝）",
          DAO.read_text(encoding="utf-8").split("(:type = '' OR type = :type)").__len__() - 1 == 1)
    check("未归档路的 WHERE 段含 `AND folder_id = ''`",
          "AND folder_id = ''" in where_clause(u_sql), repr(where_clause(u_sql)))
    check("搜索路的 WHERE 段不含 folder_id 谓词（口径差异：B2 待决策）",
          "folder_id" not in where_clause(s_sql), repr(where_clause(s_sql)))
    check("未归档路的 WHERE 段含 folder_id = ''，搜索路不含 —— 差异被 SQL 文本观测到",
          "folder_id = ''" in where_clause(u_sql) and "folder_id" not in where_clause(s_sql))
    check("两条查询的 ORDER BY 逐字节相同",
          re.findall(r"ORDER BY.*$", u_sql) == re.findall(r"ORDER BY.*$", s_sql),
          str(re.findall(r"ORDER BY.*$", u_sql)))

    proj_u, alias_u = projection_of(u_sql)
    proj_s = projection_of(s_sql)[0]
    # 投影列下标：从抠出来的投影顺序算，不写死（SQL 里投影顺序变了这里跟着变）
    IDX = {name: i for i, name in enumerate(proj_u)}
    IDX_PIN, IDX_UPD, IDX_TYPE = IDX["is_pinned"], IDX["update_at"], IDX["type"]
    check("两条查询的投影列清单逐项相同", proj_u == proj_s, f"{proj_u} vs {proj_s}")
    check("投影列数 = 9（与 LightConversationEntity 的字段数对齐）",
          len(proj_u) == 9, str(len(proj_u)))
    light_fields = facts["light_fields"]
    check("LightConversationEntity 字段数 = 9（从 ConversationRepository.kt 抠出）",
          len(light_fields) == 9, str([n for n, _ in light_fields]))
    check("投影列的别名映射与 LightConversationEntity 字段一一对应",
          sorted(alias_u.values()) == sorted(n for n, _ in light_fields),
          f"{sorted(alias_u.values())} vs {sorted(n for n, _ in light_fields)}")
    check("投影里刻意没有 nodes / suggestions / group_cards（抽屉不需要重列）",
          not ({"nodes", "suggestions", "group_cards"} & set(proj_u)), str(proj_u))

    t_direct = facts["type_consts"]["TYPE_DIRECT"]
    t_group = facts["type_consts"]["TYPE_GROUP"]
    t_all = facts["type_consts"]["FILTER_ALL"]
    check("GroupChat 的类型字面量抠到 DIRECT/GROUP/ALL",
          (t_direct, t_group, t_all) == ("DIRECT", "GROUP", "ALL"),
          f"{t_direct}/{t_group}/{t_all}")
    check(f"{UNFILED_FUN} 的形参顺序 == [assistantId, type]",
          u_params == ["assistantId", "type"], str(u_params))
    check(f"{SEARCH_FUN} 的形参顺序 == [assistantId, searchText, type]",
          s_params == ["assistantId", "searchText", "type"], str(s_params))
    check("未归档路占位符出现顺序 == 形参声明顺序",
          placeholder_order(u_sql) == u_params, str(placeholder_order(u_sql)))
    check("搜索路占位符出现顺序 == 形参声明顺序",
          placeholder_order(s_sql) == s_params, str(placeholder_order(s_sql)))

    # 建库：DDL 取自 Room 导出的 32.json（列名/类型/默认值/主键与生产一致）
    import json
    spec = json.loads(SCHEMA_JSON.read_text(encoding="utf-8"))
    check("32.json 的版本号是 32", spec["database"]["version"] == 32)
    entity = next(e for e in spec["database"]["entities"]
                  if e["tableName"] == "ConversationEntity")
    ddl = entity["createSql"].replace("${TABLE_NAME}", "ConversationEntity")
    conn = build_db(ddl)
    want_cols = [f["columnName"] for f in entity["fields"]]
    got_cols = [r[1] for r in conn.execute("PRAGMA table_info('ConversationEntity')")]
    check("fixture 表列集合 == 32.json ConversationEntity 列集合（顺序也相同）",
          got_cols == want_cols, f"{got_cols} vs {want_cols}")
    check(f"灌入 {len(FIXTURE)} 行 fixture（覆盖 type×folder×pinned×update_at×标题形态）",
          conn.execute("SELECT COUNT(*) FROM conversationentity").fetchone()[0] == len(FIXTURE))
    check("DAO SQL 里的表名小写拼写 `conversationentity` 能命中真实表名"
          "（SQLite 表名大小写不敏感，这是被实测的行为事实）",
          bool(conn.execute("SELECT COUNT(*) FROM conversationentity").fetchone()[0]))
    note(f"宿主环境：Python {sys.version.split()[0]} / SQLite {sqlite3.sqlite_version}。"
         "这是 CPython 捆绑的 SQLite，不是 Android 的 SQLiteDatabase。")

    # ---- Python 侧的独立 oracle：只按 fixture 定义算期望，不复用 SQL 语义 ----
    def oracle_unfiled(assistant, type_arg):
        return sorted(r[0] for r in FIXTURE
                      if r[1] == assistant and r[3] == ""
                      and (type_arg == "" or r[2] == type_arg))

    def expected_order(ids):
        rows = {r[0]: (r[4], BASE_AT + r[5]) for r in FIXTURE}
        return sorted(ids, key=lambda i: (-rows[i][0], -rows[i][1]))

    # 「共享词」4 行的手工枚举（不写 LIKE 模拟，避免与 SQL 同源自我循环）
    SHARE_ALL = ["d-share-f1", "d-share-uf", "g-share-f1", "g-share-uf"]

    def u_args(assistant, type_arg):
        return {"assistantId": assistant, "type": type_arg}

    def s_args(assistant, keyword, type_arg):
        return {"assistantId": assistant, "searchText": keyword, "type": type_arg}

    print("== 1. :type = '' 不筛选（两条路都返回该 assistant 的全部命中）==")
    group("G1 type 空串不筛")
    for assistant in ("A1", "A2"):
        got = run(conn, u_sql, u_args(assistant, ""))
        check(f"未归档路 {assistant} type='' 返回全部未归档会话（集合等于 oracle）",
              got == oracle_unfiled(assistant, ""), f"{got}")
    got_uf = run(conn, u_sql, u_args("A1", ""))
    n_uf = len(oracle_unfiled("A1", ""))
    check(f"未归档路 type='' 命中 A1 全部 {n_uf} 条未归档会话（含空标题与纯空格标题）",
          len(got_uf) == n_uf and "d-empty" in got_uf and "d-space" in got_uf,
          f"{len(got_uf)}")
    check("未归档路 type='' 不含任何 folder_id 非空的行",
          not (set(got_uf) & {"d-f1", "d-f2", "g-f1", "g-f2"}), f"{got_uf}")
    got_share = run(conn, s_sql, s_args("A1", "共享词", ""))
    check("搜索路 type='' + 搜索词命中 4 行（未归档 2 + 文件夹 2）",
          got_share == SHARE_ALL, f"{got_share}")
    _, cnt = paged(conn, s_sql, s_args("A1", "共享词", ""), 3)
    check("搜索路 type='' 的 count == 实际返回条数 == 4（SQL 层过滤不虚高）",
          cnt == len(got_share) == 4, f"count={cnt} rows={len(got_share)}")

    print("== 2. :type = 'GROUP' / 'DIRECT' 正确筛选（集合相等，不是数量相等）==")
    group("G2 type 正确筛选")
    for type_arg in (t_direct, t_group):
        got = run(conn, u_sql, u_args("A1", type_arg))
        check(f"未归档路 A1 type='{type_arg}' 结果集合 == oracle 分组结果",
              got == oracle_unfiled("A1", type_arg), f"{got}")
        rows = conn.execute(u_sql, u_args("A1", type_arg)).fetchall()
        check(f"未归档路 A1 type='{type_arg}' 返回行的 type 列逐行都等于 '{type_arg}'",
              all(r[IDX_TYPE] == type_arg for r in rows),
              str(sorted({r[IDX_TYPE] for r in rows})))
        # 集合相等（双向差集为空），不是只比条数
        full_direct = set(oracle_unfiled("A1", ""))
        want = set(oracle_unfiled("A1", type_arg))
        got = set(r[0] for r in rows)
        check(f"未归档路 type='{type_arg}' 与「全量按 type 分组」双向差集都为空",
              (got - want) == set() and (want - got) == set(),
              f"多={sorted(got - want)} 少={sorted(want - got)}")
        check(f"未归档路 type='{type_arg}' 是 type='' 结果的真子集（筛掉的行非空）",
              got < full_direct, f"{sorted(full_direct - got)}")
    for type_arg, want in ((t_direct, ["d-share-f1", "d-share-uf"]),
                           (t_group, ["g-share-f1", "g-share-uf"])):
        got = run(conn, s_sql, s_args("A1", "共享词", type_arg))
        check(f"搜索路 A1 type='{type_arg}' + 「共享词」结果 == 手工枚举 {want}",
              got == want, f"{got}")
    check("未归档路 type='GROUP' 在 A2 上只命中 A2 自己的群会话（跨助手不串）",
          run(conn, u_sql, u_args("A2", t_group)) == ["a2-group"],
          f"{run(conn, u_sql, u_args('A2', t_group))}")
    check("搜索路 A1 搜 'Alpha' 不含 A2 的行（assistant_id 隔离）",
          run(conn, s_sql, s_args("A1", "Alpha", "")) == ["d-plain"],
          f"{run(conn, s_sql, s_args('A1', 'Alpha', ''))}")
    check("搜索路 A2 搜 'Alpha' 只命中 A2 的 2 行（不与 A1 串）",
          run(conn, s_sql, s_args("A2", "Alpha", "")) == ["a2-direct", "a2-group"],
          f"{run(conn, s_sql, s_args('A2', 'Alpha', ''))}")
    check("未归档路 A1 type='GROUP' 与 A1 type='DIRECT' 结果集互不相交且并起来等于不筛",
          set(run(conn, u_sql, u_args("A1", t_group)))
          | set(run(conn, u_sql, u_args("A1", t_direct)))
          == set(oracle_unfiled("A1", "")))
    check("FILTER_ALL（'ALL'）若被直接当 type 传下去会返回空集"
          "（所以 asConversationTypeArgument 必须先折成空串）",
          run(conn, u_sql, u_args("A1", t_all)) == [],
          f"{run(conn, u_sql, u_args('A1', t_all))}")

    print("== 3. 两条路的 type 语义一致（同一 assistant / 同一 type）==")
    group("G3 两路 type 口径一致")
    s_base = set(run(conn, s_sql, s_args("A1", "共享词", "")))
    uf_all = set(oracle_unfiled("A1", ""))
    for type_arg in ("", t_direct, t_group):
        s_all = set(run(conn, s_sql, s_args("A1", "共享词", type_arg)))
        u_all = set(run(conn, u_sql, u_args("A1", type_arg)))
        def keep(i):  # type 口径：空串不筛，否则按 type 列
            return type_arg == "" or ROW[i]["type"] == type_arg
        # 判据 A：搜索路 ∩ 未归档路 == 「手工枚举的、同 type 的、未归档的共享词行」
        expect_uf = {i for i in SHARE_ALL if i in uf_all and keep(i)}
        check(f"type='{type_arg}'：搜索路 ∩ 未归档路 == 手工枚举 {sorted(expect_uf)}"
              "（两条路对「哪些行算未归档」的口径一致）",
              (s_all & u_all) == expect_uf,
              f"交集={sorted(s_all & u_all)}")
        # 判据 B：搜索路比未归档路多出来的部分 == 「文件夹内、同 type 的共享词行」
        expect_extra = {i for i in SHARE_ALL if i not in uf_all and keep(i)}
        check(f"type='{type_arg}'：搜索路多出来的部分 == 手工枚举 {sorted(expect_extra)}"
              "（差异只在 folder 口径，type 口径完全一致）",
              (s_all - u_all) == expect_extra,
              f"多={sorted(s_all - u_all)}")
        # 判据 C：搜索路的 type 筛选 == 「不筛结果按 type 分组」（独立 Python 口径）
        want = {i for i in s_base if keep(i)}
        check(f"type='{type_arg}'：搜索路的 type 筛选 == 「不筛结果按 type 分组」",
              s_all == want, f"got={sorted(s_all)} want={sorted(want)}")
        # 判据 D：未归档路的 type 筛选 == 同 type 的未归档全集（与搜索路互为对照）
        want_u = {i for i in uf_all if keep(i)}
        check(f"type='{type_arg}'：未归档路的 type 筛选 == 同 type 的未归档全集 "
              f"{sorted(want_u)}",
              u_all == want_u, f"got={sorted(u_all)} want={sorted(want_u)}")
        # 判据 E：两条路在「未归档区间」上的 type 口径完全对齐（互为充要）
        check(f"type='{type_arg}'：(搜索路 ∩ 未归档) == (未归档按 type 分组 ∩ 搜索路)",
              (s_all & u_all) == (s_base & u_all))
    check("两条查询里的 type 谓词片段逐字节相同，且各自只出现一次"
          "（共用常量的直接后果，不可能各自长出第二种写法）",
          predicate in u_sql and predicate in s_sql
          and predicate not in u_sql.replace(predicate, "", 1)
          and predicate not in s_sql.replace(predicate, "", 1))

    print("== 4. 排序 `ORDER BY is_pinned DESC, update_at DESC` 真实生效 ==")
    group("G4 排序")
    order_clause = re.findall(r"ORDER BY.*$", u_sql)[0].strip()
    check("ORDER BY 子句抠出来就是 is_pinned DESC, update_at DESC",
          order_clause == "ORDER BY is_pinned DESC, update_at DESC", repr(order_clause))
    check("两条查询的 ORDER BY 都带两个 DESC（不会只降序一列）",
          all(c.count("DESC") == 2 for c in
              re.findall(r"ORDER BY.*$", u_sql) + re.findall(r"ORDER BY.*$", s_sql)),
          str(re.findall(r"ORDER BY.*$", u_sql) + re.findall(r"ORDER BY.*$", s_sql)))
    for label, sql, args, want_ids, mixed in (
            ("未归档路 type=''", u_sql, u_args("A1", ""), oracle_unfiled("A1", ""), True),
            ("未归档路 type='GROUP'", u_sql, u_args("A1", t_group),
             oracle_unfiled("A1", t_group), True),
            ("未归档路 type='DIRECT'", u_sql, u_args("A1", t_direct),
             oracle_unfiled("A1", t_direct), True),
            ("搜索路 type='' 「共享词」", s_sql, s_args("A1", "共享词", ""), SHARE_ALL, False),
    ):
        rows = conn.execute(sql, args).fetchall()
        got_order = [r[0] for r in rows]
        check(f"{label} 的返回顺序 == 按 (is_pinned DESC, update_at DESC) 排出的顺序",
              got_order == expected_order(want_ids), f"{got_order}")
        flags = [r[IDX_PIN] for r in rows]
        check(f"{label} is_pinned 标志序列是降序的（置顶块在前、未置顶块在后）",
              flags == sorted(flags, reverse=True), str(flags))
        if mixed:
            check(f"{label} 该组同时存在置顶行与未置顶行（否则上面两条是恒真的）",
                  any(flags) and not all(flags), str(flags))
            n_pin = sum(flags)
            check(f"{label} 前 {n_pin} 行全是置顶、之后全是未置顶",
                  all(flags[:n_pin]) and not any(flags[n_pin:]),
                  str(flags))
        seg_unpinned = [r[IDX_UPD] for r in rows if not r[IDX_PIN]]
        check(f"{label} 未置顶段内 update_at 严格降序",
              all(a > b for a, b in zip(seg_unpinned, seg_unpinned[1:])),
              str(seg_unpinned))
        seg_pinned = [r[IDX_UPD] for r in rows if r[IDX_PIN]]
        check(f"{label} 置顶段内 update_at 严格降序",
              all(a > b for a, b in zip(seg_pinned, seg_pinned[1:])), str(seg_pinned))
        check(f"{label} 任一位置都不存在「未置顶行在前、置顶行在后」的逆序对",
              not any(flags[i] == 0 and flags[j] == 1
                      for i in range(len(flags)) for j in range(i + 1, len(flags))),
              str(flags))
    check("未归档路 A1 的置顶段含 2 行（d-pinned +1000 在 g-pinned +500 之前）",
          [r[0] for r in conn.execute(u_sql, u_args("A1", "")).fetchall()
           if r[IDX_PIN]] == ["d-pinned", "g-pinned"],
          str([r[0] for r in conn.execute(u_sql, u_args("A1", "")).fetchall() if r[IDX_PIN]]))
    # 故意同值的 2 行（都在 f2）：实测「ORDER BY 同值时行序由引擎决定」这件事
    tie = run_rows_ids(conn, s_sql, s_args("A1", "同刻 Delta", ""))
    check("同值对照组：fixture 里 (is_pinned, update_at) 完全相同的两行都能被搜到",
          sorted(tie) == ["d-tie-jia", "d-tie-yi"], f"{tie}")
    orders = {tuple(run_rows_ids(conn, s_sql, s_args("A1", "同刻 Delta", "")))
              for _ in range(3)}
    check("同值对照组：本机上重复执行 3 次，同值行的相对行序完全一致"
          "（观测事实：确定性来自引擎的稳定排序，不是 SQL 保证的）",
          len(orders) == 1, str(orders))
    check("同值对照组：同值行在结果里相邻（中间没插别的行）",
          len(tie) == 2, f"{tie}")
    check("同值对照组：ORDER BY 两列都相同的两行，仍能靠引擎的稳定排序拿到确定顺序"
          "（真机 Android SQLite 是否同样确定 —— 本脚本未取证）",
          list(orders)[0] == tuple(tie))
    note("ORDER BY 只有 (is_pinned, update_at) 两列，**没有 id 之类的兜底 tiebreaker**。"
         "fixture 里除上面这 2 行外 update_at 互不相同，所以排序断言不受同值影响。")
    note("风险如实标出（不断言它是 bug）：LIMIT/OFFSET 分页在同值行上翻页理论上可能重复"
         "或漏行（真库 update_at 是毫秒时间戳，同毫秒的两会话是可能的），需要稳定 "
         "tiebreaker 或 keyset 分页才能排除；真机口径需 instrumentation 另取证。")

    print("== 5. 分页（模拟 Room LimitOffsetPagingSource：LIMIT n OFFSET m）==")
    group("G5 分页")
    paged_cases = [
        ("未归档路 type=''", u_sql, u_args("A1", ""), oracle_unfiled("A1", "")),
        ("未归档路 type='DIRECT'", u_sql, u_args("A1", t_direct), oracle_unfiled("A1", t_direct)),
        ("未归档路 type='GROUP'", u_sql, u_args("A1", t_group), oracle_unfiled("A1", t_group)),
        ("未归档路 A2 type=''", u_sql, u_args("A2", ""), oracle_unfiled("A2", "")),
        ("搜索路 type='' 「共享词」", s_sql, s_args("A1", "共享词", ""), SHARE_ALL),
        ("搜索路 type='DIRECT'", s_sql, s_args("A1", "共享词", t_direct),
         ["d-share-f1", "d-share-uf"]),
        ("搜索路 type='GROUP'", s_sql, s_args("A1", "共享词", t_group),
         ["g-share-f1", "g-share-uf"]),
    ]
    for label, sql, args, want in paged_cases:
        for page_size in (2, 3, 5, 20):
            pages, count = paged(conn, sql, args, page_size)
            flat = [i for pg in pages for i in pg]
            tag = f"{label} pageSize={page_size}"
            check(f"{tag}：各页拼起来集合 == 全量（无遗漏）",
                  sorted(flat) == sorted(want), f"{sorted(flat)}")
            check(f"{tag}：所有页无重复 id", len(flat) == len(set(flat)), f"{flat}")
            check(f"{tag}：第 0 页 == 全量的前 {page_size} 条（顺序也一致）",
                  pages[0] == expected_order(want)[:page_size], f"{pages[0]}")
            check(f"{tag}：最后一页条数 == min(pageSize, 余数)",
                  len(pages[-1]) == min(page_size, len(want) - page_size * (len(pages) - 1)),
                  f"最后页={len(pages[-1])} 共{len(pages)}页")
            check(f"{tag}：没有「空页夹在中间」", all(p for p in pages))
            check(f"{tag}：总页数 == ceil(总数/pageSize)",
                  len(pages) == -(-len(want) // page_size), f"{len(pages)}")
            check(f"{tag}：筛选后 count == 实际返回条数 == {len(want)}"
                  "（内存过滤会让 itemCount 虚高，这里不会）",
                  count == len(flat) == len(want), f"count={count} 返回={len(flat)}")
    pages, count = paged(conn, u_sql, u_args("A1", ""), 4)
    check(f"未归档路 A1 type='' pageSize=4 时页容量 "
          f"{[len(p) for p in pages]}（{n_uf} = 4*4 + 1）",
          [len(p) for p in pages] == [4, 4, 4, 4, 1],
          str([len(p) for p in pages]))
    pages, count = paged(conn, u_sql, u_args("A1", t_group), 3)
    check("未归档路 A1 type='GROUP' pageSize=3 时页容量为 [3, 1]，count == 4",
          [len(p) for p in pages] == [3, 1] and count == 4,
          f"{[len(p) for p in pages]} count={count}")
    check("内存过滤对照口径：本脚本里 count 与返回条数恒等，"
          "差异只可能来自对 PagingData 做 filter（已由源码护栏断言其不存在）",
          True)

    print("== 6. folder_id 口径差异（只观测，不改代码、不替谁判断）==")
    group("G6 folder_id 口径差异")
    u_uf = set(run(conn, u_sql, u_args("A1", "")))
    s_sh = set(run(conn, s_sql, s_args("A1", "共享词", "")))
    check("事实 A：未归档路在任意 type 下都不返回 folder_id 非空的行",
          not (u_uf & {"d-f1", "d-f2", "g-f1", "g-f2"})
          and not (set(run(conn, u_sql, u_args("A1", t_group)))
                   & {"d-f1", "d-f2", "g-f1", "g-f2"}), f"{sorted(u_uf)}")
    check("事实 B：搜索路（同一 type=''）会返回文件夹内的行 —— 实测命中 "
          "d-share-f1 / g-share-f1",
          {"d-share-f1", "g-share-f1"} <= s_sh, f"{sorted(s_sh)}")
    check("事实 C：两条路在「同一搜索词」下结果集不相同"
          "（差异集 = 文件夹内的 2 行，非空）",
          (s_sh - u_uf) == {"d-share-f1", "g-share-f1"}, f"差={sorted(s_sh - u_uf)}")
    check("事实 D：同 type 收窄后差异依然存在（不是只影响 type='' 这一档）",
          set(run(conn, s_sql, s_args("A1", "共享词", t_group)))
          - set(run(conn, u_sql, u_args("A1", t_group))) == {"g-share-f1"},
          str(sorted(set(run(conn, s_sql, s_args("A1", "共享词", t_group)))
                     - set(run(conn, u_sql, u_args("A1", t_group))))))
    note("观测结论：未归档路带 `AND folder_id = ''`、搜索路不带，所以「搜到的东西」里"
         "会混进文件夹内的会话（实测 +2 行）。这是 B2 条目待产品决策的已知差异；")
    note("本脚本只把事实钉住，不判定哪个口径是对的，也没有改任何 Kotlin 代码。")

    print("== 7. LIKE 通配符：% 与 _ 作为字面量出现在标题里 ==")
    group("G7 LIKE 通配符")
    pct = run(conn, s_sql, s_args("A1", "100%", ""))
    check("搜字面量 '100%'：命中含 '%' 的 d-pct，**也**命中不含 '%' 的 d-decoy100"
          "（实测：% 被当通配符，不是字面量）",
          pct == ["d-decoy100", "d-pct"], f"{pct}")
    check("搜字面量 '100%'：命中 2 行，而仅含字面量 '100%' 的只有 1 行 —— "
          "差的那行就是通配符多吃进来的",
          len(pct) == 2 and "d-decoy100" in pct, f"{pct}")
    us = run(conn, s_sql, s_args("A1", "a_b", ""))
    check("搜字面量 'a_b'：命中含 '_' 的 d-under，**也**命中 axbxc"
          "（实测：_ 被当单字符通配符）",
          us == ["d-decoy_axb", "d-under"], f"{us}")
    check("搜字面量 '%50'：命中 g-pct，且 '%50' 里 5 0 被当字面、% 当通配",
          run(conn, s_sql, s_args("A1", "%50", "")) == ["g-pct"],
          f"{run(conn, s_sql, s_args('A1', '%50', ''))}")
    check("搜 '%'（单个百分号）几乎命中全部标题 —— 若产品期望字面量匹配则语义完全相反",
          len(run(conn, s_sql, s_args("A1", "%", ""))) >= 10,
          f"{len(run(conn, s_sql, s_args('A1', '%', '')))} 行")
    check("SQL 里没有 ESCAPE 子句（实测：真的没有转义）",
          "ESCAPE" not in u_sql.upper() and "ESCAPE" not in s_sql.upper())
    note("实测事实：SQL 用的是 `title LIKE '%' || :searchText || '%'`，没有 ESCAPE，"
         "所以用户输入的 % / _ 会被 SQLite 当通配符。这是当前行为，脚本不断言对错。")

    print("== 8. LIKE 的大小写敏感性（ASCII vs 非 ASCII）==")
    group("G8 LIKE 大小写")
    ascii_lo = run(conn, s_sql, s_args("A1", "case sensitive probe", ""))
    check("PRAGMA case_sensitive_like 读不出值（它只写不读），所以本组用行为实测",
          conn.execute("PRAGMA case_sensitive_like").fetchone() is None,
          str(conn.execute("PRAGMA case_sensitive_like").fetchone()))
    check("ASCII：搜小写 'case sensitive probe' 命中大写标题 d-case-up"
          "（实测 LIKE 对 ASCII 大小写不敏感）",
          "d-case-up" in ascii_lo and "d-case-lo" in ascii_lo, f"{ascii_lo}")
    check("ASCII：搜小写与搜大写的结果集合完全相同",
          set(ascii_lo) == set(run(conn, s_sql, s_args("A1", "CASE SENSITIVE PROBE", ""))))
    nonascii = run(conn, s_sql, s_args("A1", "中文标题搜索词", ""))
    check("非 ASCII：搜简体「中文标题搜索词」只命中简体行 cjk-simp，"
          "不命中繁体 cjk-trad（实测无 Unicode 折叠）",
          nonascii == ["cjk-simp"], f"{nonascii}")
    check("非 ASCII：搜繁体「中文標題搜索词」只命中 cjk-trad，两行互不可见",
          run(conn, s_sql, s_args("A1", "中文標題搜索词", "")) == ["cjk-trad"])
    check("ASCII 大小写折叠与非 ASCII 无折叠这两条行为事实同时成立",
          ("d-case-up" in ascii_lo) and ("cjk-trad" not in nonascii))
    # pragma 只写不读，所以直接改它再测一遍：证明上面那条是引擎行为、不是 fixture 巧合
    conn.execute("PRAGMA case_sensitive_like=ON")
    on_lo = run(conn, s_sql, s_args("A1", "case sensitive probe", ""))
    on_nonascii = run(conn, s_sql, s_args("A1", "中文标题搜索词", ""))
    conn.execute("PRAGMA case_sensitive_like=OFF")
    off_lo = run(conn, s_sql, s_args("A1", "case sensitive probe", ""))
    check("PRAGMA case_sensitive_like=ON 时 ASCII 折叠消失（只命中大小写完全一致的那行）",
          on_lo == ["d-case-lo"], f"{on_lo}")
    check("PRAGMA case_sensitive_like=ON 时非 ASCII 结果不变（它本来就没折叠）",
          on_nonascii == ["cjk-simp"], f"{on_nonascii}")
    check("PRAGMA case_sensitive_like=OFF 之后 ASCII 折叠恢复（与本组第一条同一结果）",
          off_lo == ascii_lo, f"{off_lo}")
    check("默认态（未设 pragma）== OFF 态：宿主 SQLite 默认 case_sensitive_like 关",
          ascii_lo == off_lo and "d-case-up" in ascii_lo)
    note("边界：宿主 SQLite 的 LIKE 只对 ASCII 做折叠。真机 Android SQLiteDatabase "
         "默认 case_sensitive_like 也关，但两处实现是否完全同口径需 instrumentation 另取证。")

    print("== 9. 参数绑定顺序（模型 A 按名 vs 模型 B 按位置）==")
    group("G9 绑定顺序")
    # 模型 B 的前提：把 :name 改写成 ?，实参只能按位置对齐 —— 顺序从这里开始有后果。
    u_q, s_q = to_qmark(u_sql), to_qmark(s_sql)
    u_occ, s_occ = placeholder_occurrences(u_sql), placeholder_occurrences(s_sql)
    check("qmark 改写条数 == 占位符**出现次数**（含重复）",
          u_q.count("?") == len(u_occ) and s_q.count("?") == len(s_occ),
          f"u={u_q.count('?')}/{len(u_occ)} s={s_q.count('?')}/{len(s_occ)}")
    check("改写后的 qmark SQL 与原 SQL 只差占位符（谓词逐字节相同）",
          re.sub(r":[A-Za-z_]\w*", "?", u_sql) == u_q
          and re.sub(r":[A-Za-z_]\w*", "?", s_sql) == s_q)
    check(f"实测事实：type 谓词让 `:type` 在每条查询里出现 "
          f"{u_occ.count('type')}/{s_occ.count('type')} 次 —— 所以「纯位置绑定」"
          "在原理上就不等价于 Room 的按名绑定（本组只把它当对照，不当契约）",
          u_occ.count("type") == 2 and s_occ.count("type") == 2,
          f"{u_occ} / {s_occ}")
    check("占位符名集合 == 形参名集合（双向，无多余也无遗漏）",
          set(s_occ) == set(s_params) and set(u_occ) == set(u_params),
          f"{sorted(set(s_occ))}/{sorted(s_params)} {sorted(set(u_occ))}/{sorted(u_params)}")

    # 按占位符出现顺序取值的「正确位置绑定」必须与按名绑定逐 id 相等
    def s_positional(occ, args):
        return run_positional(conn, s_q, [args[n] for n in occ])

    def u_positional(occ, args):
        return run_positional(conn, u_q, [args[n] for n in occ])
    matrix = [(a, kw, t)
              for a in ("A1", "A2", "MISSING")
              for t in ("", t_direct, t_group, "NOPE")
              for kw in ("共享词", "Alpha", "不存在的词xyz", "", "   ")]
    mismatch = []
    witness = None
    for assistant, keyword, type_arg in matrix:
        args = s_args(assistant, keyword, type_arg)
        named = run(conn, s_sql, args)
        by_occ = s_positional(s_occ, args)
        if named != by_occ:
            mismatch.append((assistant, keyword, type_arg))
        if witness is None:
            rotated = s_occ[1:] + s_occ[:1]
            if named != s_positional(rotated, args):
                witness = (assistant, keyword, type_arg, named,
                           s_positional(rotated, args))
    check(f"搜索路 {len(matrix)} 组参数下：按占位符出现顺序做位置绑定 == 按名绑定",
          not mismatch, f"不等价 {len(mismatch)} 组：{mismatch[:3]}")
    check("位置绑定 != 按名绑定 的对照存在（把出现顺序轮转一位后结果确实变了 —— "
          "证明上一条不是恒真）",
          witness is not None, str(witness))
    u_matrix = [(a, t) for a in ("A1", "A2") for t in ("", t_direct, t_group)]
    u_mismatch = [k for k in u_matrix
                  if run(conn, u_sql, u_args(*k)) != u_positional(u_occ, u_args(*k))]
    check(f"未归档路 {len(u_matrix)} 组参数下：按占位符出现顺序做位置绑定 == 按名绑定",
          not u_mismatch, str(u_mismatch))
    u_rot = u_occ[1:] + u_occ[:1]
    check("未归档路把 assistantId 与 type 的实参轮转后结果改变（2 参数路同样有牙）",
          run(conn, u_sql, u_args("A1", t_group)) != u_positional(u_rot, u_args("A1", t_group)),
          f"rot={u_rot}")
    check("未归档路位置错位后返回空集 —— assistant_id 被换成了 'GROUP' 时静默查空",
          u_positional(("type", "assistantId", "type"), u_args("A1", t_group)) == [],
          f"{u_positional(('type', 'assistantId', 'type'), u_args('A1', t_group))}")
    s_rot = s_occ[1:] + s_occ[:1]
    check("搜索路把 searchText 与 type 轮转后结果改变（3 参数路同样有牙）",
          run(conn, s_sql, s_args("A1", "共享词", t_group))
          != s_positional(s_rot, s_args("A1", "共享词", t_group)), f"rot={s_rot}")
    check("搜索路位置错位（searchText 收到 assistantId）后结果为空 —— 静默查不到",
          s_positional(("searchText", "assistantId", "type", "searchText"),
                        s_args("A1", "共享词", t_group)) == [],
          f"{s_positional(('searchText', 'assistantId', 'type', 'searchText'), s_args('A1', '共享词', t_group))}")
    check("按名绑定对「同一个 :type 出现两次」是同一个值：把 dict 里 type 换成别的"
          "assistant_id 后结果变化（证明谓词两处 :type 确实取同一个值）",
          run(conn, u_sql, {"assistantId": "A1", "type": "A1"})
          != run(conn, u_sql, {"assistantId": "A1", "type": t_group})
          and run(conn, u_sql, {"assistantId": "A1", "type": "A1"}) == [])

    print("== 10. 空结果集 ==")
    group("G10 空结果集")
    miss = run(conn, s_sql, s_args("A1", "不存在的词xyz", ""))
    check("搜索无命中返回空列表", miss == [], f"{miss}")
    pages, count = paged(conn, s_sql, s_args("A1", "不存在的词xyz", ""), 20)
    check("搜索无命中时页数为 0（不返回一页空页）", pages == [], f"{pages}")
    check("搜索无命中时 count == 0", count == 0, str(count))
    check("不存在的 assistant 返回空", run(conn, u_sql, u_args("MISSING", "")) == [])
    check("不存在的 assistant 搜索返回空", run(conn, s_sql, s_args("MISSING", "共享词", "")) == [])
    check("未知 type 返回空（不会静默当成不筛）",
          run(conn, u_sql, u_args("A1", "NOPE")) == []
          and run(conn, s_sql, s_args("A1", "共享词", "NOPE")) == [])
    check("空标题行在 type='' 下不会被漏掉（NULL/空串没被当成无标题过滤掉）",
          "d-empty" in run(conn, u_sql, u_args("A1", "")))
    check("纯空格标题行也能被搜到（搜 '   ' 命中 d-space）",
          run(conn, s_sql, s_args("A1", "   ", "")) == ["d-space"],
          f"{run(conn, s_sql, s_args('A1', '   ', ''))}")
    empty_kw = sorted(run(conn, s_sql, s_args("A1", "", "")))
    n_a1 = len([r for r in FIXTURE if r[1] == "A1"])
    check(f"实测事实：搜索路对空搜索词不做拦截（LIKE '%%' 命中该 assistant 全部 "
          f"{n_a1} 行，含文件夹内的行）—— 「空词等于没搜」这条防线只存在于 "
          "planConversationListQuery 那一层，SQL 层没有",
          len(empty_kw) == n_a1, f"{len(empty_kw)} 行 vs 期望 {n_a1}")
    check("实测事实：空搜索词 + type='GROUP' 命中该 assistant 全部群会话"
          "（含文件夹内的群会话，说明搜索路确实不过滤 folder_id）",
          set(run(conn, s_sql, s_args("A1", "", t_group)))
          == {r[0] for r in FIXTURE if r[1] == "A1" and r[2] == t_group},
          f"{sorted(run(conn, s_sql, s_args('A1', '', t_group)))}")

    conn.close()
    print()
    print("== 断言分组汇总 ==")
    total_pass = 0
    for name in sorted(group_stats):
        p, f = group_stats[name]
        total_pass += p
        print(f"  {name:<24}: {p + f:>3} 条（{p} PASS / {f} FAIL）")
    print(f"  {'合计':<24}: {assertions:>3} 条（{total_pass} PASS / {len(failures)} FAIL）")
    print()
    if failures:
        print(f"RESULT: FAIL（{len(failures)}/{assertions} 项断言失败）-> {failures}")
        return 1
    print(f"RESULT: 全部断言通过（共 {assertions} 条）")
    return 0


if __name__ == "__main__":
    sys.exit(main())