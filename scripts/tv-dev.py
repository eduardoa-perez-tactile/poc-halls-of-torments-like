#!/usr/bin/env python3
"""Build/install the TV lobby and expose its emulator server to phones on this LAN."""
import argparse
import asyncio
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.couchcrypt.tv'
COMPONENT = PACKAGE + '/com.couchcrypt.android.AndroidLauncher'
PORT = 8787


def run(args, **kwargs):
    return subprocess.run([str(arg) for arg in args], check=True, **kwargs)


def lan_ip():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        try:
            sock.connect(('10.255.255.255', 9))
            return sock.getsockname()[0]
        except OSError:
            raise SystemExit('Cannot find a LAN address. Connect to Wi-Fi or pass --host YOUR_LAN_IP.')


async def bridge(reader, writer, forwarded_port):
    upstream = None
    tasks = []
    try:
        remote, upstream = await asyncio.open_connection('127.0.0.1', forwarded_port)

        async def pump(source, destination):
            while True:
                data = await source.read(32 * 1024)
                if not data:
                    break
                destination.write(data)
                await destination.drain()

        tasks = [asyncio.create_task(pump(reader, upstream)), asyncio.create_task(pump(remote, writer))]
        await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
    except (OSError, ConnectionError):
        pass
    finally:
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        for stream in (writer, upstream):
            if stream:
                stream.close()
                try:
                    await stream.wait_closed()
                except (OSError, ConnectionError):
                    pass


async def serve(forwarded_port):
    server = await asyncio.start_server(lambda r, w: bridge(r, w, forwarded_port), '0.0.0.0', PORT, limit=64 * 1024)
    async with server:
        await server.serve_forever()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', help='LAN IPv4 address phones use to reach this computer')
    parser.add_argument('--avd', default='Television_1080p')
    parser.add_argument('--no-build', action='store_true')
    parser.add_argument('--headless', action='store_true')
    parser.add_argument('--profile', action='store_true')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
    adb = shutil.which('adb') or str(sdk / 'platform-tools/adb')
    host = args.host or lan_ip()
    # Fail before starting an emulator if another program owns the phone-facing port.
    with socket.socket() as probe:
        probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            probe.bind(('0.0.0.0', PORT))
        except OSError:
            raise SystemExit(f'Port {PORT} is busy. Stop the other lobby/relay first.')
    if not args.no_build:
        env = os.environ.copy()
        jdk21 = Path('/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home')
        if jdk21.exists():
            env['JAVA_HOME'] = str(jdk21)
        env['ANDROID_HOME'] = str(sdk)
        run([ROOT / 'gradlew', ':android:assembleDebug', '--console=plain'], cwd=ROOT, env=env)
    apk = ROOT / 'android/build/outputs/apk/debug/android-debug.apk'
    if not apk.exists():
        raise SystemExit('APK missing. Run without --no-build first.')
    serial = None
    for line in subprocess.check_output([adb, 'devices'], text=True).splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == 'device' and fields[0].startswith('emulator-'):
            name = subprocess.check_output([adb, '-s', fields[0], 'emu', 'avd', 'name'], text=True).splitlines()[0]
            if name == args.avd:
                serial = fields[0]
                break
    created = None
    log = None
    forwarded_port = None
    try:
        if serial is None:
            used = {line.split()[0] for line in subprocess.check_output([adb, 'devices'], text=True).splitlines()[1:] if line.split()}
            port = next(p for p in range(5554, 5584, 2) if f'emulator-{p}' not in used)
            serial = f'emulator-{port}'
            (ROOT / '.run').mkdir(exist_ok=True)
            log = (ROOT / '.run/emulator.log').open('w')
            command = [sdk / 'emulator/emulator', '-avd', args.avd, '-port', str(port), '-no-snapshot', '-no-audio']
            if args.headless:
                command += ['-no-window', '-gpu', 'swiftshader']
            env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
            created = subprocess.Popen([str(p) for p in command], stdout=log, stderr=subprocess.STDOUT, env=env)
        target = [adb, '-s', serial]
        print(f'Waiting for {args.avd} to boot…', flush=True)
        end = time.monotonic() + 180
        while time.monotonic() < end:
            if created is not None and created.poll() is not None:
                raise SystemExit('Emulator exited. See .run/emulator.log.')
            result = subprocess.run(target + ['shell', 'getprop', 'sys.boot_completed'], capture_output=True, text=True, timeout=5)
            if result.stdout.strip() == '1':
                break
            time.sleep(2)
        else:
            raise SystemExit('Emulator boot timed out. See .run/emulator.log.')
        run(target + ['install', '-r', str(apk)])
        forwarded_port = int(subprocess.check_output(target + ['forward', 'tcp:0', f'tcp:{PORT}'], text=True).strip())
        run(target + ['shell', 'am', 'start', '-S', '-n', COMPONENT, '--es', 'joinHost', host, '--ez', 'profile', str(args.profile).lower()])
        print(f'Phone access: http://{host}:{PORT} — open the TV lobby and scan its QR code.', flush=True)
        print('Keep this process running. Ctrl+C stops the relay and cleans up its ADB forwarding.', flush=True)
        asyncio.run(serve(forwarded_port))
    except KeyboardInterrupt:
        print('\nStopping the development lobby.', flush=True)
    finally:
        if forwarded_port is not None:
            subprocess.run([adb, '-s', serial, 'shell', 'am', 'force-stop', PACKAGE], capture_output=True)
            subprocess.run([adb, '-s', serial, 'forward', '--remove', f'tcp:{forwarded_port}'], capture_output=True)
        if created is not None:
            subprocess.run([adb, '-s', serial, 'emu', 'kill'], capture_output=True)
            try:
                created.wait(timeout=15)
            except subprocess.TimeoutExpired:
                created.terminate()
                try:
                    created.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    created.kill()
                    created.wait()
        if log:
            log.close()


if __name__ == '__main__':
    main()
