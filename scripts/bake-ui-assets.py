#!/usr/bin/env python3
"""Export exact original LibGDX bakes using pinned source, without starting a GL window."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE_COMMIT = 'af332dd'
NAMES = ('UiArt', 'MenuSceneArt', 'BuildIcons', 'OutcomeArt')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/art/runtime-ui')
    parser.add_argument('--phone-output', type=Path, default=ROOT / 'assets/web/art')
    args = parser.parse_args()
    jdk = Path('/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home')
    if not jdk.exists():
        jdk = Path(os.environ['JAVA_HOME'])
    version = subprocess.run([str(jdk / 'bin/java'), '-version'], capture_output=True, text=True, check=True)
    if 'version "21' not in version.stderr:
        raise SystemExit('This baker requires JDK 21.')
    env = dict(os.environ, JAVA_HOME=str(jdk))
    result = subprocess.run([str(ROOT / 'gradlew'), '-q', ':core:jar', ':desktop:uiBakeClasspath', '--offline'],
                            cwd=ROOT, env=env, check=True, capture_output=True, text=True)
    classpath = next(line for line in result.stdout.splitlines() if 'gdx-1.14.2.jar' in line)
    revision = subprocess.check_output(['git', 'rev-parse', SOURCE_COMMIT], cwd=ROOT, text=True).strip()
    temporary = ROOT / '.run/ui-bake'
    temporary.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='compile-', dir=temporary) as work:
        work = Path(work)
        package = work / 'source/com/couchcrypt/ui'
        package.mkdir(parents=True)
        sources = []
        for name in NAMES:
            path = package / (name + '.java')
            path.write_bytes(subprocess.check_output(['git', 'show', f'{revision}:core/src/main/java/com/couchcrypt/ui/{name}.java'], cwd=ROOT))
            sources.append(str(path))
        classes = work / 'classes'
        classes.mkdir()
        subprocess.run([str(jdk / 'bin/javac'), '-cp', classpath, '-d', str(classes),
                        str(ROOT / 'scripts/BakeUiAssets.java'), *sources], check=True, cwd=ROOT)
        subprocess.run([str(jdk / 'bin/java'), '-Xmx768m', '-cp', str(classes) + os.pathsep + classpath,
                        'BakeUiAssets', str(args.output.resolve()), str(args.phone_output.resolve()), revision],
                       check=True, cwd=ROOT / 'assets')
    # Verify decoded output pixels, including PNG row orientation and alpha bytes.
    from PIL import Image
    manifest = json.loads((args.output / 'manifest.json').read_text())
    for name, info in manifest['outputs'].items():
        path = (args.phone_output / Path(name).name if info.get('file', '').startswith('web/art/') else args.output / Path(info.get('file', name)).name)
        assert hashlib.sha256(path.read_bytes()).hexdigest() == info['pngSha256'], name
        if 'rgbaSha256' in info:
            image = Image.open(path).convert('RGBA')
            assert image.size == (info['width'], info['height']), name
            assert hashlib.sha256(image.tobytes()).hexdigest() == info['rgbaSha256'], name
    print(f"Verified {len(manifest['outputs'])} pixel/PNG exports against original in-memory LibGDX outputs.")


if __name__ == '__main__':
    main()
