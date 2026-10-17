"""QR en SVG (sin imágenes en disco ni Pillow) para configurar el
autenticador. El SVG se inserta directo en la página."""
import re

import qrcode
import qrcode.image.svg


def qr_svg(texto: str) -> str:
    imagen = qrcode.make(
        texto,
        image_factory=qrcode.image.svg.SvgPathFillImage,
        box_size=8,
        border=2,
    )
    etiqueta, resto = imagen.to_string(encoding="unicode").split(">", 1)
    # Sin width/height fijos (vienen en mm): el CSS decide el tamaño y se
    # adapta al celular; el viewBox conserva la proporción.
    etiqueta = re.sub(r'\s(width|height)="[^"]*"', "", etiqueta)
    etiqueta = etiqueta.replace("<svg", '<svg class="qr" role="img" aria-label="Código QR"', 1)
    return etiqueta + ">" + resto
