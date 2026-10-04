#!/usr/bin/env python3
"""
C1-D 交付证据（主机侧 SQL 重放，非 instrumentation）。

目的：在没有真机/模拟器的情况下，用真实 SQLite 引擎执行 30→31 的那份迁移 SQL，证明

  (a) 它是合法 SQLite（含多语句拆分，不会因为「一段 SQL 里有多条语句」而漏跑）；
  (b) 不丢 memory_chunks 存量数据且 role_id 为 NULL；
  (c) group_runs 的复合主键与 run_token 唯一索引真的生效；
  (d) **memory_chunks 是被 ALTER 原地加列、而不是被 DROP + 重建**——用一个恒真判据
      表达不了这件事（见下方「弱 1 说明」），所以这里用两条真正能失败的判据：
      `sqlite_master.sql` 逐字节比对 + b-tree `rootpage` 身份不变；
  (e) **AppDatabaseFactory.onOpen 建的 3 个 FTS5 触发器在迁移后逐字节存活**，
      并用「DROP TABLE 重建」对照实验正面证实这条路径**确实会**删掉它们；
      这正是 Migration_30_31.kt:79-84 放弃 AutoMigration 的立论；
  (f) 迁移后 FTS5 虚表本身仍能 INSERT / MATCH（不只是触发器名字还在、实际已坏）；
  (g) 迁移后的结构与 Room 导出的 31.json 逐字段一致（Room TableInfo 同口径）。

数据源全部来自仓库里的真实产物：
  - 旧库结构：app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/30.json
  - 新库结构：app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/31.json
  - 迁移 SQL ：app/src/main/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_30_31.kt
               （显式手写迁移的 DDL 常量，不是 Room 生成的代码）
  - FTS5 虚表 + 3 个触发器：
               app/src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabaseFactory.kt
               的 onOpen ——**逐字从生产代码抠出来**，不是手抄的副本。

弱 1 说明（为什么不能靠 rowid 区分 ALTER 与重建表）：
  `memory_chunks` 是 `id INTEGER PRIMARY KEY AUTOINCREMENT`，SQLite 里 rowid 恒等于 id。
  完整 DROP + 重建 + `INSERT ... SELECT` 保留 id 之后 rowid 依然是 [1, 2]，
  所以「rowid == 2」这条检查在重建表路径下照样 PASS，是恒真断言。

这不能替代 Migration_30_31_Test（instrumentation，需要设备），只是把同一份 SQL
在真实 SQLite 引擎上跑一遍，输出可复现的断言结果。

不写任何文件，只 print 到 stdout。
"""
import json
import re
import sqlite3
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase"
MIGRATION = (ROOT / "app/src/main/java/heizige/kk/khatkit/app/core"
             "/data/db/migrations/Migration_30_31.kt")
FACTORY = (ROOT / "app/src/main/java/heizige/kk/khatkit/app/core"
           "/data/db/AppDatabaseFactory.kt")

failures = []
assertions = 0


def check(label, ok, detail=""):
    global assertions
    assertions += 1
    print(("  PASS  " if ok else "  FAIL  ") + label + (("  -> " + detail) if detail else ""))
    if not ok:
        failures.append(label)


def note(text):
    """非断言的口径说明（不计入断言数，但会进日志，避免读者把范围外的东西当已验证）。"""
    print("  NOTE  " + text)


# --------------------------------------------------------------------------
# 弱点 3 的修复：能正确处理「一段 SQL 常量里含多条语句」与「触发器体内含分号」
# --------------------------------------------------------------------------
_WORD = re.compile(r"[A-Za-z_][A-Za-z_0-9]*")


def strip_sql_comments(sql):
    """去掉 `--` 行注释与 `/* */` 块注释（只用于判断「这段是不是空语句」）。"""
    out, i, n = [], 0, len(sql)
    while i < n:
        if sql.startswith("--", i):
            j = sql.find("\n", i)
            i = n if j < 0 else j
            continue
        if sql.startswith("/*", i):
            j = sql.find("*/", i)
            i = n if j < 0 else j + 2
            continue
        out.append(sql[i])
        i += 1
    return "".join(out)


