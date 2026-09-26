#!/usr/bin/env python3
"""
KhatKit/KodeHeap 代码截图工具
为项目生成漂亮的代码截图，适用于文档、演示和分享
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


# 项目预设配置
PROJECT_PRESETS = {
    'khatkit': {
        'theme': 'monokai',
        'bg': 'rgba(40, 44, 52, 1)',
        'font_size': 13,
        'line_numbers': False,
        'description': 'KhatKit Android 项目'
    },
    'kodeheap': {
        'theme': 'dracula',
        'bg': 'rgba(40, 42, 54, 1)',
        'font_size': 13,
        'line_numbers': True,
        'description': 'KodeHeap Server 项目'
    },
    'presentation': {
        'theme': 'github-dark',
        'bg': 'rgba(13, 17, 23, 1)',
        'font_size': 16,
        'line_numbers': False,
        'description': '演示/分享用 - 大字号无行号'
    },
    'docs': {
        'theme': 'solarized-light',
        'bg': 'rgba(253, 246, 227, 1)',
        'font_size': 12,
        'line_numbers': True,
        'description': '文档用 - 亮色主题带行号'
    }
}


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
        'Courier New'
    ]

    # 尝试查找可用字体
    for font_name in preferred_fonts:
        try:
            # 尝试创建字体对象来验证
            ImageFont.truetype(font_name, 12)
            return font_name
        except:
            continue

    return 'monospace'  # 回退到默认


def create_code_screenshot(
    code,
    output_path,
    bg_color=(40, 44, 52, 255),
    theme='monokai',
    language='auto',
    font_name=None,
    font_size=14,
    line_numbers=False,
    padding_vertical=48,
    padding_horizontal=32,
    shadow=True,
    shadow_offset=20,
    shadow_blur=68,
    window_controls=True,
    export_scale=2,
    max_width=None
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
    scaled_font_size = font_size * export_scale
    scaled_padding_v = padding_vertical * export_scale
    scaled_padding_h = padding_horizontal * export_scale

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

    # 限制最大宽度
    if max_width and code_img.width > max_width * export_scale:
        ratio = (max_width * export_scale) / code_img.width
        new_width = int(code_img.width * ratio)
        new_height = int(code_img.height * ratio)
        code_img = code_img.resize((new_width, new_height), Image.Resampling.LANCZOS)

    code_width, code_height = code_img.size
    window_controls_height = 40 * export_scale if window_controls else 0

    canvas_width = code_width + 2 * scaled_padding_h
    canvas_height = code_height + 2 * scaled_padding_v + window_controls_height

    # 阴影边距
    if shadow:
        scaled_shadow_offset = shadow_offset * export_scale
        scaled_shadow_blur = shadow_blur * export_scale
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
            radius=10 * export_scale,
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
        button_y = 15 * export_scale
        button_radius = 6 * export_scale
        button_spacing = 8 * export_scale

        # 红色 - 关闭
        container_draw.ellipse(
            [(scaled_padding_h, button_y),
             (scaled_padding_h + button_radius * 2, button_y + button_radius * 2)],
            fill=(255, 95, 86, 255)
        )
        # 黄色 - 最小化
        container_draw.ellipse(
            [(scaled_padding_h + (button_radius * 2 + button_spacing), button_y),
             (scaled_padding_h + (button_radius * 2 + button_spacing) + button_radius * 2,
              button_y + button_radius * 2)],
            fill=(255, 189, 46, 255)
        )
        # 绿色 - 最大化
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
    print(f"✓ 截图已保存: {output_path}")
    print(f"  尺寸: {final_img.size[0]}x{final_img.size[1]} px")
    print(f"  语言: {lexer.name}")


def main():
    parser = argparse.ArgumentParser(
        description='KhatKit/KodeHeap 代码截图工具',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 使用项目预设
  %(prog)s -i MainActivity.kt -o screenshot.png --preset khatkit
  %(prog)s -i server.go -o screenshot.png --preset kodeheap

  # 演示/文档预设
  %(prog)s -i example.kt -o demo.png --preset presentation
  %(prog)s -i api.kt -o doc.png --preset docs

  # 自定义配置
  %(prog)s -i code.kt -o output.png --theme dracula --font-size 16

  # 从管道读取
  cat ChatViewModel.kt | %(prog)s -o output.png --preset khatkit

  # 批量处理
  for f in *.kt; do %(prog)s -i "$f" -o "screenshots/${f%.kt}.png" --preset khatkit; done

可用预设:
  khatkit      - KhatKit Android 项目 (Monokai, 深色)
  kodeheap     - KodeHeap Server 项目 (Dracula, 带行号)
  presentation - 演示/分享用 (大字号, 无行号)
  docs         - 文档用 (亮色主题, 带行号)

常用主题:
  monokai, dracula, github-dark, nord, one-dark, solarized-dark,
  solarized-light, gruvbox-dark, material, atom-dark
        """
    )

    # 输入输出
    parser.add_argument('-i', '--input', help='输入代码文件 (不指定则从 stdin 读取)')
    parser.add_argument('-o', '--output', required=True, help='输出图片路径')

    # 预设
    parser.add_argument('--preset', choices=list(PROJECT_PRESETS.keys()),
                        help='使用项目预设配置')
    parser.add_argument('--list-presets', action='store_true',
                        help='列出所有可用预设')

    # 外观
    parser.add_argument('--bg', help='背景颜色 (如: rgba(40, 44, 52, 1))')
    parser.add_argument('-t', '--theme', help='代码高亮主题')
    parser.add_argument('-l', '--language', default='auto',
                        help='编程语言 (auto 自动检测)')

    # 字体
    parser.add_argument('--font', help='字体名称 (默认自动选择)')
    parser.add_argument('--font-size', type=int, help='字体大小')

    # 布局
    parser.add_argument('--padding-v', type=int, default=48, help='垂直内边距')
    parser.add_argument('--padding-h', type=int, default=32, help='水平内边距')
    parser.add_argument('--line-numbers', action='store_true', help='显示行号')
    parser.add_argument('--no-line-numbers', action='store_true', help='不显示行号')
    parser.add_argument('--max-width', type=int, help='最大宽度 (px)')

    # 效果
    parser.add_argument('--no-shadow', action='store_true', help='不显示阴影')
    parser.add_argument('--no-window-controls', action='store_true', help='不显示窗口按钮')

    # 导出
    parser.add_argument('--scale', type=int, default=2, choices=[1, 2, 4],
                        help='导出缩放倍数')

    args = parser.parse_args()

    # 列出预设
    if args.list_presets:
        print("可用预设:\n")
        for name, config in PROJECT_PRESETS.items():
            print(f"  {name:12} - {config['description']}")
            print(f"               主题: {config['theme']}, 字号: {config['font_size']}, "
                  f"行号: {'是' if config['line_numbers'] else '否'}")
        return

    # 读取代码
    if args.input:
        input_path = Path(args.input)
        if not input_path.exists():
            print(f"错误: 文件不存在: {args.input}", file=sys.stderr)
            sys.exit(1)
        code = input_path.read_text()

        # 从文件扩展名推断语言
        if args.language == 'auto':
            try:
                lexer = get_lexer_for_filename(input_path.name)
                args.language = lexer.aliases[0] if lexer.aliases else 'auto'
            except:
                pass
    else:
        if sys.stdin.isatty():
            print("错误: 请指定输入文件或通过管道提供代码", file=sys.stderr)
            sys.exit(1)
        code = sys.stdin.read()

    if not code.strip():
        print("错误: 代码内容为空", file=sys.stderr)
        sys.exit(1)

    # 应用预设
    if args.preset:
        preset = PROJECT_PRESETS[args.preset]
        bg_color = parse_rgba(preset['bg'])
        theme = preset['theme']
        font_size = preset['font_size']
        line_numbers = preset['line_numbers']
    else:
        bg_color = parse_rgba(args.bg) if args.bg else (40, 44, 52, 255)
        theme = args.theme or 'monokai'
        font_size = args.font_size or 14
        line_numbers = False

    # 命令行参数覆盖预设
    if args.bg:
        bg_color = parse_rgba(args.bg)
    if args.theme:
        theme = args.theme
    if args.font_size:
        font_size = args.font_size
    if args.line_numbers:
        line_numbers = True
    if args.no_line_numbers:
        line_numbers = False

    # 生成截图
    try:
        create_code_screenshot(
            code=code,
            output_path=args.output,
            bg_color=bg_color,
            theme=theme,
            language=args.language,
            font_name=args.font,
            font_size=font_size,
            line_numbers=line_numbers,
            padding_vertical=args.padding_v,
            padding_horizontal=args.padding_h,
            shadow=not args.no_shadow,
            shadow_offset=20,
            shadow_blur=68,
            window_controls=not args.no_window_controls,
            export_scale=args.scale,
            max_width=args.max_width
        )
    except Exception as e:
        print(f"错误: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()
