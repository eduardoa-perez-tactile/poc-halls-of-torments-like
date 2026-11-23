#!/usr/bin/env python3
"""Capture one short installed-APK fixture; never targets a physical Android device."""
import argparse
import base64
import hashlib
import json
import math
import os
from pathlib import Path
import re
import select
import socket
import subprocess
import threading
import time
import urllib.request
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.couchcrypt.tv'
COMPONENT = PACKAGE + '/com.couchcrypt.android.AndroidLauncher'


def websocket_frame(payload, opcode=1):
    """RFC 6455 client frame; JSON inputs can exceed the 125-byte short form."""
    data = payload.encode() if isinstance(payload, str) else payload
    size = len(data)
    length = bytes([0x80 | size]) if size < 126 else (
        b'\xfe' + size.to_bytes(2, 'big') if size < 65536 else b'\xff' + size.to_bytes(8, 'big'))
    mask = os.urandom(4)
    return bytes([0x80 | opcode]) + length + mask + bytes(value ^ mask[i % 4] for i, value in enumerate(data))


class Phone:
    """Heartbeat by default; optional deterministic session input, never choices."""
    def __init__(self, port, room, moving_input=False, player_offset=0):
        self.connection = socket.create_connection(('127.0.0.1', port), timeout=3)
        self.stopping = threading.Event()
        self.error = None
        self.send_lock = threading.Lock()
        self.moving_input = moving_input
        self.player_offset = player_offset
        self.joined = self.controllable = False
        self.sequence = 0
        self.received = bytearray()
        self.fragment = bytearray()
        self.last_dodge = -1
        self.input_messages = self.active_input_messages = self.dodge_edges = 0
        try:
            key = base64.b64encode(os.urandom(16)).decode()
            request = (f'GET /socket?room={room} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n'
                       f'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n'
                       'Sec-WebSocket-Version: 13\r\n\r\n')
            self.connection.sendall(request.encode())
            response = b''
            while b'\r\n\r\n' not in response:
                block = self.connection.recv(4096)
                if not block:
                    raise RuntimeError('WebSocket closed during handshake')
                response += block
                if len(response) > 16384:
                    raise RuntimeError('Oversized WebSocket handshake')
            header_bytes, remainder = response.split(b'\r\n\r\n', 1)
            headers = header_bytes.decode()
            expected = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
            if ' 101 ' not in headers.splitlines()[0] or expected not in headers:
                raise RuntimeError('Controller WebSocket handshake rejected')
            self.connection.settimeout(.5)
            self.receive(remainder)
            self.ping()
            self.worker = threading.Thread(target=self.heartbeat, name="capture-phone", daemon=True)
            self.worker.start()
        except BaseException:
            self.connection.close()
            raise

    def send(self, payload, opcode=1):
        with self.send_lock:
            self.connection.sendall(websocket_frame(payload, opcode))

    def receive(self, data):
        self.received.extend(data)
        while len(self.received) >= 2:
            first, second = self.received[:2]
            if second & 0x80:
                raise RuntimeError('Server sent a masked WebSocket frame')
            size, start = second & 127, 2
            if size in (126, 127):
                length_bytes = 2 if size == 126 else 8
                if len(self.received) < 2 + length_bytes:
                    return
                size = int.from_bytes(self.received[2:2 + length_bytes], 'big')
                start += length_bytes
            if size > 65536 or len(self.fragment) + size > 65536:
                raise RuntimeError('Oversized server WebSocket message')
            if len(self.received) < start + size:
                return
            payload = bytes(self.received[start:start + size])
            del self.received[:start + size]
            opcode = first & 15
            if opcode == 8:
                raise RuntimeError('Synthetic controller closed by server')
            if opcode == 9:
                self.send(payload, 10)
                continue
            if opcode == 10:
                continue
            if opcode not in (0, 1):
                raise RuntimeError('Unexpected WebSocket opcode')
            self.fragment.extend(payload)
            if not first & 0x80:
                continue
            message = json.loads(self.fragment.decode())
            self.fragment.clear()
            if message.get('type') == 'joined':
                self.joined = True
            elif message.get('type') in ('error', 'removed'):
                raise RuntimeError('Synthetic controller rejected: ' + str(message.get('reason', 'removed')))
            elif message.get('type') == 'status':
                self.controllable = message.get('phase') == 'session' and message.get('hostActive') is True and message.get('alive') is not False

    def input(self, elapsed, neutral=False):
        if not self.joined:
            return
        active = self.controllable and not neutral
        angle = elapsed * math.tau / 8 + self.player_offset * math.pi
        dodge_round = int(elapsed / 4)
        dodge = active and dodge_round > self.last_dodge and dodge_round > 0
        if dodge:
            self.last_dodge = dodge_round
        self.send(json.dumps({'type': 'input', 'x': round(.7 * math.cos(angle), 6) if active else 0,
                              'y': round(.7 * math.sin(angle), 6) if active else 0,
                              'dodge': dodge, 'seq': self.sequence}, separators=(',', ':')))
        self.sequence += 1
        self.input_messages += 1
        self.active_input_messages += int(active)
        self.dodge_edges += int(dodge)

    def ping(self):
        self.send('{"type":"ping"}')

    def heartbeat(self):
        last_ping = time.monotonic()
        began = next_input = last_ping
        try:
            while not self.stopping.is_set():
                now = time.monotonic()
                if now - last_ping >= 2:
                    self.ping()
                    last_ping = now
                if self.moving_input and now >= next_input:
                    self.input(now - began)
                    # Skip missed sends rather than replaying a burst of stale input.
                    next_input = now + 1 / 30
                timeout = min(.1, max(0, next_input - time.monotonic())) if self.moving_input else .1
                readable, _, _ = select.select([self.connection], [], [], timeout)
                if readable:
                    data = self.connection.recv(16384)
                    if not data:
                        raise RuntimeError('Synthetic controller disconnected')
                    self.receive(data)
        except (OSError, RuntimeError, ValueError) as failure:
            if not self.stopping.is_set():
                self.error = failure

    def close(self):
        self.stopping.set()
        self.worker.join(timeout=1)
        try:
            if self.moving_input:
                self.input(0, neutral=True)
            self.send('', 8)
        except OSError:
            pass
        self.connection.close()
        self.worker.join(timeout=1)


