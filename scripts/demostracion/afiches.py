"""Genera los afiches de los eventos de demostración (imagenes/<slug>.jpg) a partir de eventos.json.

    python scripts/demostracion/afiches.py

Se corre una vez y las imágenes quedan en el repositorio: son propias, sin fotos de internet.
Usa Pillow y las fuentes de Windows (Bahnschrift, Segoe UI).
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

AQUI = Path(__file__).parent
ANCHO, ALTO = 1600, 900
FUENTES = Path('C:/Windows/Fonts')


def color(hexa, alfa=255):
    hexa = hexa.lstrip('#')
    return tuple(int(hexa[i:i + 2], 16) for i in (0, 2, 4)) + (alfa,)


def mezclar(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def fuente(nombre, tamano):
    return ImageFont.truetype(str(FUENTES / nombre), tamano)


def fondo(c1, c2):
    # Degradado diagonal con un viñeteado suave
    img = Image.new('RGBA', (ANCHO, ALTO))
    px = img.load()
    a, b = color(c1), color(c2)
    for y in range(ALTO):
        for x in range(0, ANCHO, 2):
            t = min(1, max(0, (x / ANCHO) * 0.65 + (1 - y / ALTO) * 0.45))
            px[x, y] = mezclar(a, b, t)
            px[x + 1, y] = px[x, y]
    return img


def capa():
    return Image.new('RGBA', (ANCHO, ALTO), (0, 0, 0, 0))


def brillo(base, dibujo, radio):
    base.alpha_composite(dibujo.filter(ImageFilter.GaussianBlur(radio)))
    base.alpha_composite(dibujo)


def motivo_concierto(img, acento, rnd):
    luz = capa()
    d = ImageDraw.Draw(luz)
    for i in range(4):
        x = 900 + i * 170
        d.polygon([(x, -20), (x - 260 + i * 40, ALTO), (x + 160 + i * 30, ALTO)], fill=color(acento, 28))
    img.alpha_composite(luz.filter(ImageFilter.GaussianBlur(18)))
    ondas = capa()
    d = ImageDraw.Draw(ondas)
    for k in range(5):
        puntos = [(x, 650 + k * 26 + 60 * math.sin(x / 90 + k)) for x in range(820, ANCHO + 20, 8)]
        d.line(puntos, fill=color(acento, 150 - k * 25), width=4)
    brillo(img, ondas, 6)


def motivo_fiesta(img, acento, rnd):
    confeti = capa()
    d = ImageDraw.Draw(confeti)
    for _ in range(170):
        x, y, r = rnd.randint(760, ANCHO), rnd.randint(0, ALTO), rnd.randint(4, 14)
        tono = acento if rnd.random() < 0.6 else '#ffffff'
        if rnd.random() < 0.5:
            d.ellipse([x - r, y - r, x + r, y + r], fill=color(tono, rnd.randint(110, 230)))
        else:
            d.rectangle([x - r, y - r // 2, x + r, y + r // 2], fill=color(tono, rnd.randint(110, 230)))
    neon = capa()
    d = ImageDraw.Draw(neon)
    for i in range(3):
        d.line([(880 + i * 120, ALTO), (1500 + i * 120, 0)], fill=color(acento, 210), width=10)
    brillo(img, neon, 14)
    img.alpha_composite(confeti)


def motivo_standup(img, acento, rnd):
    cono = capa()
    d = ImageDraw.Draw(cono)
    d.polygon([(1190, -40), (930, ALTO), (1450, ALTO)], fill=color('#ffffff', 40))
    img.alpha_composite(cono.filter(ImageFilter.GaussianBlur(25)))
    micro = capa()
    d = ImageDraw.Draw(micro)
    d.rounded_rectangle([1150, 330, 1230, 520], radius=40, fill=color(acento, 255))
    for y in range(345, 505, 18):
        d.line([(1160, y), (1220, y)], fill=color('#000000', 90), width=4)
    d.arc([1110, 420, 1270, 600], 0, 180, fill=color(acento, 255), width=10)
    d.line([(1190, 600), (1190, 760)], fill=color(acento, 255), width=10)
    d.line([(1120, 770), (1260, 770)], fill=color(acento, 255), width=12)
    brillo(img, micro, 10)


def motivo_teatro(img, acento, rnd):
    telon = capa()
    d = ImageDraw.Draw(telon)
    for lado in (0, 1):
        for i in range(7):
            x0 = (ANCHO - 330 + i * 48) if lado else (i * 48 - 10)
            d.rectangle([x0, 0, x0 + 30, ALTO], fill=color('#000000', 60))
    img.alpha_composite(telon.filter(ImageFilter.GaussianBlur(8)))
    mascaras = capa()
    d = ImageDraw.Draw(mascaras)
    for cx, sonrie in ((1050, True), (1230, False)):
        d.ellipse([cx - 90, 300, cx + 90, 520], outline=color(acento), width=8)
        d.ellipse([cx - 50, 370, cx - 20, 395], fill=color(acento))
        d.ellipse([cx + 20, 370, cx + 50, 395], fill=color(acento))
        caja = [cx - 50, 420, cx + 50, 480] if sonrie else [cx - 50, 450, cx + 50, 510]
        d.arc(caja, 20 if sonrie else 200, 160 if sonrie else 340, fill=color(acento), width=8)
    brillo(img, mascaras, 8)


def motivo_feria(img, acento, rnd):
    banderines = capa()
    d = ImageDraw.Draw(banderines)
    tonos = [acento, '#ffffff', '#ff6b6b', '#4ecdc4']
    for fila, y0 in enumerate((90, 230)):
        puntos = [(x, y0 + 40 * math.sin((x - 700) / 300 * math.pi)) for x in range(700, ANCHO + 60, 60)]
        d.line(puntos, fill=color('#ffffff', 160), width=3)
        for i, (x, y) in enumerate(puntos[:-1]):
            d.polygon([(x + 6, y), (x + 54, y), (x + 30, y + 60)], fill=color(tonos[(i + fila) % 4], 220))
    img.alpha_composite(banderines)
    luces = capa()
    d = ImageDraw.Draw(luces)
    for _ in range(40):
        x, y = rnd.randint(780, ANCHO), rnd.randint(380, ALTO)
        d.ellipse([x - 6, y - 6, x + 6, y + 6], fill=color(acento, 200))
    brillo(img, luces, 9)


def motivo_festival(img, acento, rnd):
    sol = capa()
    d = ImageDraw.Draw(sol)
    for r in range(380, 60, -40):
        d.ellipse([1180 - r, 360 - r, 1180 + r, 360 + r], outline=color(acento, 90 + (380 - r) // 3), width=6)
    brillo(img, sol, 12)
    barras = capa()
    d = ImageDraw.Draw(barras)
    for i, x in enumerate(range(820, ANCHO, 34)):
        alto = 60 + abs(math.sin(i * 0.7)) * 260 + rnd.randint(0, 60)
        d.rectangle([x, ALTO - alto, x + 22, ALTO], fill=color(acento, 200))
    brillo(img, barras, 8)


MOTIVOS = {'concierto': motivo_concierto, 'fiesta': motivo_fiesta, 'standup': motivo_standup,
           'teatro': motivo_teatro, 'feria': motivo_feria, 'festival': motivo_festival}


def partir(texto, letra, ancho_maximo, d):
    lineas, actual = [], ''
    for palabra in texto.split():
        prueba = (actual + ' ' + palabra).strip()
        if d.textlength(prueba, font=letra) <= ancho_maximo:
            actual = prueba
        else:
            lineas.append(actual)
            actual = palabra
    return lineas + [actual]


def afiche(evento):
    c1, c2, acento = evento['colores']
    rnd = random.Random(evento['slug'])
    img = fondo(c1, c2)
    MOTIVOS[evento['motivo']](img, acento, rnd)
    # Velo detrás del texto, para que se lea sobre cualquier motivo
    velo = capa()
    ImageDraw.Draw(velo).rectangle([0, 0, 900, ALTO], fill=color(c1, 150))
    img.alpha_composite(velo.filter(ImageFilter.GaussianBlur(60)))

    d = ImageDraw.Draw(img)
    d.text((90, 110), 'SARAO PRESENTA', font=fuente('bahnschrift.ttf', 34), fill=color(acento))
    titulo = evento['nombre'].replace('Stand-up: ', '')
    letra = fuente('seguibl.ttf', 96)
    lineas = partir(titulo, letra, 760, d)
    if len(lineas) > 3:
        letra = fuente('seguibl.ttf', 76)
        lineas = partir(titulo, letra, 760, d)
    y = 180
    if evento['motivo'] == 'standup':
        d.text((90, y), 'STAND-UP', font=fuente('bahnschrift.ttf', 44), fill=color('#ffffff', 220))
        y += 70
    for linea in lineas:
        d.text((90, y), linea, font=letra, fill=color('#ffffff'))
        y += letra.size + 10
    d.line([(92, y + 34), (300, y + 34)], fill=color(acento), width=6)
    for linea in partir(evento['lema'], fuente('segoeui.ttf', 38), 720, d):
        y += 52
        d.text((90, y + 30), linea, font=fuente('segoeui.ttf', 38), fill=color('#ffffff', 225))
    d.text((90, ALTO - 110), evento['lugar'], font=fuente('segoeuib.ttf', 34), fill=color(acento))
    return img.convert('RGB')


def main():
    destino = AQUI / 'imagenes'
    destino.mkdir(exist_ok=True)
    for evento in json.loads((AQUI / 'eventos.json').read_text(encoding='utf-8')):
        ruta = destino / f"{evento['slug']}.jpg"
        afiche(evento).save(ruta, quality=86, optimize=True, progressive=True)
        assert ruta.stat().st_size < 2 * 1024 * 1024, ruta  # el límite de Eventos y Venta
        print(ruta.name, ruta.stat().st_size // 1024, 'KB')


if __name__ == '__main__':
    main()
