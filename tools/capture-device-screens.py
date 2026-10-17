"""CI-only capture of the real Compose UI with a debug-only fake queue."""

import pathlib
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


def wait_for_screen(screen, expected):
    for _ in range(20):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/sitara-window.xml")
            hierarchy = adb("shell", "cat", "/sdcard/sitara-window.xml")
            root = ET.fromstring(hierarchy)
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(0.5)
            continue
        labels = [
            node.get(key, "")
            for node in root.iter("node")
            for key in ("text", "content-desc")
        ]
        # The ready marker proves data loaded; the destination label proves navigation.
        if f"Device capture: {screen}" in labels and any(expected in label for label in labels):
            return hierarchy
        time.sleep(0.5)
    raise RuntimeError(f"The {screen} screen did not become ready")


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    try:
        for theme in ("dark", "light"):
            for screen, expected in SCREENS.items():
                adb("shell", "am", "force-stop", "app.sitara.music")
                adb(
                    "shell", "am", "start", "-W", "-n", ACTIVITY,
                    "--es", "screen", screen, "--ez", "dark", str(theme == "dark").lower(),
                    "--ez", "reduced", "false",
                )
                hierarchy = wait_for_screen(screen, expected)
                time.sleep(1)
                png = adb("exec-out", "screencap", "-p", binary=True)
                if not png.startswith(b"\x89PNG\r\n\x1a\n"):
                    raise RuntimeError("adb did not return a PNG")
                (OUTPUT / f"{screen}_{theme}.png").write_bytes(png)
                (OUTPUT / f"{screen}_{theme}.xml").write_text(hierarchy)
    finally:
        (OUTPUT / "logcat.txt").write_text(adb("logcat", "-d", "-v", "brief"))


if __name__ == "__main__":
    main()
