"""
Métodos de la verificación en dos pasos de una cuenta: app autenticadora
(TOTP) y código por correo, a elección; desde el perfil se agrega el otro y
se elige cuál se pide por defecto.

Se guardan como banderas en la columna TBPLogin.totp_activo (NUMBER(1)), que
ya existía: así no hace falta migrar el esquema y una cuenta que solo usa la
app sigue valiendo 1, igual que antes.
  1 = app autenticadora activa
  2 = código por correo activo
  4 = se pide el correo por defecto (si la app también está activa)
Igual que escritorio: el escritorio lo recibe ya resuelto por la API.
"""
APP = "app"
CORREO = "correo"
BIT_APP, BIT_CORREO, BIT_PREFIERE_CORREO = 1, 2, 4


def metodos(valor: int | None, hay_secreto: bool = True) -> dict:
    """{"app", "correo", "preferido", "configurado"} a partir de las banderas."""
    valor = int(valor or 0)
    app = bool(valor & BIT_APP) and hay_secreto
    correo = bool(valor & BIT_CORREO)
    if app and correo:
        preferido = CORREO if valor & BIT_PREFIERE_CORREO else APP
    else:
        preferido = APP if app else CORREO if correo else None
    return {"app": app, "correo": correo, "preferido": preferido, "configurado": app or correo}


def banderas(app: bool, correo: bool, preferido: str | None) -> int:
    valor = (BIT_APP if app else 0) | (BIT_CORREO if correo else 0)
    if app and correo and preferido == CORREO:
        valor |= BIT_PREFIERE_CORREO
    return valor


def de_cuenta(cred: dict) -> dict:
    return metodos(cred.get("dos_pasos"), bool(cred.get("totp_secreto")))
