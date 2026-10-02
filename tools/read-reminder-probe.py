"""Read only synthetic debug-fixture records without instrumentation/force-stop.

Never reads better.db or exports personal records. Temporary copies are removed.
"""
import json
import pathlib
import sqlite3
import subprocess
import tempfile
import time
from contextlib import closing

NAME = "phase3-reminder-probe.db"


def read_file(suffix="", optional=False):
    result = subprocess.run(
        ["adb", "exec-out", "run-as", "dev.better.app", "cat", f"databases/{NAME}{suffix}"],
        capture_output=True,
    )
    if result.returncode and not optional:
        raise RuntimeError(result.stderr.decode(errors="replace"))
    return result.stdout if result.returncode == 0 else b""


for attempt in range(3):
    before = read_file()
    wal = read_file("-wal", optional=True)
    after = read_file()
    if before == after:
        break
    time.sleep(0.1)
else:
    raise RuntimeError("Fixture checkpoint changed during inspection; retry.")

with tempfile.TemporaryDirectory(prefix="better-synthetic-probe-") as folder:
    db_path = pathlib.Path(folder) / NAME
    db_path.write_bytes(after)
    if wal:
        pathlib.Path(str(db_path) + "-wal").write_bytes(wal)
    with closing(sqlite3.connect(db_path)) as db:
        db.row_factory = sqlite3.Row
        assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        deliveries = db.execute("""
            SELECT c.title, d.* FROM reminder_deliveries d
            JOIN occurrences o ON o.id=d.occurrenceId
            JOIN commitments c ON c.id=o.commitmentId
            WHERE c.title LIKE 'BETTER test:%' ORDER BY d.plannedAt
        """).fetchall()
        occurrences = db.execute("""
            SELECT c.title,o.id,o.state,o.delivered,o.actualAt,
              (SELECT count(*) FROM action_log a WHERE a.entityId=o.id AND a.action='EXEC_COMPLETE') completeActions
            FROM occurrences o JOIN commitments c ON c.id=o.commitmentId
            WHERE c.title LIKE 'BETTER test:%'
        """).fetchall()
        recovery = db.execute("SELECT action,summary,timestamp FROM action_log WHERE action IN ('SCHEDULE_CHECKED','REMINDER_EPOCH') ORDER BY timestamp").fetchall()
        print(json.dumps({"synthetic_fixture_only": True, "deliveries": [dict(x) for x in deliveries],
                          "occurrences": [dict(x) for x in occurrences], "recovery": [dict(x) for x in recovery]}, indent=2))
