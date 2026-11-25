#!/usr/bin/env python3
"""Bounded Android-emulator stress cycles; never targets physical hardware or changes game rules."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request

spec = importlib.util.spec_from_file_location('capture_performance', Path(__file__).with_name('capture-performance.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


def validate_combat_evidence(log_text, completed_cycles):
    """A responsive controller alone is not evidence that a stress fixture actually simulated combat."""
    parsed = capture.summarize_baseline(log_text, {'fixture': 'stability'})
    boss_frames = [row for row in parsed['reports'] if row.get('phase') == 'COOP_BOSS']
    boss_worlds = [row for row in parsed['diagnostics'] if row['tag'] == 'ProfileWorld' and row.get('phase') == 'COOP_BOSS']
    runs = {}
    for row in boss_worlds:
        runs.setdefault(row['runSeed'], []).append(row)
    advancing = sum(len(rows) > 1 and max(float(row['elapsed']) for row in rows)
                    - min(float(row['elapsed']) for row in rows) >= 5 for rows in runs.values())
    crowded_windows = sum(row.get('enemies', {}).get('frameSampledPeak', 0) >= 40 for row in boss_frames)
    simulated_steps = sum(int(row.get('simulationStepsTotal', 0)) for row in boss_frames)
    attacking_runs = sum(any(int(row.get('boltCollisionCandidates', 0)) + int(row.get('meleeCollisionCandidates', 0))
                             + int(row.get('expansionCollisionCandidates', 0)) > 0 for row in rows)
                         for rows in runs.values())
    workers = {int(row['audioWorkers']) for row in boss_worlds if 'audioWorkers' in row}
    handles = {int(row['audioMusicHandles']) for row in boss_worlds if 'audioMusicHandles' in row}
    textures = [int(row['gpuTextureBytes']) for row in parsed['reports'] if 'gpuTextureBytes' in row]
    violations = []
    for condition, message in [
        (completed_cycles > 0, 'No completed stress cycles'),
        (advancing >= completed_cycles, 'Not every cycle has evidence of advancing boss combat time'),
        (attacking_runs >= completed_cycles, 'Not every cycle has real attack collision tests'),
        (crowded_windows >= completed_cycles, 'Insufficient high-enemy-occupancy boss windows'),
        (simulated_steps >= completed_cycles * 600, 'Insufficient active fixed-step boss simulation'),
        (workers == {2}, 'Audio workers failed, grew, or were not reported'),
        (bool(handles) and max(handles) <= 1, 'Music handle ownership grew or was not reported'),
        (bool(textures) and max(textures) <= 32 * 1_048_576, 'Texture budget exceeded or was not reported'),
        ('FATAL EXCEPTION' not in log_text, 'Fatal Android exception recorded')]:
        if not condition:
            violations.append(message)
    return {'valid': not violations, 'violations': violations, 'advancing_combat_runs': advancing,
            'attacking_runs': attacking_runs, 'crowded_boss_windows': crowded_windows,
            'boss_simulation_steps': simulated_steps, 'audio_worker_counts': sorted(workers),
            'music_handle_counts': sorted(handles), 'max_texture_bytes': max(textures, default=0)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--host', required=True, type=capture.phone_reachable_host)
    parser.add_argument('--forward-port', required=True, type=int)
    parser.add_argument('--seconds', type=int, choices=range(60, 901), default=600, metavar='60..900')
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('An explicit emulator serial is required; physical devices are never targeted.')
    directory = args.output_dir.resolve()
    directory.mkdir(parents=True, exist_ok=True)
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
    target = [str(sdk / 'platform-tools/adb'), '-s', args.serial]
    phones = []
    retired_inputs = []
    logger = None
    report = {'requested_seconds': args.seconds, 'source': capture.source_provenance(),
              'cycles': [], 'memory': [], 'complete': False,
              'limits': ['Profiling/debug APK and synthetic input; no physical-TV thermal or GPU certification.',
                         'ADB snapshots and transitions perturb frame pacing; use separate untraced captures for comparison.',
                         'Each cycle uses existing short fixtures; this is repeated stress, not a twenty-minute survival playthrough.']}
    began = time.monotonic()

    def command(*parts):
        return subprocess.run(target + list(parts), check=True, text=True, capture_output=True, timeout=10).stdout.strip()

    def key(code):
        command('shell', 'input', 'keyevent', str(code))

    def healthy():
        for phone in phones:
            if phone is not None and phone.error is not None:
                raise RuntimeError('Synthetic controller failed') from phone.error

    def wait(seconds):
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            healthy()
            time.sleep(min(.05, max(0, end - time.monotonic())))

    def until(predicate, label, seconds=6):
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            healthy()
            if predicate():
                return
            time.sleep(.05)
        phases = [phone.latest_status.get('phase') if phone and phone.latest_status else None for phone in phones]
        raise RuntimeError(f'Timed out waiting for {label}; phases={phases}')

    def all_phase(phase):
        return len(phones) == 2 and all(phone and phone.latest_status and phone.latest_status.get('phase') == phase for phone in phones)

    def save():
        (directory / 'summary.json').write_text(json.dumps(report, indent=2) + '\n')

    def memory(label):
        raw = command('shell', 'dumpsys', 'meminfo', capture.PACKAGE)
        snapshot = {'elapsed_seconds': round(time.monotonic() - began, 2), 'label': label}
        for name, pattern in [('total_pss_kib', r'TOTAL PSS:\s*(\d+)'),
                              ('java_pss_kib', r'Java Heap:\s*(\d+)'), ('native_pss_kib', r'Native Heap:\s*(\d+)')]:
            match = re.search(pattern, raw)
            snapshot[name] = int(match[1]) if match else None
        pid = command('shell', 'pidof', capture.PACKAGE)
        if pid != report['pid']:
            raise RuntimeError('Game process restarted during stress capture')
        tasks = command('shell', 'ls', '/proc/' + pid + '/task')
        snapshot['thread_count'] = len(tasks.split())
        report['memory'].append(snapshot)
        with (directory / 'memory.txt').open('a') as out:
            out.write('\n' + json.dumps(snapshot) + '\n' + raw + '\n')
        save()
        print(json.dumps(snapshot), flush=True)

    def choose(kind):
        phase = 'level_up' if kind == 'upgrade' else 'loot'
        until(lambda: all_phase(phase), phase)
        def offer(phone):
            status = phone.latest_status
            return status['progression']['upgrade'] if kind == 'upgrade' else status['equipment']
        for _ in range(20):  # Pending/queued rounds remain bounded by the run's existing entitlements.
            ids = []
            for phone in phones:
                card = offer(phone)
                ids.append(card['id'] if card else None)
                if card is None:
                    continue  # An already acknowledged player can wait for the rest of the party.
                if len(card['options']) != (3 if kind == 'upgrade' else 2):
                    raise RuntimeError('Expected personal choice cards missing')
                phone.send(json.dumps({'type': kind, 'offerId': card['id'], 'option': 0}))
            def advanced():
                if all_phase('session') or all_phase('loot' if kind == 'upgrade' else 'level_up'):
                    return True
                return all_phase(phase) and any(offer(phone) and offer(phone)['id'] != old
                                               for phone, old in zip(phones, ids))
            until(advanced, 'party choice acknowledgment or next queued round')
            if not all_phase(phase):
                return
        raise RuntimeError('Choice rounds exceeded the bounded run entitlement count')

    def ordinary_session(label):
        # Combat can earn a choice during an ADB snapshot or just before a pause/background event.
        for _ in range(20):
            until(lambda: any(all_phase(phase) for phase in ('session', 'level_up', 'loot')), label)
            if all_phase('session'):
                return
            choose('upgrade' if all_phase('level_up') else 'equipment')
        raise RuntimeError('Could not drain bounded pending choices on resume')

    try:
        output = (directory / 'game.log').open('wb', buffering=0)
        logger = subprocess.Popen(target + ['logcat', '-v', 'threadtime', '-T', '1', 'FrameMetrics:I',
                                  'ProfileWorld:I', 'ProfileMemory:I', 'Audio:I', 'AndroidRuntime:E', '*:S'],
                                  stdout=output, stderr=subprocess.STDOUT)
        command('shell', 'am', 'force-stop', capture.PACKAGE)
        command('shell', 'am', 'start', '-n', capture.COMPONENT, '--ez', 'profile', 'true', '--es', 'joinHost', args.host)
        wait(3)
        key(23)  # Home -> lobby.
        wait(.4)
        room = json.load(urllib.request.urlopen(f'http://127.0.0.1:{args.forward_port}/session', timeout=3))['room']
        phones.extend(capture.Phone(args.forward_port, room, True, slot) for slot in range(2))
        until(lambda: all(phone.joined for phone in phones), 'two controllers')
        report['pid'] = command('shell', 'pidof', capture.PACKAGE)
        report['installed_apks'] = []
        for line in command('shell', 'pm', 'path', capture.PACKAGE).splitlines():
            if line.startswith('package:'):
                path = line[len('package:'):]
                report['installed_apks'].append(command('shell', 'sha256sum', path))
        for code in (20, 22, 23):
            key(code)
        until(lambda: all_phase('session'), 'co-op session')
        deadline = time.monotonic() + args.seconds
        cycle = 0
        while time.monotonic() < deadline:
            cycle += 1
            # A real personal gear round before consuming the fixture's eight run rewards.
            key(132)  # F2.
            choose('equipment')
            key(135)  # F5: real shared level-up round.
            until(lambda: all_phase('level_up'), 'skill cards')
            if cycle % 4 == 1:
                # Reconnect while choices exist; identity and pending offer must survive.
                old = phones[1]
                token, player = old.token, old.player
                offer_id = old.latest_status['progression']['upgrade']['id']
                phones[1] = None
                old.close()
                retired_inputs.append({'player': old.player, 'inputs': old.input_messages,
                                       'active_inputs': old.active_input_messages, 'dodge_edges': old.dodge_edges})
                until(lambda: phones[0].latest_status.get('phase') == 'paused', 'disconnect pause')
                phones[1] = capture.Phone(args.forward_port, room, True, 1, token=token)
                until(lambda: phones[1].joined and phones[1].latest_status is not None, 'reconnected controller')
                restored = phones[1].latest_status['progression']['upgrade']
                if phones[1].player != player or restored is None or restored['id'] != offer_id:
                    raise RuntimeError('Reconnect changed player identity or pending choice')
                key(4)
            choose('upgrade')
            key(142)  # F12: equipped late encounter and bounded invulnerability.
            wait(.25)
            key(136)  # F6: final boss.
            combat_end = time.monotonic() + 20
            while time.monotonic() < combat_end:
                if all_phase('level_up'):
                    choose('upgrade')
                elif all_phase('loot'):
                    choose('equipment')
                wait(.1)
            memory(f'cycle-{cycle}-combat')
            key(4)
            until(lambda: all_phase('paused'), 'remote pause')
            wait(.35)
            key(4)
            ordinary_session('remote resume')
            if cycle % 3 == 0:
                key(3)  # Android Home; background/resume the same process.
                until(lambda: all(phone.latest_status.get('hostActive') is False for phone in phones), 'background suspension')
                wait(.35)
                command('shell', 'am', 'start', '-n', capture.COMPONENT)
                until(lambda: all_phase('paused') and all(phone.latest_status.get('hostActive') for phone in phones), 'foreground pause')
                key(4)
                ordinary_session('foreground resume')
            key(137 if cycle % 2 else 138)  # Existing Victory / Defeat result fixtures.
            until(lambda: all_phase('results'), 'results')
            wait(.8)  # Include the approved Defeat reveal/action gate.
            memory(f'cycle-{cycle}-results')
            report['cycles'].append({'cycle': cycle, 'elapsed_seconds': round(time.monotonic() - began, 2),
                                      'checks': ['gear', 'crowded-coop-boss', 'pause-resume', 'skill', 'results', 'replay'],
                                      'reconnect': cycle % 4 == 1, 'background_resume': cycle % 3 == 0})
            save()
            if cycle % 2:
                key(23)  # Real replay button, same connected phones and process.
            else:
                key(4)
                until(lambda: all_phase('lobby'), 'return to lobby')
                for code in (20, 22, 23):
                    key(code)
            until(lambda: all_phase('session'), 'replay')
        report['complete'] = True
        report['elapsed_seconds'] = round(time.monotonic() - began, 2)
        report['controllers'] = retired_inputs + [
            {'player': phone.player, 'inputs': phone.input_messages, 'active_inputs': phone.active_input_messages,
             'dodge_edges': phone.dodge_edges} for phone in phones]
    except BaseException as failure:
        report['error'] = str(failure)
        raise
    finally:
        for phone in phones:
            if phone is not None:
                phone.close()
        if logger is not None:
            logger.terminate()
            try:
                logger.wait(timeout=3)
            except subprocess.TimeoutExpired:
                logger.kill()
                logger.wait(timeout=3)
            output.close()
        log = directory / 'game.log'
        report['fatal_exception_recorded'] = log.exists() and 'FATAL EXCEPTION' in log.read_text(errors='replace')
        if report['complete']:
            report['combat_validation'] = validate_combat_evidence(log.read_text(errors='replace'), len(report['cycles']))
        save()
        command('shell', 'am', 'force-stop', capture.PACKAGE)
        print('Stress capture finished; synthetic phones closed and app stopped on its explicit emulator.', flush=True)
    if report['fatal_exception_recorded']:
        raise RuntimeError('Fatal Android exception recorded; inspect game.log')
    if not report.get('combat_validation', {}).get('valid', False):
        raise RuntimeError('Stress capture lacks expected combat/resource evidence; inspect summary.json')


if __name__ == '__main__':
    main()
