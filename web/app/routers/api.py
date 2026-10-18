"""
API JSON para la aplicación de escritorio. El escritorio no tiene acceso a la
base ni a ninguna clave: todo lo pide aquí, como el navegador pide las
páginas. Cada ruta llama a la MISMA función de servicios/ que la página web
equivalente, así que los datos, las reglas y los mensajes son uno solo.

Sesión: el mismo token de la web (6 h sin uso, máximo 8 h), en la cabecera
"Authorization: Bearer <token>". Cada respuesta trae el token renovado en
"X-Sesion". Sin sesión válida responde 401 con el motivo del cierre.
Errores de negocio: 400 {"error": "mensaje para la persona"}.
"""
import json
from datetime import date

from fastapi import APIRouter, Body, Depends, Request
from fastapi.responses import JSONResponse, Response

from .. import llaves, repositorio
from ..dependencias import UsuarioActual, exigir_admin, exigir_login
from ..seguridad import (
    MENSAJES_CIERRE,
    crear_token,
    crear_token_parcial,
    crear_token_reconfiguracion,
    descifrar_o_none,
    leer_token,
    leer_token_parcial,
    leer_token_reconfiguracion,
)
from ..servicios import ErrorServicio
from ..servicios import acceso, dashboard, perfil, registros, usuarios

router = APIRouter(prefix="/api")


def respuesta_sesion_cerrada(motivo: str | None) -> JSONResponse:
    mensaje = MENSAJES_CIERRE.get(motivo or "", "Tu sesión no es válida; ingresa de nuevo.")
    return JSONResponse({"error": mensaje, "motivo": motivo or "invalida"}, status_code=401)


def _texto(datos: dict, clave: str) -> str:
    valor = datos.get(clave)
    return "" if valor is None else str(valor)


def _sesion(cred: dict, **extra) -> dict:
    token = crear_token(cred["usuario_id"], cred["usuario"], cred["nombre"])
    estado = repositorio.obtener_estado_usuario(cred["usuario_id"]) or {}
    return {
        "sesion": token,
        "vence": leer_token(token)["exp"],
        "usuario": {"id": cred["usuario_id"], "correo": cred["usuario"], "nombre": cred["nombre"],
                    "admin": estado.get("rol") == "admin"},
        **extra,
    }


def _paso(paso: acceso.PasoIngreso) -> dict:
    cred = paso.cred
    if paso.etapa == "codigo":
        return {"etapa": "codigo", "correo": cred["usuario"],
                "token": crear_token_parcial(cred["usuario_id"], cred["usuario"], cred["nombre"], "codigo")}
    config = acceso.configuracion_autenticador(paso.secreto, cred["usuario"])
    return {"etapa": "configurar", "correo": cred["usuario"], "uri": config["uri"], "clave": config["clave"],
            "token": crear_token_parcial(cred["usuario_id"], cred["usuario"], cred["nombre"], "configurar",
                                         secreto_pendiente=paso.secreto)}


def _parcial(datos: dict, etapa: str) -> dict:
    token = leer_token_parcial(_texto(datos, "token"))
    if not token or token.get("etapa") != etapa:
        raise ErrorServicio(acceso.MENSAJE_EXPIRADO)
    return token


# ───────────────────────── Público ─────────────────────────


@router.get("/version-escritorio")
def version_escritorio():
    """Para la actualización automática: versión, URL del Release y huella SHA-256."""
    return repositorio.ultima_version_escritorio() or {}


# ───────────────────────── Acceso ─────────────────────────


@router.post("/acceso/ingresar")
def ingresar(datos: dict = Body(...)):
    return _paso(acceso.ingresar(_texto(datos, "usuario"), _texto(datos, "contrasena")))


@router.post("/acceso/codigo")
def codigo(datos: dict = Body(...)):
    token = _parcial(datos, "codigo")
    return _sesion(acceso.verificar_codigo(token["usuario_id"], _texto(datos, "codigo")))


@router.post("/acceso/configurar")
def configurar(datos: dict = Body(...)):
    token = _parcial(datos, "configurar")
    secreto = descifrar_o_none(token.get("pendiente"))
    if secreto is None:
        raise ErrorServicio(acceso.MENSAJE_EXPIRADO)
    cred, codigos = acceso.activar_autenticador(token["usuario_id"], secreto, _texto(datos, "codigo"))
    return _sesion(cred, codigos=codigos)


