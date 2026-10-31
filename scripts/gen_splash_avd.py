#!/usr/bin/env python3
"""生成 app/src/main/res/drawable/splash_animated_icon.xml。

字形 | < _ 的绘入：开场只有 |，光标 | 从基线下滑上来后，上 2/3 像合页一样折成 <（中点左移成尖、两端甩向右），
下 1/3 顺时针转 90° 落成 _。AVD 不会转 pathData，所以按 KEYFRAME_STEP 采样，每段一条线性 pathData 动画。

模型、常量与 app/.../feature/splash/SplashGlyph.kt 逐项一致，改一边必须同步改另一边。
用法：python3 scripts/gen_splash_avd.py
"""
import math
import pathlib

DRAW_IN_MILLIS = 1000
# 开场只有 |；光标 | 从基线下方滑上来（基线以下裁掉，像从地里升起），停一拍后快速折出：
# < 380→760ms，_ 晚 60ms 落下；都用带回弹的 snap 缓动。
SLIDE_START = 80
SLIDE_MILLIS = 240
SLIDE_DISTANCE = 131  # 光标含描边的高度，起点整根藏在基线下
BASELINE = 317.05  # 竖杠含描边的底边；滑入期间裁掉这条线以下
FOLD_START = 380
FOLD_MILLIS = 380
UNDERSCORE_START = FOLD_START + 60
UNDERSCORE_MILLIS = 400
KEYFRAME_STEP = 20
KEYFRAME_COUNT = (UNDERSCORE_START + UNDERSCORE_MILLIS - FOLD_START) // KEYFRAME_STEP + 1

STROKE_WIDTH = 12.9705
CURSOR_X = 280.0
HALF_W = 6.705
TOP = 193.43
BOTTOM = 310.57
CHEVRON_BOTTOM = 267.2
HINGE_Y = 230.32
L, R = CURSOR_X - HALF_W, CURSOR_X + HALF_W
UNDERSCORE_TOP = CHEVRON_BOTTOM + STROKE_WIDTH - 1  # 两段描边重叠 1，开场 | 中间不留缝

CHEVRON_START = [L, TOP, L, HINGE_Y, L, HINGE_Y, L, CHEVRON_BOTTOM, R, CHEVRON_BOTTOM, R, HINGE_Y, R, TOP]
CHEVRON_END = [310.57, 197.14, 249.78, 225.95, 249.78, 234.28, 310.57, 259.76, 310.57, 245.83, 268.7, 229.52, 310.57, 211.07]
UNDERSCORE_FROM = [L, BOTTOM, R, BOTTOM, R, UNDERSCORE_TOP, L, UNDERSCORE_TOP]
UNDERSCORE_END = [249.87, 302.44, 249.87, 310.48, 310.48, 310.48, 310.48, 302.44]

HINGE_START = (CURSOR_X, HINGE_Y)
HINGE_END = (254.05, 229.94)
UPPER_END_DEG = math.degrees(math.atan2(204.1 - HINGE_END[1], 310.57 - HINGE_END[0]))
LOWER_END_DEG = math.degrees(math.atan2(252.8 - HINGE_END[1], 310.57 - HINGE_END[0]))
UNDERSCORE_START_CENTER = (CURSOR_X, (UNDERSCORE_TOP + BOTTOM) / 2)
UNDERSCORE_END_CENTER = (280.175, 306.46)

# (顶点下标, 起点原点, 起点角度, 终点原点, 终点角度)
UPPER = ([0, 1, 6, 5], HINGE_START, -90.0, HINGE_END, UPPER_END_DEG)
LOWER = ([2, 3, 4, 5], HINGE_START, 90.0, HINGE_END, LOWER_END_DEG)
UNDERSCORE = ([0, 1, 2, 3], UNDERSCORE_START_CENTER, -90.0, UNDERSCORE_END_CENTER, 0.0)


SNAP_OVERSHOOT = 1.7


def snap(t):
    # easeOutBack：起手最快、冲过终点约 10% 再回落，与 SplashGlyph.snap 相同
    c3 = SNAP_OVERSHOOT + 1
    u = t - 1
    return 1 + c3 * u ** 3 + SNAP_OVERSHOOT * u ** 2


def progress(millis, start, duration):
    return snap(min(max((millis - start) / duration, 0.0), 1.0))


def lerp(a, b, f):
    return a + (b - a) * f


