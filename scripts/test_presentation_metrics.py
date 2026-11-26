"""Presentation collector tests using synthetic timestamps and mocked commands only."""
import importlib.util
from pathlib import Path
import subprocess
import threading
from types import SimpleNamespace
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('presentation_metrics', Path(__file__).with_name('presentation-metrics.py'))
presentation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(presentation)
T = 16_666_666
BASE = 1_000_000_000
BLAST = 'SurfaceView[com.couchcrypt.tv/Activity](BLAST)#3'
SURFACE = 'SurfaceView[com.couchcrypt.tv/Activity]#2'
BACKGROUND = 'Background for ' + BLAST


def raw(times, refresh=T):
    return str(refresh) + '\n' + '\n'.join(f'{t - 1_000_000} {t} {t - 500_000}' for t in times) + '\n'


def snapshot(times, layer=BLAST, refresh=T):
    return {'layers': {layer: raw(times, refresh)}}


class ParserTest(unittest.TestCase):
    def test_sentinels_malformed_rows_and_duplicates_do_not_invent_adjacency(self):
        a, b = BASE, BASE + T
        text = raw([a, a]) + '0 0 0\n' + f'{presentation.SENTINEL} {presentation.SENTINEL} {presentation.SENTINEL}\n'
        text += 'malformed\n' + raw([b]).split('\n', 1)[1]
        result = presentation.parse_latency(text)
        self.assertIsNone(result['error'])
        self.assertEqual(3, result['invalidRows'])
        self.assertEqual(1, result['duplicateRows'])
        sampler = presentation.PresentationSampler(['unused'], {'layers': {BLAST: text}})
        self.assertEqual(2, sampler.metadata()['sampleCount'])
        self.assertFalse(sampler.analyze(a, b)['coverage']['complete'])
        self.assertEqual(0, sampler.analyze(a, b)['intervalCount'])

    def test_invalid_headers_nonmonotonic_conflicting_and_excess_history_are_rejected(self):
        for text in ['', 'permission denied', '0\n', '-1\n', '99999999999\n',
                     raw([BASE + T, BASE]), raw([BASE, BASE]).replace(str(BASE - 1_000_000), str(BASE - 900_000), 1),
                     raw([BASE + i * T for i in range(128)])]:
            with self.subTest(text=text[:70]):
                self.assertIsNotNone(presentation.parse_latency(text)['error'])
        self.assertIsNotNone(presentation.parse_latency(raw([BASE + T]) + '0 0 0\n' + raw([BASE]).split('\n', 1)[1])['error'])