def parser_for_capture():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--host', required=True, help='Existing relay LAN IPv4 used by the TV QR')
    parser.add_argument('--forward-port', type=int, required=True, help='Existing ADB localhost forward to TV8787')
    parser.add_argument('--fixture', choices=['lobby', 'boss-solo', 'boss-coop', 'late-coop', 'items-coop', 'art-solo', 'art-coop', 'results'], default='boss-coop')
    parser.add_argument('--seconds', type=int, default=20, choices=range(5, 31), metavar='5..30')
    parser.add_argument('--lifecycle', action='store_true', help='Add one brief results/lobby/background-resume observation')
    parser.add_argument('--switch-smoke', action='store_true', help='Results only: send rapid F7/F8/Back after measurement')
    parser.add_argument('--moving-input', action='store_true', help='Send deterministic 30Hz movement and occasional Dodge; no choices')
    parser.add_argument('--warmup-seconds', type=int, choices=range(0, 11), default=0, metavar='0..10')
    parser.add_argument('--output-dir', type=Path, default=ROOT / '.run', help='Use a unique directory to preserve a baseline')
    parser.add_argument('--system-trace', action='store_true', help='Attempt one bounded Perfetto trace; compare with a separate untraced baseline')
    return parser


def trace_configuration(seconds):
    if not 5 <= seconds <= 30:
        raise ValueError('System trace duration must be 5..30 seconds')
    return f'''duration_ms: {seconds * 1000}
buffers {{ size_kb: 65536 fill_policy: RING_BUFFER }}
data_sources {{ config {{ name: "linux.ftrace" ftrace_config {{
  ftrace_events: "sched/sched_switch"
  ftrace_events: "sched/sched_wakeup"
  atrace_categories: "gfx"
  atrace_categories: "view"
  atrace_categories: "dalvik"
  atrace_apps: "{PACKAGE}"
}} }} }}
data_sources {{ config {{ name: "linux.process_stats" process_stats_config {{ scan_all_processes_on_start: true }} }} }}
data_sources {{ config {{ name: "android.surfaceflinger.frametimeline" }} }}
'''


