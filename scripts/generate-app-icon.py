#!/usr/bin/env python3
"""MISEBASE 桌面图标生成器（单一几何源）。

同一份几何定义同时产出：
  1. app/src/main/res/drawable/ic_launcher_foreground.xml（自适应图标前景层，矢量）
  2. app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png（常规兜底）
  3. app/src/main/res/mipmap-{...}/ic_launcher_round.png（圆形图标兜底）

几何坐标系：108x108（Android 自适应图标规范），中心 (54,54)，
安全区为半径 33 的圆；所有可见元素都落在安全区内。

设计：香槟金（#8A6A2B）厨师帽 + 负空间字母 M + 暖白（#FBF7F0）底，
帽檐下方两道档案页纹，构成"厨房档案"语义。
"""

from __future__ import annotations

import argparse
import pathlib
from dataclasses import dataclass

from PIL import Image, ImageDraw, ImageFont

# ---- 设计令牌（与 core:designsystem 的 Color.kt 对齐） ----
CHAMPAGNE = "#8A6A2B"        # Champagne40
CHAMPAGNE_DEEP = "#5C4517"   # Champagne30
WARM_WHITE = "#FBF7F0"       # SurfaceLight
WARM_WHITE_RAISED = "#FFFDF9"  # SurfaceLightRaised
OUTLINE_VARIANT = "#E6DAC9"  # WarmNeutral80（兜底图描边，浅色壁纸上仍有轮廓）

CANVAS = 108
CENTER = CANVAS / 2
SUPERSAMPLE = 4

# ---- 几何 ----
# 厨师帽：三个重叠椭圆构成帽冠（软件团块），下接矩形补齐平底。
CROWN_CENTER = (54.0, 47.5, 18.5, 18.0)   # cx, cy, rx, ry
CROWN_LEFT = (38.0, 52.0, 10.5, 11.5)
CROWN_RIGHT = (70.0, 52.0, 10.5, 11.5)
CROWN_BODY = (37.0, 52.0, 71.0, 64.0)     # x0, y0, x1, y1
BAND = (34.5, 63.4, 73.5, 70.8, 2.6)      # x0, y0, x1, y1, radius
# 字母 M：负空间（暖白色描边），坐落在帽冠内、帽檐之上。
M_POINTS = [(43.6, 61.4), (43.6, 44.6), (54.0, 56.8), (64.4, 44.6), (64.4, 61.4)]
M_WIDTH = 4.6
# 档案页纹：帽檐下的两道细线（自下而上收窄，像档案本的书页）。
RULE_TOP = [(41.0, 77.4), (67.0, 77.4)]
RULE_BOTTOM = [(47.0, 83.6), (61.0, 83.6)]
RULE_WIDTH = 2.3


@dataclass
class Shape:
    kind: str          # ellipse | rrect | stroke
    color: str
    args: tuple


def build_shapes(fill_style: str = "solid") -> list[Shape]:
    """返回按绘制顺序排列的图形列表。

    fill_style: solid（实心帽冠 + 负空间 M）或 outline（浅色帽冠 + 金色 M）。
    """
    crown = CHAMPAGNE if fill_style == "solid" else WARM_WHITE_RAISED
    m_color = WARM_WHITE if fill_style == "solid" else CHAMPAGNE
    band = CHAMPAGNE_DEEP if fill_style == "solid" else CHAMPAGNE

    return [
        Shape("ellipse", crown, CROWN_LEFT),
        Shape("ellipse", crown, CROWN_RIGHT),
        Shape("ellipse", crown, CROWN_CENTER),
        Shape("rrect", crown, (*CROWN_BODY, 0.0)),
        Shape("rrect", band, BAND),
        Shape("stroke", m_color, (M_POINTS, M_WIDTH)),
        Shape("stroke", CHAMPAGNE, (RULE_TOP, RULE_WIDTH)),
        Shape("stroke", CHAMPAGNE, (RULE_BOTTOM, RULE_WIDTH)),
    ]


def hex_to_rgba(color: str) -> tuple[int, int, int, int]:
    color = color.lstrip("#")
    return (int(color[0:2], 16), int(color[2:4], 16), int(color[4:6], 16), 255)


