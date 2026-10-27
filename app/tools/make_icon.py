"""Gera assets/web/icon.png (apple-touch-icon 180x180) com o desenho de res/drawable/ic_launcher.xml:
fundo escuro neutro e o projetor em traco branco (ic_glyph).

Desenha em 4x no sistema 108x108 do vetor e reduz no final (antialias).
O iOS arredonda os cantos sozinho, entao o fundo ocupa o quadrado inteiro.
Uso: python -I tools/make_icon.py
"""
from PIL import Image, ImageDraw

S = 4 * 180 / 108
W = round(108 * S)


def u(*v):
    return [round(x * S) for x in v]


img = Image.new('RGBA', (W, W))
d = ImageDraw.Draw(img)
top, bottom = (0x2C, 0x2C, 0x2E), (0x0E, 0x0E, 0x10)
for y in range(W):
    t = y / (W - 1)
    d.line([(0, y), (W, y)], fill=tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))

# Reflexo sutil no topo.
gloss = Image.new('RGBA', (W, W))
gm = Image.new('L', (W, W))
ImageDraw.Draw(gm).ellipse(u(-40, -70, 148, 50), fill=255)
gd = ImageDraw.Draw(gloss)
for y in range(u(52)[0]):
    gd.line([(0, y), (W, y)], fill=(255, 255, 255, round(0x1A * (1 - y / u(52)[0]))))
gloss.putalpha(Image.composite(gloss.getchannel('A'), Image.new('L', (W, W)), gm))
img = Image.alpha_composite(img, gloss)

d = ImageDraw.Draw(img)
white = (255, 255, 255, 255)
d.rounded_rectangle(u(18, 34, 90, 76), radius=u(12)[0], outline=white, width=u(5)[0])
d.ellipse(u(50, 42, 76, 68), outline=white, width=u(5)[0])
d.ellipse(u(58.5, 50.5, 67.5, 59.5), fill=white)
d.ellipse(u(27, 43, 33, 49), fill=white)
lw = u(4)[0]
for x0, y0, x1, y1 in ((29, 58, 39, 58), (29, 65, 35, 65), (30, 83, 38, 83), (70, 83, 78, 83)):
    d.line(u(x0, y0, x1, y1), fill=white, width=lw)
    for cx, cy in ((x0, y0), (x1, y1)):
        d.ellipse(u(cx - 2, cy - 2, cx + 2, cy + 2), fill=white)

img.resize((180, 180), Image.LANCZOS).save('assets/web/icon.png', optimize=True)
print('assets/web/icon.png')
