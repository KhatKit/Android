#!/usr/bin/env python3
"""按项目规格从 Google Fonts 拉 Material Symbols Rounded 图标源码。

规格：Material Symbols Rounded / 24dp / opsz=24, wght=400, FILL=0, GRAD=0
      → 与项目 core/ui/icons 下现有自绘图标同一规格（24dp viewport）。

命名：Material Symbols 用 snake_case（more_vert、arrow_upward），
      项目现有图标是 camelCase（MoreVert、ArrowUpward），这里做转换并去重。
      同名已存在则跳过，除非 --force。
"""
import os, re, sys, time, json, argparse, urllib.request, urllib.error

BASE = ("https://fonts.gstatic.com/render/v1/Material+Symbols+Rounded/24dp/{name}.kt"
        "?var=opsz,wght,FILL,GRAD@24,400,0,0")
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
DEST = ("app/src/main/java/heizige/kk/khatkit/app/core/ui/icons")


def camel(name):
    """more_vert -> MoreVert ; arrow_upward -> ArrowUpward"""
    return ''.join(p[:1].upper() + p[1:] for p in name.split('_') if p)


def fetch(name, retries=4):
    url = BASE.format(name=name)
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers={
                'User-Agent': UA,
                'Accept': 'text/html,application/xhtml+xml,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.9',
                'Referer': 'https://fonts.google.com/icons',
                'Connection': 'close',
            })
            with urllib.request.urlopen(req, timeout=30) as r:
                raw = r.read()
                # 端点可能返回 gzip，urllib 不会自动解压
                if raw[:2] == b'\x1f\x8b':
                    import gzip
                    raw = gzip.decompress(raw)
                body = raw.decode('utf-8')
            if 'ImageVector.Builder' in body:
                return body
        except Exception as e:
            if i == retries - 1:
                return None
            time.sleep(1.5 * (i + 1))
    return None


def fix_pkg(body):
    body = body.replace('package com.example.test',
                        'package heizige.kk.khatkit.app.core.ui.icons')
    return body


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('names', nargs='+', help='Material Symbols 图标名(snake_case)')
    ap.add_argument('--force', action='store_true', help='覆盖已有文件')
    ap.add_argument('--list-missing', action='store_true',
                    help='只列出缺的，不下载')
    args = ap.parse_args()

    os.makedirs(DEST, exist_ok=True)
    existing = {f[:-3] for f in os.listdir(DEST) if f.endswith('.kt')}
    ok = skipped = failed = 0
    failed_names = []

    for n in args.names:
        cls = camel(n)
        path = os.path.join(DEST, cls + '.kt')
        if cls in existing and not args.force:
            skipped += 1
            continue
        if args.list_missing:
            print(f'缺: {n} -> {cls}.kt')
            continue
        body = fetch(n)
        if not body:
            failed += 1
            failed_names.append(n)
            continue
        open(path, 'w', encoding='utf-8').write(fix_pkg(body))
        ok += 1
        print(f'✓ {n:<24} -> {cls}.kt')
        time.sleep(0.12)          # 轻微限速，别把官方端点打挂

    print(f'\n下载 {ok} / 跳过已有 {skipped} / 失败 {failed}')
    if failed_names:
        print('失败:', ' '.join(failed_names))


if __name__ == '__main__':
    main()