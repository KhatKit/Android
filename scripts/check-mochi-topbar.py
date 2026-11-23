"""ADB regression check. Open the settings index at its top in an isolated phone build first."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('variant', choices=('debugTest', 'releaseTest'))
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
    parser.add_argument('--title', default='\u8bbe\u7f6e', help='localized settings toolbar title')
    parser.add_argument('--screenshots', type=Path)
    args = parser.parse_args()
    pkg = 'heizige.kk.khatkit.' + args.variant
    cmd = [args.adb] + (['-s', args.serial] if args.serial else [])
    remote = '/data/local/tmp/kk-topbar-' + uuid.uuid4().hex + '.xml'

    def adb(*argv):
        result = subprocess.run([*cmd, *map(str, argv)], capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace') or 'ADB command failed')
        return result.stdout

    def foreground():
        state = adb('shell', 'dumpsys', 'activity', 'activities').decode(errors='replace')
        if not re.search(r'topResumedActivity=[^\n]* ' + re.escape(pkg) + '/', state):
            raise RuntimeError('Expected the isolated settings page in the foreground: ' + pkg)

    def nodes():
        foreground()
        adb('shell', 'uiautomator', 'dump', remote)
        root = ET.fromstring(adb('shell', 'cat', remote))
        return [(n, tuple(map(int, re.findall(r'\d+', n.get('bounds')))))
                for n in root.iter('node') if n.get('package') == pkg and n.get('bounds') != '[0,0][0,0]']

    def title(rows):
        return next(b for n, b in rows if n.get('text') == args.title)

    def search_field(rows):
        return next(b for n, b in rows if n.get('class') == 'android.widget.EditText')

    def capture(name):
        if args.screenshots:
            args.screenshots.mkdir(parents=True, exist_ok=True)
            (args.screenshots / f'{args.variant}-{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))

    def swipe(start, end, duration):
        foreground()
        adb('shell', 'input', 'swipe', width // 2, int(height * start), width // 2, int(height * end), duration)
        time.sleep(0.8)

    try:
        rows = nodes()
        bar, field = title(rows), search_field(rows)
        if field[1] < bar[3]:
            raise AssertionError('First input overlaps toolbar at rest')
        width = max(b[2] for _, b in rows)
        height = max(b[3] for _, b in rows)
        capture('start')
        swipe(0.75, 0.46, 610)
        scrolled = nodes()
        if title(scrolled) != bar or not any(
            n.get('class') == 'android.widget.TextView' and n.get('text') != args.title and b[1] < bar[3]
            for n, b in scrolled
        ):
            raise AssertionError('Expected a fixed title with list text passing behind it')
        capture('scrolled')  # Blur strength and foreground sharpness are checked visually.
        swipe(0.26, 0.88, 340)
        restored = nodes()
        if title(restored) != bar or search_field(restored) != field:
            raise AssertionError('Toolbar or first input did not return to its starting bounds')
        capture('restored')
        print(f'PASS {args.variant}: title {bar}, first input {field}, scroll-under and return')
    finally:
        adb('shell', 'rm', '-f', remote)


if __name__ == '__main__':
    main()
