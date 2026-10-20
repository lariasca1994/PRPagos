"""
Punto único de conexión a la base de datos.

python-oracledb en modo "thin" (el que se usa aqui) no necesita el Instant
Client de Oracle instalado en el sistema, pero NO soporta el wallet
SSO/PKCS12 (cwallet.sso, sin contraseña) -- usa el ewallet.pem, que sigue
protegido con la contraseña que se puso al descargar el Wallet desde la
consola de OCI. Por eso aca se pasa wallet_password ademas de config_dir.

Pool de conexiones: abrir una conexión a Autonomous Database cuesta varios
viajes de ida y vuelta (TLS mutuo con el Wallet y autenticación), y la base
está en otra región. El pool las mantiene abiertas y las reutiliza: cada
consulta paga un solo viaje. `with obtener_conexion() as conn:` devuelve la
conexión al pool al salir (no la cierra).
"""
import threading

import oracledb

from .config import settings

# Cloud Run atiende varias peticiones a la vez en hilos; con 4 conexiones
# alcanza y no se acerca al límite de sesiones de la base gratuita.
POOL_MIN = 1
POOL_MAX = 4

_pool: oracledb.ConnectionPool | None = None
_candado = threading.Lock()


def _preparar_sesion(conexion: oracledb.Connection, _etiqueta: str | None) -> None:
    """Una vez por sesión nueva: la app entra con un usuario propio de
    privilegios mínimos y trabaja sobre el esquema dueño de los datos."""
    if settings.db_esquema:
        conexion.current_schema = settings.db_esquema


def _crear_pool() -> oracledb.ConnectionPool:
    return oracledb.create_pool(
        user=settings.db_usuario,
        password=settings.db_password,
        dsn=settings.db_alias,
        config_dir=settings.wallet_path,
        wallet_location=settings.wallet_path,
        wallet_password=settings.wallet_password,
        min=POOL_MIN,
        max=POOL_MAX,
        increment=1,
        getmode=oracledb.POOL_GETMODE_WAIT,
        wait_timeout=15_000,
        # Una conexión sin uso por más de 60 s se verifica antes de entregarla
        # (la base puede cortarla); si murió, el pool abre otra.
        ping_interval=60,
        session_callback=_preparar_sesion,
    )


def obtener_conexion() -> oracledb.Connection:
    """Conexión del pool (se crea con la primera consulta)."""
    global _pool
    if _pool is None:
        with _candado:
            if _pool is None:
                _pool = _crear_pool()
    return _pool.acquire()
