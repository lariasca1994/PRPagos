"""
Registros de ingresos y gastos: consulta con filtros y totales, CSV, crear,
modificar y eliminar. Mismas reglas y mensajes para la web y el escritorio.
"""
import csv
import io
from datetime import date

import oracledb

from .. import finanzas, repositorio
from . import ErrorServicio

TIPOS = {"GASTO": "Gasto", "INGRESO": "Ingreso"}
GRUPOS = {"NECESIDADES": "Necesidades", "DESEOS": "Deseos", "AHORRO": "Ahorro y deudas"}
CATEGORIA_INVALIDA = "Categoría no válida para este registro"


def parsear_filtros(tipo=None, concepto=None, monto_min=None, monto_max=None, fecha_desde=None,
                    fecha_hasta=None) -> dict:
    """Campo vacío = sin filtro; un monto o una fecha inválidos se ignoran."""
    def _monto(valor):
        valor = (valor or "").strip().replace(",", ".")
        try:
            return float(valor) if valor else None
        except ValueError:
            return None

    def _fecha(valor):
        valor = (valor or "").strip()
        try:
            return date.fromisoformat(valor) if valor else None
        except ValueError:
            return None

    return {
        "tipo": tipo if tipo in TIPOS else None,
        "concepto": (concepto or "").strip() or None,
        "monto_min": _monto(monto_min),
        "monto_max": _monto(monto_max),
        "fecha_desde": _fecha(fecha_desde),
        "fecha_hasta": _fecha(fecha_hasta),
    }


def consultar(usuario_id: int, es_admin: bool, filtros: dict) -> dict:
    """Registros visibles para la cuenta (el admin ve todos) y sus totales."""
    registros = repositorio.buscar_registros(None if es_admin else usuario_id, filtros)
    ingresos = sum(r["monto"] for r in registros if r["tipo"] == "INGRESO")
    gastos = sum(r["monto"] for r in registros if r["tipo"] == "GASTO")
    return {"registros": registros, "ingresos": ingresos, "gastos": gastos, "balance": ingresos - gastos}


def csv_texto(usuario_id: int, es_admin: bool, filtros: dict) -> str:
    registros = consultar(usuario_id, es_admin, filtros)["registros"]
    buffer = io.StringIO()
    escritor = csv.writer(buffer)
    if es_admin:
        escritor.writerow(["ID", "Usuario", "Nombre", "Tipo", "Monto", "Fecha", "Categoría", "Concepto"])
        for r in registros:
            escritor.writerow([r["id_pago"], r["usuario"], r["nombre"], TIPOS[r["tipo"]], r["monto"],
                               r["fecha"], r["categoria"], r["concepto"]])
    else:
        escritor.writerow(["ID", "Tipo", "Monto", "Fecha", "Categoría", "Concepto"])
        for r in registros:
            escritor.writerow([r["id_pago"], TIPOS[r["tipo"]], r["monto"], r["fecha"], r["categoria"],
                               r["concepto"]])
    return buffer.getvalue()


def formulario(usuario_id: int, es_admin: bool, id_pago: int | None = None) -> dict:
    """Datos para Nuevo / Modificar registro: categorías del dueño y el registro."""
    registro = None
    dueno = usuario_id
    if id_pago is not None:
        registro = repositorio.obtener_registro(id_pago, None if es_admin else usuario_id)
        if registro is None:
            raise ErrorServicio("No se pudo modificar: este registro no te pertenece.")
        if es_admin:
            dueno = repositorio.dueno_registro(id_pago) or usuario_id
    return {"tipos": TIPOS, "categorias": repositorio.categorias(dueno), "registro": registro,
            "hoy": finanzas.hoy().isoformat(), "grupos": GRUPOS}


def _monto(texto: str) -> float | None:
    """Coma o punto decimal. Debe ser mayor que 0."""
    try:
        valor = float((texto or "").strip().replace(",", "."))
    except ValueError:
        return None
    return valor if valor > 0 else None


def _categoria(texto) -> int | None:
    texto = str(texto or "").strip()
    return int(texto) if texto.isdigit() else None


def _validar(tipo: str, monto: str, fecha: str, concepto: str) -> tuple[float, date, str]:
    if tipo not in TIPOS:
        raise ErrorServicio("Elige si es un ingreso o un gasto")
    valor = _monto(monto)
    if valor is None:
        raise ErrorServicio("Monto inválido")
    try:
        dia = date.fromisoformat((fecha or "").strip())
    except ValueError:
        raise ErrorServicio("Fecha inválida") from None
    concepto = (concepto or "").strip()
    if not concepto:
        raise ErrorServicio("Escribe el concepto")
    return valor, dia, concepto[:100]


def crear(usuario_id: int, tipo: str, monto: str, fecha: str, categoria_id, concepto: str) -> str:
    valor, dia, concepto = _validar(tipo, monto, fecha, concepto)
    try:
        repositorio.insertar_registro(tipo, valor, dia, concepto, _categoria(categoria_id), usuario_id)
    except oracledb.DatabaseError:
        raise ErrorServicio(CATEGORIA_INVALIDA) from None
    return "Registro guardado"


def actualizar(usuario_id: int, es_admin: bool, id_pago: int, tipo: str, monto: str, fecha: str,
               categoria_id, concepto: str) -> str:
    valor, dia, concepto = _validar(tipo, monto, fecha, concepto)
    categoria = _categoria(categoria_id)
    try:
        if es_admin:
            filas = 1 if repositorio.actualizar_registro_admin(
                id_pago, tipo, valor, dia, concepto, categoria, usuario_id) else 0
        else:
            filas = repositorio.actualizar_registro(id_pago, tipo, valor, dia, concepto, categoria, usuario_id)
    except oracledb.DatabaseError:
        raise ErrorServicio(CATEGORIA_INVALIDA) from None
    if filas == 0:
        raise ErrorServicio("No se pudo modificar: este registro no te pertenece.")
    return f"Registro #{id_pago} actualizado"


def eliminar(usuario_id: int, es_admin: bool, id_pago: int) -> str:
    if not es_admin:
        raise ErrorServicio("Solo un administrador puede eliminar registros.")
    repositorio.eliminar_pago(id_pago, usuario_id)
    return f"Registro #{id_pago} eliminado"
