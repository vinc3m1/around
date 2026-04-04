# /// script
# requires-python = ">=3.12"
# dependencies = [
#     "pillow",
# ]
# ///
"""Generate store assets for Google Play and Apple App Store."""

from PIL import Image, ImageDraw, ImageFont
import math
import os

# Colors from the SVG gradient
DARK = (0x1A, 0x23, 0x7E)   # #1A237E
MID = (0x39, 0x49, 0xAB)    # #3949AB
LIGHT = (0x5C, 0x6B, 0xC0)  # #5C6BC0
WHITE = (255, 255, 255)

GRADIENT_STOPS = [(0.0, DARK), (0.5, MID), (1.0, LIGHT)]

# Supersample factor for antialiasing
SS = 4


def lerp_color(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def draw_radial_gradient(img, cx, cy, radius, stops):
    pixels = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            dist = math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
            t = min(dist / radius, 1.0)
            color = stops[-1][1]
            for i in range(len(stops) - 1):
                t0, c0 = stops[i]
                t1, c1 = stops[i + 1]
                if t0 <= t <= t1:
                    local_t = (t - t0) / (t1 - t0) if t1 != t0 else 0
                    color = lerp_color(c0, c1, local_t)
                    break
            pixels[x, y] = color


def draw_circle(draw, cx, cy, r, stroke_width, color, opacity=1.0):
    alpha = int(255 * opacity)
    rgba = color + (alpha,)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=rgba, width=stroke_width)


def render_icon(size):
    """Render the icon at SS * size then downsample for antialiasing."""
    big = size * SS
    img = Image.new("RGB", (big, big))
    draw_radial_gradient(img, big // 2, big // 2, big // 2, GRADIENT_STOPS)

    img_rgba = img.convert("RGBA")
    overlay = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    s = big / 108.0
    c = big // 2

    # Emanating rings
    draw_circle(draw, c, c, int(36 * s), max(1, int(0.5 * s)), WHITE, 0.133)
    draw_circle(draw, c, c, int(44 * s), max(1, int(0.5 * s)), WHITE, 0.082)
    draw_circle(draw, c, c, int(52 * s), max(1, int(0.5 * s)), WHITE, 0.039)

    # Main circles
    draw_circle(draw, c, c, int(12 * s), int(3 * s), WHITE)
    draw_circle(draw, c, c, int(24 * s), int(3 * s), WHITE)

    composited = Image.alpha_composite(img_rgba, overlay)
    return composited.resize((size, size), Image.LANCZOS).convert("RGB")


FONT_CONFIGS = {
    "rounded": {
        "title": "/Library/Fonts/SF-Compact-Rounded-Medium.otf",
        "subtitle": "/Library/Fonts/SF-Compact-Rounded-Regular.otf",
    },
    "compact": {
        "title": "/Library/Fonts/SF-Compact-Display-Semibold.otf",
        "subtitle": "/Library/Fonts/SF-Compact-Display-Regular.otf",
    },
    "avenir": {
        "title": "/System/Library/Fonts/Avenir Next.ttc",
        "subtitle": "/System/Library/Fonts/Avenir Next.ttc",
    },
}


def render_feature_graphic(w, h, font_key="rounded"):
    """Render the feature graphic at SS resolution then downsample."""
    bw, bh = w * SS, h * SS
    img = Image.new("RGB", (bw, bh))
    draw_radial_gradient(img, bw // 2, bh // 2, bw // 2, GRADIENT_STOPS)

    img_rgba = img.convert("RGBA")
    overlay = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    # Circles on the left side
    cx, cy = int(280 * SS), bh // 2

    # Emanating rings
    draw_circle(draw, cx, cy, 170 * SS, 1 * SS, WHITE, 0.133)
    draw_circle(draw, cx, cy, 210 * SS, 1 * SS, WHITE, 0.082)
    draw_circle(draw, cx, cy, 250 * SS, 1 * SS, WHITE, 0.039)

    # Main circles
    draw_circle(draw, cx, cy, 55 * SS, 6 * SS, WHITE)
    draw_circle(draw, cx, cy, 110 * SS, 6 * SS, WHITE)

    # Text on the right side
    text_cx = int(680 * SS)
    fonts = FONT_CONFIGS[font_key]

    # Title
    title_size = 64 * SS
    try:
        title_font = ImageFont.truetype(fonts["title"], title_size)
    except Exception:
        title_font = ImageFont.load_default()

    # Subtitle
    sub_size = 28 * SS
    try:
        sub_font = ImageFont.truetype(fonts["subtitle"], sub_size)
    except Exception:
        sub_font = ImageFont.load_default()

    title_text = "Around"
    sub_text = "BLE Scanner & Ring Finder"
    gap = 20 * SS

    title_bbox = draw.textbbox((0, 0), title_text, font=title_font)
    title_w = title_bbox[2] - title_bbox[0]
    title_h = title_bbox[3] - title_bbox[1]

    sub_bbox = draw.textbbox((0, 0), sub_text, font=sub_font)
    sub_w = sub_bbox[2] - sub_bbox[0]
    sub_h = sub_bbox[3] - sub_bbox[1]

    total_h = title_h + gap + sub_h
    top_y = bh // 2 - total_h // 2

    draw.text(
        (text_cx - title_w // 2, top_y),
        title_text,
        fill=(255, 255, 255, 255),
        font=title_font,
    )
    draw.text(
        (text_cx - sub_w // 2, top_y + title_h + gap),
        sub_text,
        fill=(255, 255, 255, 200),
        font=sub_font,
    )

    composited = Image.alpha_composite(img_rgba, overlay)
    return composited.resize((w, h), Image.LANCZOS).convert("RGB")


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))

    google_dir = os.path.join(script_dir, "google-play")
    apple_dir = os.path.join(script_dir, "apple-app-store")
    os.makedirs(google_dir, exist_ok=True)
    os.makedirs(apple_dir, exist_ok=True)

    # Google Play: 512x512 icon + 1024x500 feature graphic
    print("Rendering Google Play icon (512x512)...")
    render_icon(512).save(os.path.join(google_dir, "app_icon.png"), "PNG")

    print("Rendering Google Play feature graphic (1024x500)...")
    render_feature_graphic(1024, 500, "avenir").save(
        os.path.join(google_dir, "feature_graphic.png"), "PNG"
    )

    # Apple App Store: 1024x1024 icon
    print("Rendering Apple App Store icon (1024x1024)...")
    render_icon(1024).save(os.path.join(apple_dir, "app_icon.png"), "PNG")

    # Verify
    for dirpath in [google_dir, apple_dir]:
        for fname in sorted(os.listdir(dirpath)):
            fpath = os.path.join(dirpath, fname)
            with Image.open(fpath) as im:
                size_kb = os.path.getsize(fpath) / 1024
                print(f"  {os.path.relpath(fpath, script_dir)}: {im.size[0]}x{im.size[1]}, {size_kb:.0f} KB")

    print("Done!")


if __name__ == "__main__":
    main()
