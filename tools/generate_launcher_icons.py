import os
import re
import base64
from PIL import Image, ImageDraw

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SRC_PATH = os.path.join(PROJECT_ROOT, "tools", "werus_brand_icon_source.png")
RES_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "res")

src = Image.open(SRC_PATH).convert("RGB")
min_x, min_y, max_x, max_y = 94, 34, 820, 791
fg_w, fg_h = max_x - min_x + 1, max_y - min_y + 1

bg_g, fg_g = 2.0, 233.0
fg = Image.new("RGBA", (fg_w, fg_h), (0, 0, 0, 0))
for y in range(min_y, max_y + 1):
    for x in range(min_x, max_x + 1):
        r, g, b = src.getpixel((x, y))
        alpha = max(0.0, min(1.0, (g - bg_g) / (fg_g - bg_g)))
        if alpha > 0.01:
            fg.putpixel((x - min_x, y - min_y), (248, 233, 212, int(alpha * 255)))

print(f"FG extracted successfully: {fg.size}")

# 1. Update values/ic_launcher_background.xml
values_dir = os.path.join(RES_DIR, "values")
os.makedirs(values_dir, exist_ok=True)
bg_xml_path = os.path.join(values_dir, "ic_launcher_background.xml")
with open(bg_xml_path, "w", encoding="utf-8") as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#ed0229</color>
</resources>
''')
print("Updated ic_launcher_background.xml to #ed0229")

# 2. Generate drawable/ic_launcher_foreground.png (432x432)
drawable_dir = os.path.join(RES_DIR, "drawable")
os.makedirs(drawable_dir, exist_ok=True)

target_fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
scale_432 = 0.255
sw_432 = int(fg_w * scale_432)
sh_432 = int(fg_h * scale_432)
scaled_fg_432 = fg.resize((sw_432, sh_432), Image.Resampling.LANCZOS)
ox_432 = (432 - sw_432) // 2 - 2
oy_432 = (432 - sh_432) // 2 - 4
target_fg.alpha_composite(scaled_fg_432, (ox_432, oy_432))
fg_png_path = os.path.join(drawable_dir, "ic_launcher_foreground.png")
target_fg.save(fg_png_path, "PNG")
print("Saved ic_launcher_foreground.png (432x432)")

# 3. Ensure mipmap-anydpi-v26 xmls exist
anydpi_dir = os.path.join(RES_DIR, "mipmap-anydpi-v26")
os.makedirs(anydpi_dir, exist_ok=True)
adaptive_content = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
with open(os.path.join(anydpi_dir, "ic_launcher.xml"), "w", encoding="utf-8") as f:
    f.write(adaptive_content)
with open(os.path.join(anydpi_dir, "ic_launcher_round.xml"), "w", encoding="utf-8") as f:
    f.write(adaptive_content)
print("Verified mipmap-anydpi-v26 adaptive icon xmls")

# 4. Helper function to generate supersampled masked icon for fallback
def render_icon(target_size, shape="squircle"):
    super_size = target_size * 4
    canvas = Image.new("RGBA", (super_size, super_size), (0, 0, 0, 0))

    mask = Image.new("L", (super_size, super_size), 0)
    draw_mask = ImageDraw.Draw(mask)
    if shape == "circle":
        draw_mask.ellipse((0, 0, super_size - 1, super_size - 1), fill=255)
    else:  # squircle / rounded rectangle
        radius = int(super_size * 0.22)
        draw_mask.rounded_rectangle((0, 0, super_size - 1, super_size - 1), radius=radius, fill=255)

    bg_layer = Image.new("RGBA", (super_size, super_size), (237, 2, 41, 255))

    art_scale = (super_size / 432.0) * scale_432 * (432.0 / 288.0)
    art_w = int(fg_w * art_scale)
    art_h = int(fg_h * art_scale)
    scaled_art = fg.resize((art_w, art_h), Image.Resampling.LANCZOS)

    art_ox = (super_size - art_w) // 2 + int(-2 * super_size / 288.0)
    art_oy = (super_size - art_h) // 2 + int(-4 * super_size / 288.0)
    bg_layer.alpha_composite(scaled_art, (art_ox, art_oy))

    canvas.paste(bg_layer, (0, 0), mask)
    final_icon = canvas.resize((target_size, target_size), Image.Resampling.LANCZOS)
    return final_icon

densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, size in densities.items():
    dir_path = os.path.join(RES_DIR, folder)
    os.makedirs(dir_path, exist_ok=True)

    # Square / Squircle
    sq_icon = render_icon(size, "squircle")
    sq_icon.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")

    # Round
    rd_icon = render_icon(size, "circle")
    rd_icon.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")
    print(f"Generated {folder}: {size}x{size} (sq + rd)")

# 5. 512x512 store/web icon in drawable/ic_launcher.png
icon_512 = render_icon(512, "squircle")
icon_512.save(os.path.join(drawable_dir, "ic_launcher.png"), "PNG")
print("Saved drawable/ic_launcher.png (512x512)")

# 6. Base64 for worker
with open(os.path.join(drawable_dir, "ic_launcher.png"), "rb") as f:
    b64_str = base64.b64encode(f.read()).decode("utf-8")

worker_ts_path = os.path.join(PROJECT_ROOT, "app-download-worker", "src", "index.ts")
with open(worker_ts_path, "r", encoding="utf-8") as f:
    worker_ts = f.read()

worker_ts_new = re.sub(
    r'const APP_ICON_B64 = "[^"]+";',
    f'const APP_ICON_B64 = "{b64_str}";',
    worker_ts
)
worker_ts_new, landing_icon_replacements = re.subn(
    r'<img src="data:image/png;base64,[^"]+" class="app-icon" alt="全员俄人WeRus 图标">',
    '<img src="data:image/png;base64,${APP_ICON_B64}" class="app-icon" alt="全员俄人WeRus 图标">',
    worker_ts_new,
)
if landing_icon_replacements != 1:
    raise RuntimeError(
        f"Expected one Worker landing icon to normalize, replaced {landing_icon_replacements}"
    )
with open(worker_ts_path, "w", encoding="utf-8") as f:
    f.write(worker_ts_new)

print("Updated Worker APP_ICON_B64 successfully!")
print("All launcher and brand icon assets generated successfully!")