def render_mark(size: int, scale: float = 1.0, fill_style: str = "solid") -> Image.Image:
    """在 size×size 透明画布上渲染图标前景（108 单位坐标）。"""
    big = size * SUPERSAMPLE
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    unit = big / CANVAS

    def tx(x: float) -> float:
        return (CENTER + (x - CENTER) * scale) * unit

    def ty(y: float) -> float:
        return (CENTER + (y - CENTER) * scale) * unit

    for shape in build_shapes(fill_style):
        color = hex_to_rgba(shape.color)
        if shape.kind == "ellipse":
            cx, cy, rx, ry = shape.args
            draw.ellipse([tx(cx - rx), ty(cy - ry), tx(cx + rx), ty(cy + ry)], fill=color)
        elif shape.kind == "rrect":
            x0, y0, x1, y1, radius = shape.args
            draw.rounded_rectangle(
                [tx(x0), ty(y0), tx(x1), ty(y1)],
                radius=max(0.0, radius * scale * unit),
                fill=color,
            )
        elif shape.kind == "stroke":
            points, width = shape.args
            pts = [(tx(x), ty(y)) for x, y in points]
            w = max(1, round(width * scale * unit))
            draw.line(pts, fill=color, width=w, joint="curve")
            r = w / 2.0
            for px, py in (pts[0], pts[-1]):
                draw.ellipse([px - r, py - r, px + r, py + r], fill=color)
        else:  # pragma: no cover
            raise ValueError(shape.kind)

    return img.resize((size, size), Image.LANCZOS)


def background(size: int, round_icon: bool, bg_color: str = WARM_WHITE) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    width = max(1, round(size * 0.012))
    if round_icon:
        draw.ellipse([0, 0, size - 1, size - 1], fill=hex_to_rgba(bg_color),
                     outline=hex_to_rgba(OUTLINE_VARIANT), width=width)
    else:
        radius = round(size * 0.22)
        draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius,
                               fill=hex_to_rgba(bg_color),
                               outline=hex_to_rgba(OUTLINE_VARIANT), width=width)
    return img


def render_legacy(size: int, round_icon: bool, scale: float, bg_color: str) -> Image.Image:
    base = background(size, round_icon, bg_color)
    # 兜底图没有 108→72 的裁切余量，内容略放大填满视觉重量。
    mark = render_mark(size, scale=scale)
    return Image.alpha_composite(base, mark)


def fmt(value: float) -> str:
    text = f"{value:.3f}".rstrip("0").rstrip(".")
    return text if text else "0"


def ellipse_path(cx: float, cy: float, rx: float, ry: float) -> str:
    return (
        f"M {fmt(cx - rx)},{fmt(cy)} "
        f"a {fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0 "
        f"a {fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0 Z"
    )


def rrect_path(x0: float, y0: float, x1: float, y1: float, r: float) -> str:
    if r <= 0:
        return f"M {fmt(x0)},{fmt(y0)} H {fmt(x1)} V {fmt(y1)} H {fmt(x0)} Z"
    return (
        f"M {fmt(x0 + r)},{fmt(y0)} H {fmt(x1 - r)} "
        f"A {fmt(r)},{fmt(r)} 0 0 1 {fmt(x1)},{fmt(y0 + r)} "
        f"V {fmt(y1 - r)} A {fmt(r)},{fmt(r)} 0 0 1 {fmt(x1 - r)},{fmt(y1)} "
        f"H {fmt(x0 + r)} A {fmt(r)},{fmt(r)} 0 0 1 {fmt(x0)},{fmt(y1 - r)} "
        f"V {fmt(y0 + r)} A {fmt(r)},{fmt(r)} 0 0 1 {fmt(x0 + r)},{fmt(y0)} Z"
    )


def stroke_path(points: list[tuple[float, float]]) -> str:
    head, *tail = points
    data = f"M {fmt(head[0])},{fmt(head[1])} "
    data += " ".join(f"L {fmt(x)},{fmt(y)}" for x, y in tail)
    return data


def vector_xml(fill_style: str = "solid") -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- MISEBASE 自适应图标前景层：由 scripts/generate-app-icon.py 生成，请勿手改。 -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
    ]
    for shape in build_shapes(fill_style):
        if shape.kind == "ellipse":
            data = ellipse_path(*shape.args)
            lines.append(
                f'    <path\n        android:fillColor="{shape.color}"\n'
                f'        android:pathData="{data}" />'
            )
        elif shape.kind == "rrect":
            x0, y0, x1, y1, radius = shape.args
            data = rrect_path(x0, y0, x1, y1, radius)
            lines.append(
                f'    <path\n        android:fillColor="{shape.color}"\n'
                f'        android:pathData="{data}" />'
            )
        elif shape.kind == "stroke":
            points, width = shape.args
            data = stroke_path(points)
            lines.append(
                "    <path\n"
                f'        android:strokeColor="{shape.color}"\n'
                f'        android:strokeWidth="{fmt(width)}"\n'
                '        android:strokeLineCap="round"\n'
                '        android:strokeLineJoin="round"\n'
                '        android:fillColor="@android:color/transparent"\n'
                f'        android:pathData="{data}" />'
            )
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def write_assets(repo: pathlib.Path, fill_style: str, legacy_scale: float,
                 bg_color: str) -> None:
    res = repo / "app" / "src" / "main" / "res"
    drawable = res / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "ic_launcher_foreground.xml").write_text(
        vector_xml(fill_style), encoding="utf-8"
    )

    for name, px in DENSITIES.items():
        folder = res / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        render_legacy(px, False, legacy_scale, bg_color).save(folder / "ic_launcher.png")
        render_legacy(px, True, legacy_scale * 0.94, bg_color).save(
            folder / "ic_launcher_round.png"
        )


