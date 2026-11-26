"""Read-only, bounded SurfaceFlinger presentation observations for short captures.

The compositor's timestamps are evidence of emulator presentation cadence, not a
hardware scan-out or GPU execution-time certification. No global state is reset.
"""
import math
import shlex
import subprocess
import threading
import time

PACKAGE = 'com.couchcrypt.tv'
SENTINEL = (1 << 63) - 1
MAX_SNAPSHOTS = 64
MAX_ROWS = 8192


def parse_latency(raw):
    """Validate the desired/actual/ready timestamp triples and preserve row holes."""
    result = {'refreshNs': None, 'rows': [], 'invalidRows': 0, 'duplicateRows': 0, 'error': None}
    if not isinstance(raw, str) or len(raw) > 65536:
        result['error'] = 'Missing or oversized latency response'
        return result
    lines = raw.strip().splitlines()
    try:
        refresh = int(lines[0])
        if not 1_000_000 <= refresh <= 1_000_000_000:
            raise ValueError()
    except (ValueError, IndexError):
        result['error'] = 'Unsupported refresh header'
        return result
    result['refreshNs'] = refresh
    if sum(bool(line.strip()) for line in lines[1:]) > 127:
        result['error'] = 'More than the supported 127 latency history records'
        return result
    previous = None
    last_actual = None
    for line in lines[1:]:
        if not line.strip():
            continue
        try:
            values = line.split()
            if len(values) != 3:
                raise ValueError()
            row = tuple(int(v) for v in values)
            if not all(0 < v < SENTINEL for v in row):
                raise ValueError()
        except ValueError:
            # Null marks prevent inventing adjacency across a missing/invalid record.
            result['rows'].append(None)
            result['invalidRows'] += 1
            previous = None
            continue
        if previous is not None and row[1] == previous[1]:
            if row == previous:
                result['duplicateRows'] += 1
                continue
            result['error'] = 'Conflicting actual-present timestamp in one snapshot'
            return result
        if last_actual is not None and row[1] < last_actual:
            result['error'] = 'Nonmonotonic actual-present timestamps'
            return result
        result['rows'].append(row)
        previous = row
        last_actual = row[1]
    return result


def _candidate(layer):
    return (isinstance(layer, str) and PACKAGE in layer and 'SurfaceView' in layer
            and not layer.startswith('Background for '))


def _percentiles(values):
    if not values:
        return None
    values = sorted(values)
    n = len(values)
    return {**{f'p{p}': values[max(0, math.ceil(n * p / 100) - 1)] / 1e6 for p in (50, 95, 99)},
            'min': values[0] / 1e6, 'max': values[-1] / 1e6}


