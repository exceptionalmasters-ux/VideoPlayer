#!/usr/bin/env python3
"""Download verified official Gradle, generate a wrapper and build a debug APK."""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import urllib.request
import zipfile

VERSION = '8.11.1'
ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / '.tools'
URL = f'https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip'


def main():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk and not (ROOT / 'local.properties').exists():
        raise SystemExit('Ustaw ANDROID_HOME na Android SDK lub dodaj sdk.dir w local.properties (patrz README.md).')
    TOOLS.mkdir(exist_ok=True)
    gradle_dir = TOOLS / f'gradle-{VERSION}'
    if not gradle_dir.is_dir():
        archive = TOOLS / f'gradle-{VERSION}-bin.zip'
        print('Pobieranie oficjalnego Gradle…', flush=True)
        with urllib.request.urlopen(URL + '.sha256', timeout=60) as response:
            expected = response.read().decode().strip().split()[0].lower()
        urllib.request.urlretrieve(URL, archive)
        actual = hashlib.sha256(archive.read_bytes()).hexdigest()
        if actual != expected:
            archive.unlink()
            raise SystemExit('Suma SHA-256 Gradle nie zgadza się. Przerwano.')
        with zipfile.ZipFile(archive) as bundle:
            bundle.extractall(TOOLS)
        archive.unlink()
    windows = os.name == 'nt'
    executable = gradle_dir / 'bin' / ('gradle.bat' if windows else 'gradle')
    if not windows:
        executable.chmod(0o755)
    subprocess.run([str(executable), 'wrapper', '--gradle-version', VERSION,
                    '--distribution-type', 'bin', '--console=plain'], cwd=ROOT, check=True)
    subprocess.run([str(executable), 'testDebugUnitTest', 'lintDebug', 'assembleDebug', '--console=plain'], cwd=ROOT, check=True)
    print('Gotowe: app/build/outputs/apk/debug/app-debug.apk')


if __name__ == '__main__':
    try:
        main()
    except (OSError, subprocess.CalledProcessError) as error:
        print(f'Nie udało się zbudować aplikacji: {error}', file=sys.stderr)
        sys.exit(1)
