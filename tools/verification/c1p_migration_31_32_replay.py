#!/usr/bin/env python3
"""
C1-P 主机侧 SQL 重放证据（31→32 迁移，非 instrumentation）。

目的：在没有真机/模拟器的情况下，用真实 SQLite 引擎执行 31→32 的那份迁移 SQL，证明

  (a) 它是合法 SQLite（DDL 常量不是空壳，真能在引擎上跑）；
  (b) 旧行迁移后 `group_cards` 是**空串而不是 NULL**（列是 NOT NULL DEFAULT ''，
      Repository 侧据此回落到 null——这条判据能失败：若迁移写成可空无默认，这里会是 NULL）；
  (c) 旧行的其余列（type / group_config / 标题 / 时间戳）一字不丢；
  (d) ConversationEntity 是被 ALTER **原地加列**、而不是被 DROP + 重建：用
      `sqlite_master.sql` 逐字节比对 + b-tree `rootpage` 身份不变两条判据表达；
  (e) **memory_chunks 的 3 个 FTS5 触发器在迁移后逐字节存活**（本迁移不碰那张表），
      且 `message_node` 指向 ConversationEntity 的外键完好；
  (f) **对照实验：DROP 父表 + 重建这条路会真丢数据**——`foreign_keys=ON` 下
      `DROP TABLE ConversationEntity` 等价于对父表做一次隐式 `DELETE`，`message_node`
      上的 `ON DELETE CASCADE` 会把**存量消息行全部删掉**，而父表数据又被拷了回去，
      于是「会话还在、消息全没了」。这比 C1-D 那个「触发器被删」更硬：C1-D 重建的是
      `memory_chunks`（触发器挂在它上面才会被删），这里重建的是父表，触发器根本不在
      同一张表上，所以 C1-D 的那条理由**不能**照抄过来，本迁移改用外键级联这条理由；
  (g) 迁移后的结构与 Room 导出的 32.json 逐字段一致（Room TableInfo 同口径）；
  (h) 角色卡 blob（含非 ASCII persona / null 字段）可写可读回，`''` / `'[]'` 区分得开。

数据源全部来自仓库里的真实产物：
  - 旧库结构：app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/31.json
  - 新库结构：app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/32.json
  - 迁移 SQL ：app/src/main/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_31_32.kt
               （显式手写迁移的 DDL 常量，不是 Room 生成的代码）
  - FTS5 虚表 + 3 个触发器：
               app/src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabaseFactory.kt
               的 onOpen ——**逐字从生产代码抠出来**，不是手抄的副本。

SQL 拆分 / DDL 抽取那几个辅助函数从 C1-D 的脚本 import 复用，不另抄一份：
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
    from c1d_migration_30_31_replay import (...)

不能替代 `Migration_31_32_Test`（instrumentation，需要设备），只是把同一份 SQL
在真实 SQLite 引擎上跑一遍，输出可复现的断言结果。

不写任何文件，只 print 到 stdout。
"""
import json
import pathlib
import re
import sqlite3
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from c1d_migration_30_31_replay import (  # noqa: E402
    FTS_TRIGGERS,
    check,
    create_tables,
    execute_migration,
    failures,
    fts_matches,
    fts_ddl_from_factory,
    live_tables,
    note,
    normalize_ddl,
    read_index,
    rootpage,
    table_sql,
    triggers_on,
)
import c1d_migration_30_31_replay as c1d  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase"
MIGRATION = (ROOT / "app/src/main/java/heizige/kk/khatkit/app/core"
             "/data/db/migrations/Migration_31_32.kt")

CONVERSATION_ADD_COLUMN_RE = re.compile(
    r"ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+COLUMN\s+(.+)$", re.S | re.I)


def migration_sql():
    """抠出 Migration_31_32.kt 里 migrate() 真正 execSQL 的那几条 SQL。

    复用 C1-D 脚本的抽取实现（常量名 → 字符串字面量 → 按 execSQL 出现顺序返回），
    只是换了一个文件路径，因此重放的就是生产迁移执行的同一份 SQL。
    """
    original = c1d.MIGRATION
    c1d.MIGRATION = MIGRATION
    try:
        return c1d.migration_sql()
    finally:
        c1d.MIGRATION = original


