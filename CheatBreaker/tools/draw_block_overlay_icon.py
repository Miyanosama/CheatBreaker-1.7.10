"""Regenerate the small pixel-art Block Overlay module icon without image dependencies."""

from pathlib import Path
import struct
import zlib


SIZE = 32
TRANSPARENT = (0, 0, 0, 0)
pixels = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]


def polygon(points, color):
    for y in range(SIZE):
        for x in range(SIZE):
            inside = False
            previous = points[-1]
            for current in points:
                x1, y1 = previous
                x2, y2 = current
                if (y1 > y + .5) != (y2 > y + .5):
                    crossing = x1 + (y + .5 - y1) * (x2 - x1) / (y2 - y1)
                    if x + .5 < crossing:
                        inside = not inside
                previous = current
            if inside:
                pixels[y][x] = (*color, 255)


# The dark silhouette and three faces make the icon legible at the 28 px UI size.
polygon([(16, 1), (30, 8), (30, 24), (16, 31), (2, 24), (2, 8)], (11, 30, 40))
polygon([(16, 3), (28, 9), (16, 15), (4, 9)], (36, 99, 111))
polygon([(4, 11), (15, 17), (15, 29), (4, 23)], (42, 72, 91))
polygon([(17, 17), (28, 11), (28, 23), (17, 29)], (28, 70, 87))

# Sparse, fixed pixels suggest a Minecraft block texture without visual noise.
for x, y in [(9, 8), (11, 9), (15, 6), (19, 7), (23, 9), (17, 11), (13, 12)]:
    pixels[y][x] = (48, 123, 134, 255)
for x, y in [(6, 14), (9, 17), (12, 19), (7, 21), (11, 25), (14, 27)]:
    pixels[y][x] = (48, 89, 108, 255)
for x, y in [(22, 16), (26, 14), (19, 20), (25, 22), (20, 26)]:
    pixels[y][x] = (34, 83, 102, 255)


def chunk(kind, data):
    payload = kind + data
    return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload))


rows = b"".join(b"\0" + b"".join(bytes(pixel) for pixel in row) for row in pixels)
png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(rows, 9))
       + chunk(b"IEND", b""))
destination = (Path(__file__).resolve().parent.parent / "src/main/resources/assets/minecraft/client/icons/mods/block_overlay.png")
destination.write_bytes(png)
print(destination)
