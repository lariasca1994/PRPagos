"""
Aplica sql/seguridad.sql a la base de PRPagos: columnas nuevas (TOTP,
bloqueo por intentos fallidos, contraseña más larga) y los procedimientos
de acceso. Es aditivo: no borra ni modifica datos existentes.

Usa las mismas credenciales y el mismo Wallet que la app web (el .env de
esta carpeta). Se puede correr más de una vez: si las columnas ya existen,
ese paso se salta.

Uso, parado en la carpeta web/ con la venv activada:
    python migrar_seguridad.py            # muestra lo que haría, sin ejecutar
    python migrar_seguridad.py --aplicar  # ejecuta la migración
"""
import sys
from pathlib import Path

import oracledb

from app.config import settings

RUTA_SQL = Path(__file__).resolve().parent.parent / "sql" / "seguridad.sql"
# ORA-01430: la columna que se intenta agregar ya existe.
COLUMNA_YA_EXISTE = 1430


def sentencias() -> list[str]:
    """Divide el archivo en sentencias: cada una termina en una línea con
    "/" sola (convención de SQL*Plus). Se quitan los comentarios de línea
    completa y el ";" final de las sentencias que no son PL/SQL."""
    bloques: list[str] = []
    actual: list[str] = []
    for linea in RUTA_SQL.read_text(encoding="utf-8").splitlines():
        if linea.strip() == "/":
            texto = "\n".join(actual).strip()
            if texto:
                bloques.append(texto)
            actual = []
        elif actual or (linea.strip() and not linea.lstrip().startswith("--")):
            # Los comentarios y líneas en blanco ANTES de cada sentencia se
            # descartan; los de adentro (p. ej. en el ALTER) se conservan.
            actual.append(linea)
    resultado = []
    for bloque in bloques:
        es_plsql = bloque.upper().startswith("CREATE OR REPLACE")
        resultado.append(bloque if es_plsql else bloque.rstrip(";"))
    return resultado


def main() -> None:
    aplicar = "--aplicar" in sys.argv
    lista = sentencias()
    if not aplicar:
        for i, sql in enumerate(lista, start=1):
            print(f"-- [{i}/{len(lista)}]\n{sql}\n")
        print("Vista previa: no se ejecutó nada. Usa --aplicar para ejecutar.")
        return

    settings.validar()
    conn = oracledb.connect(
        user=settings.db_usuario,
        password=settings.db_password,
        dsn=settings.db_alias,
        config_dir=settings.wallet_path,
        wallet_location=settings.wallet_path,
        wallet_password=settings.wallet_password,
    )
    try:
        cur = conn.cursor()
        for i, sql in enumerate(lista, start=1):
            primera = sql.splitlines()[0]
            try:
                cur.execute(sql)
                print(f"[{i}/{len(lista)}] ok: {primera}")
            except oracledb.DatabaseError as error:
                (detalle,) = error.args
                if getattr(detalle, "code", None) == COLUMNA_YA_EXISTE:
                    print(f"[{i}/{len(lista)}] ya aplicado, se salta: {primera}")
                    continue
                raise
            # Un procedimiento con errores de compilación no lanza excepción
            # al crearse: se revisa USER_ERRORS para no dejarlo inválido.
            if sql.upper().startswith("CREATE OR REPLACE PROCEDURE"):
                nombre = sql.split("(")[0].split()[-1].upper()
                cur.execute(
                    "SELECT line, text FROM user_errors WHERE name = :n ORDER BY sequence",
                    n=nombre,
                )
                errores = cur.fetchall()
                if errores:
                    raise RuntimeError(f"{nombre} quedó con errores de compilación: {errores}")
        conn.commit()
    finally:
        conn.close()
    print("Listo: migración de seguridad aplicada. Ningún dato existente se modificó.")


if __name__ == "__main__":
    main()
