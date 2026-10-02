"""Physical-phone real-clock gate. Commands target only the separate debug fixture.

Run after starting the fixture and bringing Android Settings to the foreground.
Never reads or exports better.db. Never uninstalls or clears app storage.
"""
import json
import pathlib
import subprocess
import time

OUT = pathlib.Path("evidence")

def adb(*args):
    result = subprocess.run(["adb", *args], capture_output=True, check=True)
    return result.stdout.decode("utf-8", errors="replace")

def snapshot(label):
    value = json.loads(adb("shell", "run-as", "dev.better.app", "cat", "files/phase4-focus-probe.json"))
    (OUT / f"phase4-live-{label}.json").write_text(json.dumps(value, indent=2), encoding="utf-8")
    return value

def command(action):
    adb("shell", "am", "start", "--user", "0", "-W", "-n", "dev.better.app/.probe.FocusProbeActivity", "--es", "command", action)
    time.sleep(2)
    return snapshot(action)

def uptime():
    return int(float(adb("shell", "cat", "/proc/uptime").split()[0]) * 1000)

def until(target):
    while uptime() < target:
        time.sleep(min(15, max(0.1, (target-uptime())/1000)))

start = snapshot("background-baseline")
until(start["elapsed"] + 300_000)
background = snapshot("background-before-return")
resumed = command("inspect")
assert resumed["sessions"][-1]["state"] == "RUNNING", resumed
segment = resumed["segments"][-1]
assert abs(resumed["sessions"][-1]["millis"] - (resumed["elapsed"] - segment["startElapsed"])) < 1500
assert resumed["sessions"][-1]["millis"] >= 300_000
paused = command("pause")
assert paused["sessions"][-1]["state"] == "PAUSED"
until(paused["elapsed"] + 300_000)
after_pause = snapshot("five-minute-pause")
assert after_pause["sessions"][-1]["millis"] == paused["sessions"][-1]["millis"]
resumed_again = command("resume")
until(resumed_again["elapsed"] + 10_000)
stopped = command("stop")
assert stopped["sessions"][-1]["state"] == "COMPLETED"
assert 10_000 <= stopped["sessions"][-1]["millis"] - paused["sessions"][-1]["millis"] <= 16_000
print(json.dumps({"background_five_minutes": "passed", "pause_five_minutes": "passed",
    "paused_millis": paused["sessions"][-1]["millis"], "saved_millis": stopped["sessions"][-1]["millis"],
    "offline": True, "synthetic_fixture_only": True}, indent=2), flush=True)
