import os
from PIL import Image

os.makedirs('docs/store_assets', exist_ok=True)

# 1. App Icon 512x512 from actual project asset ic_jelly_symbol.png
symbol_path = 'app/src/main/res/drawable/ic_jelly_symbol.png'
if os.path.exists(symbol_path):
    symbol = Image.open(symbol_path)
    icon = Image.new('RGBA', (512, 512), (7, 10, 16, 255))
    h = 430
    w = int(symbol.width * (h / symbol.height))
    symbol_scaled = symbol.resize((w, h), Image.Resampling.LANCZOS)
    x = (512 - w) // 2
    y = (512 - h) // 2
    icon.paste(symbol_scaled, (x, y), symbol_scaled)
    icon.save('docs/store_assets/app_icon_512.png', 'PNG')
    print("✓ app_icon_512.png (512x512) generated from actual ic_jelly_symbol.png")

# 2. TV Banner 1280x720 from actual banner.png
banner_path = 'app/src/main/res/drawable/banner.png'
if os.path.exists(banner_path):
    banner_src = Image.open(banner_path)
    banner_1280 = banner_src.resize((1280, 720), Image.Resampling.LANCZOS)
    banner_1280.save('docs/store_assets/tv_banner_1280x720.png', 'PNG')
    print("✓ tv_banner_1280x720.png (1280x720) generated from actual banner.png")

# 3. Feature Graphic 1024x500 from actual banner.png
if os.path.exists(banner_path):
    feat = Image.new('RGB', (1024, 500), color=(7, 10, 16))
    banner_scaled = banner_src.resize((888, 500), Image.Resampling.LANCZOS)
    x_offset = (1024 - 888) // 2
    feat.paste(banner_scaled, (x_offset, 0))
    feat.save('docs/store_assets/feature_graphic_1024x500.png', 'PNG')
    print("✓ feature_graphic_1024x500.png (1024x500) generated from actual banner.png")

# Remove fake AI screenshots if they exist
for fake_screen in ['screenshot_1_home_1920x1080.png', 'screenshot_2_tv_experience_1920x1080.png']:
    fake_path = os.path.join('docs/store_assets', fake_screen)
    if os.path.exists(fake_path):
        os.remove(fake_path)
        print(f"✓ Removed fake screenshot {fake_screen}")

print("Assets updated with 100% genuine project artwork.")
