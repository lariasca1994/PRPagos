"""
Punto único de conexión a la base de datos.

Equivalente directo de ConexionBD.java de la versión de escritorio: mismo
Wallet, mismo alias TNS, mismo usuario/password -- ninguno de los dos
procedimientos ni tablas cambia, solo el cliente que los llama.

python-oracledb en modo "thin" (el que se usa aqui) no necesita el Instant
Client de Oracle instalado en el sistema, pero a diferencia de Java NO
soporta el wallet SSO/PKCS12 (cwallet.sso, sin contraseña) -- usa el
ewallet.pem, que sigue protegido con la contraseña que se puso al
descargar el Wallet desde la consola de OCI. Por eso aca se pasa
wallet_password ademas de config_dir.
"""
import oracledb

from .config import settings


def obtener_conexion():
    return oracledb.connect(
        user=settings.db_usuario,
        password=settings.db_password,
        dsn=settings.db_alias,
        config_dir=settings.wallet_path,
        wallet_location=settings.wallet_path,
        wallet_password=settings.wallet_password,
    )
