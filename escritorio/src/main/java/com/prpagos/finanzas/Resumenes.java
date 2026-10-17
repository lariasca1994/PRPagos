package com.prpagos.finanzas;

import java.sql.SQLException;

import com.prpagos.conexion.ConexionBD;
import com.prpagos.datos.RepositorioFinanzas;

/**
 * "Enviarme el resumen ahora" desde el escritorio: el mismo contenido que la
 * web (web/app/resumenes.py) con las mismas consultas y la misma plantilla.
 * Los resúmenes programados (viernes y día 29) los envía el servidor, para
 * que lleguen aunque el equipo esté apagado y sin duplicarse.
 */
public final class Resumenes {

    /** Mismo enlace "Ver mi dashboard" que usa la web (URL_PUBLICA). */
    private static final String URL_PUBLICA_POR_DEFECTO = "https://prpagos-web-1087929107584.southamerica-east1.run.app";

    private Resumenes() {
    }

    public static Finanzas.DatosResumen datos(long usuarioId, String nombre, Finanzas.Periodo p) throws SQLException {
        RepositorioFinanzas.Totales t = RepositorioFinanzas.totales(usuarioId, p.desde(), p.hasta());
        return new Finanzas.DatosResumen(nombre, p, t.ingresos(), t.gastos(), t.movimientos(),
                RepositorioFinanzas.gastosPorCategoria(usuarioId, p.desde(), p.hasta()),
                RepositorioFinanzas.plan(usuarioId),
                RepositorioFinanzas.gastosPorGrupo(usuarioId, p.desde(), p.hasta()));
    }

    public static void enviar(long usuarioId, String correo, String nombre, Finanzas.Periodo p)
            throws SQLException, Correo.ErrorCorreo {
        String html = Finanzas.htmlResumen(datos(usuarioId, nombre, p), urlPublica());
        Correo.enviar(correo, nombre, Finanzas.asuntoResumen(p), html);
    }

    private static String urlPublica() {
        try {
            String valor = ConexionBD.propiedad("url.publica");
            return valor == null || valor.isBlank() ? URL_PUBLICA_POR_DEFECTO : valor.trim();
        } catch (java.io.IOException e) {
            return URL_PUBLICA_POR_DEFECTO;
        }
    }
}
