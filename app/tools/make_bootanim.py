"""Gera bootanimation.zip (1280x720) no estilo do app: fundo preto, simbolo do projetor em
traco branco surgindo suave, "Projetor" em Inter e uma barra de carregamento fina, estilo Apple.

Parte 1 (toca uma vez): simbolo e nome aparecem. Parte 2 (repete ate o sistema subir): a barra corre.
O zip fica sem compressao (exigido pelo bootanimation). Instalar em /oem/media/bootanimation.zip.
Uso: python -I tools/make_bootanim.py build/bootanimation.zip
"""
import io
import math
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H, FPS = 1280, 720, 30
ROOT = Path(__file__).resolve().parent.parent
FONT = ROOT / 'assets' / 'fonts' / 'Inter.ttf'
SS = 2  # supersampling


def glyph(size, alpha):
    """Simbolo do projetor (mesmo desenho de ic_glyph.xml, viewport 108) em branco com alfa."""
    s = size * SS / 108
    img = Image.new('L', (round(108 * s), round(108 * s)))
    d = ImageDraw.Draw(img)
    u = lambda *v: [round(x * s) for x in v]
    d.rounded_rectangle(u(18, 34, 90, 76), radius=u(12)[0], outline=255, width=u(5)[0])
    d.ellipse(u(50, 42, 76, 68), outline=255, width=u(5)[0])
    d.ellipse(u(58.5, 50.5, 67.5, 59.5), fill=255)
    d.ellipse(u(27, 43, 33, 49), fill=255)
    for x0, y0, x1, y1 in ((29, 58, 39, 58), (29, 65, 35, 65), (30, 83, 38, 83), (70, 83, 78, 83)):
        d.line(u(x0, y0, x1, y1), fill=255, width=u(4)[0])
        for cx, cy in ((x0, y0), (x1, y1)):
            d.ellipse(u(cx - 2, cy - 2, cx + 2, cy + 2), fill=255)
    img = img.resize((size, size), Image.LANCZOS)
    return img.point(lambda v: round(v * alpha))


def text_mask(txt, px, alpha):
    f = ImageFont.truetype(str(FONT), px)
    try:
        f.set_variation_by_axes([28, 600])  # opsz, wght
    except Exception:
        pass
    box = f.getbbox(txt)
    img = Image.new('L', (box[2] - box[0] + 4, box[3] - box[1] + 4))
    ImageDraw.Draw(img).text((2 - box[0], 2 - box[1]), txt, fill=255, font=f)
    return img.point(lambda v: round(v * alpha))


def ease(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


GLYPH = 132
GY = H // 2 - 140


def frame(g_alpha, g_scale, t_alpha, bar_pos=None, bar_alpha=0.0):
    img = Image.new('L', (W, H), 0)
    size = max(8, round(GLYPH * g_scale))
    g = glyph(size, g_alpha)
    img.paste(255, (W // 2 - size // 2, GY + (GLYPH - size) // 2), g)
    if t_alpha > 0:
        t = text_mask('Projetor', 40, t_alpha)
        img.paste(255, (W // 2 - t.width // 2, GY + GLYPH + 26), t)
    if bar_alpha > 0:
        # Trilho fino e um brilho que corre da esquerda para a direita.
        bw, bh, by = 180, 4, GY + GLYPH + 108
        x0 = W // 2 - bw // 2
        track = Image.new('L', (bw, bh), round(48 * bar_alpha))
        img.paste(track, (x0, by))
        if bar_pos is not None:
            seg = 60
            start = round(-seg + (bw + seg) * bar_pos)
            for i in range(seg):
                x = start + i
                if 0 <= x < bw:
                    v = round(255 * bar_alpha * math.sin(math.pi * i / seg))
                    img.putpixel((x0 + x, by), max(img.getpixel((x0 + x, by)), v))
                    for yy in range(1, bh):
                        img.putpixel((x0 + x, by + yy), max(img.getpixel((x0 + x, by + yy)), v))
    return img.convert('RGB')


def jpg(img):
    b = io.BytesIO()
    img.save(b, 'JPEG', quality=88)
    return b.getvalue()


def main(out):
    part0, part1 = [], []
    n0 = 36
    for i in range(n0):
        t = i / (n0 - 1)
        ga = ease(t / 0.6)
        gs = 0.9 + 0.1 * ease(t / 0.7)
        ta = ease((t - 0.45) / 0.45)
        ba = ease((t - 0.75) / 0.25)
        part0.append(frame(ga, gs, ta, None, ba))
    n1 = 40
    for i in range(n1):
        part1.append(frame(1, 1, 1, i / n1, 1))
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED) as z:
        z.writestr('desc.txt', f'{W} {H} {FPS}\np 1 0 part0\np 0 0 part1\n')
        for i, f in enumerate(part0):
            z.writestr(f'part0/{i:03d}.jpg', jpg(f))
        for i, f in enumerate(part1):
            z.writestr(f'part1/{i:03d}.jpg', jpg(f))
    part1[n1 // 2].save(Path(out).with_suffix('.preview.png'))
    print(out, Path(out).stat().st_size // 1024, 'KB')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'build/bootanimation.zip')
