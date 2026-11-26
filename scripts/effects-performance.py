#!/usr/bin/env python3
"""One controlled installed-APK crowded co-op arm; primary caller owns APK installation.

Uses existing profile fixtures only. No simulation changes, compositor resets,
tracing, screenshots, video, forwarding changes or join-host overrides.
"""
from pathlib import Path
import argparse
import importlib.util
import json
import os
import re
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('capture_performance', ROOT / 'scripts/capture-performance.py')
cap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cap)


def args_for_run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--host', required=True, type=cap.phone_reachable_host)
    parser.add_argument('--forward-port', type=int, required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--expected-apk-sha256', required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--windows', type=int, choices=range(1, 7), default=4)
    parser.add_argument('--seconds', type=int, choices=range(10, 31), default=20)
    parser.add_argument('--warmup-seconds', type=int, choices=range(5, 31), default=15)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('Only an explicit Android emulator serial is supported')
    if not re.fullmatch(r'[a-zA-Z0-9_-]+', args.label):
        parser.error('label must be a simple filename component')
    if not re.fullmatch(r'[a-f0-9]{64}', args.expected_apk_sha256):
        parser.error('Expected installed APK SHA-256 is required')
    return args


def main():
    args = args_for_run()
    directory = args.output_dir.resolve() / args.label
    directory.mkdir(parents=True, exist_ok=False)
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
               or Path.home() / 'Library/Android/sdk')
    target = [str(sdk / 'platform-tools/adb'), '-s', args.serial]
    phones = []
    logger = output = sampler = None
    report = {'label': args.label, 'declared_apk_source': args.source,
              'target_fps': 30, 'windows': [], 'memory': [], 'complete': False,
              'protocol': {'windows': args.windows, 'seconds': args.seconds,
                           'warmup_seconds': args.warmup_seconds,
                           'controllers': 'Two neutral heartbeat-only phones; fixed movement and Dodge at zero',
                           'heroes': 'Fresh-process defaults: player 1 Warden; player 2 Ranger',
                           'fixture': 'Existing NUM4 full class skills, F12 level/equipment/late crowd; level20 preprimed',
                           'measurement': 'No choices, keyboard, screenshots, video, tracing or memory queries during cadence windows'},
              'limits': ['Debug/profile APK on Android emulator, not physical-TV certification',
                         'Normal fixed simulation advances; host scheduling can produce small workload differences',
                         'PSS snapshots are OS estimates outside cadence windows; short observations do not prove leak freedom']}

    def command(*parts):
        return subprocess.run(target + list(parts), check=True, text=True, capture_output=True, timeout=12).stdout.strip()

    def key(code):
        command('shell', 'input', 'keyevent', str(code))

    def healthy():
        for phone in phones:
            if phone.error is not None:
                raise RuntimeError('Synthetic controller failed') from phone.error

    def all_phase(phase):
        return len(phones) == 2 and all(p.latest_status and p.latest_status.get('phase') == phase for p in phones)

    def wait(seconds, require_session=False):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            healthy()
            if require_session and not (all_phase('session') and all(
                    p.latest_status.get('hostActive') is True and p.latest_status.get('alive') is True for p in phones)):
                raise RuntimeError('Non-combat phase during a claimed measurement: ' + str([p.latest_status for p in phones]))
            time.sleep(min(.05, max(0, deadline - time.monotonic())))

    def until(predicate, label, seconds=8):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            healthy()
            if predicate():
                return
            time.sleep(.05)
        raise RuntimeError('Timed out: ' + label + '; states=' + str([p.latest_status for p in phones]))

    def save():
        (directory / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')

    def memory(label):
        raw = command('shell', 'dumpsys', 'meminfo', cap.PACKAGE)
        row = {'label': label, 'host_monotonic_ns': time.monotonic_ns()}
        for name, pattern in [('total_pss_kib', r'TOTAL PSS:\s*(\d+)'),
                              ('java_pss_kib', r'Java Heap:\s*(\d+)'), ('native_pss_kib', r'Native Heap:\s*(\d+)')]:
            match = re.search(pattern, raw)
            if not match:
                raise RuntimeError('Memory category unavailable: ' + name)
            row[name] = int(match[1])
        pid = command('shell', 'pidof', cap.PACKAGE)
        if pid != report['pid']:
            raise RuntimeError('App PID changed')
        row['thread_count'] = len(command('shell', 'ls', '/proc/' + pid + '/task').split())
        report['memory'].append(row)
        with (directory / 'memory.txt').open('a') as file:
            file.write('\n' + json.dumps(row) + '\n' + raw + '\n')
        save()
        return row

    def fixture():
        key(11)  # NUM4: existing full-class skills + reset to repeatable placement.
        wait(.2)
        dismissed = cap.dismiss_fixture_keyboard(command, key, wait)
        key(142)  # F12: equipment, invulnerability and late crowd, without a boss.
        until(lambda: all_phase('session'), 'late co-op fixture')
        wait(.5, require_session=True)
        return dismissed

    def choose_upgrade():
        until(lambda: all_phase('level_up'), 'level-up choices')
        for phone in phones:
            offer = phone.latest_status['progression']['upgrade']
            if not offer or len(offer['options']) != 3:
                raise RuntimeError('No personal three-card offer')
            phone.send(json.dumps({'type': 'upgrade', 'offerId': offer['id'], 'option': 0}))
        until(lambda: all_phase('session'), 'choice completion')

    before_prefs = command('shell', 'run-as', cap.PACKAGE, 'cat', 'shared_prefs/emulator_join.xml')
    before_forwards = subprocess.check_output([target[0], 'forward', '--list'], text=True)
    if args.host not in before_prefs:
        raise RuntimeError('Saved phone-reachable QR host differs from requested host')
    installed_paths = command('shell', 'pm', 'path', cap.PACKAGE).splitlines()
    if len(installed_paths) != 1 or not installed_paths[0].startswith('package:'):
        raise RuntimeError('Expected one installed APK')
    installed_sha = command('shell', 'sha256sum', installed_paths[0][8:]).split()[0]
    report['installed_apk_sha256'] = installed_sha
    if installed_sha != args.expected_apk_sha256:
        raise RuntimeError('Installed APK is not the declared measurement build')
    save()
    try:
        output = (directory / 'game.log').open('wb', buffering=0)
        logger = subprocess.Popen(target + ['logcat', '-v', 'threadtime', '-T', '1', 'FrameMetrics:I',
            'ProfileWorld:I', 'ProfileMemory:I', 'ProfileRenderStall:I', 'FramePacing:I', 'Audio:I', 'AndroidRuntime:E', '*:S'],
            stdout=output, stderr=subprocess.STDOUT)
        command('shell', 'am', 'force-stop', cap.PACKAGE)
        command('shell', 'am', 'start', '-n', cap.COMPONENT, '--ez', 'profile', 'true')
        wait(3)
        report['pid'] = command('shell', 'pidof', cap.PACKAGE)
        key(23)
        wait(.4)
        room = json.load(urllib.request.urlopen(f'http://127.0.0.1:{args.forward_port}/session', timeout=3))['room']
        phones.extend(cap.Phone(args.forward_port, room, False, slot) for slot in range(2))
        until(lambda: all(p.joined for p in phones), 'two controllers')
        for code in (20, 22, 23):
            key(code)
        until(lambda: all_phase('session'), 'co-op start')
        fixture()
        until(lambda: all(p.latest_status['progression']['level'] == 18 for p in phones), 'preview level18')
        for _ in range(2):
            key(135)  # F5, real personal choices before all timing observations.
            choose_upgrade()
        until(lambda: all(p.latest_status['progression']['level'] == 20 for p in phones), 'max party level20')
        fixture()
        wait(args.warmup_seconds, require_session=True)
        memory('warmup-combat')
        for index in range(1, args.windows + 1):
            dismissed = fixture()
            memory(f'window-{index}-before')
            key(93)  # PAGE_DOWN resets callback report window; presentation counters stay untouched.
            sampler = cap.presentation_metrics.PresentationSampler(target, cap.surface_latency(command))
            start = {'window': index, 'pid': report['pid'], 'host_monotonic_ns': time.monotonic_ns(),
                     'party_levels': [p.latest_status['progression']['level'] for p in phones]}
            output.write(('# CAPTURE START ' + json.dumps(start) + '\n').encode())
            offset = (directory / 'game.log').stat().st_size
            sampler.start()
            wait(args.seconds, require_session=True)
            stopped = sampler.stop()
            output.write(('# CAPTURE END ' + json.dumps({'window': index, 'host_monotonic_ns': time.monotonic_ns()}) + '\n').encode())
            sampling = sampler.metadata()
            analysis = sampler.analyze(*sampler.observation_bounds(), target_fps=30)
            sampler = None
            if not stopped or not analysis.get('available') or not analysis.get('coverage', {}).get('complete'):
                raise RuntimeError('Incomplete presentation coverage: ' + str(analysis))
            window = {'window': index, 'target_fps': 30, 'presentation_analysis': analysis,
                      'fixture_keyboard_dismissed': dismissed, 'start': start}
            (directory / f'window-{index:02d}.presentation.json').write_text(json.dumps(
                {'window': index, 'target_fps': 30, 'presentation_sampling': sampling, 'presentation_analysis': analysis}, indent=2) + '\n')
            # Log parsing and all dumpsys memory calls happen only after cadence sampling stops.
            raw = (directory / 'game.log').read_bytes()[offset:].decode(errors='replace')
            parsed = cap.summarize_baseline('# CAPTURE START {}\n' + raw, {'fixture': 'effects-controlled'})
            window['frame_reports'] = parsed['reports']
            window['world_reports'] = [r for r in parsed['diagnostics'] if r['tag'] == 'ProfileWorld']
            frames = [r for r in window['frame_reports'] if r.get('phase') == 'COOP_LATE']
            worlds = [r for r in window['world_reports'] if r.get('phase') == 'COOP_LATE']
            checks = {
                'enough_simulation_steps': sum(int(r.get('simulationStepsTotal', 0)) for r in frames) >= 600,
                'crowded': max((r.get('enemies', {}).get('frameSampledPeak', 0) for r in frames), default=0) >= 40,
                'warden_orbiters': max((r.get('orbiters', {}).get('frameSampledPeak', 0) for r in frames), default=0) == 4,
                'ranger_bolts': max((r.get('bolts', {}).get('frameSampledPeak', 0) for r in frames), default=0) > 0,
                'max_level_no_choices': bool(worlds) and all(int(r.get('partyLevel', 0)) == 20 for r in worlds),
                'advancing_arena': len(worlds) >= 2 and max(float(r['elapsed']) for r in worlds) - min(float(r['elapsed']) for r in worlds) >= 5,
                'single_seed': len({r.get('runSeed') for r in worlds}) == 1,
            }
            for name in ('boltCollisionCandidates', 'meleeCollisionCandidates', 'expansionCollisionCandidates'):
                values = [int(r.get(name, 0)) for r in worlds]
                checks[name + '_advances'] = len(values) >= 2 and max(values) > min(values)
            window['combat_checks'] = checks
            report['windows'].append(window)
            if not all(checks.values()):
                raise RuntimeError('Incomplete real crowded combat evidence: ' + str(checks))
            memory(f'window-{index}-after')
            save()
            print(json.dumps({'label': args.label, 'window': index, 'fps': analysis['observedFps'],
                              'interval_ms': analysis['intervalMs'], 'gaps_over_50ms': analysis['missedTargetFrameLikeGaps']}), flush=True)
        report['pid_after'] = command('shell', 'pidof', cap.PACKAGE)
        if report['pid_after'] != report['pid']:
            raise RuntimeError('App restarted during capture')
        report['controllers'] = [{'player': p.player, 'input_messages': p.input_messages,
                                  'moving_input': p.moving_input, 'error': str(p.error) if p.error else None} for p in phones]
        report['complete'] = True
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        if sampler is not None:
            sampler.stop()
        for phone in phones:
            phone.close()
        if logger is not None:
            logger.terminate()
            try:
                logger.wait(timeout=3)
            except subprocess.TimeoutExpired:
                logger.kill(); logger.wait(timeout=3)
        if output is not None:
            output.close()
        if (directory / 'game.log').exists():
            text = (directory / 'game.log').read_text(errors='replace')
            report['fatal_exception_recorded'] = 'FATAL EXCEPTION' in text
            (directory / 'parsed-log.json').write_text(json.dumps(cap.summarize_baseline(
                text, {'fixture': 'effects-controlled'}), indent=2) + '\n')
        after_prefs = command('shell', 'run-as', cap.PACKAGE, 'cat', 'shared_prefs/emulator_join.xml')
        after_forwards = subprocess.check_output([target[0], 'forward', '--list'], text=True)
        report['saved_qr_host_unchanged'] = before_prefs == after_prefs
        report['forwards_unchanged'] = before_forwards == after_forwards
        if not report['saved_qr_host_unchanged'] or not report['forwards_unchanged']:
            report['complete'] = False
            report['preservation_error'] = True
        save()
        command('shell', 'am', 'force-stop', cap.PACKAGE)
        command('shell', 'am', 'start', '-n', cap.COMPONENT, '--ez', 'profile', 'true')
    if not report['complete'] or report.get('fatal_exception_recorded'):
        raise RuntimeError('Incomplete or failed capture; see summary.json')


if __name__ == '__main__':
    main()
