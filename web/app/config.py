import os
from pathlib import Path

from dotenv import load_dotenv

# Carga EXACTAMENTE el .env de la carpeta web/ (un nivel arriba de este
# archivo, que vive en web/app/config.py). No se deja a la busqueda
# automatica de python-dotenv hacia arriba en el arbol de carpetas, porque
# en este repo hay otro .env suelto en la raiz (A:\Github\PRPagos\.env,
# de otra cosa, no de esta app) que esa busqueda puede encontrar primero
# y dejar sin efecto lo que se edite aca.
_RUTA_ENV = Path(__file__).resolve().parent.parent / ".env"
load_dotenv(_RUTA_ENV)


class Settings:
    wallet_path: str = os.getenv("WALLET_PATH", "../wallet")
    # Contrasena del Wallet en si (la que se puso al descargar el .zip
    # desde la consola de OCI). python-oracledb en modo "thin" no soporta
    # el wallet SSO/PKCS12 que usa la app de escritorio (cwallet.sso, sin
    # contrasena); usa el ewallet.pem, que si esta protegido con esta
    # contrasena.
    wallet_password: str = os.getenv("WALLET_PASSWORD", "")
    db_alias: str = os.getenv("DB_ALIAS", "prpagosdb_tp")
    db_usuario: str = os.getenv("DB_USUARIO", "ADMIN")
    db_password: str = os.getenv("DB_PASSWORD", "")
    jwt_secret: str = os.getenv("JWT_SECRET", "")

    def validar(self) -> None:
        faltantes = [
            nombre
            for nombre, valor in (
                ("DB_PASSWORD", self.db_password),
                ("JWT_SECRET", self.jwt_secret),
                ("WALLET_PASSWORD", self.wallet_password),
            )
            if not valor
        ]
        if faltantes:
            raise RuntimeError(
                "Faltan variables de entorno: " + ", ".join(faltantes)
                + ". Copia .env.example como .env y completa tus datos."
            )


settings = Settings()