class PresentationSampler:
    """One finite polling thread and fixed capture limits; no command backlog.

    Public API: start(), stop() -> bool, metadata() -> dict, analyze(startNs,
    endNs) -> dict. Boundaries must use the same device monotonic clock as the
    compositor. stop() requests a final snapshot after the end boundary; allow at
    least one display interval before stopping so that boundary can be bracketed.
    Optional runner/clock arguments exist for deterministic, device-free tests.
    """
    def __init__(self, target, initial_snapshot, *, interval_seconds=.75, max_seconds=40,
                 runner=None, clock=None):
        if not .1 <= interval_seconds <= 1 or not 1 <= max_seconds <= 45:
            raise ValueError('Short capture sampling must remain bounded')
        self.target = list(target)
        self.interval = interval_seconds
        self.max_seconds = max_seconds
        self.runner = runner or subprocess.run
        self.clock = clock or time.monotonic
        self.lock = threading.Lock()
        self.stopping = threading.Event()
        self.thread = None
        self.layer = None
        self.refresh = None
        self.samples = {}
        self.edges = set()
        self.snapshots = []
        self.errors = []
        self.error_count = self.invalid_rows = self.duplicate_rows = 0
        self.refresh_changes = self.conflicting_rows = 0
        self.limit_reached = self.duration_limit_reached = False
        self.stopped = False
        self._initial_last = self._final_last = None
        self._select_initial(initial_snapshot or {})

    def _error(self, message):
        self.error_count += 1
        if len(self.errors) < 8:
            self.errors.append(str(message)[:300])

    def _select_initial(self, snapshot):
        choices = []
        for layer, raw in snapshot.get('layers', {}).items():
            if not _candidate(layer):
                continue
            parsed = parse_latency(raw)
            valid = sum(row is not None for row in parsed['rows']) if parsed['error'] is None else 0
            choices.append((bool(valid), '(BLAST)' in layer, valid, layer, raw))
        if not choices:
            self._error(snapshot.get('unavailable', 'No app SurfaceView latency layer available'))
            return
        _, _, _, self.layer, raw = max(choices)
        self.add_snapshot({'layers': {self.layer: raw}})
        self._initial_last = max(self.samples, default=None)

    def add_snapshot(self, snapshot, *, final=False):
        """Ingest a same-capture snapshot without invoking ADB (also useful in tests)."""
        with self.lock:
            if len(self.snapshots) >= MAX_SNAPSHOTS:
                self.limit_reached = True
                return
            if self.layer is None:
                self._error('No selected app layer')
                return
            raw = snapshot.get('layers', {}).get(self.layer)
            parsed = parse_latency(raw)
            self.snapshots.append({'hostMonotonicNs': int(self.clock() * 1e9), 'layer': self.layer,
                                   'refreshNs': parsed['refreshNs'], 'rows': parsed['rows'],
                                   'invalidRows': parsed['invalidRows'], 'error': parsed['error']})
            self.invalid_rows += parsed['invalidRows']
            self.duplicate_rows += parsed['duplicateRows']
            if parsed['error']:
                self._error(parsed['error'])
                return
            if self.refresh is None:
                self.refresh = parsed['refreshNs']
            elif self.refresh != parsed['refreshNs']:
                self.refresh_changes += 1
                self._error('Display refresh period changed; cadence cannot use one fixed period')
                return
            previous = None
            for row in parsed['rows']:
                if row is None:
                    previous = None
                    continue
                actual = row[1]
                old = self.samples.get(actual)
                if old is not None:
                    self.duplicate_rows += 1
                    if old != row:
                        self.conflicting_rows += 1
                elif len(self.samples) < MAX_ROWS:
                    self.samples[actual] = row
                else:
                    self.limit_reached = True
                    return
                if previous is not None:
                    self.edges.add((previous, actual))
                previous = actual
            if final:
                self._final_last = max((row[1] for row in parsed['rows'] if row is not None), default=None)

    def _command(self, *args):
        return self.runner(self.target + list(args), check=True, capture_output=True,
                           text=True, timeout=1).stdout

    def _poll(self, *, final=False):
        try:
            if self.layer is None:
                listing = self._command('shell', 'dumpsys', 'SurfaceFlinger', '--list')
                candidates = [x.strip() for x in listing.splitlines() if _candidate(x.strip())]
                if not candidates:
                    with self.lock:
                        self._error('No matching app SurfaceView layer exposed')
                    return
                self.layer = max(candidates, key=lambda x: ('(BLAST)' in x, x))
            raw = self._command('shell', 'dumpsys', 'SurfaceFlinger', '--latency', shlex.quote(self.layer))
            self.add_snapshot({'layers': {self.layer: raw}}, final=final)
        except (subprocess.SubprocessError, OSError) as failure:
            with self.lock:
                self._error(failure)

    def start(self):
        with self.lock:
            if self.thread is not None or self.stopped:
                raise RuntimeError('Presentation sampler can start only once')
            self.thread = threading.Thread(target=self._run, name='presentation-sampler', daemon=True)
            self.thread.start()

    def _run(self):
        deadline = self.clock() + self.max_seconds
        try:
            while not self.stopping.is_set() and self.clock() < deadline:
                self._poll()
                self.stopping.wait(self.interval)
            if not self.stopping.is_set():
                with self.lock:
                    self.duration_limit_reached = True
            # One owned, bounded final query brackets the caller's end marker.
            self._poll(final=True)
        finally:
            with self.lock:
                self.stopped = True

    def stop(self):
        self.stopping.set()
        thread = self.thread
        if thread is None:
            with self.lock:
                self.stopped = True
            return True
        # A discovery poll and one final query can each issue up to two 1s calls.
        thread.join(timeout=4.5)
        return not thread.is_alive()

    def observation_bounds(self):
        """Same-device-clock span from initial history's last frame to the final poll's last frame."""
        with self.lock:
            return self._initial_last, self._final_last

    def metadata(self):
        with self.lock:
            return {'source': 'SurfaceFlinger latency actualPresentTime', 'layer': self.layer,
                    'status': 'available' if self.samples else 'unavailable',
                    'unavailableReason': None if self.samples else 'No valid app actual-present timestamps',
                    'nonBlastFallback': self.layer is not None and '(BLAST)' not in self.layer,
                    'refreshNs': self.refresh, 'sampleCount': len(self.samples),
                    'errorCount': self.error_count, 'errors': list(self.errors),
                    'invalidRows': self.invalid_rows, 'duplicateRows': self.duplicate_rows,
                    'conflictingRows': self.conflicting_rows, 'refreshChanges': self.refresh_changes,
                    'storageLimitReached': self.limit_reached, 'durationLimitReached': self.duration_limit_reached,
                    'stopped': self.stopped, 'observationBoundsNs': {'start': self._initial_last, 'end': self._final_last},
                    'observationScope': 'Presented-observation span; not exact host or gameplay markers',
                    'snapshots': [dict(s, rows=list(s['rows'])) for s in self.snapshots],
                    'limits': ['Presentation timestamps do not certify hardware scan-out or GPU execution time.',
                               'A gap above 1.5 refresh periods is an emulator missed-refresh-like heuristic.',
                               'Missing adjacency or unbracketed device boundaries prevents complete coverage.']}

    def analyze(self, startNs, endNs):
        if (not isinstance(startNs, int) or isinstance(startNs, bool) or not isinstance(endNs, int)
                or isinstance(endNs, bool) or startNs < 0 or endNs <= startNs):
            return {'available': False, 'coverage': {'complete': False, 'reasons': ['Invalid device-clock boundaries']}}
        with self.lock:
            times = sorted(self.samples)
            edges = set(self.edges)
            refresh = self.refresh
            compromised = self.limit_reached or bool(self.refresh_changes) or bool(self.conflicting_rows)
            layer = self.layer
        before = [v for v in times if v <= startNs]
        after = [v for v in times if v >= endNs]
        inside = [v for v in times if startNs <= v <= endNs]
        bracketed = bool(before and after)
        bounded = [v for v in times if (not before or v >= before[-1]) and (not after or v <= after[0])]
        holes = [(a, b) for a, b in zip(bounded, bounded[1:]) if (a, b) not in edges]
        gaps = [b - a for a, b in zip(inside, inside[1:]) if (a, b) in edges]
        reasons = []
        if not bracketed:
            reasons.append('Device measurement boundaries are not both bracketed by presentation samples')
        if holes:
            reasons.append('Ring-buffer overwrite, invalid records or missing polls left unobserved adjacency')
        if compromised:
            reasons.append('Storage limit, conflicting samples or refresh changes compromise this capture')
        if len(inside) < 2:
            reasons.append('Fewer than two presentation samples within the measurement')
        coverage = {'complete': not reasons, 'reasons': reasons, 'boundaryBracketed': bracketed,
                    'unobservedGapCount': len(holes), 'unobservedGapsNs': holes[:16],
                    'requestedDurationNs': endNs - startNs,
                    'observedStartNs': inside[0] if inside else None, 'observedEndNs': inside[-1] if inside else None}
        missed = [gap > refresh * 1.5 for gap in gaps] if refresh else []
        longest = streak = 0
        for a, b in zip(inside, inside[1:]):
            late = refresh and (a, b) in edges and b - a > refresh * 1.5
            streak = streak + 1 if late else 0
            longest = max(longest, streak)
        histogram = {}
        if refresh:
            for gap in gaps:
                multiple = str(max(0, math.floor(gap / refresh + .5)))
                histogram[multiple] = histogram.get(multiple, 0) + 1
        return {'available': bool(gaps and refresh), 'layer': layer, 'refreshNs': refresh,
                'frameCount': len(inside), 'intervalCount': len(gaps), 'coverage': coverage,
                'intervalMs': _percentiles(gaps), 'refreshMultipleHistogram': histogram,
                'observedFps': ((len(inside) - 1) * 1e9 / (inside[-1] - inside[0]))
                if len(inside) >= 2 and not holes else None,
                'missedRefreshLikeGaps': sum(missed),
                'missedRefreshLikeGapRate': sum(missed) / len(gaps) if gaps else None,
                'estimatedMissedRefreshes': sum(max(0, math.floor(gap / refresh + .5) - 1) for gap in gaps)
                if refresh else None,
                'longestMissedRefreshLikeStreak': longest,
                'thresholdNs': refresh * 1.5 if refresh else None,
                'hardwareDeadlineProof': False}