def build_v31_db(fts_virtual, fts_triggers):
    """建一份「31 版真库」：31.json 结构 + onOpen 的 FTS5 虚表与 3 个触发器 + 存量数据。

    触发器先于存量数据建立，所以存量行会像真机 onOpen 的回填那样自动进 FTS 索引。
    """
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys=ON")
    spec = create_tables(conn, 31)
    conn.execute(fts_virtual)
    for name in FTS_TRIGGERS:
        conn.execute(fts_triggers[name])

    conn.execute(
        "INSERT INTO memory_spaces (id, kind, name, created_at)"
        " VALUES ('__global__','GLOBAL','',1)")
    conn.execute(
        "INSERT INTO memory_chunks (id, space_id, content, source_kind, confidence,"
        " extracted_at, last_hit_at, created_at, updated_at) VALUES"
        " (1,'__global__','存量群记忆 omega','MESSAGE',1.0,1,0,1,1)")
    conn.execute(
        "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at,"
        " suggestions, type, group_config) VALUES ('conv-group-1','a1','群','[]',1700000000000,"
        "1700000000001,'[]','GROUP','{\"schema_version\":1,\"mode\":\"pipeline\"}')")
    conn.execute(
        "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at,"
        " suggestions, type, group_config) VALUES ('conv-direct-1','a1','单聊','[]',1700000000002,"
        "1700000000003,'[]','DIRECT','')")
    conn.execute(
        "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index)"
        " VALUES ('node-1','conv-group-1',0,'[]',0)")
    conn.commit()
    return conn, spec


def control_rebuild_statements():
    """AutoMigration 的等价重放：建 _new → 拷贝 → DROP 旧表 → RENAME。

    DDL 全部来自 32.json（Room 自己会按 32 的实体声明生成这几条），所以这份对照迁移除了
    「触发器」和「表身份 / 外键」以外处处与手写 ALTER 等价——差异只可能来自 DROP TABLE。
    """
    spec = json.loads((SCHEMA_DIR / "32.json").read_text(encoding="utf-8"))
    entity = next(e for e in spec["database"]["entities"]
                  if e["tableName"] == "ConversationEntity")
    old_cols = [f["columnName"] for f in entity["fields"] if f["columnName"] != "group_cards"]
    stmts = [
        entity["createSql"].replace("${TABLE_NAME}", "_new_ConversationEntity"),
        "INSERT INTO `_new_ConversationEntity` ({}) SELECT {} FROM `ConversationEntity`".format(
            ", ".join(f"`{c}`" for c in old_cols),
            ", ".join(f"`{c}`" for c in old_cols),
        ),
        "DROP TABLE `ConversationEntity`",
        "ALTER TABLE `_new_ConversationEntity` RENAME TO `ConversationEntity`",
    ]
    for idx in entity.get("indices", []):
        stmts.append(idx["createSql"].replace("${TABLE_NAME}", "ConversationEntity"))
    return stmts


