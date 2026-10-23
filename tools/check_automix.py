"""Fails unless the emulator's automix check found 126 BPM (within 2 %) at both ends of the check track."""
import re
import sys


def check(text: str) -> list[str]:
    problems = []
    for side in ("INTRO", "OUTRO"):
        match = re.search(rf"AUTOMIX CHECK {side} bpm=([0-9.]+)", text)
        if not match:
            problems.append(f"{side}: no tempo found")
        elif abs(float(match.group(1)) - 126.0) > 126.0 * 0.02:
            problems.append(f"{side}: {match.group(1)} BPM, expected 126")
    return problems


if __name__ == "__main__":
    try:
        text = open(sys.argv[1], encoding="utf-8").read()
    except OSError as error:
        sys.exit(f"automix check: no result ({error})")
    found = check(text)
    if found:
        sys.exit("automix check failed: " + "; ".join(found))
    print("automix check passed")
