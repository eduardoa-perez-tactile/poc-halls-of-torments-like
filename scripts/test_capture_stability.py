"""Reject idle/frozen or failed-audio captures that superficially complete UI transitions."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('capture_stability', Path(__file__).with_name('capture-stability.py'))
stability = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stability)


class CombatEvidenceTest(unittest.TestCase):
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


if __name__ == '__main__':
    unittest.main()