class SystemTrace:
    """One finite foreground adb/Perfetto process and one owned device filename."""
    def __init__(self, target, prefix, seconds, command):
        self.target, self.prefix, self.seconds, self.command = target, prefix, seconds, command
        self.device_path = f'/data/misc/perfetto-traces/couchcrypt-baseline-{os.getpid()}.perfetto-trace'
        self.process = None
        self.metadata = {'requested': True, 'status': 'pending', 'duration_ms': seconds * 1000,
                         'buffer_kib': 65536, 'device_path': self.device_path,
                         'note': 'Trace attempt adds overhead; compare with a separate trace_on=false baseline. FrameTimeline source is optional and does not establish SurfaceView presentation timing.'}

    def start(self):
        try:
            config = trace_configuration(self.seconds)
            self.process = subprocess.Popen(self.target + ['shell', 'perfetto', '--txt', '-c', '-', '-o', self.device_path],
                                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            self.process.stdin.write(config)
            self.process.stdin.close()
            self.process.stdin = None
            self.metadata.update(status='started', config=config)
        except (subprocess.SubprocessError, OSError) as failure:
            self.metadata.update(status='unavailable', error=str(failure))
            if self.process is not None and self.process.stdin is not None:
                try:
                    self.process.stdin.close()
                except OSError:
                    pass
                self.process.stdin = None

    def finish(self):
        if self.process is None:
            return
        try:
            stdout, _ = self.process.communicate(timeout=10)
            self.metadata['process_output'] = stdout[-4096:] if stdout else ''
            self.metadata['returncode'] = self.process.returncode
            if self.process.returncode != 0:
                self.metadata['status'] = 'unavailable'
                return
            local_path = self.prefix.with_suffix('.system.perfetto-trace')
            self.command('pull', self.device_path, str(local_path))
            if not local_path.is_file() or local_path.stat().st_size == 0:
                raise OSError('Perfetto produced no local trace data')
            self.metadata.update(status='captured', local_file=str(local_path), bytes=local_path.stat().st_size)
        except (subprocess.SubprocessError, OSError) as failure:
            self.metadata.update(status='unavailable', error=str(failure))

    def cleanup(self):
        try:
            if self.process is not None and self.process.poll() is None:
                self.process.terminate()
                try:
                    self.process.communicate(timeout=3)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.communicate(timeout=3)
        except (subprocess.SubprocessError, OSError) as failure:
            self.metadata['process_cleanup_error'] = str(failure)
        try:
            self.command('shell', 'rm', '-f', self.device_path)
        except (subprocess.SubprocessError, OSError) as failure:
            self.metadata['device_cleanup_error'] = str(failure)


def source_provenance():
    def git(*args):
        return subprocess.run(['git', '-C', str(ROOT), *args], check=True, capture_output=True, timeout=10).stdout
    paths = git('ls-files', '-z', '--cached', '--others', '--exclude-standard', '--',
                'core/src', 'android/src', 'desktop/src', 'assets', 'scripts', '*.gradle', 'gradle.properties', 'settings.gradle')
    hashes = {}
    for name in sorted(set(paths.decode().split('\0')) - {''}):
        path = ROOT / name
        if path.is_file():
            hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    return {'git_head': git('rev-parse', 'HEAD').decode().strip(),
            'git_status_porcelain': git('status', '--porcelain=v1', '-uall').decode().splitlines(),
            'sha256': hashes,
            'note': 'Source snapshot is observational; it does not prove the installed APK was built from this source.'}


def surface_latency(command):
    """Read-only ring-buffer evidence; never reset the global compositor counters."""
    try:
        listing = command('shell', 'dumpsys', 'SurfaceFlinger', '--list')
        layers = [line.strip() for line in listing.splitlines()
                  if PACKAGE in line and 'SurfaceView' in line][:4]
        result = {'layers': {}, 'note': 'Last compositor ring-buffer samples only; availability and timestamp semantics vary by Android version. No counters cleared.'}
        for layer in layers:
            # Pass an explicitly quoted single shell argument; layer names contain spaces/parentheses.
            quoted = "'" + layer.replace("'", "'\\''") + "'"
            result['layers'][layer] = command('shell', 'dumpsys', 'SurfaceFlinger', '--latency', quoted)
        if not layers:
            result['unavailable'] = 'No matching SurfaceView layer exposed by SurfaceFlinger --list'
        return result
    except (subprocess.SubprocessError, OSError) as failure:
        return {'unavailable': str(failure)}


def summarize_baseline(log_text, metadata):
    reports, diagnostics, measured, saw_start, saw_end = [], [], False, False, False
    for line in log_text.splitlines():
        if line.startswith('# CAPTURE START '):
            measured = saw_start = True
            continue
        if line.startswith('# CAPTURE END '):
            measured = False
            saw_end = True
            continue
        tag = next((name for name in ('FrameMetrics', 'ProfileStartup', 'ProfileMemory', 'ProfileWorld') if name in line), None)
        if tag is None or '=' not in line:
            continue
        report = {'within_measurement_markers': measured, 'tag': tag, 'raw': line}
        # Strip distribution bodies before scalar parsing to avoid leaking p50/min/max
        # into the report root; keep integer count distributions as integers.
        for key, value in re.findall(r'(\w+)\[([^\]]+)\]', line):
            report[key] = {name: float(number) if key.endswith('Ms') or '.' in number else int(number)
                           for name, number in re.findall(r'(\w+)=([\d.]+)', value)}
        scalar_line = re.sub(r'\w+\[[^\]]+\]', '', line)
        for key, value in re.findall(r'([\w.]+)=([^\s\[\],]+)', scalar_line):
            if isinstance(report.get(key), dict):
                report[key + 'Distribution'] = report[key]
            report[key] = int(value) if re.fullmatch(r'-?\d+', value) else value
        (reports if tag == 'FrameMetrics' else diagnostics).append(report)
    return {'metadata_file': 'performance-' + metadata['fixture'] + '.metadata.json',
            'fixture': metadata['fixture'], 'measurement_complete': saw_start and saw_end,
            'trace_on': metadata.get('system_trace', {}).get('requested', False),
            'trace_captured': metadata.get('system_trace', {}).get('status') == 'captured',
            'reports': reports, 'diagnostics': diagnostics,
            'measured_report_count': sum(report['within_measurement_markers'] for report in reports),
            'limits': ['Report windows may straddle measurement markers; raw reports are retained.',
                       'Window percentiles cannot be combined into a run percentile.',
                       'Render callback wall time is not GPU duration or displayed-frame timing.',
                       'Emulator evidence does not establish physical-TV 60 FPS.']}


def main():
    parser = parser_for_capture()
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('Only an explicit emulator serial is supported; physical devices are never targeted.')
    if args.switch_smoke and args.fixture != 'results':
        parser.error('--switch-smoke applies only to the results fixture.')
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
    adb = str(sdk / 'platform-tools/adb')
    target = [adb, '-s', args.serial]
    directory = args.output_dir.expanduser().resolve()
    directory.mkdir(parents=True, exist_ok=True)
    prefix = directory / ('performance-' + args.fixture)
    phones = []
    logger = None
    output = None
    system_trace = None
    metadata = {'fixture': args.fixture, 'arguments': {key: str(value) if isinstance(value, Path) else value for key, value in vars(args).items()},
                'created_utc': datetime.now(timezone.utc).isoformat(), 'source': source_provenance()}

    def command(*parts):
        return subprocess.run(target + list(parts), check=True, capture_output=True, text=True, timeout=10).stdout

    def optional_command(*parts):
        try:
            return command(*parts).strip()
        except (subprocess.SubprocessError, OSError) as failure:
            return {'unavailable': str(failure)}

    def save_metadata():
        prefix.with_suffix('.metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')

    def marker(kind):
        # PID was sampled outside the measured segment, so markers add no ADB work.
        stamp = {'utc': datetime.now(timezone.utc).isoformat(), 'host_monotonic_ns': time.monotonic_ns(),
                 'pid': metadata.get('pid'),
                 'controllers': [{'joined': phone.joined, 'controllable': phone.controllable,
                                  'input_messages': phone.input_messages, 'active_input_messages': phone.active_input_messages,
                                  'dodge_edges': phone.dodge_edges} for phone in phones]}
        metadata['measurement_' + kind.lower()] = stamp
        output.write(('# CAPTURE ' + kind + ' ' + json.dumps(stamp) + '\n').encode())
        save_metadata()

    def key(code):
        command('shell', 'input', 'keyevent', str(code))

    def wait(seconds):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            for phone in phones:
                if phone.error is not None:
                    raise RuntimeError('Synthetic heartbeat failed') from phone.error
            time.sleep(min(.1, max(0, until - time.monotonic())))

    def fresh_lobby():
        command('shell', 'am', 'force-stop', PACKAGE)
        command('shell', 'am', 'start', '-n', COMPONENT, '--ez', 'profile', 'true', '--es', 'joinHost', args.host)
        wait(2)
        key(23)
        wait(.3)

    try:
        output = prefix.with_suffix('.log').open('ab', buffering=0)
        # A unique --output-dir preserves baselines; default filenames retain replacement behavior.
        output.truncate(0)
        logger = subprocess.Popen(target + ['logcat', '-v', 'threadtime', '-T', '1', 'FrameMetrics:I',
                                            'ProfileStartup:I', 'ProfileMemory:I', 'ProfileWorld:I',
                                            'LobbyPerf:I', 'OutcomeArt:I', 'AndroidRuntime:E', '*:S'],
                                  stdout=output, stderr=subprocess.STDOUT)
        fresh_lobby()
        metadata['pid'] = optional_command('shell', 'pidof', PACKAGE)
        metadata['device'] = {'serial': args.serial, 'getprop': optional_command('shell', 'getprop'),
                              'display': optional_command('shell', 'wm', 'size'),
                              'density': optional_command('shell', 'wm', 'density')}
        metadata['installed_apks'] = []
        paths = optional_command('shell', 'pm', 'path', PACKAGE)
        if isinstance(paths, str):
            for path in paths.splitlines():
                if path.startswith('package:'):
                    apk = path[len('package:'):]
                    metadata['installed_apks'].append({'path': apk, 'sha256sum': optional_command('shell', 'sha256sum', apk)})
        else:
            metadata['apk_query'] = paths
        save_metadata()
        if args.fixture != 'lobby':
            room = json.load(urllib.request.urlopen(f'http://127.0.0.1:{args.forward_port}/session', timeout=3))['room']
            for slot in range(1 if args.fixture in ('boss-solo', 'art-solo') else 2):
                phones.append(Phone(args.forward_port, room, args.moving_input, slot))
            wait(.3)
            for code in (20, 22, 23):  # Player I -> Back -> Start -> activate.
                key(code)
            wait(.5)
            if args.fixture in ('art-solo', 'art-coop'):
                # Consume fixture rewards before the boss time jump, so timed loot
                # does not turn a claimed combat measurement into a choice pause.
                key(142)  # F12: equipped late encounter, real builds and temporary invulnerability.
                wait(.2)
                key(136)  # F6: add the real final boss and existing telegraphs.
            else:
                key(137 if args.fixture == 'results' else 141 if args.fixture == 'late-coop' else 142 if args.fixture == 'items-coop' else 136)
                # F7 sample victory / F11 late encounter / F12 equipped late encounter / F6 boss fixture.
            wait(.5)
        wait(args.warmup_seconds)
        metadata['surfaceflinger_start'] = surface_latency(command)
        start_memory = command('shell', 'dumpsys', 'meminfo', PACKAGE)
        prefix.with_suffix('.memory.txt').write_text('FIXTURE START\n' + start_memory)
        if args.system_trace:
            system_trace = SystemTrace(target, prefix, args.seconds, command)
            metadata['system_trace'] = system_trace.metadata
            system_trace.start()
        key(93)  # Profile-only PAGE_DOWN resets app metric windows after setup/warmup.
        metadata['metric_reset_key'] = 'PAGE_DOWN (93); requires the matching profiling APK'
        marker('START')
        wait(args.seconds)
        marker('END')
        if system_trace is not None:
            system_trace.finish()
        metadata['pid_after_measurement'] = optional_command('shell', 'pidof', PACKAGE)
        metadata['surfaceflinger_end'] = surface_latency(command)
        save_metadata()
        # Observe memory before screenshot capture adds its own temporary work.
        # This remains a delayed end observation after optional trace collection.
        end_memory = command('shell', 'dumpsys', 'meminfo', PACKAGE)
        with prefix.with_suffix('.memory.txt').open('a') as memory_file:
            memory_file.write('\nFIXTURE END\n' + end_memory)
        if args.fixture in ('art-solo', 'art-coop'):
            with prefix.with_suffix('.scene.png').open('wb') as image:
                subprocess.run(target + ['exec-out', 'screencap', '-p'], stdout=image, check=True, timeout=10)
        # One transition observes resource release; it is not a repeated leak/stability test.
        observations = []
        if args.lifecycle and args.fixture != 'lobby':
            key(137)  # F7 is supported in SESSION or RESULTS; load sample victory.
            wait(.5)
            observations.append(('OUTCOME LOADED', command('shell', 'dumpsys', 'meminfo', PACKAGE)))
        if args.switch_smoke:
            command('shell', 'input', 'keyevent', '137', '138', '4')
            wait(.5)
            with prefix.with_suffix('.cancel.png').open('wb') as image:
                subprocess.run(target + ['exec-out', 'screencap', '-p'], stdout=image, check=True, timeout=10)
        if args.fixture == 'results' or (args.lifecycle and args.fixture != 'lobby'):
            if not args.switch_smoke:
                key(4)
            wait(.5)
            observations.append(('LOBBY AFTER OUTCOME', command('shell', 'dumpsys', 'meminfo', PACKAGE)))
        if args.lifecycle:
            key(3)  # Home backgrounds this activity; do not force a process/context kill.
            wait(.3)
            command('shell', 'am', 'start', '-n', COMPONENT)
            wait(1)
            observations.append(('LOBBY AFTER RESUME', command('shell', 'dumpsys', 'meminfo', PACKAGE)))
            with prefix.with_suffix('.resume.png').open('wb') as image:
                subprocess.run(target + ['exec-out', 'screencap', '-p'], stdout=image, check=True, timeout=10)
        memory = 'FIXTURE START\n' + start_memory + '\nFIXTURE END\n' + end_memory
        for label, snapshot in observations:
            memory += '\n' + label + '\n' + snapshot
        prefix.with_suffix('.memory.txt').write_text(memory)
        print(f'Captured {args.seconds}s {args.fixture}: {prefix}.log and {prefix}.memory.txt')
        print('Frame interval includes scheduling/presentation; CPU submission is not GPU time. Emulator results do not establish physical-TV60FPS.')
    finally:
        try:
            if system_trace is not None:
                system_trace.cleanup()
            if logger is not None:
                logger.terminate()
                try:
                    logger.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    logger.kill()
                    logger.wait(timeout=3)
            if output is not None:
                output.close()
                save_metadata()
                summary = summarize_baseline(prefix.with_suffix('.log').read_text(errors='replace'), metadata)
                prefix.with_suffix('.summary.json').write_text(json.dumps(summary, indent=2) + '\n')
        finally:
            for phone in phones:
                phone.close()
            phones.clear()
            fresh_lobby()
            print('Synthetic phones closed; fresh lobby restored. Existing emulator/relay/forward left running.')


if __name__ == '__main__':
    main()
