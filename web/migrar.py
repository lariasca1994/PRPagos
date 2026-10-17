"""
Aplica una migración de sql/ a la base de PRPagos. Las migraciones son
aditivas: no borran ni modifican datos existentes, y se pueden correr más
de una vez (lo que ya existe se salta).

Usa las mismas credenciales y el mismo Wallet que la app web (web/.env).

Uso, parado en web/ con la venv activada:
    python migrar.py seguridad            # muestra el SQL de sql/seguridad.sql
    python migrar.py finanzas --aplicar   # aplica sql/finanzas.sql
"""
import sys
from pathlib import Path

import oracledb

from app.config import settings

CARPETA_SQL = Path(__file__).resolve().parent.parent / "sql"
# Errores que significan "ya estaba aplicado": se saltan.
YA_EXISTE = {
    955: "el objeto ya existe",          # ORA-00955 (tabla, secuencia)
    1430: "la columna ya existe",        # ORA-01430
    2264: "la restricción ya existe",    # ORA-02264
    2275: "la restricción ya existe",    # ORA-02275
    1: "los datos iniciales ya están",   # ORA-00001 (INSERT de semillas)
}


def sentencias(ruta: Path) -> list[str]:
    """Divide el archivo en sentencias: cada una termina en una línea con
    "/" sola (convención de SQL*Plus). Se descartan los comentarios previos
    a cada sentencia y el ";" final de las que no son PL/SQL."""
    bloques: list[str] = []
    actual: list[str] = []
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        if linea.strip() == "/":
            texto = "\n".join(actual).strip()
            if texto:
                bloques.append(texto)
            actual = []
        elif actual or (linea.strip() and not linea.lstrip().startswith("--")):
            actual.append(linea)
    resultado = []
    for bloque in bloques:
        es_plsql = bloque.upper().startswith("CREATE OR REPLACE PROCEDURE")
        resultado.append(bloque if es_plsql else bloque.rstrip(";"))
    return resultado


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    ruta = CARPETA_SQL / f"{sys.argv[1]}.sql"
    lista = sentencias(ruta)
    if "--aplicar" not in sys.argv:
        for i, sql in enumerate(lista, start=1):
            print(f"-- [{i}/{len(lista)}]\n{sql}\n")
        print(f"Vista previa de {ruta.name}: no se ejecutó nada. Usa --aplicar para ejecutar.")
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
                motivo = YA_EXISTE.get(getattr(detalle, "code", None))
                if motivo:
                    print(f"[{i}/{len(lista)}] se salta ({motivo}): {primera}")
                    continue
                raise
            if sql.upper().startswith("CREATE OR REPLACE PROCEDURE") or sql.upper().startswith(
                "CREATE OR REPLACE VIEW"
            ):
                nombre = sql.split("(")[0].split()[-1].upper() if "PROCEDURE" in sql.upper()[:30] else (
                    sql.split()[4].upper()
                )
                cur.execute(
                    "SELECT line, text FROM user_errors WHERE name = :n ORDER BY sequence", n=nombre
                )
                errores = cur.fetchall()
                if errores:
                    raise RuntimeError(f"{nombre} quedó con errores de compilación: {errores}")
        conn.commit()
        # Al cambiar tablas, Oracle invalida los objetos que dependen de ellas:
        # se recompilan ya para que la app publicada no espere a la primera llamada.
        cur.execute("SELECT object_name, object_type FROM user_objects WHERE status = 'INVALID'")
        for nombre, tipo in cur.fetchall():
            if tipo in ("PROCEDURE", "VIEW"):
                cur.execute(f'ALTER {tipo} "{nombre}" COMPILE')
                print(f"recompilado: {tipo} {nombre}")
    finally:
        conn.close()
    print(f"Listo: {ruta.name} aplicada. Ningún dato existente se borró.")


if __name__ == "__main__":
    main()
