"""Performance gates for Device screens: cold start (am start -W) and scrolling jank (dumpsys gfxinfo).

    perf_gate.py measure <package> <activity> <out.json>   runs on a connected emulator through adb
    perf_gate.py check <out.json> <thresholds.json>        fails when a median passes its threshold
"""
import json
import re
import statistics
import subprocess
import sys
import time

STARTS = 5
SWIPES = 6


def adb(*args: str) -> str:
    return subprocess.run(["adb", "shell", *args], check=True, capture_output=True, text=True).stdout


def total_time_ms(am_output: str) -> int | None:
    """TotalTime from `am start -W`, the time to the first frame of a cold start."""
    match = re.search(r"TotalTime:\s*(\d+)", am_output)
    return int(match.group(1)) if match else None


def janky_percent(gfxinfo: str) -> float | None:
    """The share of janky frames dumpsys gfxinfo reports since the last reset."""
    match = re.search(r"Janky frames:\s*\d+\s*\(([\d.]+)%\)", gfxinfo)
    return float(match.group(1)) if match else None


def measure(package: str, activity: str, out: str) -> None:
    starts = []
    for _ in range(STARTS):
        adb("am", "force-stop", package)
        time.sleep(1)
        value = total_time_ms(adb("am", "start", "-W", "-n", f"{package}/{activity}"))
        if value is not None:
            starts.append(value)
        time.sleep(2)
    # Scroll Home with the app warm, then read the frames it drew.
    adb("dumpsys", "gfxinfo", package, "reset")
    for _ in range(SWIPES):
        adb("input", "swipe", "540", "1800", "540", "600", "300")
        time.sleep(0.6)
        adb("input", "swipe", "540", "600", "540", "1800", "300")
        time.sleep(0.6)
    jank = janky_percent(adb("dumpsys", "gfxinfo", package))
    result = {"cold_start_ms": statistics.median(starts) if starts else None, "cold_starts": starts, "janky_frames_percent": jank}
    with open(out, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2)
    print(json.dumps(result))


def check(result: dict, thresholds: dict) -> list[str]:
    problems = []
    for key in ("cold_start_ms", "janky_frames_percent"):
        value = result.get(key)
        if value is None:
            problems.append(f"{key}: not measured")
        elif value > thresholds[key]:
            problems.append(f"{key}: {value} is over {thresholds[key]}")
    return problems


if __name__ == "__main__":
    if sys.argv[1] == "measure":
        measure(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        with open(sys.argv[2], encoding="utf-8") as f:
            measured = json.load(f)
        with open(sys.argv[3], encoding="utf-8") as f:
            limits = json.load(f)
        found = check(measured, limits)
        if found:
            sys.exit("performance gate failed: " + "; ".join(found))
        print("performance gate passed")
