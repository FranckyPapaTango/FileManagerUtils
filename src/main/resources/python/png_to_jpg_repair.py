from PIL import Image, ImageFile
import sys
import os

ImageFile.LOAD_TRUNCATED_IMAGES = True

if len(sys.argv) < 2:
    sys.exit(1)

input_file = sys.argv[1]
output_file = (
    sys.argv[2]
    if len(sys.argv) > 2
    else os.path.splitext(input_file)[0] + ".jpg"
)

try:
    img = Image.open(input_file)
    img = img.convert("RGB")
    img.save(output_file, "JPEG", quality=90, subsampling=0)
    sys.exit(0)
except Exception as e:
    print(e)
    sys.exit(2)