import sys
from PIL import Image, ImageFile

def main():
    if len(sys.argv) != 3:
        print("Usage: png_to_jpg_repair.py <input.png> <output.jpg>")
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2]

    # 🔑 Autorise le chargement d’images partiellement corrompues
    ImageFile.LOAD_TRUNCATED_IMAGES = True

    try:
        with Image.open(input_path) as img:
            # Force le décodage COMPLET en mémoire
            img.load()

            # Gestion transparence (équivalent IrfanView)
            if img.mode in ("RGBA", "LA", "P"):
                background = Image.new("RGB", img.size, (255, 255, 255))
                background.paste(img, mask=img.split()[-1] if img.mode != "RGB" else None)
                img = background
            else:
                img = img.convert("RGB")

            # Réenregistrement propre
            img.save(output_path, "JPEG", quality=95, subsampling=0, optimize=True)

        print(f"SUCCESS: repaired -> {output_path}")
        sys.exit(0)

    except Exception as e:
        print(f"ERROR: {e}")
        sys.exit(2)


if __name__ == "__main__":
    main()