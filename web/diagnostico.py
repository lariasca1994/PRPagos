import sys

print("1) python arrancó bien", flush=True)

try:
    import oracledb
    print("2) oracledb se importó, version:", oracledb.__version__, flush=True)
except Exception as e:
    print("2) FALLÓ el import de oracledb:", repr(e), flush=True)
    sys.exit(1)

try:
    from app.config import settings
    print("3) config.py se importó bien", flush=True)
    print("   wallet_path =", settings.wallet_path, flush=True)
    print("   db_alias    =", settings.db_alias, flush=True)
    print("   db_usuario  =", settings.db_usuario, flush=True)
    print("   db_password está vacío:", not bool(settings.db_password), flush=True)
    print("   jwt_secret está vacío: ", not bool(settings.jwt_secret), flush=True)
    print("   wallet_password está vacío:", not bool(settings.wallet_password), flush=True)
except Exception as e:
    print("3) FALLÓ importar config:", repr(e), flush=True)
    sys.exit(1)

print("4) intentando conectar a la base...", flush=True)
try:
    conn = oracledb.connect(
        user=settings.db_usuario,
        password=settings.db_password,
        dsn=settings.db_alias,
        config_dir=settings.wallet_path,
        wallet_location=settings.wallet_path,
        wallet_password=settings.wallet_password,
    )
    print("5) CONECTÓ correctamente", flush=True)
    conn.close()
except Exception as e:
    print("5) FALLÓ la conexión. Mensaje completo:", flush=True)
    print(str(e), flush=True)
    for arg in getattr(e, "args", []):
        print("   detalle:", getattr(arg, "message", arg), flush=True)
    sys.exit(1)

print("6) todo bien", flush=True)
