"""
Crea o asciende a admin las cuentas indicadas en TBPLogin.

A diferencia del registro público (POST /registro en routers/auth.py),
este script NO exige que el "usuario" sea un correo @EAN.com -- esa regla
(heredada de la versión de escritorio) solo tiene sentido para las cuentas
de prueba/clientes; las 2 cuentas admin reales de este portafolio son
@proton.me. Por eso este script llama directo a repositorio.crear_usuario
/ repositorio.ascender_a_admin, sin pasar por el router ni por
PATRON_CORREO.

No abre ninguna conexión nueva ni agrega ningún procedimiento almacenado:
usa las MISMAS funciones de acceso a datos (repositorio.py) que ya usa el
resto de la app, solo que con rol='admin'.

Uso (desde la carpeta web/, con el entorno virtual activado y el .env de
web/ completo -- ver app/config.py):

    python -m app.comandos.crear_admin correo1@proton.me:clave1 correo2@proton.me:clave2

Cada argumento es "correo:contrasena". Si preferís no dejar las
contraseñas en el historial de la terminal, definí en tu .env (el de
web/, que NUNCA se sube a git -- ver .gitignore) una línea:

    PRPAGOS_ADMINS=correo1@proton.me:clave1,correo2@proton.me:clave2

y corré el script sin argumentos; los toma de ahí.

Si el correo ya existía en TBPLogin (por ejemplo porque ya se había
registrado como usuario común), se lo asciende a rol='admin', se
reactiva si estaba suspendido, y SE LE ACTUALIZA LA CONTRASEÑA a la
indicada -- a diferencia del script equivalente de TaskFlow, que al
ascender no toca la contraseña; acá sí se actualiza siempre, para que el
valor que le pasás a este script sea, de forma predecible, la contraseña
que queda. Si no existía, se crea directo como admin.
"""
import os
import sys

from .. import repositorio
from ..seguridad import hash_password

NOMBRE_ADMIN = "Administrador PRPagos"


def _pares_desde_entorno() -> list[str]:
    bruto = os.environ.get("PRPAGOS_ADMINS", "")
    return [p.strip() for p in bruto.split(",") if p.strip()]


def crear_o_ascender(correo: str, contrasena: str) -> str:
    hash_ = hash_password(contrasena)
    creado = repositorio.crear_usuario(correo, NOMBRE_ADMIN, hash_, rol="admin")
    if creado:
        return "creado como admin"
    ascendido = repositorio.ascender_a_admin(correo, hash_)
    if ascendido:
        return "ya existía -- ascendido a admin, reactivado y contraseña actualizada"
    return "ERROR: no se pudo crear ni ascender (revisa el correo)"


def main():
    pares = sys.argv[1:] or _pares_desde_entorno()
    if not pares:
        print("Uso: python -m app.comandos.crear_admin correo:contrasena [correo:contrasena ...]")
        print("     (o definí PRPAGOS_ADMINS=correo:clave,correo:clave en tu .env y corré sin argumentos)")
        sys.exit(1)

    for par in pares:
        if ":" not in par:
            print(f"⚠️  '{par}' ignorado -- formato esperado correo:contrasena")
            continue
        correo, contrasena = par.split(":", 1)
        correo = correo.strip()
        contrasena = contrasena.strip()
        if len(contrasena) < 8:
            print(f"⚠️  {correo}: contraseña muy corta (mínimo 8), se omite")
            continue
        resultado = crear_o_ascender(correo, contrasena)
        print(f"✅ {correo}: {resultado}")


if __name__ == "__main__":
    main()
