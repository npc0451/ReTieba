"""Check the actual v4->v5 SQL and data preservation without an Android device.

Run: python scripts/check_local_library_migration.py
This complements, rather than replaces, the Room instrumentation tests.
"""
import json
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "core/data/schemas/dev.infinityf4p.tiebapure.core.data.TiebaPureDatabase"


def create_schema(connection, schema):
    for entity in schema["entities"]:
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))


def check():
    old = json.loads((SCHEMAS / "4.json").read_text(encoding="utf-8"))["database"]
    new = json.loads((SCHEMAS / "5.json").read_text(encoding="utf-8"))["database"]
    old_tables = {entity["tableName"]: entity for entity in old["entities"]}
    new_tables = {entity["tableName"]: entity for entity in new["entities"]}
    assert all(new_tables[name] == entity for name, entity in old_tables.items())
    source = (ROOT / "core/data/src/main/kotlin/dev/infinityf4p/tiebapure/core/data/TiebaPureDatabase.kt").read_text(encoding="utf-8")
    migration = source.split("val MIGRATION_4_5:", 1)[1].split("val MIGRATION_", 1)[0]
    statements = re.findall(r'db\.execSQL\("([^"\n]*)"\)', migration)
    assert len(statements) == 3
    with sqlite3.connect(":memory:") as actual, sqlite3.connect(":memory:") as expected:
        actual.execute("PRAGMA foreign_keys=ON")
        create_schema(actual, old)
        create_schema(expected, new)
        actual.execute("INSERT INTO browsing_history VALUES (8,'kept','author','forum',10)")
        actual.execute("INSERT INTO reading_positions VALUES (8,'123',7,10)")
        actual.execute("INSERT INTO saved_threads (thread_id,title,author_name,forum_name,saved_at_ms,snapshot_blob) VALUES (9,'saved','author','forum',11,?)", (b"\x00\xffsnapshot",))
        for statement in statements:
            actual.execute(statement)
        for table in new_tables:
            assert actual.execute(f"PRAGMA table_info(`{table}`)").fetchall() == expected.execute(f"PRAGMA table_info(`{table}`)").fetchall(), table
            assert actual.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall() == expected.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(), table
        assert actual.execute("SELECT title FROM browsing_history WHERE thread_id=8").fetchone() == ("kept",)
        assert actual.execute("SELECT post_id,floor FROM reading_positions WHERE thread_id=8").fetchone() == ("123", 7)
        assert actual.execute("SELECT snapshot_blob FROM saved_threads WHERE thread_id=9").fetchone() == (b"\x00\xffsnapshot",)
        actual.execute("INSERT INTO local_subscriptions VALUES ('android',0,'Android','Android吧',NULL,10)")
        actual.execute("INSERT INTO forum_feed_cache VALUES ('android',10,X'0102')")
        actual.execute("INSERT INTO local_bookmarks VALUES (9,'saved','author','forum',20)")
        actual.execute("DELETE FROM local_subscriptions WHERE normalized_name='android'")
        assert actual.execute("SELECT COUNT(*) FROM forum_feed_cache").fetchone() == (0,)
        actual.execute("DELETE FROM local_bookmarks WHERE thread_id=9")
        assert actual.execute("SELECT COUNT(*) FROM saved_threads").fetchone() == (1,)
        actual.execute("INSERT INTO local_bookmarks VALUES (9,'saved','author','forum',20)")
        actual.execute("DELETE FROM saved_threads WHERE thread_id=9")
        assert actual.execute("SELECT COUNT(*) FROM local_bookmarks").fetchone() == (1,)
    print("v4->v5 migration: schema, retained data, cache cascade and bookmark/offline independence passed")


if __name__ == "__main__":
    check()
