"""Focused development-relay checks; loopback sockets only, never live ADB."""
import asyncio
import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('tv_dev', Path(__file__).with_name('tv-dev.py'))
tv_dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tv_dev)


class RelayRecoveryTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.handlers = set()

        async def echo(reader, writer):
            try:
                while data := await reader.read(32768):
                    writer.write(data)
                    await writer.drain()
            finally:
                writer.close()
                await writer.wait_closed()

        self.upstream = await asyncio.start_server(echo, '127.0.0.1', 0)
        port = self.upstream.sockets[0].getsockname()[1]
        self.forward = tv_dev.EmulatorForward(['mock-adb', '-s', 'emulator-test'], port)

        async def relay(reader, writer):
            task = asyncio.current_task()
            self.handlers.add(task)
            try:
                await tv_dev.bridge(reader, writer, self.forward)
            finally:
                self.handlers.discard(task)

        self.server = await asyncio.start_server(relay, '127.0.0.1', 0)
        self.port = self.server.sockets[0].getsockname()[1]

    async def asyncTearDown(self):
        self.server.close()
        await self.server.wait_closed()
        self.upstream.close()
        await self.upstream.wait_closed()
        if self.handlers:
            await asyncio.wait_for(asyncio.gather(*self.handlers), timeout=3)

    async def roundtrip(self, payload):
        reader, writer = await asyncio.open_connection('127.0.0.1', self.port)
        try:
            writer.write(payload)
            await writer.drain()
            return await asyncio.wait_for(reader.readexactly(len(payload)), timeout=3)
        finally:
            writer.close()
            await writer.wait_closed()

    async def unavailable(self):
        reader, writer = await asyncio.open_connection('127.0.0.1', self.port)
        try:
            writer.write(b'GET /session HTTP/1.1\r\nHost: localhost\r\n\r\n')
            await writer.drain()
            # read() must reach EOF, proving the failed relay closes the socket.
            response = await asyncio.wait_for(reader.read(), timeout=3)
            self.assertTrue(response.startswith(b'HTTP/1.1 503 Service Unavailable\r\n'))
            self.assertIn(b'Retry-After: 2\r\n', response)
            headers, body = response.split(b'\r\n\r\n', 1)
            self.assertIn(f'Content-Length: {len(body)}'.encode(), headers)
        finally:
            writer.close()
            await writer.wait_closed()

    async def test_http_and_websocket_bytes_unchanged_without_repair(self):
        http = b'GET /session HTTP/1.1\r\nHost: localhost\r\n\r\n'
        websocket = (b'GET /socket HTTP/1.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n'
                     b'\x81\x85mask\x00\xff\x01\x00\x82')
        with mock.patch.object(self.forward, '_restore') as repair:
            self.assertEqual(http, await self.roundtrip(http))
            self.assertEqual(websocket, await self.roundtrip(websocket))
            repair.assert_not_called()

    async def test_lost_forward_repaired_once_for_simultaneous_clients(self):
        connect = self.forward._connect
        repaired = False

        async def missing_forward():
            if not repaired:
                raise ConnectionRefusedError('ADB listener disappeared')
            return await connect()

        def restore(*args, **kwargs):
            nonlocal repaired
            repaired = True

        with mock.patch.object(self.forward, '_connect', side_effect=missing_forward), \
             mock.patch.object(tv_dev.subprocess, 'run', side_effect=restore) as adb, \
             contextlib.redirect_stderr(io.StringIO()):
            payloads = [b'GET /session HTTP/1.1\r\n\r\n', b'\x82\x04\x00\x01\xff\x7f']
            self.assertEqual(payloads, await asyncio.gather(*(self.roundtrip(p) for p in payloads)))
            adb.assert_called_once_with(
                ['mock-adb', '-s', 'emulator-test', 'forward', '--no-rebind',
                 f'tcp:{self.forward.port}', 'tcp:8787'],
                check=True, capture_output=True, text=True, timeout=5)

    async def test_adb_timeout_returns_503_and_closes(self):
        with mock.patch.object(self.forward, '_connect', side_effect=ConnectionRefusedError('missing')), \
             mock.patch.object(tv_dev.subprocess, 'run', side_effect=subprocess.TimeoutExpired('mock-adb', 5)) as adb, \
             contextlib.redirect_stderr(io.StringIO()) as errors:
            await self.unavailable()
            adb.assert_called_once()
            self.assertIn('timed out', errors.getvalue())

    async def test_failed_reconnect_returns_503_without_repair_loop(self):
        with mock.patch.object(self.forward, '_connect', side_effect=ConnectionRefusedError('TV unavailable')), \
             mock.patch.object(tv_dev.subprocess, 'run') as adb, \
             contextlib.redirect_stderr(io.StringIO()) as errors:
            await self.unavailable()
            adb.assert_called_once()
            self.assertIn('TV unavailable', errors.getvalue())


if __name__ == '__main__':
    unittest.main()