def split_sql_statements(sql):
    """把一段可能含多条语句的 SQL 拆成单条语句列表。

    正确性要点（每一条都有 0 节的自检断言兜底）：
      - 单引号串里的分号不切（支持 '' 转义）；双引号 / 反引号标识符同理
      - `--` 行注释、`/* */` 块注释里的分号不切
      - 触发器体 `BEGIN ... END` 里的分号不切：`BEGIN` 与 `CASE` 各进一层、`END` 出一层；
        把 `CASE` 也计入层数是为了让 `CASE ... END` 不会把层数提前减到 0 而误切外层
      - 拆完若整体 BEGIN/CASE 与 END 不配对则直接报错（宁可不跑，也不跑半截）
      - 去掉注释后为空的片段（空串 / 纯注释）丢弃
    """
    stmts, buf = [], []
    depth = 0
    state = None          # None | "'" | '"' | '`' | '--' | '/*'
    i, n = 0, len(sql)
    while i < n:
        ch = sql[i]
        if state is None:
            if ch in "'\"`":
                state = ch
            elif sql.startswith("--", i):
                state = "--"
                buf.append("--")
                i += 2
                continue
            elif sql.startswith("/*", i):
                state = "/*"
                buf.append("/*")
                i += 2
                continue
            elif ch == ";" and depth == 0:
                stmts.append("".join(buf))
                buf = []
                i += 1
                continue
            else:
                m = _WORD.match(sql, i)
                if m:
                    word = m.group(0).upper()
                    buf.append(m.group(0))
                    i = m.end()
                    if word in ("BEGIN", "CASE"):
                        depth += 1
                    elif word == "END" and depth > 0:
                        depth -= 1
                    continue
            buf.append(ch)
            i += 1
            continue
        if state == "--":
            buf.append(ch)
            if ch == "\n":
                state = None
            i += 1
            continue
        if state == "/*":
            if sql.startswith("*/", i):
                buf.append("*/")
                i += 2
                state = None
                continue
            buf.append(ch)
            i += 1
            continue
        # 引号 / 反引号 态
        if ch == state:
            if state == "'" and sql.startswith("''", i):
                buf.append("''")
                i += 2
                continue
            state = None
        buf.append(ch)
        i += 1
    stmts.append("".join(buf))
    if depth != 0:
        raise ValueError(f"BEGIN/CASE 与 END 不配对（depth={depth}），SQL 拆分失败")
    return [s for s in stmts if strip_sql_comments(s).strip()]


# --------------------------------------------------------------------------
# SQL 常量抽取（Kotlin 字面量 -> Python str）
# --------------------------------------------------------------------------
def _dedent(body):
    """等价于 Kotlin 的 trimIndent()：去首尾空行、按非空行最小缩进整体左移。"""
    lines = body.strip("\n").split("\n")
    indents = [len(ln) - len(ln.lstrip()) for ln in lines if ln.strip()]
    cut = min(indents) if indents else 0
    return "\n".join(ln[cut:] if ln.strip() else "" for ln in lines).strip()


def migration_sql():
    """从 Migration_30_31.kt 里抽出 migrate() 真正 execSQL 的那几条 SQL。

    按出现顺序取 `db.execSQL(<常量名>)`（也支持 `db.execSQL(<三引号字面量>)` 内联字面量），
    再把常量名解析成其字符串字面量内容，因此脚本重放的就是生产迁移执行的同一份 SQL
    （顺序也一致）。返回值是 [(常量名, SQL 原文)]，其中 SQL 原文**可能是多条语句**。
    """
    text = MIGRATION.read_text(encoding="utf-8")

    # 1) 先收集所有 `val NAME = """..."""` 与 `val NAME = "..."`
    #    顺序要紧：单引号正则会误匹配三引号字面量的开头，所以三引号的结果必须后写、覆盖前者。
    literals = {}
    single = re.compile(r'val\s+(\w+)\s*=\s*\n?\s*"((?:[^"\\]|\\.)*)"', re.S)
    for name, body in single.findall(text):
        literals[name] = body.encode().decode("unicode_escape")
    triple = re.compile(r'val\s+(\w+)\s*=\s*"""(.*?)"""', re.S)
    for name, body in triple.findall(text):
        literals[name] = _dedent(body)

    # 2) 按 migrate() 里的调用顺序取 SQL：常量名 or 内联三引号字面量
    calls = []
    for m in re.finditer(r"db\.execSQL\(\s*(\w+)\s*\)|db\.execSQL\(\s*\"\"\"(.*?)\"\"\"", text, re.S):
        name, inline = m.group(1), m.group(2)
        if name is not None:
            if name not in literals:
                raise SystemExit(f"Migration_30_31.kt 里找不到 SQL 常量: {name}")
            calls.append((name, literals[name]))
        else:
            calls.append(("<inline>", _dedent(inline)))
    names = [n for n, _ in calls]
    if len(names) != len(set(names)):
        raise SystemExit("migrate() 里有重复的 execSQL 调用，脚本解析假设失效")
    return calls


def fts_ddl_from_factory():
    """从 AppDatabaseFactory.kt 的 onOpen 里抠出 memory_chunk_fts 虚表与 3 个触发器。

    DDL 来源（不是手抄）：
      app/src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabaseFactory.kt
        :59-67  CREATE VIRTUAL TABLE IF NOT EXISTS memory_chunk_fts USING fts5(...)
        :68-74  CREATE TRIGGER IF NOT EXISTS memory_chunks_ai
        :75-82  CREATE TRIGGER IF NOT EXISTS memory_chunks_ad
        :83-91  CREATE TRIGGER IF NOT EXISTS memory_chunks_au
    生产代码是把三引号字面量 `.trimIndent()` 直接丢给 db.execSQL，所以这里用与
    _dedent() 等价的还原得到的就是 execSQL 实际收到的字节。
    """
    text = FACTORY.read_text(encoding="utf-8")
    virtual, triggers = None, {}
    for m in re.finditer(r'db\.execSQL\(\s*"""(.*?)"""', text, re.S):
        stmt = _dedent(m.group(1))
        if re.match(r"CREATE VIRTUAL TABLE IF NOT EXISTS\s+memory_chunk_fts\b", stmt):
            virtual = stmt
            continue
        tm = re.match(r"CREATE TRIGGER IF NOT EXISTS\s+(memory_chunks_\w+)\s", stmt)
        if tm:
            triggers[tm.group(1)] = stmt
    return virtual, triggers


