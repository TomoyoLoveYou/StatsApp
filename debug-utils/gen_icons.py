"""生成「统计」应用的多款图标（颜色 + 英文名 Stats）。

输出：
  debug-shots/icon-preview/grid-colors.png   13 款颜色对比（默认色 + 12 调色板色）
  debug-shots/icon-preview/grid-styles.png   3 种文字样式对比
  debug-shots/icon-assets/foreground-*.png   自适应图标前景（5 档分辨率，白色文字，透明底）
"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = r"C:/Users/Administrator/Documents/StatsApp"
PREVIEW = os.path.join(ROOT, "debug-shots", "icon-preview")
ASSETS = os.path.join(ROOT, "debug-shots", "icon-assets")
os.makedirs(PREVIEW, exist_ok=True)
os.makedirs(ASSETS, exist_ok=True)

FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_REG = "C:/Windows/Fonts/segoeui.ttf"

# 自适应图标：108dp 画布，中心 72dp 为可见区
CANVAS = 432                    # 108dp @ xxxhdpi
MASK_RATIO = 72 / 108

PALETTE = [
    ("blue",   "#4A6CF7"),
    ("indigo", "#6366F1"),
    ("purple", "#8B5CF6"),
    ("pink",   "#EC4899"),
    ("red",    "#EF4444"),
    ("orange", "#F59E0B"),
    ("lime",   "#84CC16"),
    ("green",  "#34A853"),
    ("teal",   "#14B8A6"),
    ("cyan",   "#06B6D4"),
    ("slate",  "#64748B"),
    ("brown",  "#A16207"),
]
DEFAULT_COLOR = ("default", "#185FA5")


def make_foreground(text="Stats", style="bar", size=CANVAS):
    """白色前景：文字（可选下划线短横）。style: bar=带横线, plain=纯文字"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    target_w = size * 0.52
    fs = int(size * 0.20)
    font = ImageFont.truetype(FONT_BOLD, fs)
    while fs > 8:
        font = ImageFont.truetype(FONT_BOLD, fs)
        bbox = d.textbbox((0, 0), text, font=font)
        if bbox[2] - bbox[0] <= target_w:
            break
        fs -= 2

    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    cx = size / 2
    cy = size / 2 - (size * 0.045 if style == "bar" else 0)
    d.text((cx - tw / 2 - bbox[0], cy - th / 2 - bbox[1]), text, font=font,
           fill=(255, 255, 255, 255))

    if style == "bar":
        line_w = tw * 0.42
        line_h = max(5, int(size * 0.028))
        ly = cy + th / 2 + size * 0.062
        d.rounded_rectangle([cx - line_w / 2, ly, cx + line_w / 2, ly + line_h],
                            radius=line_h / 2, fill=(255, 255, 255, 215))
    return img


