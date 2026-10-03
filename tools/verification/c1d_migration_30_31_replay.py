#!/usr/bin/env python3
"""
C1-D 交付证据（主机侧 SQL 重放，非 instrumentation）。

目的：在没有真机/模拟器的情况下，真实执行 Room 为 30→31 生成的那份 SQL，
证明它 (a) 是合法 SQLite、(b) 不丢 memory_chunks 存量数据且 role_id 为 NULL、
(c) group_runs 的 conversation_id + round_id 唯一索引真的生效。

数据源全部来自仓库里的真实产物：
  - 旧库结构：app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/30.json
  - 迁移 SQL ：app/build/generated/ksp/debug/kotlin/.../AppDatabase_AutoMigration_30_31_Impl.kt
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
GENERATED = (ROOT / "app/build/generated/ksp/debug/kotlin/heizige/kk/khatkit/app/core"
             "/data/db/AppDatabase_AutoMigration_30_31_Impl.kt")

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
    text = GENERATED.read_text(encoding="utf-8")
    stmts = re.findall(r'connection\.execSQL\("((?:[^"\\]|\\.)*)"\)', text)
    return [s.encode().decode("unicode_escape") for s in stmts]


def main():
    print("== 1. 从 30.json 建旧库并写入存量数据 ==")
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys=ON")
    old = create_tables(conn, 30)
    check("30.json 版本号是 30", old["database"]["version"] == 30)
    check("旧库有 memory_chunks", "memory_chunks" in old_tables(old))
    check("旧库没有 group_runs", "group_runs" not in old_tables(old))

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

    print("== 2. 执行 Room 为 30→31 生成的迁移 SQL ==")
    stmts = migration_sql()
    check("生成的迁移 SQL 条数 >= 8", len(stmts) >= 8, f"实际 {len(stmts)} 条")
    for s in stmts:
        try:
            conn.execute(s)
        except sqlite3.Error as e:
            check("执行失败: " + s[:70], False, str(e))
            return 1
    conn.commit()
    check("全部迁移 SQL 执行成功", True)

    print("== 3. 迁移后结构 ==")
    tables = live_tables(conn)
    check("group_runs 已建", "group_runs" in tables)
    check("memory_chunks 仍在", "memory_chunks" in tables)
    check("_new_memory_chunks 临时表已清理", "_new_memory_chunks" not in tables)

    indices = conn.execute("PRAGMA index_list('group_runs')").fetchall()
    idx_names = [r[1] for r in indices]
    # sqlite_autoindex_* 是 TEXT PRIMARY KEY 的隐式唯一索引，不算业务唯一索引
    uniq = [r[1] for r in indices if r[2] == 1 and not r[1].startswith("sqlite_autoindex_")]
    check("group_runs 恰好一个业务唯一索引", len(uniq) == 1, str(uniq))
    check("唯一索引覆盖 conversation_id + round_id",
          any("conversation_id" in n and "round_id" in n for n in uniq), str(uniq))
    check("有 conversation_id + started_at 索引",
          any("conversation_id" in n and "started_at" in n for n in idx_names), str(idx_names))
    check("有 status 索引", any("status" in n for n in idx_names), str(idx_names))

    mc_idx = [r[1] for r in conn.execute("PRAGMA index_list('memory_chunks')").fetchall()]
    check("memory_chunks 有 role_id 索引", any("role_id" in n for n in mc_idx), str(mc_idx))
    for kept in ("space_id", "source_message_id", "deleted_at"):
        check(f"memory_chunks 保留 {kept} 索引", any(kept in n for n in mc_idx), str(mc_idx))

    print("== 4. 存量数据不丢 + role_id 为 NULL ==")
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

    print("== 6. run token 唯一索引（幂等兜底）==")
    conn.execute(
        "INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,"
        " limit_tokens, skipped_role_ids, committed_role_ids, started_at)"
        " VALUES ('token-a','conv-1','round-msg-1','RUNNING',0,4096,'[]','[]',100)")
    conn.commit()
    dup_failed = False
    try:
        conn.execute(
            "INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,"
            " limit_tokens, skipped_role_ids, committed_role_ids, started_at)"
            " VALUES ('token-b','conv-1','round-msg-1','RUNNING',0,4096,'[]','[]',101)")
        conn.commit()
    except sqlite3.IntegrityError as e:
        dup_failed = True
        print(f"        第二条被拒绝: {e}")
    check("同一 conversationId + roundId 第二条被拒", dup_failed)
    check("group_runs 只有 1 行",
          conn.execute("SELECT COUNT(*) FROM group_runs").fetchone()[0] == 1)
    conn.execute(
        "INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,"
        " limit_tokens, skipped_role_ids, committed_role_ids, started_at)"
        " VALUES ('token-c','conv-2','round-msg-1','RUNNING',0,4096,'[]','[]',102)")
    conn.execute(
        "INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,"
        " limit_tokens, skipped_role_ids, committed_role_ids, started_at)"
        " VALUES ('token-d','conv-1','round-msg-2','RUNNING',0,4096,'[]','[]',103)")
    conn.commit()
    check("不同群 / 不同轮次都能插入",
          conn.execute("SELECT COUNT(*) FROM group_runs").fetchone()[0] == 3)

    print("== 7. 预算截断可落库 ==")
    conn.execute(
        "INSERT INTO group_runs (id, conversation_id, round_id, status, spent_tokens,"
        " limit_tokens, skipped_role_ids, reason, committed_role_ids, started_at, ended_at)"
        " VALUES ('token-e','conv-3','round-msg-9','COMMITTED',4096,4096,'[\"r2\",\"r3\"]',"
        "'token_budget_exceeded','[\"r1\"]',10,20)")
    conn.commit()
    row = conn.execute(
        "SELECT spent_tokens, limit_tokens, skipped_role_ids, reason, committed_role_ids"
        " FROM group_runs WHERE id='token-e'").fetchone()
    check("已用/上限", list(row[:2]) == [4096, 4096], str(row))
    check("未运行角色 JSON", row[2] == '["r2","r3"]', row[2])
    check("超预算原因", row[3] == "token_budget_exceeded", row[3])
    check("已提交角色 JSON", row[4] == '["r1"]', row[4])

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
    check("全部表/列/主键/索引与 31.json 一致", not mismatches, "; ".join(mismatches[:4]))

    print()
    if failures:
        print(f"RESULT: FAIL ({len(failures)} 项) -> {failures}")
        return 1
    print("RESULT: 全部断言通过")
    return 0


def read_index(conn, table):
    """返回 [(name, unique, (cols...))]，跳过 TEXT PRIMARY KEY 的隐式索引。"""
    out = []
    for r in conn.execute(f"PRAGMA index_list(`{table}`)"):
        name, unique = r[1], r[2]
        if name.startswith("sqlite_autoindex_"):
            continue
        cols = tuple(
            row[2] for row in conn.execute(f"PRAGMA index_info(`{name}`)")
        )
        out.append((name, bool(unique), cols))
    return out


def old_tables(spec):
    return {e["tableName"] for e in spec["database"]["entities"]}


def live_tables(conn):
    return {r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table'")}


if __name__ == "__main__":
    sys.exit(main())