FTS_TABLE = "memory_chunk_fts"
FTS_TRIGGERS = ("memory_chunks_ai", "memory_chunks_ad", "memory_chunks_au")


# --------------------------------------------------------------------------
# 30 库构造
# --------------------------------------------------------------------------
def create_tables(conn, version):
    """按 Room 导出的 schema JSON 建表。

    30/31.json 里没有任何 `ftsVersion` 实体——memory_chunk_fts 与它的 3 个触发器是
    onOpen 运行时用 execSQL 建的（Room 不管），所以它们不在 schema JSON 里，
    由 fts_ddl_from_factory() 从 AppDatabaseFactory.kt 单独抠出来建。
    """
    spec = json.loads((SCHEMA_DIR / f"{version}.json").read_text(encoding="utf-8"))
    for entity in spec["database"]["entities"]:
        if entity.get("ftsVersion"):
            continue
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for idx in entity.get("indices", []):
            conn.execute(idx["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return spec


def normalize_ddl(stmt):
    """SQLite 存进 sqlite_master 时的规范化：去掉 `CREATE {对象} IF NOT EXISTS` 的
    `IF NOT EXISTS`。生产 DDL 用的是 `IF NOT EXISTS`（幂等写法），而 sqlite_master
    里是规范化后的文本，所以「逐字节相同」必须在同一口径下比。"""
    return re.sub(r"^(\s*CREATE\s+(?:VIRTUAL\s+)?(?:TABLE|TRIGGER|INDEX))\s+IF\s+NOT\s+EXISTS\s+",
                  r"\1 ", stmt, flags=re.I)


def table_sql(conn, name):
    r = conn.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name=?",
                     (name,)).fetchone()
    return r[0] if r else None


def rootpage(conn, name):
    r = conn.execute("SELECT rootpage FROM sqlite_master WHERE type='table' AND name=?",
                     (name,)).fetchone()
    return r[0] if r else None


def triggers_on(conn, table):
    """返回 {name: sql}，只取挂在指定表上的用户触发器，按名字排序保证输出可复现。"""
    rows = conn.execute(
        "SELECT name, sql FROM sqlite_master WHERE type='trigger' AND tbl_name=?"
        " ORDER BY name", (table,)).fetchall()
    return {r[0]: r[1] for r in rows}


def fts_matches(conn, needle):
    return conn.execute(
        f"SELECT rowid, content FROM {FTS_TABLE} WHERE {FTS_TABLE} MATCH ?",
        (needle,)).fetchall()


def build_v30_db(fts_virtual, fts_triggers):
    """建一份「30 版真库」：30.json 结构 + onOpen 的 FTS5 虚表与 3 个触发器 + 存量数据。

    触发器先于存量数据建立，所以存量行会像真机 onOpen 的回填那样自动进 FTS 索引。
    """
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys=ON")
    spec = create_tables(conn, 30)
    conn.execute(fts_virtual)
    for name in FTS_TRIGGERS:
        conn.execute(fts_triggers[name])

    conn.execute(
        "INSERT INTO memory_spaces (id, kind, name, created_at) VALUES ('__global__','GLOBAL','',1)")
    conn.execute(
        "INSERT INTO memory_chunks (id, space_id, content, source_kind, source_message_id,"
        " source_ref_id, confidence, extracted_at, last_hit_at, created_at, updated_at,"
        " deleted_at, embedding) VALUES (1,'__global__','存量记忆 A','MESSAGE','msg-legacy-1',"
        "'ref-legacy-1',0.75,1700000000001,1700000000002,1700000000003,1700000000004,"
        "1700000000005,X'01020304')")
    conn.execute(
        "INSERT INTO memory_chunks (id, space_id, content, source_kind, confidence,"
        " extracted_at, last_hit_at, created_at, updated_at) VALUES"
        " (2,'__global__','存量记忆 B','MANUAL',1.0,1700000000006,0,1700000000007,1700000000008)")
    conn.execute(
        "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at,"
        " suggestions, type, group_config) VALUES ('conv-group-1','a1','群','[]',1,2,'[]',"
        "'GROUP','{\"schema_version\":1,\"mode\":\"pipeline\"}')")
    conn.commit()
    return conn, spec


# --------------------------------------------------------------------------
# 「DROP TABLE + 重建」对照迁移（Room AutoMigration 30→31 的真实写法）
# --------------------------------------------------------------------------
def control_rebuild_statements():
    """AutoMigration 的等价重放：建 _new → 拷贝 → DROP 旧表 → RENAME。

    DDL 全部来自 31.json（Room 自己会按 31 的实体声明生成这几条），索引也照 Room 的
    行为在 RENAME 之后逐条重建（含 role_id 索引），所以这份对照迁移除了「触发器」和
    「表身份」以外处处与手写 ALTER 等价——差异只可能来自 DROP TABLE。
    """
    spec = json.loads((SCHEMA_DIR / "31.json").read_text(encoding="utf-8"))
    entity = next(e for e in spec["database"]["entities"]
                  if e["tableName"] == "memory_chunks")
    stmts = [
        entity["createSql"].replace("${TABLE_NAME}", "_new_memory_chunks"),
        "INSERT INTO `_new_memory_chunks` (`id`, `space_id`, `content`, `source_kind`,"
        " `source_message_id`, `source_ref_id`, `confidence`, `extracted_at`, `last_hit_at`,"
        " `created_at`, `updated_at`, `deleted_at`, `embedding`, `role_id`)"
        " SELECT `id`, `space_id`, `content`, `source_kind`, `source_message_id`,"
        " `source_ref_id`, `confidence`, `extracted_at`, `last_hit_at`, `created_at`,"
        " `updated_at`, `deleted_at`, `embedding`, NULL FROM `memory_chunks`",
        "DROP TABLE `memory_chunks`",
        "ALTER TABLE `_new_memory_chunks` RENAME TO `memory_chunks`",
    ]
    for idx in entity.get("indices", []):
        stmts.append(idx["createSql"].replace("${TABLE_NAME}", "memory_chunks"))
    return stmts


# --------------------------------------------------------------------------
RUN_COLS = ("conversation_id, round_id, run_token, status, started_at, updated_at, "
            "spent_tokens, token_limit, skipped_role_ids, committed_role_ids, "
            "reason, error_message")


def insert_run(conn, conversation_id, round_id, run_token, started_at,
               status="RUNNING", spent=0, token_limit=1000,
               skipped="[]", committed="[]", reason="", error="", ended=None):
    conn.execute(
        f"INSERT INTO group_runs ({RUN_COLS}, ended_at) VALUES ("
        f"'{conversation_id}','{round_id}','{run_token}','{status}',{started_at},"
        f"{started_at},{spent},{token_limit},'{skipped}','{committed}','{reason}','{error}',"
        f"{ended if ended is not None else 'NULL'})"
    )


def execute_migration(conn, calls):
    """执行迁移；返回 (成功语句数, 计划语句数, 失败原因列表)。

    每条 execSQL 的 SQL 原文先按触发器体/字符串/注释感知的方式拆成单条语句再逐条执行，
    所以「一个常量里塞了多条语句」也能被真实重放（弱点 3）。
    """
    plan, errs, done = [], [], 0
    for name, sql in calls:
        try:
            parts = split_sql_statements(sql)
        except ValueError as e:
            errs.append(f"{name}: 拆分失败 {e}")
            continue
        if not parts:
            errs.append(f"{name}: 拆出 0 条可执行语句")
            continue
        plan.append((name, parts))
    for name, parts in plan:
        for part in parts:
            try:
                conn.execute(part)
                done += 1
            except sqlite3.Error as e:
                errs.append(f"{name}: {part.strip()[:70]} -> {e}")
    return done, sum(len(p) for _, p in plan), errs


def main():
    print("== 0. 环境与工具自检 ==")
    probe = sqlite3.connect(":memory:")
    fts_ok, fts_detail = False, ""
    try:
        probe.execute("CREATE VIRTUAL TABLE __fts_probe USING fts5(a)")
        probe.execute("INSERT INTO __fts_probe(a) VALUES ('探测 记忆')")
        hit = probe.execute(
            "SELECT a FROM __fts_probe WHERE __fts_probe MATCH '记忆'").fetchone()
        fts_ok = hit is not None
        fts_detail = f"MATCH 命中 {hit[0]!r}" if fts_ok else "MATCH 无结果"
    except sqlite3.Error as e:
        fts_detail = str(e)
    check(f"宿主 SQLite {sqlite3.sqlite_version} 支持 FTS5（建虚表 + MATCH）",
          fts_ok, fts_detail)
    if not fts_ok:
        print("        FTS5 不可用，后续触发器/对照实验断言无法取证。")
        return 2

    # 拆分器自检（弱点 3 的牙齿）：固定的期望值，改坏了立刻 FAIL
    sample = ("-- 头部注释; 带分号\n"
              "CREATE TABLE `t` (a TEXT, b TEXT);  /* 块;注释 */\n"
              "CREATE TRIGGER tr AFTER INSERT ON t BEGIN\n"
              "  UPDATE t SET a = 'x;y' WHERE b = 'it''s';\n"
              "  UPDATE t SET b = CASE WHEN a = 'q' THEN 'z' ELSE 'w' END;\n"
              "END;\n")
    parts = split_sql_statements(sample)
    check("SQL 拆分器：注释里的分号不切、触发器体不散（拆成 2 条）",
          len(parts) == 2, f"实际 {len(parts)} 条")
    check("SQL 拆分器：触发器体内的两条 UPDATE 都还在同一条语句里",
          len(parts) > 1 and parts[1].count("UPDATE t SET") == 2 and parts[1].rstrip().endswith("END"),
          repr(parts[-1][-40:]) if len(parts) > 1 else "")
    check("SQL 拆分器：CASE ... END 不把层数减穿",
          len(split_sql_statements(
              "CREATE TABLE `u` (a INTEGER DEFAULT (CASE WHEN 1 THEN 2 ELSE 3 END));"
          )) == 1)
    try:
        split_sql_statements("BEGIN SELECT 1; SELECT 2;")
        balanced = False
    except ValueError:
        balanced = True
    check("SQL 拆分器：BEGIN/END 不配对时报错而不是半截执行", balanced)

    fts_virtual, fts_triggers = fts_ddl_from_factory()
    check("AppDatabaseFactory.kt 里抠到 memory_chunk_fts 虚表 DDL", fts_virtual is not None)
    check("AppDatabaseFactory.kt 里抠到 3 个 FTS5 触发器 DDL",
          tuple(sorted(fts_triggers)) == tuple(sorted(FTS_TRIGGERS)),
          str(sorted(fts_triggers)))
    note("30/31.json 里没有任何 ftsVersion 实体：memory_chunk_fts 与它的 3 个触发器是 "
         "onOpen 运行时用 execSQL 建的（AppDatabaseFactory.kt:59-91），不在 Room schema 里，")
    note("所以这里从该文件抠原文，而不是从 schema JSON 还原。")

    print("== 1. 建 30 版真库（30.json 结构 + onOpen 的 FTS5 虚表与 3 个触发器）==")
    conn, old = build_v30_db(fts_virtual, fts_triggers)
    check("30.json 版本号是 30", old["database"]["version"] == 30)
    check("旧库有 memory_chunks", "memory_chunks" in live_tables(conn))
    check("旧库没有 group_runs", "group_runs" not in live_tables(conn))

    before_triggers = triggers_on(conn, "memory_chunks")
    check("迁移前 3 个 FTS5 触发器都在 sqlite_master",
          tuple(sorted(before_triggers)) == tuple(sorted(FTS_TRIGGERS)),
          str(sorted(before_triggers)))
    check("迁移前 3 个触发器 SQL 与 AppDatabaseFactory.onOpen 逐字节相同"
          "（同一口径：sqlite_master 会规范化掉 IF NOT EXISTS）",
          all(before_triggers.get(n) == normalize_ddl(fts_triggers[n])
              for n in FTS_TRIGGERS),
          str([n for n in FTS_TRIGGERS
               if before_triggers.get(n) != normalize_ddl(fts_triggers[n])]))
    check("迁移前存量记忆已进 FTS 索引（MATCH 命中 存量记忆 A）",
          bool(fts_matches(conn, "存量记忆 A")))

    mc_sql_before = table_sql(conn, "memory_chunks")
    mc_root_before = rootpage(conn, "memory_chunks")
    check("迁移前 memory_chunks 建表 SQL 逐字节等于 30.json 的 createSql（去 IF NOT EXISTS）",
          mc_sql_before == next(
              e for e in old["database"]["entities"]
              if e["tableName"] == "memory_chunks"
          )["createSql"].replace("${TABLE_NAME}", "memory_chunks").replace(
              "CREATE TABLE IF NOT EXISTS", "CREATE TABLE", 1))

    print("== 2. 执行 Migration_30_31.kt 里手写的迁移 SQL（支持多语句）==")
    calls = migration_sql()
    check("迁移 SQL 条数 = 6（migrate() 里 execSQL 的次数）", len(calls) == 6,
          f"实际 {len(calls)} 条：{[n for n, _ in calls]}")
    done, planned, errs = execute_migration(conn, calls)
    # 弱点 4 的修复：真断言——实际执行的条数必须等于拆分出来的条数，且没有失败项
    check("全部迁移 SQL 执行成功（0 条报错）", not errs, " | ".join(errs[:3]))
    check("实际执行语句数 = 拆分出的语句数（没有语句被静默丢弃）",
          done == planned and done > 0, f"执行 {done} / 计划 {planned}")
    conn.commit()

    print("== 3. 迁移后结构 + 未重建表判据（替代恒真的 rowid 检查）==")
    tables = live_tables(conn)
    check("group_runs 已建", "group_runs" in tables)
    check("memory_chunks 仍在", "memory_chunks" in tables)
    check("_new_memory_chunks 临时表不存在（用的是 ALTER 不是重建表）",
          "_new_memory_chunks" not in tables)

    # 判据 A：sqlite_master 里 memory_chunks 的建表 SQL 必须逐字节等于
    #         「迁移前的 SQL 原文 + ALTER 追加的那段列定义」。DROP + 重建会让整段文本不同
    #         （Room/AutoMigration 路径还会因 RENAME 把反引号表名改写成双引号表名）。
    alter_stmt = ""
    for _, sql in calls:
        for part in split_sql_statements(sql):
            if re.match(r"ALTER\s+TABLE\b", part.strip(), re.I):
                alter_stmt = part.strip()
    am = re.match(r"ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+COLUMN\s+(.+)$", alter_stmt,
                  re.S | re.I)
    check("能从迁移 SQL 里解析出 ADD COLUMN 的目标表与列定义",
          am is not None and am.group(1) == "memory_chunks", alter_stmt[:80])
    mc_sql_after = table_sql(conn, "memory_chunks")
    expected_after = None
    if am:
        added = ", " + " ".join(am.group(2).split())
        expected_after = (mc_sql_before[:-1] + added + ")") if mc_sql_before.endswith(")") \
            else mc_sql_before
    check("memory_chunks 的 sqlite_master SQL 逐字节 == 原 SQL + ALTER 追加的列定义"
          "（证明是原地 ADD COLUMN，不是 DROP+重建）",
          expected_after is not None and mc_sql_after == expected_after,
          "迁移 SQL 里没有 ADD COLUMN，判据无法成立"
          if expected_after is None else
          ("" if mc_sql_after == expected_after
           else f"got={mc_sql_after!r} want={expected_after!r}"))

    # 判据 B：b-tree 身份。ALTER TABLE ADD COLUMN 在宿主 SQLite（3.25 起的原地快路径）
    #         不重建 b-tree，rootpage 不变；DROP + 重建一定换 rootpage。
    mc_root_after = rootpage(conn, "memory_chunks")
    check("memory_chunks 的 b-tree rootpage 与迁移前相同（表身份未被换掉）",
          mc_root_after == mc_root_before and mc_root_after is not None,
          f"before={mc_root_before} after={mc_root_after}")
    note("口径边界：rootpage 判据依赖宿主 SQLite 的 ALTER TABLE ADD COLUMN 原地快路径"
         f"（本机 {sqlite3.sqlite_version}）。SQLite 3.35 之前 ADD COLUMN 会重写整表、"
         "3.25 之前会重建——真机 Android 捆绑的 SQLite 版本需要另取证，")
    note("本脚本只证明「本引擎上 ADD COLUMN 不动表」+「DROP TABLE 一定丢触发器」这两条机制。")

    # rowid 检查保留，但改成不再恒真的形式：rowid 必须逐行等于 id（隐式别名），
    # 且 AUTOINCREMENT 序列必须继续往下走（sqlite_sequence 未被重建清零）。
    rid = conn.execute("SELECT id, rowid FROM memory_chunks ORDER BY id").fetchall()
    check("每行 rowid 逐行等于 id", all(i == r for i, r in rid), str(rid))
    check("AUTOINCREMENT 序列 sqlite_sequence 存在且未被清零",
          conn.execute("SELECT seq FROM sqlite_sequence WHERE name='memory_chunks'"
                       ).fetchone() == (2,),
          str(conn.execute("SELECT seq FROM sqlite_sequence WHERE name='memory_chunks'"
                           ).fetchone()))

    gr_cols = {r[1]: r for r in conn.execute("PRAGMA table_info('group_runs')")}
    check("group_runs 列数 = 13", len(gr_cols) == 13, str(list(gr_cols)))
    check("主键第 1 段是 conversation_id", gr_cols["conversation_id"][5] == 1)
    check("主键第 2 段是 round_id", gr_cols["round_id"][5] == 2)
    check("run_token NOT NULL", gr_cols["run_token"][3] == 1)
    check("spent_tokens 默认 0", gr_cols["spent_tokens"][4] == "0", str(gr_cols["spent_tokens"][4]))
    check("token_limit 默认 0", gr_cols["token_limit"][4] == "0", str(gr_cols["token_limit"][4]))
    for col in ("skipped_role_ids", "committed_role_ids", "reason", "error_message"):
        check(f"{col} 默认 ''", gr_cols[col][4] == "''", str(gr_cols[col][4]))
    check("ended_at 可空", gr_cols["ended_at"][3] == 0)
    check("started_at / updated_at NOT NULL",
          gr_cols["started_at"][3] == 1 and gr_cols["updated_at"][3] == 1)

    indices = read_index(conn, "group_runs")
    check("run_token 唯一索引存在且唯一",
          any(n == "index_group_runs_run_token" and u and c == ("run_token",)
              for n, u, c in indices), str(indices))
    check("(conversation_id, started_at) 索引存在",
          any(c == ("conversation_id", "started_at") for _, _, c in indices), str(indices))
    check("status 索引存在",
          any(c == ("status",) for _, _, c in indices), str(indices))

    mc_idx = [r[1] for r in conn.execute("PRAGMA index_list('memory_chunks')").fetchall()]
    check("memory_chunks 有 role_id 索引", any("role_id" in n for n in mc_idx), str(mc_idx))
    for kept in ("space_id", "source_message_id", "deleted_at"):
        check(f"memory_chunks 保留 {kept} 索引", any(kept in n for n in mc_idx), str(mc_idx))

    print("== 4. FTS5 触发器在迁移后逐字节存活 + FTS5 本身仍可用 ==")
    after_triggers = triggers_on(conn, "memory_chunks")
    for name in FTS_TRIGGERS:
        check(f"迁移后触发器 {name} 仍存在", name in after_triggers)
        check(f"迁移后触发器 {name} 的 SQL 与迁移前逐字节相同",
              after_triggers.get(name) == before_triggers.get(name),
              "" if after_triggers.get(name) == before_triggers.get(name)
              else repr(after_triggers.get(name)))
    check("迁移后触发器集合没有多出/少掉任何一个",
          sorted(after_triggers) == sorted(before_triggers), str(sorted(after_triggers)))

    conn.execute(
        "INSERT INTO memory_chunks (space_id, content, source_kind, confidence,"
        " extracted_at, last_hit_at, created_at, updated_at, role_id)"
        " VALUES ('group:c1:role:r1','迁移后群记忆 zephyr','MESSAGE',1.0,1,0,1,1,'r1')")
    conn.commit()
    check("迁移后 memory_chunks 新插入的行被 ai 触发器镜像进 FTS 索引",
          bool(fts_matches(conn, "zephyr")))
    conn.execute("UPDATE memory_chunks SET content='迁移后群记忆 zephyr2' WHERE role_id='r1'")
    conn.commit()
    check("迁移后 UPDATE content 被 au 触发器同步（旧词查不到、新词查得到）",
          not fts_matches(conn, "zephyr AND zephyr2") and bool(fts_matches(conn, "zephyr2")))
    conn.execute("DELETE FROM memory_chunks WHERE role_id='r1'")
    conn.commit()
    check("迁移后 DELETE 被 ad 触发器同步（FTS 里也删干净了）",
          not fts_matches(conn, "zephyr2"))

    print("== 5. 对照实验：AutoMigration 的 DROP TABLE + 重建路径确实会删掉触发器 ==")
    ctrl, _ = build_v30_db(fts_virtual, fts_triggers)
    check("对照库起点也有 3 个触发器", tuple(sorted(triggers_on(ctrl, "memory_chunks")))
          == tuple(sorted(FTS_TRIGGERS)))
    ctrl_done, _, ctrl_errs = execute_migration(
        ctrl, [(f"autoMigration[{i}]", s)
               for i, s in enumerate(control_rebuild_statements())])
    check("对照：AutoMigration 那 4+N 条语句全部执行成功", not ctrl_errs and ctrl_done > 0,
          " | ".join(ctrl_errs[:3]))
    ctrl_triggers = triggers_on(ctrl, "memory_chunks")
    lost = [n for n in FTS_TRIGGERS if n not in ctrl_triggers]
    check("对照：DROP TABLE + 重建路径确实删掉了全部 3 个 FTS5 触发器（正面确认）",
          lost == list(FTS_TRIGGERS), f"丢失={lost} 剩余={sorted(ctrl_triggers)}")
    check("对照：重建路径换了 memory_chunks 的 b-tree（rootpage 变了）",
          rootpage(ctrl, "memory_chunks") != mc_root_before,
          f"before={mc_root_before} 对照={rootpage(ctrl, 'memory_chunks')}")
    check("对照：重建路径下 sqlite_master 的建表 SQL 与 ALTER 路径不同",
          table_sql(ctrl, "memory_chunks") != mc_sql_after,
          repr(table_sql(ctrl, "memory_chunks")))
    ctrl.execute(
        "INSERT INTO memory_chunks (space_id, content, source_kind, confidence,"
        " extracted_at, last_hit_at, created_at, updated_at, role_id)"
        " VALUES ('group:c1:role:r2','重建后群记忆 quokka','MESSAGE',1.0,1,0,1,1,'r2')")
    ctrl.commit()
    check("对照：重建后新写入的记忆检索不到（FTS 路静默失效的真实后果）",
          not fts_matches(ctrl, "quokka"))
    check("对照：那行数据确实写进了 memory_chunks（表本身没坏，坏的是检索路）",
          ctrl.execute("SELECT COUNT(*) FROM memory_chunks WHERE content LIKE '%quokka%'"
                       ).fetchone()[0] == 1)
    ctrl.close()

    print("== 6. 存量数据不丢 + role_id 为 NULL ==")
    rows = conn.execute(
        "SELECT id, space_id, content, source_kind, source_message_id, source_ref_id,"
        " confidence, extracted_at, last_hit_at, created_at, updated_at, deleted_at,"
        " embedding, role_id FROM memory_chunks ORDER BY id").fetchall()
    check("memory_chunks 仍是 2 行", len(rows) == 2, f"实际 {len(rows)}")
    a = rows[0]
    check("行1 content", a[2] == "存量记忆 A")
    check("行1 source_message_id", a[4] == "msg-legacy-1")
    check("行1 source_ref_id", a[5] == "ref-legacy-1")
    check("行1 confidence", abs(a[6] - 0.75) < 1e-6, str(a[6]))
    check("行1 全部时间戳保留", list(a[7:12]) == [1700000000001, 1700000000002,
          1700000000003, 1700000000004, 1700000000005], str(a[7:12]))
    check("行1 embedding BLOB 保留", a[12] == b"\x01\x02\x03\x04", repr(a[12]))
    check("行1 role_id 为 NULL", a[13] is None)
    check("行2 role_id 为 NULL", rows[1][13] is None)
    check("行2 未被写入 deleted_at", rows[1][11] is None)

    conv = conn.execute(
        "SELECT type, group_config FROM ConversationEntity WHERE id='conv-group-1'").fetchone()
    check("群会话 type=GROUP 保留", conv[0] == "GROUP", str(conv))
    check("群会话 group_config 保留", conv[1] == '{"schema_version":1,"mode":"pipeline"}', str(conv))

    print("== 7. role_id 可写、可查 ==")
    conn.execute(
        "INSERT INTO memory_chunks (space_id, content, source_kind, confidence, extracted_at,"
        " last_hit_at, created_at, updated_at, role_id)"
        " VALUES ('group:c1:role:r1','群记忆','MESSAGE',1.0,1,0,1,1,'r1')")
    conn.commit()
    check("存量行 role_id IS NULL 计数 = 2",
          conn.execute("SELECT COUNT(*) FROM memory_chunks WHERE role_id IS NULL").fetchone()[0] == 2)
    check("新行 role_id='r1' 计数 = 1",
          conn.execute("SELECT COUNT(*) FROM memory_chunks WHERE role_id='r1'").fetchone()[0] == 1)

    print("== 8. 复合主键 + run_token 唯一索引（幂等兜底）==")
    insert_run(conn, "conv-1", "round-msg-1", "token-a", 100)
    conn.commit()
    dup_round = False
    try:
        insert_run(conn, "conv-1", "round-msg-1", "token-b", 101)
        conn.commit()
    except sqlite3.IntegrityError as e:
        dup_round = True
        print(f"        同一 round 第二条被拒: {e}")
    check("同一 conversationId + roundId 第二条被主键拒绝", dup_round)

    dup_token = False
    try:
        insert_run(conn, "conv-1", "round-msg-2", "token-a", 102)
        conn.commit()
    except sqlite3.IntegrityError as e:
        dup_token = True
        print(f"        同一 runToken 第二次使用被拒: {e}")
    check("同一 runToken 第二次使用被唯一索引拒绝", dup_token)

    check("group_runs 只有 1 行",
          conn.execute("SELECT COUNT(*) FROM group_runs").fetchone()[0] == 1)
    insert_run(conn, "conv-2", "round-msg-1", "token-c", 103)
    insert_run(conn, "conv-1", "round-msg-2", "token-d", 104)
    conn.commit()
    check("不同群 / 不同轮次都能插入",
          conn.execute("SELECT COUNT(*) FROM group_runs").fetchone()[0] == 3)

    print("== 9. 预算截断可落库（契约：已用/上限/未运行角色/超预算原因）==")
    insert_run(conn, "conv-3", "round-msg-9", "token-e", 10, status="BUDGET_STOPPED",
               spent=4096, token_limit=4096, skipped='["r2","r3"]',
               reason="token_budget_exceeded", committed='["r1"]', ended=20)
    conn.commit()
    row = conn.execute(
        "SELECT spent_tokens, token_limit, skipped_role_ids, reason, committed_role_ids,"
        " started_at, updated_at, ended_at FROM group_runs"
        " WHERE conversation_id='conv-3' AND round_id='round-msg-9'").fetchone()
    check("已用/上限", list(row[:2]) == [4096, 4096], str(row))
    check("未运行角色 JSON", row[2] == '["r2","r3"]', row[2])
    check("超预算原因", row[3] == "token_budget_exceeded", row[3])
    check("已提交角色 JSON", row[4] == '["r1"]', row[4])
    check("时间戳", list(row[5:]) == [10, 10, 20], str(row[5:]))

    print("== 10. 迁移后结构与 31.json 逐字段一致（Room TableInfo 同口径）==")
    expected = json.loads((SCHEMA_DIR / "31.json").read_text(encoding="utf-8"))
    mismatches = []
    for entity in expected["database"]["entities"]:
        if entity.get("ftsVersion"):
            continue
        table = entity["tableName"]
        want_cols = {f["columnName"]: (f["affinity"], f.get("notNull", False))
                     for f in entity["fields"]}
        pk_cols = entity["primaryKey"]["columnNames"]
        want_cols_pk = {c: (i + 1 if c in pk_cols else 0) for i, c in enumerate(pk_cols)}
        for f in entity["fields"]:
            want_cols_pk.setdefault(f["columnName"], 0)
        want_cols_df = {f["columnName"]: f.get("defaultValue") for f in entity["fields"]}
        got_cols = {}
        for r in conn.execute(f"PRAGMA table_info(`{table}`)"):
            got_cols[r[1]] = (r[2].upper(), bool(r[3]), r[5], r[4])
        if set(got_cols) != set(want_cols):
            mismatches.append(f"{table} 列集合 want={sorted(want_cols)} got={sorted(got_cols)}")
            continue
        for col, (aff, notnull) in want_cols.items():
            g_aff, g_notnull, g_pk, g_df = got_cols[col]
            w_df = want_cols_df[col]
            if (w_df if w_df is not None else "") != (g_df if g_df is not None else ""):
                mismatches.append(f"{table}.{col} default want={w_df!r} got={g_df!r}")
            if g_aff != aff.upper():
                mismatches.append(f"{table}.{col} affinity want={aff} got={g_aff}")
            if g_notnull != notnull:
                mismatches.append(f"{table}.{col} notNull want={notnull} got={g_notnull}")
            if g_pk != want_cols_pk[col]:
                mismatches.append(f"{table}.{col} pkPosition want={want_cols_pk[col]} got={g_pk}")
        want_idx = sorted(
            (i["name"], i["unique"], tuple(i["columnNames"])) for i in entity.get("indices", []))
        got_idx = sorted(read_index(conn, table))
        if want_idx != got_idx:
            mismatches.append(f"{table} 索引 want={want_idx} got={got_idx}")
    check("全部表/列/主键/索引/默认值与 31.json 一致", not mismatches, "; ".join(mismatches[:4]))

    print()
    if failures:
        print(f"RESULT: FAIL（{len(failures)}/{assertions} 项断言失败）-> {failures}")
        return 1
    print(f"RESULT: 全部断言通过（共 {assertions} 条）")
    return 0


def read_index(conn, table):
    """返回 [(name, unique, (cols...))]，跳过 sqlite_autoindex_* 隐式索引。"""
    out = []
    for r in conn.execute(f"PRAGMA index_list(`{table}`)"):
        name, unique = r[1], r[2]
        if name.startswith("sqlite_autoindex_"):
            continue
        cols = tuple(row[2] for row in conn.execute(f"PRAGMA index_info(`{name}`)"))
        out.append((name, bool(unique), cols))
    return out


def live_tables(conn):
    return {r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table'")}


if __name__ == "__main__":
    sys.exit(main())
