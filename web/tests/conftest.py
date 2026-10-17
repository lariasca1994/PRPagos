"""Configuración de prueba: valores ficticios para que app.config cargue sin
un .env real. Las pruebas de seguridad no tocan la base de datos."""
import base64
import os
import sys
from pathlib import Path

os.environ.setdefault("DB_PASSWORD", "prueba")
os.environ.setdefault("WALLET_PASSWORD", "prueba")
os.environ.setdefault("JWT_SECRET", "secreto-de-prueba-solo-para-tests-0123456789")
os.environ.setdefault("TOTP_CLAVE", base64.b64encode(bytes(range(32))).decode())

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