def render_icon(bg_hex, fg, size=CANVAS, out=256, shadow=True):
    """把背景 + 前景合成，按桌面圆形遮罩裁切，返回预览图"""
    canvas = Image.new("RGBA", (size, size), bg_hex)
    canvas.alpha_composite(fg)

    mask_d = int(size * MASK_RATIO)
    off = (size - mask_d) // 2
    cropped = canvas.crop((off, off, off + mask_d, off + mask_d)).resize(
        (out, out), Image.LANCZOS)

    circle = Image.new("L", (out, out), 0)
    ImageDraw.Draw(circle).ellipse([0, 0, out - 1, out - 1], fill=255)

    if shadow:
        pad = out // 8
        tile = Image.new("RGBA", (out + pad * 2, out + pad * 2), (0, 0, 0, 0))
        sh = Image.new("RGBA", (out + pad * 2, out + pad * 2), (0, 0, 0, 0))
        ImageDraw.Draw(sh).ellipse(
            [pad, pad + pad // 3, pad + out, pad + out + pad // 3],
            fill=(15, 23, 42, 40))
        tile.alpha_composite(sh)
        tile.paste(cropped, (pad, pad), circle)
        return tile
    else:
        tile = Image.new("RGBA", (out, out), (0, 0, 0, 0))
        tile.paste(cropped, (0, 0), circle)
        return tile


def grid(items, cols, cell, label_font_size, path, title=None):
    """items: [(label, image)]"""
    rows = (len(items) + cols - 1) // cols
    pad, top = 20, (46 if title else 12)
    row_h = cell + 56
    W = cols * cell + pad * 2
    H = rows * row_h + pad + top
    canvas = Image.new("RGB", (W, H), "#FFFFFF")
    d = ImageDraw.Draw(canvas)

    if title:
        d.text((pad, 14), title, font=ImageFont.truetype(FONT_BOLD, 26),
               fill="#0F172A")

    lf = ImageFont.truetype(FONT_REG, label_font_size)
    for i, (label, im) in enumerate(items):
        r, c = divmod(i, cols)
        x = pad + c * cell + (cell - im.width) // 2
        y = top + r * row_h + 4
        canvas.paste(im, (x, y), im)
        bb = d.textbbox((0, 0), label, font=lf)
        d.text((pad + c * cell + (cell - (bb[2] - bb[0])) / 2 - bb[0],
                y + im.height + 6), label, font=lf, fill="#64748B")
    canvas.save(path)
    print("saved", path, canvas.size)


# ---------- 图 1：13 款颜色 ----------
fg_bar = make_foreground("Stats", "bar")
items = [(n, render_icon(c, fg_bar, out=190)) for n, c in [DEFAULT_COLOR] + PALETTE]
grid(items, 4, 200, 18, os.path.join(PREVIEW, "grid-colors.png"),
     title="Icon colors — Stats + bar")

# ---------- 图 2：3 种文字样式 ----------
styles = [
    ("A · Stats + bar", make_foreground("Stats", "bar")),
    ("B · stats + bar", make_foreground("stats", "bar")),
    ("C · Stats plain", make_foreground("Stats", "plain")),
]
items2 = [(n, render_icon("#EC4899", f, out=220)) for n, f in styles]
grid(items2, 3, 232, 19, os.path.join(PREVIEW, "grid-styles.png"),
     title="Icon styles")

# ---------- 正式资源：前景多分辨率（白色，透明底） ----------
densities = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for name, px in densities.items():
    f = make_foreground("Stats", "bar", size=px)
    p = os.path.join(ASSETS, f"ic_launcher_stats_fg-{name}.png")
    f.save(p)
    print("saved", p, f.size)


# ---------- 输出到 Android 工程 ----------
def emit_android_assets():
    res = os.path.join(ROOT, "app", "src", "main", "res")
    colors = [DEFAULT_COLOR] + PALETTE

    # 1) 前景 PNG（各密度）
    for name, px in densities.items():
        d = os.path.join(res, f"drawable-{name}")
        os.makedirs(d, exist_ok=True)
        make_foreground("Stats", "bar", size=px).save(
            os.path.join(d, "ic_launcher_stats_fg.png"))

    # 2) 背景色资源
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for n, c in colors:
        lines.append(f'    <color name="ic_launcher_bg_{n}">{c}</color>')
    lines.append('</resources>')
    with open(os.path.join(res, "values", "icon_colors.xml"), "w",
              encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")

    # 3) adaptive icon（13 个）
    adir = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(adir, exist_ok=True)
    for n, _ in colors:
        fname = "ic_launcher.xml" if n == "default" else f"ic_launcher_{n}.xml"
        content = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@color/ic_launcher_bg_{n}" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_stats_fg" />\n'
            '</adaptive-icon>\n'
        )
        with open(os.path.join(adir, fname), "w", encoding="utf-8",
                  newline="\n") as f:
            f.write(content)
    print(f"android assets emitted: {len(colors)} icons")


emit_android_assets()
print("done")
