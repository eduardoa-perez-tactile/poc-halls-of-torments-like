"""Focused capture protocol/parser tests. No ADB, emulator or network connections."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('capture_performance', Path(__file__).with_name('capture-performance.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


def decode_client_frame(frame):
    opcode, marker = frame[0] & 15, frame[1] & 127
    assert frame[1] & 128
    start = 2
    if marker in (126, 127):
        count = 2 if marker == 126 else 8
        size = int.from_bytes(frame[start:start + count], 'big')
        start += count
    else:
        size = marker
    mask = frame[start:start + 4]
    payload = frame[start + 4:]
    assert len(payload) == size
    return opcode, bytes(value ^ mask[i % 4] for i, value in enumerate(payload))


def server_frame(payload, opcode=1, final=True):
    data = payload.encode() if isinstance(payload, str) else payload
    size = len(data)
    length = bytes([size]) if size < 126 else bytes([126]) + size.to_bytes(2, 'big')
    return bytes([(128 if final else 0) | opcode]) + length + data


def fake_phone():
    phone = capture.Phone.__new__(capture.Phone)
    phone.connection = mock.Mock()
    phone.send_lock = threading.Lock()
    phone.joined = phone.controllable = False
    phone.sequence = 0
    phone.player_offset = 0
    phone.last_dodge = -1
    phone.input_messages = phone.active_input_messages = phone.dodge_edges = 0
    phone.received = bytearray()
    phone.fragment = bytearray()
    return phone


class ControllerProtocolTest(unittest.TestCase):
    def test_client_masking_all_length_forms(self):
        for size in (0, 125, 126, 65535, 65536):
            with self.subTest(size=size):
                payload = b'z' * size
                self.assertEqual((1, payload), decode_client_frame(capture.websocket_frame(payload)))

    def test_receive_split_and_coalesced_frames_and_exact_input(self):
        phone = fake_phone()
        joined = server_frame('{"type":"joined","player":1,"token":"not-logged"}')
        status = server_frame(json.dumps({'type': 'status', 'phase': 'session', 'hostActive': True,
                                         'alive': True, 'padding': 'x' * 160}))
        phone.receive(joined[:5])
        self.assertFalse(phone.joined)
        phone.receive(joined[5:] + status[:3])
        self.assertTrue(phone.joined)
        self.assertFalse(phone.controllable)
        phone.receive(status[3:])
        phone.input(0)
        payload = json.loads(decode_client_frame(phone.connection.sendall.call_args.args[0])[1])
        self.assertEqual({'type': 'input', 'x': .7, 'y': 0.0, 'dodge': False, 'seq': 0}, payload)

    def test_paused_input_is_neutral_and_dodge_is_an_edge(self):
        phone = fake_phone()
        phone.joined = phone.controllable = True
        phone.input(4)
        first = json.loads(decode_client_frame(phone.connection.sendall.call_args.args[0])[1])
        phone.input(4.01)
        second = json.loads(decode_client_frame(phone.connection.sendall.call_args.args[0])[1])
        self.assertTrue(first['dodge'])
        self.assertFalse(second['dodge'])
        self.assertEqual(1, second['seq'])
        phone.receive(server_frame('{"type":"status","phase":"paused","hostActive":true}'))
        phone.input(5)
        neutral = json.loads(decode_client_frame(phone.connection.sendall.call_args.args[0])[1])
        self.assertEqual((0, 0, False, 2), (neutral['x'], neutral['y'], neutral['dodge'], neutral['seq']))

    def test_unjoined_sends_no_input(self):
        phone = fake_phone()
        phone.input(3)
        phone.connection.sendall.assert_not_called()

    def test_fragmented_status_and_ping(self):
        phone = fake_phone()
        phone.receive(server_frame('{"type":"sta', final=False) + server_frame(b'keepalive', opcode=9))
        self.assertEqual((10, b'keepalive'), decode_client_frame(phone.connection.sendall.call_args.args[0]))
        phone.receive(server_frame('tus","phase":"session","hostActive":true}', opcode=0))
        self.assertTrue(phone.controllable)

    def test_rejection_is_reported(self):
        phone = fake_phone()
        with self.assertRaisesRegex(RuntimeError, 'full'):
            phone.receive(server_frame('{"type":"error","reason":"full"}'))

    def test_stop_sends_neutral_before_close(self):
        phone = fake_phone()
        phone.joined = phone.controllable = phone.moving_input = True
        phone.stopping = threading.Event()
        phone.worker = mock.Mock()
        phone.close()
        frames = [decode_client_frame(call.args[0]) for call in phone.connection.sendall.call_args_list]
        self.assertEqual({'type': 'input', 'x': 0, 'y': 0, 'dodge': False, 'seq': 0}, json.loads(frames[0][1]))
        self.assertEqual((8, b''), frames[1])
        phone.connection.close.assert_called_once()


class BaselineSummaryTest(unittest.TestCase):
    def test_summary_preserves_windows_diagnostics_and_marker_scope(self):
        text = '\n'.join([
            '11-23 I ProfileStartup: stage=gameArt wallNs=321',
            '11-23 I FrameMetrics: phase=LOBBY frames=20 intervalMs[p95=20.00,p99=21.10,max=22.20]',
            '# CAPTURE START {"pid":"123"}',
            '11-23 I FrameMetrics: phase=COOP frames=300 intervalSamples=300 intervalMs[p95=16.67,p99=20.30,max=30.00] cpuMs[p95=4.00,p99=5.00,max=6.00] over16.7ms=15 drawCalls=36',
            '11-23 I ProfileWorld: phase=COOP runSeed=42 elapsed=900 candidateChecks=600',
            '# CAPTURE END {}',
            '11-23 I FrameMetrics: phase=PAUSED frames=10 cpuMs[p95=1.00,p99=2.00,max=3.00]'])
        summary = capture.summarize_baseline(text, {'fixture': 'art-coop'})
        self.assertTrue(summary['measurement_complete'])
        self.assertEqual(1, summary['measured_report_count'])
        report = summary['reports'][1]
        self.assertEqual(16.67, report['intervalMs']['p95'])
        self.assertEqual(15, report['over16.7ms'])
        self.assertEqual(300, report['frames'])
        self.assertEqual(321, summary['diagnostics'][0]['wallNs'])
        self.assertEqual(600, summary['diagnostics'][1]['candidateChecks'])
        self.assertFalse(summary['reports'][2]['within_measurement_markers'])

    def test_incomplete_capture_is_explicit(self):
        summary = capture.summarize_baseline('# CAPTURE START {}', {'fixture': 'lobby'})
        self.assertFalse(summary['measurement_complete'])

    def test_cli_keeps_defaults_and_bounds_warmup(self):
        parser = capture.parser_for_capture()
        args = parser.parse_args(['--host', '192.168.1.50', '--forward-port', '12345'])
        self.assertFalse(args.moving_input)
        self.assertFalse(args.system_trace)
        self.assertEqual(0, args.warmup_seconds)
        self.assertEqual(capture.ROOT / '.run', args.output_dir)
        with mock.patch('sys.stderr'), self.assertRaises(SystemExit):
            parser.parse_args(['--host', '192.168.1.50', '--forward-port', '12345', '--warmup-seconds', '11'])

    def test_capture_rejects_phone_unreachable_hosts_before_any_device_work(self):
        parser = capture.parser_for_capture()
        for host in ('10.0.2.2', '10.0.2.15', '127.0.0.1', '0.0.0.0', '169.254.1.2', '224.0.0.1', 'localhost', '::1'):
            with self.subTest(host=host), mock.patch('sys.stderr'), mock.patch.object(capture.subprocess, 'run') as device:
                with self.assertRaises(SystemExit):
                    parser.parse_args(['--host', host, '--forward-port', '12345'])
                device.assert_not_called()
        for host in ('192.168.14.248', '10.1.2.3', '172.16.1.2'):
            with self.subTest(host=host):
                self.assertEqual(host, parser.parse_args(['--host', host, '--forward-port', '12345']).host)

    def test_surface_layer_quoting_and_no_clear(self):
        command = mock.Mock(side_effect=['SurfaceView[com.couchcrypt.tv/Activity](BLAST)#3\n', '16666666\n1 2 3\n'])
        result = capture.surface_latency(command)
        self.assertEqual(1, len(result['layers']))
        self.assertEqual("'SurfaceView[com.couchcrypt.tv/Activity](BLAST)#3'", command.call_args.args[-1])
        self.assertNotIn('--latency-clear', str(command.call_args_list))

    def test_integer_distributions_stay_nested(self):
        text = 'I FrameMetrics: phase=COOP draws[p50=36,p95=40,p99=44,max=48,min=30] intervalMs[p50=16.6,p95=17.0,p99=18.0,max=20.0]'
        report = capture.summarize_baseline(text, {'fixture': 'art-coop'})['reports'][0]
        self.assertEqual(36, report['draws']['p50'])
        self.assertIsInstance(report['draws']['p95'], int)
        self.assertEqual(16.6, report['intervalMs']['p50'])
        self.assertNotIn('p50', report)
        self.assertNotIn('min', report)


class DuplicateFieldsTest(unittest.TestCase):
    def test_preserves_last_value_and_distribution_with_same_name(self):
        report = capture.summarize_baseline(
            'I FrameMetrics: phase=COOP frames=301 drawCalls=36 '
            'drawCalls[min=12,p50=24,p95=36,p99=38,max=40] '
            'gpuTextureBytes=100 gpuTextureBytes[min=90,max=100]',
            {'fixture': 'art-coop'})['reports'][0]
        self.assertEqual(36, report['drawCalls'])
        self.assertEqual(40, report['drawCallsDistribution']['max'])
        self.assertEqual(100, report['gpuTextureBytes'])
        self.assertEqual(90, report['gpuTextureBytesDistribution']['min'])


class SystemTraceTest(unittest.TestCase):
    def test_configuration_is_finite_and_bounded(self):
        config = capture.trace_configuration(7)
        for expected in ('duration_ms: 7000', 'size_kb: 65536', 'RING_BUFFER',
                         'sched/sched_switch', 'sched/sched_wakeup', '"gfx"', '"view"', '"dalvik"',
                         'atrace_apps: "com.couchcrypt.tv"', 'scan_all_processes_on_start: true',
                         'android.surfaceflinger.frametimeline'):
            self.assertIn(expected, config)
        for invalid in (0, 4, 31, 999999):
            with self.assertRaises(ValueError):
                capture.trace_configuration(invalid)

    def test_capture_process_pull_and_owned_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            prefix = Path(directory) / 'performance-art-coop'
            command = mock.Mock(side_effect=lambda *args: Path(args[-1]).write_bytes(b'trace') if args[0] == 'pull' else '')
            process = mock.Mock()
            process.returncode = 0
            process.poll.return_value = 0
            process.communicate.return_value = ('Wrote trace', None)
            trace = capture.SystemTrace(['fake-adb', '-s', 'emulator-5554'], prefix, 7, command)
            with mock.patch.object(capture.subprocess, 'Popen', return_value=process) as popen:
                trace.start()
                trace.finish()
                trace.cleanup()
            self.assertEqual('captured', trace.metadata['status'])
            self.assertEqual(5, trace.metadata['bytes'])
            self.assertEqual(10, process.communicate.call_args.kwargs['timeout'])
            argv = popen.call_args.args[0]
            self.assertIn('perfetto', argv)
            self.assertEqual(trace.device_path, argv[-1])
            command.assert_any_call('shell', 'rm', '-f', trace.device_path)
            self.assertTrue(capture.summarize_baseline('', {'fixture': 'art-coop', 'system_trace': trace.metadata})['trace_on'])

    def test_unsupported_trace_is_metadata_only(self):
        trace = capture.SystemTrace(['fake-adb'], Path('/unused/baseline'), 7, mock.Mock())
        with mock.patch.object(capture.subprocess, 'Popen', side_effect=OSError('not available')):
            trace.start()
        trace.finish()
        trace.cleanup()
        self.assertEqual('unavailable', trace.metadata['status'])
        self.assertIn('not available', trace.metadata['error'])

    def test_timeout_terminates_owned_process(self):
        trace = capture.SystemTrace(['fake-adb'], Path('/unused/baseline'), 7, mock.Mock())
        trace.process = mock.Mock()
        trace.process.poll.return_value = None
        trace.process.communicate.side_effect = [subprocess.TimeoutExpired('perfetto', 10), ('stopped', None)]
        trace.finish()
        trace.cleanup()
        self.assertEqual('unavailable', trace.metadata['status'])
        trace.process.terminate.assert_called_once()
        trace.command.assert_called_once_with('shell', 'rm', '-f', trace.device_path)


if __name__ == '__main__':
    unittest.main()
