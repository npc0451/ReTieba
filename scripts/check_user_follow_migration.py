"""Execute the actual v5 -> v6 SQL without an Android device."""
import json
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "core/data/schemas/dev.infinityf4p.tiebapure.core.data.TiebaPureDatabase"

old = json.loads((SCHEMAS / "5.json").read_text())["database"]
new = json.loads((SCHEMAS / "6.json").read_text())["database"]
old_entities = {e["tableName"]: e for e in old["entities"]}
new_entities = {e["tableName"]: e for e in new["entities"]}
assert all(new_entities[name] == entity for name, entity in old_entities.items())

connection = sqlite3.connect(":memory:")
connection.execute("PRAGMA foreign_keys = ON")
for entity in old["entities"]:
    connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
connection.execute("INSERT INTO local_subscriptions VALUES ('android',1,'android','android吧',NULL,1)")
connection.execute("INSERT INTO local_bookmarks VALUES (9,'帖子','作者','android',1)")
connection.execute("INSERT INTO browsing_history VALUES (9,'帖子','作者','android',1)")
connection.execute("INSERT INTO reading_positions VALUES (9,'123',7,1)")
connection.execute("INSERT INTO saved_threads (thread_id,title,author_name,forum_name,saved_at_ms,snapshot_blob,media_mode,media_byte_count,new_reply_count,metadata_version) VALUES (9,'saved','author','android',1,X'0102','TextOnly',0,0,0)")
source = (ROOT / "core/data/src/main/kotlin/dev/infinityf4p/tiebapure/core/data/TiebaPureDatabase.kt").read_text(encoding="utf-8")
block = source.split("val MIGRATION_5_6:", 1)[1].split("val MIGRATION_", 1)[0]
statements = re.findall(r'db\.execSQL\("([^"\n]+)"\)', block)
assert len(statements) == 2
for statement in statements:
    connection.execute(statement)
connection.execute("INSERT INTO local_user_follows VALUES (42,'user','用户','portrait',1)")
connection.execute("INSERT INTO user_feed_cache VALUES (42,1,X'0102')")
connection.execute("DELETE FROM local_user_follows WHERE user_id=42")
assert connection.execute("SELECT count(*) FROM user_feed_cache").fetchone()[0] == 0
for table in ("local_subscriptions", "local_bookmarks", "browsing_history", "reading_positions", "saved_threads"):
    assert connection.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 1
print("v5->v6: existing schemas/data retained; user cache cascades only on unfollow")
