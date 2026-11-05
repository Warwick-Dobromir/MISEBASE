"""MB-UI-001 图标资源管线（一次性，可复现）。

从 Material Symbols（Apache-2.0）取 24px Outlined SVG 的 path 数据，
转换成 Compose `PathBuilder` 调用（24dp 视口、y 轴从 -960 平移到 0）。

用法：python tools/icon_pipeline/convert_symbols.py
输出：stdout（Kotlin 函数），人工粘入 CulinaryIcons.kt。
"""

import re
import urllib.request

BASE = (
    "https://raw.githubusercontent.com/google/material-design-icons/master/"
    "symbols/web/{name}/materialsymbolsoutlined/{name}_24px.svg"
)

# Kotlin 常量名 -> Material Symbols 图标名
ICONS = {
    "Home": "home",
    "Library": "local_library",
    "Sauce": "liquor",
    "Ingredient": "eco",
    "Recipe": "menu_book",
    "Inventory": "inventory_2",
    "Scan": "qr_code_scanner",
    "Settings": "tune",
    "Add": "add",
    "Back": "arrow_back",
    "Edit": "edit",
    "Check": "check",
    "ChevronRight": "chevron_right",
    "ChevronDown": "expand_more",
    "Bolt": "bolt",
    "Search": "search",
    "Close": "close",
    "More": "more_horiz",
    "Warning": "warning",
    "Info": "info",
    "CheckCircle": "check_circle",
    "Archive": "archive",
    "Filter": "filter_list",
    "Trash": "delete",
    "Flash": "flash_on",
    "ArrowForward": "arrow_forward",
    "History": "history",
    "Sort": "sort",
}

SCALE = 24.0 / 960.0
NUM = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?")


def fmt(v: float) -> str:
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0f" if s in ("", "-0") else f"{s}f"


def px(x: float) -> str:
    return fmt(x * SCALE)


def py(y: float) -> str:
    return fmt((y + 960.0) * SCALE)


def convert(d: str) -> list[str]:
    tokens = NUM.findall(d)
    i = 0
    out: list[str] = []
    cmd = ""
    cx = cy = 0.0
    sx = sy = 0.0
    last_c = None  # 上一条三次曲线控制点
    last_q = None  # 上一条二次曲线控制点

    def num() -> float:
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    while i < len(tokens):
        t = tokens[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                out.append("close()")
                cx, cy = sx, sy
                last_c = last_q = None
                continue
        # 隐式重复：保留 cmd
        rel = cmd.islower()
        c = cmd.upper()
        ox, oy = (cx, cy) if rel else (0.0, 0.0)

        if c == "M":
            cx, cy = num() + ox, num() + oy
            sx, sy = cx, cy
            out.append(f"moveTo({px(cx)}, {py(cy)})")
            cmd = "l" if rel else "L"  # 后续隐式坐标为 lineto
            last_c = last_q = None
        elif c == "L":
            cx, cy = num() + ox, num() + oy
            out.append(f"lineTo({px(cx)}, {py(cy)})")
            last_c = last_q = None
        elif c == "H":
            cx = num() + ox
            out.append(f"lineTo({px(cx)}, {py(cy)})")
            last_c = last_q = None
        elif c == "V":
            cy = num() + oy
            out.append(f"lineTo({px(cx)}, {py(cy)})")
            last_c = last_q = None
        elif c == "C":
            x1, y1 = num() + ox, num() + oy
            x2, y2 = num() + ox, num() + oy
            x, y = num() + ox, num() + oy
            out.append(f"curveTo({px(x1)}, {py(y1)}, {px(x2)}, {py(y2)}, {px(x)}, {py(y)})")
            last_c = (x2, y2)
            last_q = None
            cx, cy = x, y
        elif c == "S":
            x1, y1 = (2 * cx - last_c[0], 2 * cy - last_c[1]) if last_c else (cx, cy)
            x2, y2 = num() + ox, num() + oy
            x, y = num() + ox, num() + oy
            out.append(f"curveTo({px(x1)}, {py(y1)}, {px(x2)}, {py(y2)}, {px(x)}, {py(y)})")
            last_c = (x2, y2)
            last_q = None
            cx, cy = x, y
        elif c == "Q":
            x1, y1 = num() + ox, num() + oy
            x, y = num() + ox, num() + oy
            out.append(f"quadTo({px(x1)}, {py(y1)}, {px(x)}, {py(y)})")
            last_q = (x1, y1)
            last_c = None
            cx, cy = x, y
        elif c == "T":
            x1, y1 = (2 * cx - last_q[0], 2 * cy - last_q[1]) if last_q else (cx, cy)
            x, y = num() + ox, num() + oy
            out.append(f"quadTo({px(x1)}, {py(y1)}, {px(x)}, {py(y)})")
            last_q = (x1, y1)
            last_c = None
            cx, cy = x, y
        elif c == "A":
            raise SystemExit("arc command encountered; extend converter before use")
        else:
            raise SystemExit(f"unsupported command {cmd!r}")

    return out


def main() -> None:
    for kotlin_name, symbol in ICONS.items():
        with urllib.request.urlopen(BASE.format(name=symbol), timeout=30) as resp:
            svg = resp.read().decode("utf-8")
        d = re.search(r'd="([^"]+)"', svg).group(1)
        print(f"/** Material Symbols Outlined `{symbol}`（Apache-2.0）。 */")
        print(f"internal fun PathBuilder.{kotlin_name.lower()}Symbol() {{")
        for line in convert(d):
            print(f"    {line}")
        print("}")
        print()


if __name__ == "__main__":
    main()
