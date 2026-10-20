"""CI-only capture of the real Compose UI with a debug-only fake queue."""

import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUTPUT = pathlib.Path("artifacts/device-screens")
ACTIVITY = "app.sitara.music/.DeviceCaptureActivity"
SCREENS = {
    "home": "Good evening",
    "player": "Collapse player",
    "lyrics": "Sitara, light the dark",
    "library": "Night Drive",
    "mini": "Open player",
}


def adb(*args, binary=False):
    return subprocess.run(
        ["adb", *args], check=True, capture_output=True, text=not binary, timeout=30
    ).stdout


def anr_wait_button(root):
    """Centre of the "Wait" button when a system "isn't responding" dialog is showing, else None."""
    texts = [node.get("text", "").replace("\u2019", "'") for node in root.iter("node")]
    if not any("isn't responding" in text for text in texts):
        return None
    for node in root.iter("node"):
        if node.get("text") == "Wait":
            left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
            return (left + right) // 2, (top + bottom) // 2
    return None


def wait_for_screen(screen, expected, timeout=30):
    deadline = time.monotonic() + timeout
    while True:
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/sitara-window.xml")
            hierarchy = adb("shell", "cat", "/sdcard/sitara-window.xml")
            root = ET.fromstring(hierarchy)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
            root = None
        if root is not None:
            labels = [
                node.get(key, "")
                for node in root.iter("node")
                for key in ("text", "content-desc")
            ]
            # The ready marker proves data loaded; the destination label proves navigation.
            if f"Device capture: {screen}" in labels and any(expected in label for label in labels):
                return hierarchy
            # An overloaded emulator can ANR the launcher on top of the app; let it keep running.
            wait = anr_wait_button(root)
            if wait:
                adb("shell", "input", "tap", str(wait[0]), str(wait[1]))
        if time.monotonic() >= deadline:
            raise RuntimeError(f"The {screen} screen did not become ready")
        time.sleep(0.5)


def launch_and_wait(screen, expected, dark, timeout):
    for attempt in range(2):
        adb("shell", "am", "force-stop", "app.sitara.music")
        adb(
            "shell", "am", "start", "-W", "-n", ACTIVITY,
            "--es", "screen", screen, "--ez", "dark", str(dark).lower(),
            "--ez", "reduced", "false",
        )
        try:
            return wait_for_screen(screen, expected, timeout)
        except RuntimeError:
            if attempt == 1:
                raise


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    timeout = 90  # the first launch also waits out a cold emulator
    try:
        for theme in ("dark", "light"):
            for screen, expected in SCREENS.items():
                hierarchy = launch_and_wait(screen, expected, theme == "dark", timeout)
                timeout = 30
                time.sleep(1)
                png = adb("exec-out", "screencap", "-p", binary=True)
                if not png.startswith(b"\x89PNG\r\n\x1a\n"):
                    raise RuntimeError("adb did not return a PNG")
                (OUTPUT / f"{screen}_{theme}.png").write_bytes(png)
                (OUTPUT / f"{screen}_{theme}.xml").write_text(hierarchy)
    finally:
        # Raw bytes: logcat can carry non-UTF-8 output from any process, which must not fail a finished capture.
        (OUTPUT / "logcat.txt").write_bytes(adb("logcat", "-d", "-v", "brief", binary=True))


if __name__ == "__main__":
    main()