@router.post("/acceso/perdi-telefono")
def perdi_telefono(datos: dict = Body(...)):
    token = _parcial(datos, "codigo")
    cred = acceso.perdi_telefono(token["usuario_id"])
    return {"mensaje": acceso.texto_codigo_enviado(cred["usuario"]),
            "token": crear_token_parcial(cred["usuario_id"], cred["usuario"], cred["nombre"], "correo")}


@router.post("/acceso/correo")
def codigo_correo(datos: dict = Body(...)):
    token = _parcial(datos, "correo")
    respuesta = _paso(acceso.verificar_codigo_correo(token["usuario_id"], _texto(datos, "codigo")))
    respuesta["mensaje"] = "Código verificado: configura tu autenticador nuevo."
    return respuesta


@router.post("/acceso/registro")
def registro(datos: dict = Body(...)):
    return {"mensaje": acceso.registrar(_texto(datos, "usuario"), _texto(datos, "nombre"),
                                        _texto(datos, "contrasena"), _texto(datos, "confirmacion"))}


@router.post("/acceso/recuperar")
def recuperar(datos: dict = Body(...)):
    return {"mensaje": acceso.recuperar(_texto(datos, "correo"), _texto(datos, "nombre_registrado"),
                                        _texto(datos, "codigo"), _texto(datos, "nueva"),
                                        _texto(datos, "confirmacion"))}


def _identidad_sitio() -> dict:
    """Con estos datos el escritorio arma clientDataJSON igual que un navegador."""
    return {"origen": llaves.origen(), "sitio": llaves.id_sitio()}


@router.post("/acceso/llave/opciones")
def opciones_llave():
    reto, token = llaves.crear_reto("ingreso")
    return {"opciones": json.loads(llaves.opciones_ingreso(reto)), "reto": token, **_identidad_sitio()}


@router.post("/acceso/llave")
def ingresar_con_llave(datos: dict = Body(...)):
    reto = llaves.leer_reto(_texto(datos, "reto"), "ingreso")
    if reto is None:
        raise ErrorServicio("La solicitud expiró; inténtalo de nuevo.")
    return _sesion(acceso.ingresar_con_llave(datos.get("credencial") or {}, reto["reto"]))


@router.get("/sesion")
def sesion(usuario: UsuarioActual = Depends(exigir_login)):
    return {"usuario": {"id": usuario.usuario_id, "correo": usuario.usuario, "nombre": usuario.nombre,
                        "admin": usuario.es_admin}, "vence": usuario.vence}


# ───────────────────────── Dashboard ─────────────────────────


@router.get("/dashboard")
def ver_dashboard(mes: str | None = None, usuario: UsuarioActual = Depends(exigir_login)):
    return dashboard.vista(usuario.usuario_id, dashboard.mes_elegido(mes))


@router.post("/dashboard/resumen")
def enviar_resumen(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": dashboard.enviar_resumen(usuario.usuario_id, usuario.usuario, usuario.nombre,
                                                _texto(datos, "tipo"))}


# ───────────────────────── Registros ─────────────────────────


def _json_registro(r: dict) -> dict:
    return {**r, "fecha": r["fecha"].isoformat() if isinstance(r.get("fecha"), date) else r.get("fecha")}


@router.get("/registros")
def consultar_registros(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    filtros = registros.parsear_filtros(**{k: request.query_params.get(k) for k in (
        "tipo", "concepto", "monto_min", "monto_max", "fecha_desde", "fecha_hasta")})
    resultado = registros.consultar(usuario.usuario_id, usuario.es_admin, filtros)
    return {**resultado, "registros": [_json_registro(r) for r in resultado["registros"]],
            "tipos": registros.TIPOS, "admin": usuario.es_admin}


@router.get("/registros/exportar.csv")
def exportar_csv(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    filtros = registros.parsear_filtros(**{k: request.query_params.get(k) for k in (
        "tipo", "concepto", "monto_min", "monto_max", "fecha_desde", "fecha_hasta")})
    return Response(registros.csv_texto(usuario.usuario_id, usuario.es_admin, filtros), media_type="text/csv")


@router.get("/registros/formulario")
def formulario(id: int | None = None, usuario: UsuarioActual = Depends(exigir_login)):
    datos = registros.formulario(usuario.usuario_id, usuario.es_admin, id)
    if datos["registro"]:
        datos["registro"] = _json_registro(datos["registro"])
    return datos


@router.post("/registros")
def crear_registro(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": registros.crear(usuario.usuario_id, _texto(datos, "tipo"), _texto(datos, "monto"),
                                       _texto(datos, "fecha"), datos.get("categoria_id"), _texto(datos, "concepto"))}


@router.put("/registros/{id_pago}")
def actualizar_registro(id_pago: int, datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": registros.actualizar(usuario.usuario_id, usuario.es_admin, id_pago, _texto(datos, "tipo"),
                                            _texto(datos, "monto"), _texto(datos, "fecha"), datos.get("categoria_id"),
                                            _texto(datos, "concepto"))}


