import importlib.util
import pathlib
import subprocess
import tempfile
import unittest
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
                capture.wait_for_screen("lyrics", "Sitara, light the dark")

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
                capture.wait_for_screen("library", "Library")

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


if __name__ == "__main__":
    unittest.main()
