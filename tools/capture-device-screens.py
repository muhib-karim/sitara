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
    "settings": "Appearance",
    "search": "Try a search",
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


def bounds(root, label):
    """(left, top, right, bottom) of the first node whose text or content description is [label], else None."""
    for node in root.iter("node"):
        if label in (node.get("text"), node.get("content-desc")):
            return tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    return None


def hidden_under_dock(row, dock):
    """Whether a row's bottom edge reaches below the top of the dock (the mini player is the dock's top part)."""
    return row[3] > dock[1]


def scroll_to_end(screen):
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "1700", "540", "500", "300")
    time.sleep(1)
    return wait_for_screen(screen, "Storage used")


def type_then_close_keyboard(screen, hierarchy, while_typing):
    """Types a query, calls [while_typing] with the keyboard up, then closes the keyboard with Back, as a listener does."""
    for node in ET.fromstring(hierarchy).iter("node"):
        if node.get("class") == "android.widget.EditText":
            left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
            adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            break
    else:
        raise RuntimeError("No search field on the search screen")
    time.sleep(1.5)
    adb("shell", "input", "text", "night")
    time.sleep(1.5)
    while_typing()
    adb("shell", "input", "keyevent", "4")
    time.sleep(2)
    return wait_for_screen(screen, "Songs")


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


def save(name, hierarchy):
    png = adb("exec-out", "screencap", "-p", binary=True)
    if not png.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("adb did not return a PNG")
    (OUTPUT / f"{name}.png").write_bytes(png)
    (OUTPUT / f"{name}.xml").write_text(hierarchy)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    timeout = 90  # the first launch also waits out a cold emulator
    buttons = False
    try:
        passes = [(theme, screen, "") for theme in ("dark", "light") for screen in SCREENS]
        # Three-button navigation draws its own bar, where the system may add a contrast scrim.
        passes += [(theme, screen, "_buttons") for theme in ("dark", "light") for screen in ("settings", "search") if screen in SCREENS]
        for theme, screen, nav in passes:
            if nav and not buttons:
                adb("shell", "cmd", "overlay", "enable-exclusive", "--category", "com.android.internal.systemui.navbar.threebutton")
                time.sleep(3)
                buttons = True
            name = f"{screen}{nav}_{theme}"
            expected = SCREENS[screen]
            hierarchy = launch_and_wait(screen, expected, theme == "dark", timeout)
            timeout = 30
            time.sleep(1)
            if screen == "search":
                hierarchy = type_then_close_keyboard(screen, hierarchy, lambda: save(f"search_typing{nav}_{theme}", hierarchy))
            if screen == "settings":
                hierarchy = scroll_to_end(screen)
            save(name, hierarchy)
            if screen == "settings":
                root = ET.fromstring(hierarchy)
                row, dock = bounds(root, "Storage used"), bounds(root, "Open player")
                print(f"{name}: last row {row}, mini player {dock}")
                if row is None or dock is None or hidden_under_dock(row, dock):
                    raise RuntimeError(f"The last Settings row is not clear of the mini player ({name})")
    finally:
        # Raw bytes: logcat can carry non-UTF-8 output from any process, which must not fail a finished capture.
        (OUTPUT / "logcat.txt").write_bytes(adb("logcat", "-d", "-v", "brief", binary=True))


if __name__ == "__main__":
    main()
