import importlib.util
import pathlib
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "capture", pathlib.Path(__file__).with_name("capture-device-screens.py")
)
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class CaptureReadinessTest(unittest.TestCase):
    def test_ready_marker_alone_does_not_accept_the_wrong_screen(self):
        xml = '<hierarchy><node content-desc="Device capture: lyrics" text="Starlit Avenue"/></hierarchy>'
        with patch.object(capture, "adb", return_value=xml), patch.object(capture.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "lyrics"):
                capture.wait_for_screen("lyrics", "Sitara, light the dark", timeout=0)

    def test_lyrics_require_loaded_data_and_visible_lyric_text(self):
        xml = '<hierarchy><node content-desc="Device capture: lyrics"/><node text="Sitara, light the dark"/></hierarchy>'
        with patch.object(capture, "adb", return_value=xml):
            self.assertEqual(xml, capture.wait_for_screen("lyrics", "Sitara, light the dark"))

    def test_retries_a_transient_incomplete_hierarchy(self):
        xml = '<hierarchy><node content-desc="Device capture: library"/><node text="Night Drive"/></hierarchy>'
        with patch.object(capture, "adb", side_effect=["", "incomplete", "", xml]), patch.object(capture.time, "sleep"):
            self.assertEqual(xml, capture.wait_for_screen("library", "Night Drive"))

    def test_destination_without_fixture_marker_is_rejected(self):
        xml = '<hierarchy><node text="Library"/></hierarchy>'
        with patch.object(capture, "adb", return_value=xml), patch.object(capture.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "library"):
                capture.wait_for_screen("library", "Library", timeout=0)

    def test_logcat_with_non_utf8_bytes_is_saved_as_is(self):
        raw = b"I/proc: \xc0\xff\n"

        def run(cmd, **kwargs):
            if kwargs.get("text"):
                raw.decode("utf-8")  # what subprocess does with text=True, and where the capture used to fail
            return subprocess.CompletedProcess(cmd, 0, stdout=raw)

        with tempfile.TemporaryDirectory() as out, patch.object(capture, "OUTPUT", pathlib.Path(out)), \
                patch.dict(capture.SCREENS, clear=True), patch.object(capture.subprocess, "run", side_effect=run):
            capture.main()
            self.assertEqual(raw, (pathlib.Path(out) / "logcat.txt").read_bytes())


ANR = (
    '<hierarchy><node text="Pixel Launcher isn\u2019t responding" bounds="[63,1000][1017,1100]"/>'
    '<node text="Close app" bounds="[63,1150][1017,1280]"/><node text="Wait" bounds="[63,1280][1017,1410]"/></hierarchy>'
)


class AnrDialogTest(unittest.TestCase):
    def test_finds_the_wait_button_of_a_system_anr_dialog(self):
        self.assertEqual((540, 1345), capture.anr_wait_button(ET.fromstring(ANR)))

    def test_matches_the_straight_apostrophe_too(self):
        xml = '<hierarchy><node text="App isn\'t responding"/><node text="Wait" bounds="[0,0][100,50]"/></hierarchy>'
        self.assertEqual((50, 25), capture.anr_wait_button(ET.fromstring(xml)))

    def test_a_wait_label_without_an_anr_title_is_not_a_dialog(self):
        xml = '<hierarchy><node text="Wait" bounds="[0,0][100,50]"/></hierarchy>'
        self.assertIsNone(capture.anr_wait_button(ET.fromstring(xml)))

    def test_waiting_for_a_screen_taps_wait_on_an_anr_dialog(self):
        ready = '<hierarchy><node content-desc="Device capture: home"/><node text="Good evening"/></hierarchy>'
        calls = []

        def adb(*args, **kwargs):
            calls.append(args)
            if args[:2] == ("shell", "cat"):
                return ready if ("shell", "input", "tap", "540", "1345") in calls else ANR
            return ""

        with patch.object(capture, "adb", side_effect=adb), patch.object(capture.time, "sleep"):
            self.assertEqual(ready, capture.wait_for_screen("home", "Good evening"))
        self.assertIn(("shell", "input", "tap", "540", "1345"), calls)

    def test_a_screen_that_misses_its_deadline_is_launched_once_more(self):
        ready = '<hierarchy><node content-desc="Device capture: home"/><node text="Good evening"/></hierarchy>'
        waits = iter([RuntimeError("The home screen did not become ready"), ready])

        def wait(screen, expected, timeout):
            result = next(waits)
            if isinstance(result, Exception):
                raise result
            return result

        with patch.object(capture, "adb") as adb, patch.object(capture, "wait_for_screen", side_effect=wait):
            self.assertEqual(ready, capture.launch_and_wait("home", "Good evening", dark=True, timeout=90))
        launches = [c for c in adb.call_args_list if c.args[:2] == ("shell", "am") and c.args[2] == "start"]
        self.assertEqual(2, len(launches))


if __name__ == "__main__":
    unittest.main()
