#!/usr/bin/env python3
"""Compatibility entry point for the sampled dark-fantasy bank; see BakeSoundEffects.py."""
from pathlib import Path
import runpy

if __name__ == '__main__':
    runpy.run_path(str(Path(__file__).with_name('BakeSoundEffects.py')), run_name='__main__')
