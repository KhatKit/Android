"""Device check for F03. Open the isolated app's F03-Local-QA provider balance section.

Test fixture (no key): base URL http://127.0.0.1:38673, path /balance, result data.remaining,
query and alert enabled, threshold 5. The check will only interact with this provider.
"""

import argparse
import json
import os
import re
import subprocess
import threading
import time
import uuid
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('variant', choices=('debugTest', 'releaseTest'))
    ap.add_argument('--adb', default='adb')
    ap.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
    args = ap.parse_args()
    pkg = 'heizige.kk.khatkit.' + args.variant
    cmd = [args.adb] + (['-s', args.serial] if args.serial else [])
    remote = '/data/local/tmp/kk-balance-' + uuid.uuid4().hex + '.xml'
    port = 38673
    payload = [200, '6']
    requested = threading.Event()

    def adb(*argv):
        return subprocess.run([*cmd, *map(str, argv)], capture_output=True, check=True, timeout=24).stdout

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != '/balance':
                self.send_error(404)
                return
            status, amount = payload
            body = json.dumps({'data': {'remaining': amount}}).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            requested.set()

        def log_message(self, *args):
            pass

    def nodes():
        adb('shell', 'uiautomator', 'dump', remote)
        root = ET.fromstring(adb('shell', 'cat', remote))
        return [n for n in root.iter('node') if n.get('package') == pkg]

    def check_page(rows):
        if not any(n.get('text') == 'F03-Local-QA' for n in rows):
            raise RuntimeError('Open the F03-Local-QA provider, not a real account')
        matches = [n for n in rows if n.get('content-desc') == '刷新余额']
        if not matches:
            raise RuntimeError('Scroll until the refresh control is visible')
        return matches[0]

    def text(names, needle):
        return any(needle == n.get('text') for n in names)

    def tap(node):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        adb('shell', 'input', 'tap', (x1 + x2) // 2, (y1 + y2) // 2)

    def replace_field(old, new):
        rows = nodes()
        check_page(rows)
        field = next(n for n in rows if n.get('class') == 'android.widget.EditText' and n.get('text') == old)
        tap(field)
        time.sleep(0.5)
        rows = nodes()
        field = next(n for n in rows if n.get('class') == 'android.widget.EditText' and n.get('text') == old)
        _, y1, _, y2 = map(int, re.findall(r'\d+', field.get('bounds')))
        _, clear = min(
            ((abs(sum(list(map(int, re.findall(r'\d+', n.get('bounds'))))[1:4:2]) / 2 - (y1 + y2) / 2), n)
             for n in rows if n.get('content-desc') == '清除'),
            key=lambda pair: pair[0],
        )
        tap(clear)
        time.sleep(0.7)
        for character in new:
            adb('shell', 'input', 'text', character)
            time.sleep(0.12)
        time.sleep(0.35)
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(0.5)
        if not any(n.get('text') == new for n in nodes()):
            raise AssertionError(f'Field did not commit {new!r}')

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    mapped = False
    try:
        adb('reverse', '--no-rebind', f'tcp:{port}', f'tcp:{server.server_port}')
        mapped = True
        for amount, status, expected, toast in (
            ('5', 200, '5', False),
            ('4.999', 200, '低余额 · 4.999', True),
            ('3.75', 200, '低余额 · 3.75', False),
            ('0', 503, '余额不可用', False),
            ('unknown', 200, 'unknown', False),
            ('2.5', 200, '低余额 · 2.5', False),
            ('5', 200, '5', False),
            ('0', 200, '低余额 · 0', True),
        ):
            refresh = check_page(nodes())
            requested.clear()
            payload[:] = [status, amount]
            tap(refresh)
            if not requested.wait(6):
                raise AssertionError('No request to the local fixture')
            after = nodes()
            row = [n for n in after if any(child.get('text') == '当前余额' for child in list(n))]
            if not any(text(n.iter('node'), expected) for n in row):
                raise AssertionError(f'Expected balance label {expected!r}')
            actual_toast = text(after, f'F03-Local-QA 余额偏低：{amount}')
            if actual_toast != toast:
                raise AssertionError(f'Unexpected toast state for {amount}: {actual_toast}')
            print(f'PASS {args.variant} HTTP {status} {amount}: {expected}, toast={toast}', flush=True)

        requested.clear()
        replace_field('5', '2.25')
        if not text(nodes(), '2.25') or requested.is_set():
            raise AssertionError('Editing threshold should persist without issuing a query')
        replace_field('2.25', '5')
        replace_field('data.remaining', 'data.missing')
        if not text(nodes(), '待刷新') or requested.is_set():
            raise AssertionError('Edited source must wait for manual refresh')
        replace_field('data.missing', 'data.remaining')
        tap(check_page(nodes()))
        if not requested.wait(6) or not text(nodes(), '低余额 · 0'):
            raise AssertionError('Manual refresh did not query the restored source')
        print(f'PASS {args.variant} threshold edit and explicit query after changing source', flush=True)
    finally:
        if mapped:
            adb('reverse', '--remove', f'tcp:{port}')
        adb('shell', 'rm', '-f', remote)
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()