class PresentationAnalysisTest(unittest.TestCase):
    def test_selects_valid_app_blast_layer_and_explicitly_labels_fallback(self):
        data = {'layers': {BACKGROUND: raw([BASE + T]), SURFACE: raw([BASE, BASE + T]),
                           'SurfaceView[other.app/Activity](BLAST)#4': raw([BASE, BASE + T]),
                           BLAST: raw([BASE])}}
        sampler = presentation.PresentationSampler(['unused'], data)
        self.assertEqual(BLAST, sampler.metadata()['layer'])
        self.assertFalse(sampler.metadata()['nonBlastFallback'])
        fallback = presentation.PresentationSampler(['unused'], {'layers': {BLAST: '0\n', SURFACE: raw([BASE])}})
        self.assertEqual(SURFACE, fallback.metadata()['layer'])
        self.assertTrue(fallback.metadata()['nonBlastFallback'])

    def test_full_history_overlap_deduplicates_and_brackets_observation_span(self):
        sampler = presentation.PresentationSampler(['unused'], snapshot([BASE + i * T for i in range(127)]))
        sampler.add_snapshot(snapshot([BASE + i * T for i in range(45, 172)]), final=True)
        start, end = sampler.observation_bounds()
        self.assertEqual((BASE + 126 * T, BASE + 171 * T), (start, end))
        result = sampler.analyze(start, end)
        self.assertTrue(result['available'])
        self.assertTrue(result['coverage']['complete'])
        self.assertEqual(46, result['frameCount'])
        self.assertEqual(45, result['intervalCount'])
        self.assertEqual(172, sampler.metadata()['sampleCount'])
        self.assertEqual(82, sampler.metadata()['duplicateRows'])
        self.assertAlmostEqual(60, result['observedFps'], places=4)
        self.assertEqual(0, result['missedRefreshLikeGaps'])
        self.assertFalse(result['hardwareDeadlineProof'])

    def test_ring_overwrite_is_a_coverage_hole_not_a_fake_long_presented_frame(self):
        sampler = presentation.PresentationSampler(['unused'], snapshot([BASE + i * T for i in range(127)]))
        sampler.add_snapshot(snapshot([BASE + i * T for i in range(255, 382)]), final=True)
        result = sampler.analyze(*sampler.observation_bounds())
        self.assertFalse(result['coverage']['complete'])
        self.assertEqual(1, result['coverage']['unobservedGapCount'])
        self.assertIsNone(result['observedFps'])
        self.assertEqual(0, result['missedRefreshLikeGaps'])
        self.assertAlmostEqual(T / 1e6, result['intervalMs']['max'])

    def test_true_observed_adjacent_gaps_preserve_misses_and_longest_streak(self):
        times = [BASE]
        for units in [1, 2, 2, 1, 3]:
            times.append(times[-1] + units * T)
        sampler = presentation.PresentationSampler(['unused'], snapshot(times[:1]))
        sampler.add_snapshot(snapshot(times), final=True)
        result = sampler.analyze(*sampler.observation_bounds())
        self.assertTrue(result['coverage']['complete'])
        self.assertEqual(3, result['missedRefreshLikeGaps'])
        self.assertEqual(4, result['estimatedMissedRefreshes'])
        self.assertEqual(2, result['longestMissedRefreshLikeStreak'])
        self.assertAlmostEqual(3 * T / 1e6, result['intervalMs']['p99'])
        self.assertAlmostEqual(1.5 * T, result['thresholdNs'])
        self.assertEqual(60, result['targetFps'])
        self.assertEqual(result['missedRefreshLikeGaps'], result['missedTargetFrameLikeGaps'])
        self.assertEqual(result['estimatedMissedRefreshes'], result['estimatedMissedTargetFrames'])

    def test_stable_thirty_fps_is_two_display_refreshes_without_missed_target_frames(self):
        times = [BASE + i * 2 * T for i in range(6)]
        sampler = presentation.PresentationSampler(['unused'], snapshot(times[:1]))
        sampler.add_snapshot(snapshot(times), final=True)
        result = sampler.analyze(*sampler.observation_bounds(), target_fps=30)
        self.assertTrue(result['coverage']['complete'])
        self.assertEqual(30, result['targetFps'])
        self.assertAlmostEqual(1e9 / 30, result['targetPeriodNs'])
        self.assertAlmostEqual(2, result['expectedRefreshesPerFrame'], places=6)
        self.assertEqual({'2': 5}, result['refreshMultipleHistogram'])
        self.assertEqual(0, result['missedTargetFrameLikeGaps'])
        self.assertEqual(0, result['missedTargetFrameLikeGapRate'])
        self.assertEqual(0, result['estimatedMissedTargetFrames'])
        self.assertEqual(0, result['longestMissedTargetFrameLikeStreak'])
        self.assertAlmostEqual(50_000_000, result['targetFrameThresholdNs'])
        # Keep legacy display-refresh metrics intact, but explicitly separate their meaning.
        self.assertEqual(5, result['missedRefreshLikeGaps'])
        self.assertIn('not target-frame acceptance', result['legacyRefreshHeuristicScope'])

    def test_sixty_six_ms_gap_is_a_missed_target_frame_at_thirty_fps(self):
        times = [BASE, BASE + 2 * T, BASE + 6 * T, BASE + 8 * T]
        sampler = presentation.PresentationSampler(['unused'], snapshot(times[:1]))
        sampler.add_snapshot(snapshot(times), final=True)
        result = sampler.analyze(*sampler.observation_bounds(), target_fps=30)
        self.assertEqual(1, result['missedTargetFrameLikeGaps'])
        self.assertAlmostEqual(1 / 3, result['missedTargetFrameLikeGapRate'])
        self.assertEqual(1, result['estimatedMissedTargetFrames'])
        self.assertEqual(1, result['longestMissedTargetFrameLikeStreak'])
        self.assertEqual({'2': 2, '4': 1}, result['refreshMultipleHistogram'])

    def test_unsupported_capture_targets_are_rejected(self):
        sampler = presentation.PresentationSampler(['unused'], snapshot([BASE]))
        for target in [0, 15, 120, None, float('nan')]:
            with self.subTest(target=target), self.assertRaises(ValueError):
                sampler.analyze(BASE, BASE + T, target_fps=target)

    def test_device_boundaries_filter_history_and_require_coverage_at_both_ends(self):
        times = [BASE + i * T for i in range(9)]
        sampler = presentation.PresentationSampler(['unused'], snapshot(times))
        result = sampler.analyze(BASE + T // 2, BASE + 7 * T + T // 2)
        self.assertTrue(result['coverage']['complete'])
        self.assertEqual(7, result['frameCount'])
        self.assertEqual(6, result['intervalCount'])
        self.assertFalse(sampler.analyze(BASE - T, BASE + 8 * T)['coverage']['complete'])
        self.assertFalse(sampler.analyze(BASE, BASE + 9 * T)['coverage']['complete'])
        for boundaries in [(None, None), (BASE, BASE), (True, BASE), (BASE + T, BASE)]:
            self.assertFalse(sampler.analyze(*boundaries)['available'])

    def test_changed_refresh_or_conflicting_cross_snapshot_record_compromises_coverage(self):
        times = [BASE, BASE + T, BASE + 2 * T]
        for data in [snapshot(times, refresh=20_000_000),
                     {'layers': {BLAST: raw(times).replace(str(BASE - 1_000_000), str(BASE - 900_000), 1)}}]:
            sampler = presentation.PresentationSampler(['unused'], snapshot(times))
            sampler.add_snapshot(data, final=True)
            self.assertFalse(sampler.analyze(BASE, BASE + 2 * T)['coverage']['complete'])

    def test_empty_initial_or_failed_final_observation_has_no_fabricated_fence(self):
        sampler = presentation.PresentationSampler(['unused'], {'unavailable': 'not supported'})
        self.assertEqual((None, None), sampler.observation_bounds())
        self.assertFalse(sampler.analyze(*sampler.observation_bounds())['available'])
        sampler = presentation.PresentationSampler(['unused'], snapshot([BASE]))
        sampler.add_snapshot({'layers': {BLAST: 'unsupported'}}, final=True)
        self.assertEqual((BASE, None), sampler.observation_bounds())
        self.assertEqual(1, sampler.metadata()['errorCount'])

    def test_recoverable_query_failure_is_not_a_coverage_hole_when_history_overlaps(self):
        times = [BASE + i * T for i in range(6)]
        sampler = presentation.PresentationSampler(['unused'], snapshot(times[:2]))
        sampler.add_snapshot({'layers': {BLAST: 'not supported'}})
        sampler.add_snapshot(snapshot(times), final=True)
        self.assertEqual(1, sampler.metadata()['errorCount'])
        self.assertTrue(sampler.analyze(*sampler.observation_bounds())['coverage']['complete'])

    def test_missed_gap_streak_cannot_cross_an_unobserved_history_hole(self):
        times = [BASE, BASE + 2 * T, BASE + 4 * T]
        sampler = presentation.PresentationSampler(['unused'], snapshot(times[:1]))
        sampler.add_snapshot(snapshot(times))
        sampler.add_snapshot(snapshot([BASE + 100 * T, BASE + 102 * T]), final=True)
        result = sampler.analyze(*sampler.observation_bounds())
        self.assertFalse(result['coverage']['complete'])
        self.assertEqual(3, result['missedRefreshLikeGaps'])
        self.assertEqual(2, result['longestMissedRefreshLikeStreak'])
        self.assertEqual({'2': 3}, result['refreshMultipleHistogram'])

    def test_snapshot_storage_remains_bounded(self):
        sampler = presentation.PresentationSampler(['unused'], snapshot([BASE]))
        for _ in range(presentation.MAX_SNAPSHOTS + 5):
            sampler.add_snapshot(snapshot([BASE]))
        self.assertEqual(presentation.MAX_SNAPSHOTS, len(sampler.metadata()['snapshots']))
        self.assertTrue(sampler.metadata()['storageLimitReached'])


class CollectorLifecycleTest(unittest.TestCase):
    def test_read_only_poll_final_fence_and_bounded_idempotent_shutdown(self):
        entered = threading.Event()
        calls = []
        def runner(argv, **kwargs):
            calls.append((argv, kwargs))
            entered.set()
            return SimpleNamespace(stdout=raw([BASE, BASE + T]))
        sampler = presentation.PresentationSampler(['fake-adb', '-s', 'emulator-5556'], snapshot([BASE]), runner=runner)
        sampler.start()
        self.assertTrue(entered.wait(.5))
        self.assertTrue(sampler.stop())
        self.assertTrue(sampler.stop())
        self.assertTrue(sampler.metadata()['stopped'])
        self.assertEqual((BASE, BASE + T), sampler.observation_bounds())
        self.assertNotIn('--latency-clear', str(calls))
        self.assertTrue(all(call[1]['timeout'] == 1 for call in calls))
        self.assertTrue(all(call[0][-1] == "'" + BLAST + "'" for call in calls))
        with self.assertRaises(RuntimeError):
            sampler.start()

    def test_discovery_missing_command_and_final_timeout_are_explicit(self):
        runner = mock.Mock(side_effect=subprocess.TimeoutExpired('adb', 1))
        sampler = presentation.PresentationSampler(['fake-adb'], snapshot([BASE]), runner=runner)
        sampler.start()
        self.assertTrue(sampler.stop())
        self.assertGreater(sampler.metadata()['errorCount'], 0)
        self.assertEqual((BASE, None), sampler.observation_bounds())
        sampler = presentation.PresentationSampler(['fake-adb'], {}, runner=mock.Mock(return_value=SimpleNamespace(stdout='other.app\n')))
        sampler.start()
        self.assertTrue(sampler.stop())
        self.assertIsNone(sampler.metadata()['layer'])
        self.assertGreater(sampler.metadata()['errorCount'], 0)

    def test_invalid_configuration_is_rejected_before_a_command(self):
        for kwargs in [{'interval_seconds': 0}, {'interval_seconds': 2}, {'max_seconds': 1000}]:
            with self.assertRaises(ValueError):
                presentation.PresentationSampler(['unused'], {}, **kwargs)

    def test_worker_exits_at_its_own_duration_limit_even_without_stop(self):
        clock = mock.Mock(side_effect=[0, 0, 2, 3])
        runner = mock.Mock(return_value=SimpleNamespace(stdout=raw([BASE, BASE + T])))
        sampler = presentation.PresentationSampler(['unused'], snapshot([BASE]), max_seconds=1,
                                                     runner=runner, clock=clock)
        sampler.start()
        sampler.thread.join(timeout=.5)
        self.assertFalse(sampler.thread.is_alive())
        self.assertTrue(sampler.metadata()['durationLimitReached'])
        self.assertTrue(sampler.stop())


if __name__ == '__main__':
    unittest.main()
