"""
Capa de servicios: la ÚNICA lógica de PRPagos. Validaciones, mensajes,
cálculos y acceso a los datos viven aquí; las páginas web (routers/*.py) y la
API JSON para la aplicación de escritorio (routers/api.py) solo los llaman y
muestran el resultado. Así un mismo dato y una misma regla se ven igual desde
el navegador, la PWA o el escritorio.

Un error de negocio se lanza como ErrorServicio con el mensaje para la persona.
"""


class ErrorServicio(Exception):
    """Regla de negocio no cumplida: el mensaje se muestra tal cual."""

    def __init__(self, mensaje: str):
        super().__init__(mensaje)
        self.mensaje = mensaje
