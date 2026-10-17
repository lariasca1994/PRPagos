import base64
import os
from pathlib import Path

from dotenv import load_dotenv

# Carga EXACTAMENTE el .env de la carpeta web/ (un nivel arriba de este
# archivo, que vive en web/app/config.py), sin dejarlo a la busqueda
# automatica de python-dotenv hacia arriba en el arbol de carpetas. Es el
# unico .env del proyecto; la version de escritorio usa
# escritorio/config.properties.
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
    # Clave AES-256 (32 bytes en base64) con la que se cifran los secretos
    # TOTP en la base. Es la MISMA que totp.clave en el config.properties
    # de la version de escritorio: si difieren, una interfaz no puede leer
    # los autenticadores configurados en la otra.
    totp_clave: str = os.getenv("TOTP_CLAVE", "")
    # Correos con la API de Brevo (opcionales: sin ellos la app funciona,
    # pero no envía resúmenes ni códigos de recuperación por correo). El
    # remitente debe estar verificado en la cuenta de Brevo.
    brevo_api_key: str = os.getenv("BREVO_API_KEY", "")
    brevo_remitente: str = os.getenv("BREVO_REMITENTE", "")
    # Dirección pública de la web (enlace "Ver mi dashboard" de los correos).
    url_publica: str = os.getenv(
        "URL_PUBLICA", "https://prpagos-web-1087929107584.southamerica-east1.run.app"
    )
    # Token con el que GitHub Actions llama a /tareas/resumenes cada día.
    tareas_token: str = os.getenv("TAREAS_TOKEN", "")

    def validar(self) -> None:
        faltantes = [
            nombre
            for nombre, valor in (
                ("DB_PASSWORD", self.db_password),
                ("JWT_SECRET", self.jwt_secret),
                ("WALLET_PASSWORD", self.wallet_password),
                ("TOTP_CLAVE", self.totp_clave),
            )
            if not valor
        ]
        if faltantes:
            raise RuntimeError(
                "Faltan variables de entorno: " + ", ".join(faltantes)
                + ". Copia .env.example como .env y completa tus datos."
            )
        try:
            clave_valida = len(base64.b64decode(self.totp_clave, validate=True)) == 32
        except ValueError:
            clave_valida = False
        if not clave_valida:
            raise RuntimeError(
                "TOTP_CLAVE debe ser una clave de 32 bytes en base64. Genera una con: "
                'python -c "import base64, secrets; '
                'print(base64.b64encode(secrets.token_bytes(32)).decode())"'
            )


settings = Settings()