def main():
    print("== 0. 环境自检 ==")
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

    fts_virtual, fts_triggers = fts_ddl_from_factory()
    check("AppDatabaseFactory.kt 里抠到 memory_chunk_fts 虚表 DDL", fts_virtual is not None)
    check("AppDatabaseFactory.kt 里抠到 3 个 FTS5 触发器 DDL",
          tuple(sorted(fts_triggers)) == tuple(sorted(FTS_TRIGGERS)),
          str(sorted(fts_triggers)))
    note("31/32.json 里没有任何 ftsVersion 实体：memory_chunk_fts 与它的 3 个触发器是 "
         "onOpen 运行时用 execSQL 建的，不在 Room schema 里，所以这里从该文件抠原文。")

    print("== 1. 建 31 版真库（31.json 结构 + onOpen 的 FTS5 虚表与 3 个触发器 + 存量数据）==")
    conn, old = build_v31_db(fts_virtual, fts_triggers)
    check("31.json 版本号是 31", old["database"]["version"] == 31)
    conv_cols_before = {r[1] for r in conn.execute("PRAGMA table_info('ConversationEntity')")}
    check("迁移前 ConversationEntity 没有 group_cards", "group_cards" not in conv_cols_before)
    before_triggers = triggers_on(conn, "memory_chunks")
    check("迁移前 3 个 FTS5 触发器都在 sqlite_master",
          tuple(sorted(before_triggers)) == tuple(sorted(FTS_TRIGGERS)),
          str(sorted(before_triggers)))
    check("迁移前存量记忆已进 FTS 索引（MATCH 命中 存量群记忆 omega）",
          bool(fts_matches(conn, "存量群记忆 omega")))
    conv_sql_before = table_sql(conn, "ConversationEntity")
    conv_root_before = rootpage(conn, "ConversationEntity")
    fk_before = foreign_keys_on(conn, "message_node")
    check("迁移前 message_node 的外键指向 ConversationEntity",
          any(t == "ConversationEntity" for t in fk_before), str(fk_before))

    print("== 2. 执行 Migration_31_32.kt 里手写的迁移 SQL ==")
    calls = migration_sql()
    check("迁移 SQL 条数 = 1（migrate() 里 execSQL 的次数）", len(calls) == 1,
          f"实际 {len(calls)} 条：{[n for n, _ in calls]}")
    done, planned, errs = execute_migration(conn, calls)
    check("全部迁移 SQL 执行成功（0 条报错）", not errs, " | ".join(errs[:3]))
    check("实际执行语句数 = 拆分出的语句数（没有语句被静默丢弃）",
          done == planned and done > 0, f"执行 {done} / 计划 {planned}")
    conn.commit()

    print("== 3. 结构：原地 ADD COLUMN 的两条判据 + 新列属性 ==")
    conv_cols = {r[1]: r for r in conn.execute("PRAGMA table_info('ConversationEntity')")}
    check("group_cards 列已存在", "group_cards" in conv_cols, str(list(conv_cols)))
    check("group_cards 是 TEXT", conv_cols["group_cards"][2].upper() == "TEXT",
          str(conv_cols["group_cards"][2]))
    check("group_cards NOT NULL", conv_cols["group_cards"][3] == 1)
    check("group_cards 默认 ''", conv_cols["group_cards"][4] == "''",
          str(conv_cols["group_cards"][4]))
    check("group_cards 不是主键", conv_cols["group_cards"][5] == 0)
    check("列数 = 16（31 版 15 列 + 1）", len(conv_cols) == 16, str(len(conv_cols)))
    check("_new_ConversationEntity 临时表不存在（用的是 ALTER 不是重建表）",
          "_new_ConversationEntity" not in live_tables(conn))

    alter_stmt = ""
    for _, sql in calls:
        for part in c1d.split_sql_statements(sql):
            if re.match(r"ALTER\s+TABLE\b", part.strip(), re.I):
                alter_stmt = part.strip()
    am = CONVERSATION_ADD_COLUMN_RE.match(alter_stmt)
    check("能从迁移 SQL 里解析出 ADD COLUMN 的目标表与列定义",
          am is not None and am.group(1) == "ConversationEntity", alter_stmt[:80])
    conv_sql_after = table_sql(conn, "ConversationEntity")
    expected_after = None
    if am:
        # SQLite 把 ADD COLUMN 的新列定义追加在**列清单末尾**：有表级约束（这里的
        # `PRIMARY KEY(\`id\`)`）时插在约束**之前**，没有时插在最后一个 ")" 之前。
        # 两种拼法都构造一遍再比对——哪条不成立就说明建表 SQL 不是「原句 + 一列」。
        added = " " + " ".join(am.group(2).split())
        tail = ", PRIMARY KEY(`id`))"
        if tail in conv_sql_before:
            expected_after = conv_sql_before.replace(
                tail, "," + added + tail, 1)
        elif conv_sql_before.endswith(")"):
            expected_after = conv_sql_before[:-1] + ", " + added + ")"
        else:
            expected_after = conv_sql_before
    check("ConversationEntity 的 sqlite_master SQL 逐字节 == 原 SQL + ALTER 追加的列定义"
          "（证明是原地 ADD COLUMN，不是 DROP+重建）",
          expected_after is not None and conv_sql_after == expected_after,
          "迁移 SQL 里没有 ADD COLUMN，判据无法成立" if expected_after is None else
          ("" if conv_sql_after == expected_after
           else f"got={conv_sql_after!r} want={expected_after!r}"))
    conv_root_after = rootpage(conn, "ConversationEntity")
    check("ConversationEntity 的 b-tree rootpage 与迁移前相同（表身份未被换掉）",
          conv_root_after == conv_root_before and conv_root_after is not None,
          f"before={conv_root_before} after={conv_root_after}")
    note("rootpage 判据依赖宿主 SQLite 的 ALTER TABLE ADD COLUMN 原地快路径"
         f"（本机 {sqlite3.sqlite_version}）；真机 Android 捆绑的 SQLite 版本需要另取证。")

    print("== 4. 旧行数据不丢 + group_cards 是空串而不是 NULL ==")
    rows = conn.execute(
        "SELECT id, title, type, group_config, group_cards FROM ConversationEntity"
        " ORDER BY id").fetchall()
    check("ConversationEntity 仍是 2 行", len(rows) == 2, str(rows))
    group_row = next(r for r in rows if r[0] == "conv-group-1")
    check("群会话标题保留", group_row[1] == "群")
    check("群会话 type 保留", group_row[2] == "GROUP", str(group_row))
    check("群会话 group_config 保留",
          group_row[3] == '{"schema_version":1,"mode":"pipeline"}', str(group_row[3]))
    check("旧行 group_cards 是空串（NOT NULL DEFAULT '' 的语义）", group_row[4] == "",
          repr(group_row[4]))
    direct_row = next(r for r in rows if r[0] == "conv-direct-1")
    check("单聊旧行 group_cards 也是空串", direct_row[4] == "", repr(direct_row[4]))
    check("没有任何行 group_cards IS NULL",
          conn.execute("SELECT COUNT(*) FROM ConversationEntity"
                       " WHERE group_cards IS NULL").fetchone()[0] == 0)

    print("== 5. 角色卡 blob 可写可读回，''/[] 区分得开 ==")
    blob = ('[{"role_id":"r1","name":"阿斯莫德","assistant_id":"assistant-1",'
            '"card_id":"card-42","persona":"你是一个冷静的记录者。\\n只陈述事实。",'
            '"avatar_ref":null}]')
    conn.execute(
        "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at,"
        " suggestions, type, group_config, group_cards)"
        " VALUES ('conv-cards','a1','群','[]',1,2,'[]','GROUP','',?)", (blob,))
    conn.execute(
        "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at,"
        " suggestions, type, group_config, group_cards)"
        " VALUES ('conv-empty','a1','群','[]',1,2,'[]','GROUP','','[]')")
    conn.commit()
    got = conn.execute("SELECT group_cards FROM ConversationEntity"
                       " WHERE id='conv-cards'").fetchone()[0]
    check("含非 ASCII persona 的 blob 逐字节读回", got == blob, repr(got))
    check("'[]'（导入过但零张卡）与 ''（从没导入过）是两个不同的值",
          conn.execute("SELECT group_cards FROM ConversationEntity WHERE id='conv-empty'"
                       ).fetchone()[0] == "[]")
    check("blob 里 persona 能被 sqlite 读成 JSON（编码没写坏）",
          json.loads(got)[0]["persona"].startswith("你是一个冷静的记录者。"))

    print("== 6. FTS5 触发器在迁移后逐字节存活 + 外键仍在 ==")
    after_triggers = triggers_on(conn, "memory_chunks")
    for name in FTS_TRIGGERS:
        check(f"迁移后触发器 {name} 仍存在", name in after_triggers)
        check(f"迁移后触发器 {name} 的 SQL 与迁移前逐字节相同",
              after_triggers.get(name) == before_triggers.get(name))
    check("迁移后触发器集合没有多出/少掉任何一个",
          sorted(after_triggers) == sorted(before_triggers), str(sorted(after_triggers)))
    conn.execute(
        "INSERT INTO memory_chunks (space_id, content, source_kind, confidence,"
        " extracted_at, last_hit_at, created_at, updated_at, role_id)"
        " VALUES ('group:c1:role:r1','迁移后群记忆 zephyr','MESSAGE',1.0,1,0,1,1,'r1')")
    conn.commit()
    check("迁移后新写入的群记忆被 ai 触发器镜像进 FTS 索引", bool(fts_matches(conn, "zephyr")))
    fk_after = foreign_keys_on(conn, "message_node")
    check("迁移后 message_node 的外键仍指向 ConversationEntity",
          fk_after == fk_before, f"before={fk_before} after={fk_after}")

    print("== 7. 对照实验：DROP TABLE + 重建路径会真丢数据（这才是放弃它的理由）==")
    ctrl, _ = build_v31_db(fts_virtual, fts_triggers)
    nodes_before = ctrl.execute("SELECT COUNT(*) FROM message_node").fetchone()[0]
    check("对照库起点有 1 行 message_node", nodes_before == 1, str(nodes_before))
    check("对照库起点也有 3 个触发器",
          tuple(sorted(triggers_on(ctrl, "memory_chunks"))) == tuple(sorted(FTS_TRIGGERS)))
    ctrl_done, _, ctrl_errs = execute_migration(
        ctrl, [(f"autoMigration[{i}]", s) for i, s in enumerate(control_rebuild_statements())])
    check("对照：AutoMigration 那 4+N 条语句全部执行成功", not ctrl_errs and ctrl_done > 0,
          " | ".join(ctrl_errs[:3]))
    nodes_after = ctrl.execute("SELECT COUNT(*) FROM message_node").fetchone()[0]
    check("对照：DROP 父表（foreign_keys=ON）等价于隐式 DELETE，ON DELETE CASCADE 把"
          " message_node 的存量行全部删掉——重建路径真丢消息",
          nodes_after == 0, f"before={nodes_before} after={nodes_after}")
    check("对照：重建路径换了 ConversationEntity 的 b-tree（rootpage 变了）",
          rootpage(ctrl, "ConversationEntity") != conv_root_before,
          f"before={conv_root_before} 对照={rootpage(ctrl, 'ConversationEntity')}")
    check("对照：重建路径下 sqlite_master 的建表 SQL 与 ALTER 路径不同",
          table_sql(ctrl, "ConversationEntity") != conv_sql_after,
          repr(table_sql(ctrl, "ConversationEntity")))
    check("对照：父表数据本身被拷回去了（丢的只是子表行）",
          ctrl.execute("SELECT COUNT(*) FROM ConversationEntity").fetchone()[0] == 2)
    ctrl.close()
    check("ALTER 路径：message_node 的存量行一行没少",
          conn.execute("SELECT COUNT(*) FROM message_node").fetchone()[0] == nodes_before)

    print("== 8. 迁移后结构与 32.json 逐字段一致（Room TableInfo 同口径）==")
    expected = json.loads((SCHEMA_DIR / "32.json").read_text(encoding="utf-8"))
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
    check("全部表/列/主键/索引/默认值与 32.json 一致", not mismatches, "; ".join(mismatches[:4]))

    conn.close()
    print()
    if failures:
        print(f"RESULT: FAIL（{len(failures)}/{c1d.assertions} 项断言失败）-> {failures}")
        return 1
    print(f"RESULT: 全部断言通过（共 {c1d.assertions} 条）")
    return 0


def foreign_keys_on(conn, table):
    """返回该表外键指向的父表名列表（去重、排序，保证输出可复现）。"""
    return sorted({r[2] for r in conn.execute(f"PRAGMA foreign_key_list(`{table}`)")})


if __name__ == "__main__":
    sys.exit(main())