@router.delete("/registros/{id_pago}")
def eliminar_registro(id_pago: int, usuario: UsuarioActual = Depends(exigir_admin)):
    return {"mensaje": registros.eliminar(usuario.usuario_id, True, id_pago)}


# ───────────────────────── Perfil ─────────────────────────


@router.get("/perfil")
def ver_perfil(usuario: UsuarioActual = Depends(exigir_login)):
    datos = perfil.datos(usuario.usuario_id)
    for llave in datos["llaves"]:
        llave.pop("credencial_id", None)
    return datos


@router.post("/perfil/datos")
def guardar_datos(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": perfil.guardar_datos(usuario.usuario_id, _texto(datos, "nombre"),
                                            bool(datos.get("notificaciones")))}


@router.post("/perfil/plan")
def guardar_plan(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": perfil.guardar_plan(usuario.usuario_id, _texto(datos, "necesidades"),
                                           _texto(datos, "deseos"), _texto(datos, "ahorro"))}


@router.post("/perfil/categorias")
def crear_categoria(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": perfil.crear_categoria(usuario.usuario_id, _texto(datos, "nombre"), _texto(datos, "tipo"),
                                              _texto(datos, "grupo"))}


@router.delete("/perfil/categorias/{categoria_id}")
def eliminar_categoria(categoria_id: int, usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": perfil.eliminar_categoria(usuario.usuario_id, categoria_id)}


@router.post("/perfil/contrasena")
def cambiar_contrasena(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": perfil.cambiar_contrasena(usuario.usuario_id, _texto(datos, "actual"), _texto(datos, "nueva"),
                                                 _texto(datos, "confirmacion"), _texto(datos, "codigo"))}


@router.post("/perfil/respaldo")
def regenerar_codigos(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"codigos": perfil.regenerar_codigos(usuario.usuario_id, _texto(datos, "codigo"))}


@router.post("/perfil/autenticador")
def iniciar_cambio_autenticador(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    secreto = perfil.iniciar_cambio_autenticador(usuario.usuario_id, _texto(datos, "codigo"))
    config = acceso.configuracion_autenticador(secreto, usuario.usuario)
    return {"token": crear_token_reconfiguracion(usuario.usuario_id, secreto), "uri": config["uri"],
            "clave": config["clave"]}


@router.post("/perfil/autenticador/confirmar")
def confirmar_autenticador(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    secreto = leer_token_reconfiguracion(_texto(datos, "token"), usuario.usuario_id)
    if secreto is None:
        raise ErrorServicio("El cambio de autenticador expiró; empieza de nuevo")
    return {"codigos": perfil.confirmar_autenticador(usuario.usuario_id, secreto, _texto(datos, "codigo"))}


@router.post("/perfil/llaves/opciones")
def opciones_llave_nueva(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    opciones, token = perfil.opciones_llave(usuario.usuario_id, _texto(datos, "nombre"), _texto(datos, "codigo"))
    return {"opciones": json.loads(opciones), "reto": token, **_identidad_sitio()}


@router.post("/perfil/llaves")
def registrar_llave(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": perfil.registrar_llave(usuario.usuario_id, _texto(datos, "reto"),
                                              datos.get("credencial") or {})}


@router.delete("/perfil/llaves/{llave_id}")
def eliminar_llave(llave_id: int, usuario: UsuarioActual = Depends(exigir_login)):
    return {"mensaje": perfil.eliminar_llave(usuario.usuario_id, llave_id)}


# ───────────────────────── Cuentas (rol admin) ─────────────────────────


@router.get("/usuarios")
def listar_usuarios(usuario: UsuarioActual = Depends(exigir_admin)):
    return {"usuarios": usuarios.listar()}


@router.post("/usuarios/{usuario_id}/suspender")
def suspender(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    return {"mensaje": usuarios.suspender(usuario.usuario_id, usuario_id)}


@router.post("/usuarios/{usuario_id}/reactivar")
def reactivar(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    return {"mensaje": usuarios.reactivar(usuario_id)}


@router.delete("/usuarios/{usuario_id}")
def eliminar(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    return {"mensaje": usuarios.eliminar(usuario.usuario_id, usuario_id)}
