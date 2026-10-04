#!/usr/bin/env python3
"""
C1-D 交付证据（主机侧 SQL 重放，非 instrumentation）。

目的：在没有真机/模拟器的情况下，用真实 SQLite 引擎执行 30→31 的那份迁移 SQL，
证明它 (a) 是合法 SQLite、(b) 不丢 memory_chunks 存量数据且 role_id 为 NULL、
(c) group_runs 的复合主键与 run_token 唯一索引真的生效、
(d) 迁移后的结构与 Room 导出的 31.json 逐字段一致（Room TableInfo 同口径）。

数据源全部来自仓库里的真实产物：
  - 旧库结构：app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/30.json
  - 迁移 SQL ：app/src/main/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_30_31.kt
               （显式手写迁移的 DDL 常量，不是 Room 生成的代码）
  - 新库结构：app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/31.json

这不能替代 Migration_30_31_Test（instrumentation，需要设备），只是把同一份 SQL
在真实 SQLite 引擎上跑一遍，输出可复现的断言结果。
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

failures = []


def check(label, ok, detail=""):
    print(("  PASS  " if ok else "  FAIL  ") + label + (("  -> " + detail) if detail else ""))
    if not ok:
        failures.append(label)


def create_tables(conn, version):
    """按 Room 导出的 schema JSON 建表（跳过 FTS 虚表，迁移不涉及它们）。"""
    spec = json.loads((SCHEMA_DIR / f"{version}.json").read_text(encoding="utf-8"))
    for entity in spec["database"]["entities"]:
        if entity.get("ftsVersion"):
            continue
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for idx in entity.get("indices", []):
            conn.execute(idx["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return spec


def migration_sql():
    """从 Migration_30_31.kt 里抽出 migrate() 真正 execSQL 的那几条 DDL。

    按出现顺序取 `db.execSQL(<常量名>)`，再把常量名解析成其字符串字面量内容，
    因此脚本重放的就是生产迁移执行的同一份 SQL（顺序也一致）。
    """
    text = MIGRATION.read_text(encoding="utf-8")

    # 1) 先收集所有 `val NAME = """..."""` 与 `val NAME = "..."`（可跨行、可带 trimIndent）
    #    顺序要紧：单引号正则会误匹配三引号字面量的开头，所以三引号的结果必须后写、覆盖前者。
    literals = {}
    single = re.compile(r'val\s+(\w+)\s*=\s*\n?\s*"((?:[^"\\]|\\.)*)"', re.S)
    for name, body in single.findall(text):
        literals[name] = body.encode().decode("unicode_escape")
    triple = re.compile(r'val\s+(\w+)\s*=\s*"""(.*?)"""', re.S)
    for name, body in triple.findall(text):
        literals[name] = _dedent(body.strip("\n"))

    # 2) 再按 migrate() 里的调用顺序取常量名
    calls = re.findall(r"db\.execSQL\((\w+)\)", text)
    missing = [c for c in calls if c not in literals]
    if missing:
        raise SystemExit(f"Migration_30_31.kt 里找不到这些 SQL 常量: {missing}")
    if len(calls) != len(set(calls)):
        raise SystemExit("migrate() 里有重复的 execSQL 调用，脚本解析假设失效")
    return [literals[c] for c in calls], calls


def _dedent(body):
    lines = [ln for ln in body.split("\n")]
    indents = [len(ln) - len(ln.lstrip()) for ln in lines if ln.strip()]
    cut = min(indents) if indents else 0
    return "\n".join(ln[cut:] if ln.strip() else "" for ln in lines).strip()


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


def main():
    print("== 1. 从 30.json 建旧库并写入存量数据 ==")
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys=ON")
    old = create_tables(conn, 30)
    check("30.json 版本号是 30", old["database"]["version"] == 30)
    check("旧库有 memory_chunks", "memory_chunks" in live_tables(conn))
    check("旧库没有 group_runs", "group_runs" not in live_tables(conn))

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
    # 30 库里模拟 FTS5 触发器：显式 ADD COLUMN 不该把它们删掉
    conn.commit()

    print("== 2. 执行 Migration_30_31.kt 里手写的迁移 SQL ==")
    stmts, names = migration_sql()
    check("迁移 SQL 条数 = 6", len(stmts) == 6, f"实际 {len(stmts)} 条：{names}")
    for name, s in zip(names, stmts):
        try:
            conn.execute(s)
        except sqlite3.Error as e:
            check(f"执行失败 {name}: {s[:70]}", False, str(e))
            return 1
    conn.commit()
    check("全部迁移 SQL 执行成功", True)

    print("== 3. 迁移后结构 ==")
    tables = live_tables(conn)
    check("group_runs 已建", "group_runs" in tables)
    check("memory_chunks 仍在", "memory_chunks" in tables)
    check("_new_memory_chunks 临时表不存在（用的是 ALTER 不是重建表）",
          "_new_memory_chunks" not in tables)

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

    print("== 4. 存量数据不丢 + role_id 为 NULL + rowid 未改写 ==")
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
    check("rowid 未被改写（未重建表）",
          conn.execute("SELECT rowid FROM memory_chunks WHERE content='存量记忆 B'").fetchone()[0] == 2)

    conv = conn.execute(
        "SELECT type, group_config FROM ConversationEntity WHERE id='conv-group-1'").fetchone()
    check("群会话 type=GROUP 保留", conv[0] == "GROUP", str(conv))
    check("群会话 group_config 保留", conv[1] == '{"schema_version":1,"mode":"pipeline"}', str(conv))

    print("== 5. role_id 可写、可查 ==")
    conn.execute(
        "INSERT INTO memory_chunks (space_id, content, source_kind, confidence, extracted_at,"
        " last_hit_at, created_at, updated_at, role_id)"
        " VALUES ('group:c1:role:r1','群记忆','MESSAGE',1.0,1,0,1,1,'r1')")
    conn.commit()
    check("存量行 role_id IS NULL 计数 = 2",
          conn.execute("SELECT COUNT(*) FROM memory_chunks WHERE role_id IS NULL").fetchone()[0] == 2)
    check("新行 role_id='r1' 计数 = 1",
          conn.execute("SELECT COUNT(*) FROM memory_chunks WHERE role_id='r1'").fetchone()[0] == 1)

    print("== 6. 复合主键 + run_token 唯一索引（幂等兜底）==")
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

    print("== 7. 预算截断可落库（契约：已用/上限/未运行角色/超预算原因）==")
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

    print("== 8. 迁移后结构与 31.json 逐字段一致（Room TableInfo 同口径）==")
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
        print(f"RESULT: FAIL ({len(failures)} 项) -> {failures}")
        return 1
    print("RESULT: 全部断言通过")
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
