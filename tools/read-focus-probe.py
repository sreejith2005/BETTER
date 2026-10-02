"""Read only the synthetic Phase 4 DB, without instrumentation or app launch."""
import json
import pathlib
import sqlite3
import subprocess
import tempfile
from contextlib import closing

NAME = "phase4-focus-probe.db"

def read(suffix="", optional=False):
    result = subprocess.run(["adb", "exec-out", "run-as", "dev.better.app", "cat", f"databases/{NAME}{suffix}"], capture_output=True)
    if result.returncode and not optional:
        raise RuntimeError(result.stderr.decode(errors="replace"))
    return result.stdout if result.returncode == 0 else b""

for attempt in range(3):
    before = read()
    wal = read("-wal", optional=True)
    after = read()
    if before == after:
        break
else:
    raise RuntimeError("Fixture checkpoint changed during inspection; retry")

with tempfile.TemporaryDirectory(prefix="better-focus-fixture-") as folder:
    path = pathlib.Path(folder) / NAME
    path.write_bytes(after)
    if wal:
        pathlib.Path(str(path) + "-wal").write_bytes(wal)
    with closing(sqlite3.connect(path)) as db:
        db.row_factory = sqlite3.Row
        assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        result = {"synthetic_fixture_only": True}
        for table in ("focus_sessions", "focus_segments", "action_log"):
            result[table] = [dict(row) for row in db.execute(f"SELECT * FROM {table} ORDER BY rowid")]
        print(json.dumps(result, indent=2))
