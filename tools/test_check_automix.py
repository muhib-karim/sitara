import unittest

from check_automix import check


class CheckAutomixTest(unittest.TestCase):
    def test_both_sides_at_the_tempo_pass(self):
        text = "AUTOMIX CHECK INTRO bpm=126.0 confidence=0.80\nAUTOMIX CHECK OUTRO bpm=125.4 confidence=0.70\n"
        self.assertEqual([], check(text))

    def test_a_wrong_tempo_or_a_failed_side_is_reported(self):
        text = "AUTOMIX CHECK INTRO bpm=63.0 confidence=0.80\nAUTOMIX CHECK OUTRO FAILED nothing decoded\n"
        self.assertEqual(["INTRO: 63.0 BPM, expected 126", "OUTRO: no tempo found"], check(text))


if __name__ == "__main__":
    unittest.main()