def local(v, i, o, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    dx, dy = v[2 * i] - o[0], v[2 * i + 1] - o[1]
    return dx * c + dy * s, -dx * s + dy * c


def place(piece, frm, to, i, f):
    _, o0, a0, o1, a1 = piece
    along0, across0 = local(frm, i, o0, a0)
    along1, across1 = local(to, i, o1, a1)
    along, across = lerp(along0, along1, f), lerp(across0, across1, f)
    ox, oy = lerp(o0[0], o1[0], f), lerp(o0[1], o1[1], f)
    rad = math.radians(lerp(a0, a1, f))
    c, s = math.cos(rad), math.sin(rad)
    return ox + along * c - across * s, oy + along * s + across * c


def chevron_at(f):
    out = [0.0] * len(CHEVRON_END)
    for arm in (UPPER, LOWER):
        for i in arm[0]:
            x, y = place(arm, CHEVRON_START, CHEVRON_END, i, f)
            if i == 5:  # 尖内侧两臂共用，取平均
                out[10] += x / 2
                out[11] += y / 2
            else:
                out[2 * i], out[2 * i + 1] = x, y
    return out


def underscore_at(f):
    out = [0.0] * len(UNDERSCORE_END)
    for i in UNDERSCORE[0]:
        out[2 * i], out[2 * i + 1] = place(UNDERSCORE, UNDERSCORE_FROM, UNDERSCORE_END, i, f)
    return out


def path_data(v):
    pts = [f"{v[2 * i]:.2f},{v[2 * i + 1]:.2f}" for i in range(len(v) // 2)]
    return "M" + "L".join(pts) + "Z"


def keyframes(shape, start, duration):
    times = [FOLD_START + k * KEYFRAME_STEP for k in range(KEYFRAME_COUNT)]
    return [path_data(shape(progress(t, start, duration))) for t in times]


def animators(frames):
    lines = []
    for k in range(len(frames) - 1):
        lines.append(
            "        <objectAnimator\n"
            f'          android:duration="{KEYFRAME_STEP}"\n'
            '          android:interpolator="@android:interpolator/linear"\n'
            '          android:propertyName="pathData"\n'
            f'          android:startOffset="{FOLD_START + k * KEYFRAME_STEP}"\n'
            f'          android:valueFrom="{frames[k]}"\n'
            f'          android:valueTo="{frames[k + 1]}"\n'
            '          android:valueType="pathType" />'
        )
    return "\n".join(lines)


# 折叠开始后 _ 转动时会扫到基线下面，裁剪换成整张画布（两条 path 命令结构相同才能做 pathData 动画）。
CLIP_SLIDE = f"M0,0L512,0L512,{BASELINE}L0,{BASELINE}Z"
CLIP_NONE = "M0,0L512,0L512,512L0,512Z"


def main():
    chevron = keyframes(chevron_at, FOLD_START, FOLD_MILLIS)
    underscore = keyframes(underscore_at, UNDERSCORE_START, UNDERSCORE_MILLIS)
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!--
  由 scripts/gen_splash_avd.py 生成，不要手改。
  系统 SplashScreen（API 31+）的动画图标：开场只有 |，光标 | 从基线下滑上来，上 2/3 折成 <、下 1/3 转成 _。
  模型与 feature/splash/SplashGlyph.kt 逐项一致：系统启动页退场时，Compose 覆盖层从同一毫秒接着画。
  root 组：字形中心 (252,252) 移到画布中心并放大 1.5 倍，仍落在启动页 2/3 直径的圆形遮罩内。
-->
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
  xmlns:aapt="http://schemas.android.com/aapt">
  <aapt:attr name="android:drawable">
    <vector
      android:width="240dp"
      android:height="240dp"
      android:viewportWidth="512"
      android:viewportHeight="512">
      <group
        android:name="root"
        android:pivotX="252"
        android:pivotY="252"
        android:scaleX="1.5"
        android:scaleY="1.5"
        android:translateX="4"
        android:translateY="4">
        <path
          android:fillColor="@color/splash_foreground"
          android:pathData="M193.43,193.43L193.43,310.57L206.84,310.57L206.84,193.43Z"
          android:strokeColor="@color/splash_foreground"
          android:strokeWidth="{STROKE_WIDTH}" />
        <group android:name="cursor_clip">
          <clip-path
            android:name="cursor_clip_path"
            android:pathData="{CLIP_SLIDE}" />
        <group
          android:name="cursor"
          android:translateY="{SLIDE_DISTANCE}">
        <path
          android:name="chevron"
          android:fillColor="@color/splash_foreground"
          android:pathData="{chevron[0]}"
          android:strokeColor="@color/splash_foreground"
          android:strokeWidth="{STROKE_WIDTH}" />
        <path
          android:name="underscore"
          android:fillColor="@color/splash_foreground"
          android:pathData="{underscore[0]}"
          android:strokeColor="@color/splash_foreground"
          android:strokeWidth="{STROKE_WIDTH}" />
          </group>
        </group>
      </group>
    </vector>
  </aapt:attr>
  <target android:name="cursor">
    <aapt:attr name="android:animation">
      <objectAnimator
        android:duration="{SLIDE_MILLIS}"
        android:propertyName="translateY"
        android:startOffset="{SLIDE_START}"
        android:valueFrom="{SLIDE_DISTANCE}"
        android:valueTo="0"
        android:valueType="floatType">
        <aapt:attr name="android:interpolator">
          <pathInterpolator
            android:controlX1="0.05"
            android:controlY1="0.7"
            android:controlX2="0.1"
            android:controlY2="1" />
        </aapt:attr>
      </objectAnimator>
    </aapt:attr>
  </target>
  <target android:name="cursor_clip_path">
    <aapt:attr name="android:animation">
      <objectAnimator
        android:duration="0"
        android:propertyName="pathData"
        android:startOffset="{FOLD_START}"
        android:valueFrom="{CLIP_SLIDE}"
        android:valueTo="{CLIP_NONE}"
        android:valueType="pathType" />
    </aapt:attr>
  </target>
  <target android:name="chevron">
    <aapt:attr name="android:animation">
      <set>
{animators(chevron)}
      </set>
    </aapt:attr>
  </target>
  <target android:name="underscore">
    <aapt:attr name="android:animation">
      <set>
{animators(underscore)}
      </set>
    </aapt:attr>
  </target>
</animated-vector>
"""
    out = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/drawable/splash_animated_icon.xml"
    out.write_text(xml, encoding="utf-8")
    print(f"wrote {out} ({KEYFRAME_COUNT} keyframes)")
    print("chevron end  :", chevron[-1])
    print("underscore end:", underscore[-1])


if __name__ == "__main__":
    main()
