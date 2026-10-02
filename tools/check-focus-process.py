"""Real same-boot process-loss test using only the synthetic timer fixture."""
import json
import pathlib
import subprocess
import time

def adb(*args):
    return subprocess.check_output(["adb", *args]).decode("utf-8", errors="replace")

def capture(label):
    data=json.loads(adb("shell","run-as","dev.better.app","cat","files/phase4-focus-probe.json"))
    pathlib.Path(f"evidence/phase4-process-{label}.json").write_text(json.dumps(data,indent=2),encoding="utf-8")
    return data

def command(action):
    adb("shell","am","start","--user","0","-W","-n","dev.better.app/.probe.FocusProbeActivity","--es","command",action)
    time.sleep(2)
    return capture(action)

started=command("start")
time.sleep(20)
before=capture("before-kill")
adb("shell","am","force-stop","--user","0","dev.better.app")
persisted=json.loads(subprocess.check_output(["python","tools/read-focus-probe.py"]))
pathlib.Path("evidence/phase4-process-saved.json").write_text(json.dumps(persisted,indent=2),encoding="utf-8")
saved=sum(x["durationMillis"] for x in persisted["focus_segments"] if x["sessionId"]==before["sessions"][-1]["id"])
time.sleep(10)
recovered=command("inspect")
assert recovered["boot"]==before["boot"]
assert recovered["sessions"][-1]["state"]=="REVIEW"
assert recovered["sessions"][-1]["millis"]==saved
candidate=sum(x["candidate"] if x["candidate"] is not None else x["millis"] for x in recovered["segments"] if x["session"]==before["sessions"][-1]["id"])
assert candidate>=saved+10000
time.sleep(10)
confirmed=command("confirm")
assert confirmed["sessions"][-1]["state"]=="PAUSED"
assert confirmed["sessions"][-1]["millis"]==candidate
stopped=command("stop")
assert stopped["sessions"][-1]["millis"]==candidate
print(json.dumps({"real_force_stop_same_boot":"passed","saved_millis":saved,"confirmed_millis":candidate,
    "review_time_excluded":True,"synthetic_fixture_only":True},indent=2))