def write_adaptive_xml(repo: pathlib.Path, bg_color: str) -> None:
    res = repo / "app" / "src" / "main" / "res"
    anydpi = res / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    theme = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        "</adaptive-icon>\n"
    )
    (anydpi / "ic_launcher.xml").write_text(theme, encoding="utf-8")
    (anydpi / "ic_launcher_round.xml").write_text(theme, encoding="utf-8")

    values = res / "values"
    values.mkdir(parents=True, exist_ok=True)
    color_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f'    <color name="ic_launcher_background">{bg_color}</color>\n'
        "</resources>\n"
    )
    (values / "ic_launcher_background.xml").write_text(color_xml, encoding="utf-8")


def preview_font(size: int = 15) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for candidate in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf"):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default()


def write_preview(path: pathlib.Path, fill_style: str, legacy_scale: float,
                  bg_color: str) -> None:
    """候选图预览：自适应铺底、圆形遮罩、圆角遮罩与各尺寸兜底图。"""
    font = preview_font()
    tile = 216
    gap = 20
    cols = 3
    width = gap + cols * (tile + gap)
    sheet = Image.new("RGBA", (width, 760), (236, 227, 214, 255))
    draw = ImageDraw.Draw(sheet)

    labels = ["自适应（108 全出血）", "圆形遮罩（Launcher）", "圆角遮罩（Launcher）"]
    for index in range(cols):
        base = Image.new("RGBA", (tile, tile), hex_to_rgba(bg_color))
        base = Image.alpha_composite(
            base, render_mark(tile, scale=1.0, fill_style=fill_style)
        )
        mask = Image.new("L", (tile, tile), 0)
        mask_draw = ImageDraw.Draw(mask)
        if index == 1:
            mask_draw.ellipse([0, 0, tile - 1, tile - 1], fill=255)
        elif index == 2:
            mask_draw.rounded_rectangle([0, 0, tile - 1, tile - 1], radius=58, fill=255)
        else:
            mask_draw.rectangle([0, 0, tile - 1, tile - 1], fill=255)
        base.putalpha(mask)
        x = gap + index * (tile + gap)
        y = gap
        sheet.alpha_composite(base, (x, y))
        draw.text((x, y + tile + 4), labels[index], fill=(36, 31, 25, 255), font=font)

    top = gap + tile + 40
    px = 192
    for column, round_icon in enumerate((False, True)):
        x = gap + column * (px + gap)
        icon = render_legacy(px, round_icon, legacy_scale, bg_color)
        sheet.alpha_composite(icon, (x, top))
        draw.text((x, top + px + 4),
                  "ic_launcher_round" if round_icon else "ic_launcher",
                  fill=(36, 31, 25, 255), font=font)

    small_top = top + px + 32
    x = gap
    for size in (96, 72, 48):
        icon = render_legacy(size, False, legacy_scale, bg_color)
        sheet.alpha_composite(icon, (x, small_top))
        x += size + 12
    strip = Image.new("RGBA", (width - gap * 2, 110), (23, 19, 16, 255))
    sheet.alpha_composite(strip, (gap, small_top + 100))
    x = gap + 6
    for size in (96, 72, 48):
        icon = render_legacy(size, False, legacy_scale, bg_color)
        sheet.alpha_composite(icon, (x, small_top + 106))
        x += size + 12

    sheet.save(path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=str(pathlib.Path(__file__).resolve().parents[1]))
    parser.add_argument("--style", default="solid", choices=["solid", "outline"])
    parser.add_argument("--legacy-scale", type=float, default=1.16)
    parser.add_argument("--bg", default=WARM_WHITE)
    parser.add_argument("--preview", default="")
    parser.add_argument("--assets", action="store_true", help="写入 Android 资源")
    args = parser.parse_args()

    repo = pathlib.Path(args.repo)
    if args.preview:
        out = pathlib.Path(args.preview)
        out.parent.mkdir(parents=True, exist_ok=True)
        write_preview(out, args.style, args.legacy_scale, args.bg)
        print(f"preview -> {out}")
    if args.assets:
        write_assets(repo, args.style, args.legacy_scale, args.bg)
        write_adaptive_xml(repo, args.bg)
        print(f"assets -> {repo / 'app' / 'src' / 'main' / 'res'}")


if __name__ == "__main__":
    main()
