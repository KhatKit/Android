#!/usr/bin/env python3
"""
KhatKit Code Screenshot Generator
为 KhatKit AI 工具生成精美的代码截图
"""

import argparse
import sys
import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pygments import highlight
from pygments.lexers import get_lexer_by_name, guess_lexer, get_lexer_for_filename
from pygments.formatters import ImageFormatter
from pygments.styles import get_style_by_name
import io


def parse_rgba(rgba_str):
    """解析 rgba 字符串"""
    rgba_str = rgba_str.replace('rgba(', '').replace(')', '')
    parts = [p.strip() for p in rgba_str.split(',')]
    r, g, b = int(parts[0]), int(parts[1]), int(parts[2])
    a = int(float(parts[3]) * 255) if len(parts) > 3 else 255
    return (r, g, b, a)


def find_font():
    """查找系统可用的等宽字体"""
    preferred_fonts = [
        'Source Code Pro',
        'JetBrains Mono',
        'Fira Code',
        'Cascadia Code',
        'Hack',
        'Monaco',
        'Consolas',
        'Liberation Mono',
        'DejaVu Sans Mono',
        'Courier New'
    ]

    for font_name in preferred_fonts:
        try:
            ImageFont.truetype(font_name, 12)
            return font_name
        except:
            continue

    return 'monospace'


def create_screenshot(
    code,
    output_path,
    bg_color=(40, 44, 52, 255),
    theme='monokai',
    language='auto',
    font_name=None,
    font_size=14,
    line_numbers=False,
    padding_v=48,
    padding_h=32,
    shadow=True,
    shadow_offset=20,
    shadow_blur=68,
    window_controls=True,
    scale=2
):
    """生成代码截图"""

    if font_name is None:
        font_name = find_font()

    # 确定语言
    if language == 'auto':
        try:
            lexer = guess_lexer(code)
        except:
            lexer = get_lexer_by_name('text')
    else:
        try:
            lexer = get_lexer_by_name(language)
        except:
            lexer = get_lexer_by_name('text')

    # 缩放参数
    scaled_font_size = font_size * scale
    scaled_padding_v = padding_v * scale
    scaled_padding_h = padding_h * scale

    # 获取主题
    try:
        style = get_style_by_name(theme)
    except:
        style = get_style_by_name('monokai')

    # 生成代码图片
    formatter = ImageFormatter(
        style=style,
        font_name=font_name,
        font_size=scaled_font_size,
        line_numbers=line_numbers,
        line_number_bg='#1e1e1e',
        line_number_fg='#666',
    )

    code_img_data = highlight(code, lexer, formatter)
    code_img = Image.open(io.BytesIO(code_img_data))

    code_width, code_height = code_img.size
    window_controls_height = 40 * scale if window_controls else 0

    canvas_width = code_width + 2 * scaled_padding_h
    canvas_height = code_height + 2 * scaled_padding_v + window_controls_height

    # 阴影边距
    if shadow:
        scaled_shadow_offset = shadow_offset * scale
        scaled_shadow_blur = shadow_blur * scale
        shadow_margin = scaled_shadow_blur + scaled_shadow_offset
        final_width = canvas_width + shadow_margin * 2
        final_height = canvas_height + shadow_margin * 2
    else:
        shadow_margin = 0
        final_width = canvas_width
        final_height = canvas_height

    # 创建最终图片
    final_img = Image.new('RGBA', (final_width, final_height), bg_color)

    # 绘制阴影
    if shadow:
        shadow_img = Image.new('RGBA', (canvas_width, canvas_height), (0, 0, 0, 0))
        shadow_draw = ImageDraw.Draw(shadow_img)
        shadow_draw.rounded_rectangle(
            [(0, 0), (canvas_width, canvas_height)],
            radius=10 * scale,
            fill=(0, 0, 0, 80)
        )
        shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(scaled_shadow_blur // 10))
        final_img.paste(
            shadow_img,
            (shadow_margin + scaled_shadow_offset, shadow_margin + scaled_shadow_offset),
            shadow_img
        )

    # 创建代码容器
    container = Image.new('RGBA', (canvas_width, canvas_height), (40, 44, 52, 255))
    container_draw = ImageDraw.Draw(container)

    # 绘制 macOS 风格窗口控制按钮
    if window_controls:
        button_y = 15 * scale
        button_radius = 6 * scale
        button_spacing = 8 * scale

        # 红色
        container_draw.ellipse(
            [(scaled_padding_h, button_y),
             (scaled_padding_h + button_radius * 2, button_y + button_radius * 2)],
            fill=(255, 95, 86, 255)
        )
        # 黄色
        container_draw.ellipse(
            [(scaled_padding_h + (button_radius * 2 + button_spacing), button_y),
             (scaled_padding_h + (button_radius * 2 + button_spacing) + button_radius * 2,
              button_y + button_radius * 2)],
            fill=(255, 189, 46, 255)
        )
        # 绿色
        container_draw.ellipse(
            [(scaled_padding_h + (button_radius * 2 + button_spacing) * 2, button_y),
             (scaled_padding_h + (button_radius * 2 + button_spacing) * 2 + button_radius * 2,
              button_y + button_radius * 2)],
            fill=(39, 201, 63, 255)
        )

    # 粘贴代码
    container.paste(
        code_img,
        (scaled_padding_h, scaled_padding_v + window_controls_height),
        code_img if code_img.mode == 'RGBA' else None
    )

    # 粘贴到最终图片
    final_img.paste(container, (shadow_margin, shadow_margin), container)

    # 保存
    final_img.save(output_path, 'PNG', optimize=True)
    print(f"✓ {output_path}")
    print(f"{final_img.size[0]}x{final_img.size[1]}")
    print(f"{lexer.name}")


def main():
    parser = argparse.ArgumentParser(description='代码截图生成器')

    parser.add_argument('-i', '--input', help='输入代码文件')
    parser.add_argument('-o', '--output', required=True, help='输出图片路径')
    parser.add_argument('--bg', default='rgba(40, 44, 52, 1)', help='背景颜色')
    parser.add_argument('-t', '--theme', default='monokai', help='主题')
    parser.add_argument('-l', '--language', default='auto', help='语言')
    parser.add_argument('--font', help='字体')
    parser.add_argument('--font-size', type=int, default=14, help='字体大小')
    parser.add_argument('--line-numbers', action='store_true', help='显示行号')
    parser.add_argument('--no-shadow', action='store_true', help='不显示阴影')
    parser.add_argument('--no-window-controls', action='store_true', help='不显示窗口按钮')
    parser.add_argument('--scale', type=int, default=2, choices=[1, 2, 4], help='缩放')

    args = parser.parse_args()

    # 读取代码
    if args.input:
        code = Path(args.input).read_text()
        if args.language == 'auto':
            try:
                lexer = get_lexer_for_filename(args.input)
                args.language = lexer.aliases[0] if lexer.aliases else 'auto'
            except:
                pass
    else:
        code = sys.stdin.read()

    if not code.strip():
        print("错误: 代码内容为空", file=sys.stderr)
        sys.exit(1)

    bg_color = parse_rgba(args.bg)

    try:
        create_screenshot(
            code=code,
            output_path=args.output,
            bg_color=bg_color,
            theme=args.theme,
            language=args.language,
            font_name=args.font,
            font_size=args.font_size,
            line_numbers=args.line_numbers,
            shadow=not args.no_shadow,
            window_controls=not args.no_window_controls,
            scale=args.scale
        )
    except Exception as e:
        print(f"错误: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
