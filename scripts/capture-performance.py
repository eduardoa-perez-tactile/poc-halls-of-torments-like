#!/usr/bin/env python3
"""Capture one short installed-APK fixture; never targets a physical Android device."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import select
import socket
import subprocess
import threading
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.couchcrypt.tv'
COMPONENT = PACKAGE + '/com.couchcrypt.android.AndroidLauncher'


class Phone:
    """Minimal local WebSocket heartbeat client; no input or upgrade automation."""
    def __init__(self, port, room):
        self.connection = socket.create_connection(('127.0.0.1', port), timeout=3)
        self.stopping = threading.Event()
        self.error = None
        self.send_lock = threading.Lock()
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
            headers = response.split(b'\r\n\r\n', 1)[0].decode()
            expected = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
            if ' 101 ' not in headers.splitlines()[0] or expected not in headers:
                raise RuntimeError('Controller WebSocket handshake rejected')
            self.ping()
            self.worker = threading.Thread(target=self.heartbeat, name="capture-phone", daemon=True)
            self.worker.start()
        except BaseException:
            self.connection.close()
            raise

    def send(self, payload, opcode=1):
        mask = os.urandom(4)
        data = payload.encode()
        with self.send_lock:
            self.connection.sendall(bytes([0x80 | opcode, 0x80 | len(data)]) + mask +
                                    bytes(value ^ mask[i % 4] for i, value in enumerate(data)))

    def ping(self):
        self.send('{"type":"ping"}')

    def heartbeat(self):
        last_ping = time.monotonic()
        try:
            while not self.stopping.is_set():
                if time.monotonic() - last_ping >= 2:
                    self.ping()
                    last_ping = time.monotonic()
                readable, _, _ = select.select([self.connection], [], [], .1)
                if readable and not self.connection.recv(16384):
                    raise RuntimeError('Synthetic controller disconnected')
        except (OSError, RuntimeError, ValueError) as failure:
            if not self.stopping.is_set():
                self.error = failure

    def close(self):
        self.stopping.set()
        try:
            self.send('', 8)
        except OSError:
            pass
        self.connection.close()
        self.worker.join(timeout=1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--host', required=True, help='Existing relay LAN IPv4 used by the TV QR')
    parser.add_argument('--forward-port', type=int, required=True, help='Existing ADB localhost forward to TV8787')
    parser.add_argument('--fixture', choices=['lobby', 'boss-solo', 'boss-coop', 'results'], default='boss-coop')
    parser.add_argument('--seconds', type=int, default=20, choices=range(5, 31), metavar='5..30')
    parser.add_argument('--lifecycle', action='store_true', help='Add one brief results/lobby/background-resume observation')
    parser.add_argument('--switch-smoke', action='store_true', help='Results only: send rapid F7/F8/Back after measurement')
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('Only an explicit emulator serial is supported; physical devices are never targeted.')
    if args.switch_smoke and args.fixture != 'results':
        parser.error('--switch-smoke applies only to the results fixture.')
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
    adb = str(sdk / 'platform-tools/adb')
    target = [adb, '-s', args.serial]
    directory = ROOT / '.run'
    directory.mkdir(exist_ok=True)
    prefix = directory / ('performance-' + args.fixture)
    phones = []
    logger = None
    output = None

    def command(*parts):
        return subprocess.run(target + list(parts), check=True, capture_output=True, text=True, timeout=10).stdout

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
        fresh_lobby()
        output = prefix.with_suffix('.log').open('w')
        logger = subprocess.Popen(target + ['logcat', '-v', 'threadtime', '-T', '1', 'FrameMetrics:I', 'LobbyPerf:I', 'OutcomeArt:I', 'AndroidRuntime:E', '*:S'], stdout=output, stderr=subprocess.STDOUT)
        if args.fixture != 'lobby':
            room = json.load(urllib.request.urlopen(f'http://127.0.0.1:{args.forward_port}/session', timeout=3))['room']
            for _ in range(1 if args.fixture == 'boss-solo' else 2):
                phones.append(Phone(args.forward_port, room))
            wait(.3)
            for code in (20, 22, 23):  # Player I -> Back -> Start -> activate.
                key(code)
            wait(.5)
            key(137 if args.fixture == 'results' else 136)  # F7 sample victory / F6 boss fixture.
            wait(.5)
        start_memory = command('shell', 'dumpsys', 'meminfo', PACKAGE)
        prefix.with_suffix('.memory.txt').write_text('FIXTURE START\n' + start_memory)
        wait(args.seconds)
        end_memory = command('shell', 'dumpsys', 'meminfo', PACKAGE)
        with prefix.with_suffix('.memory.txt').open('a') as memory_file:
            memory_file.write('\nFIXTURE END\n' + end_memory)
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
        if logger is not None:
            logger.terminate()
            try:
                logger.wait(timeout=3)
            except subprocess.TimeoutExpired:
                logger.kill()
                logger.wait(timeout=3)
        if output is not None:
            output.close()
        for phone in phones:
            phone.close()
        phones.clear()
        fresh_lobby()
        print('Synthetic phones closed; fresh lobby restored. Existing emulator/relay/forward left running.')


if __name__ == '__main__':
    main()
