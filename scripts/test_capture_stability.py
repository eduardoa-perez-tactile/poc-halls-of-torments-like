"""Reject idle/frozen or failed-audio captures that superficially complete UI transitions."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock
import contextlib
import io

spec = importlib.util.spec_from_file_location('capture_stability', Path(__file__).with_name('capture-stability.py'))
stability = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stability)


class CombatEvidenceTest(unittest.TestCase):
    def test_full_skills_requires_real_late_combat_and_multiple_occupied_skill_pools(self):
        log = '\n'.join([
            'I FrameMetrics: phase=COOP_LATE simulationStepsTotal=900 enemies[last=48,frameSampledPeak=62] gpuTextureBytes=32959308 '
            'orbiters[last=4,frameSampledPeak=4] fields[last=2,frameSampledPeak=3] glaives[last=1,frameSampledPeak=2]',
            'I ProfileWorld: phase=COOP_LATE runSeed=42 elapsed=1115 expansionCollisionCandidates=400 audioWorkers=2 audioMusicHandles=1',
            'I ProfileWorld: phase=COOP_LATE runSeed=42 elapsed=1125 expansionCollisionCandidates=900 audioWorkers=2 audioMusicHandles=1'])
        self.assertTrue(stability.validate_combat_evidence(log, 1, phase='COOP_LATE', require_skills=True)['valid'])
        for bad in (log.replace('COOP_LATE', 'RESULTS'), log.replace('1115', '1125'),
                    log.replace('frameSampledPeak=62', 'frameSampledPeak=12'),
                    log.replace('fields[last=2,frameSampledPeak=3]', 'fields[last=0,frameSampledPeak=0]')):
            with self.subTest(log=bad):
                self.assertFalse(stability.validate_combat_evidence(bad, 1, phase='COOP_LATE', require_skills=True)['valid'])

    def test_requires_advancing_combat_and_bounded_live_resources(self):
        log = '\n'.join([
            'I FrameMetrics: phase=COOP_BOSS simulationStepsTotal=900 enemies[last=48,frameSampledPeak=62] gpuTextureBytes=32959308',
            'I ProfileWorld: phase=COOP_BOSS runSeed=42 elapsed=1205 meleeCollisionCandidates=400 audioWorkers=2 audioMusicHandles=1',
            'I ProfileWorld: phase=COOP_BOSS runSeed=42 elapsed=1215 meleeCollisionCandidates=900 audioWorkers=2 audioMusicHandles=1'])
        self.assertTrue(stability.validate_combat_evidence(log, 1)['valid'])
        for bad in (log.replace('1215', '1205'), log.replace('COOP_BOSS', 'PAUSED'),
                    log.replace('audioWorkers=2', 'audioWorkers=1'),
                    log.replace('audioMusicHandles=1', 'audioMusicHandles=2'),
                    log.replace('simulationStepsTotal=900', 'simulationStepsTotal=0'),
                    log.replace('gpuTextureBytes=32959308', 'gpuTextureBytes=99999999')):
            with self.subTest(log=bad):
                self.assertFalse(stability.validate_combat_evidence(bad, 1)['valid'])


class OptionalInstrumentationTest(unittest.TestCase):
    def test_options_are_opt_in_and_trace_interval_is_bounded(self):
        base = ['--serial', 'emulator-5554', '--host', '192.168.1.2', '--forward-port', '12345', '--output-dir', '/tmp/stability-test']
        args = stability.parse_args(base)
        self.assertFalse(args.presentation)
        self.assertFalse(args.full_skills)
        self.assertEqual(0, args.trace_every)
        args = stability.parse_args(base + ['--presentation', '--full-skills', '--trace-every', '3'])
        self.assertTrue(args.presentation and args.full_skills)
        self.assertEqual([1, 4, 7, 10], [cycle for cycle in range(1, 11) if stability.trace_selected(cycle, args.trace_every)])
        self.assertFalse(stability.trace_selected(1, 0))
        for invalid in ('-1', '11'):
            with self.subTest(invalid=invalid), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                stability.parse_args(base + ['--trace-every', invalid])

    def test_sampler_is_stopped_and_analyzed_at_thirty_before_trace_finish_and_cleanup(self):
        order = []
        sampler = mock.Mock()
        sampler.stop.side_effect = lambda: order.append('sampler-stop') or True
        sampler.metadata.return_value = {'snapshots': ['bounded-data']}
        sampler.observation_bounds.return_value = (100, 200)
        sampler.analyze.side_effect = lambda *args, **kwargs: order.append(('analyze', args, kwargs)) or {'available': True}
        trace = mock.Mock(metadata={'status': 'captured'})
        trace.finish.side_effect = lambda: order.append('trace-finish')
        trace.cleanup.side_effect = lambda: order.append('trace-cleanup')
        result = stability.finish_instrumentation(sampler, trace)
        self.assertEqual(['sampler-stop', ('analyze', (100, 200), {'target_fps': 30}), 'trace-finish', 'trace-cleanup'], order)
        self.assertEqual({'available': True}, result['presentation_analysis'])
        self.assertEqual({'status': 'captured'}, result['system_trace'])

    def test_failed_observation_still_finishes_and_cleans_trace_and_preserves_failure_metadata(self):
        sampler = mock.Mock()
        sampler.stop.side_effect = RuntimeError('sampler failed')
        trace = mock.Mock(metadata={'status': 'unavailable'})
        trace.finish.side_effect = RuntimeError('trace failed')
        result = stability.finish_instrumentation(sampler, trace)
        trace.finish.assert_called_once()
        trace.cleanup.assert_called_once()
        self.assertEqual('sampler failed', result['presentation_error'])
        self.assertEqual('trace failed', result['trace_error'])

    def test_cancelled_observation_still_finishes_and_cleans_trace(self):
        sampler = mock.Mock()
        sampler.stop.side_effect = KeyboardInterrupt()
        trace = mock.Mock(metadata={})
        with self.assertRaises(KeyboardInterrupt):
            stability.finish_instrumentation(sampler, trace)
        trace.finish.assert_called_once()
        trace.cleanup.assert_called_once()


if __name__ == '__main__':
    unittest.main()
