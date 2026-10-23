import unittest

from perf_gate import check, janky_percent, total_time_ms


class PerfGateTest(unittest.TestCase):
    def test_reads_total_time_from_am_start(self):
        out = "Starting: Intent { cmp=app.sitara.music/.MainActivity }\nStatus: ok\nLaunchState: COLD\nTotalTime: 1834\nWaitTime: 1840\n"
        self.assertEqual(1834, total_time_ms(out))
        self.assertIsNone(total_time_ms("Error: Activity not started"))

    def test_reads_janky_share_from_gfxinfo(self):
        out = "Total frames rendered: 412\nJanky frames: 37 (8.98%)\n50th percentile: 9ms\n"
        self.assertEqual(8.98, janky_percent(out))
        self.assertIsNone(janky_percent("No process found"))

    def test_gates_fail_past_their_threshold_or_when_unmeasured(self):
        limits = {"cold_start_ms": 3000, "janky_frames_percent": 20}
        self.assertEqual([], check({"cold_start_ms": 2500, "janky_frames_percent": 10.0}, limits))
        self.assertEqual(
            ["cold_start_ms: 3200 is over 3000", "janky_frames_percent: not measured"],
            check({"cold_start_ms": 3200, "janky_frames_percent": None}, limits),
        )


if __name__ == "__main__":
    unittest.main()
