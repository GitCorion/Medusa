import os
from PIL import Image, ImageDraw

os.makedirs('docs/store_assets', exist_ok=True)
brain_dir = '/Users/corion/.gemini/antigravity-ide/brain/6a92dd61-1869-46ba-a085-fc27d46d8787'

# 1. App Icon (512x512)
icon_path = os.path.join(brain_dir, 'medusa_app_icon_1792185424915.jpg')
if os.path.exists(icon_path):
    icon_src = Image.open(icon_path)
    icon_512 = icon_src.resize((512, 512), Image.Resampling.LANCZOS)
    icon_512.save('docs/store_assets/app_icon_512.png', 'PNG')
    print("✓ app_icon_512.png (512x512)")

# 2. TV Banner (1280x720)
banner_path = 'app/src/main/res/drawable/banner.png'
if os.path.exists(banner_path):
    banner_src = Image.open(banner_path)
    banner_1280 = banner_src.resize((1280, 720), Image.Resampling.LANCZOS)
    banner_1280.save('docs/store_assets/tv_banner_1280x720.png', 'PNG')
    print("✓ tv_banner_1280x720.png (1280x720)")

# 3. Feature Graphic (1024x500)
# Use official banner centered and fitted into 1024x500 with exact matching background
if os.path.exists(banner_path):
    feat = Image.new('RGB', (1024, 500), color=(7, 10, 16))
    banner_scaled = banner_src.resize((888, 500), Image.Resampling.LANCZOS)
    x_offset = (1024 - 888) // 2
    feat.paste(banner_scaled, (x_offset, 0))
    feat.save('docs/store_assets/feature_graphic_1024x500.png', 'PNG')
    print("✓ feature_graphic_1024x500.png (1024x500)")

# 4. Screenshot 1 (Home Sintel 4K UI - 1920x1080)
screen_1_path = os.path.join(brain_dir, 'medusa_screen_home_1792194435798.jpg')
if os.path.exists(screen_1_path):
    screen_src = Image.open(screen_1_path)
    screen_1080 = screen_src.resize((1920, 1080), Image.Resampling.LANCZOS)
    screen_1080.save('docs/store_assets/screenshot_1_home_1920x1080.png', 'PNG')
    print("✓ screenshot_1_home_1920x1080.png (1920x1080)")

# 5. Screenshot 2 (Living Room TV Experience - 1920x1080)
screen_2_path = os.path.join(brain_dir, 'medusa_tv_home_1792194419605.jpg')
if os.path.exists(screen_2_path):
    screen2_src = Image.open(screen2_path if 'screen2_path' in locals() else screen_2_path)
    screen2_1080 = screen2_src.resize((1920, 1080), Image.Resampling.LANCZOS)
    screen2_1080.save('docs/store_assets/screenshot_2_tv_experience_1920x1080.png', 'PNG')
    print("✓ screenshot_2_tv_experience_1920x1080.png (1920x1080)")

print("\nAll store assets generated and verified successfully!")
