package com.prpagos.seguridad;

import java.sql.SQLException;

import com.prpagos.datos.Credenciales;
import com.prpagos.datos.RepositorioFinanzas;
import com.prpagos.datos.RepositorioUsuarios;

/**
 * Segundo factor común al ingreso, la recuperación y el perfil: acepta el
 * código de 6 dígitos de la app autenticadora o uno de los códigos de
 * respaldo (ABCD-EFGH). Cada fallo cuenta para el bloqueo de 5 minutos.
 * Gemelo de web/app/routers/verificacion.py.
 */
public final class Verificacion {

    private Verificacion() {
    }

    public static boolean segundoFactor(Credenciales cred, String codigo, String motivoExito) throws SQLException {
        boolean ok;
        // 8 caracteres = código de respaldo; 6 dígitos = código de la app.
        String respaldo = Recuperacion.normalizarRespaldo(codigo);
        if (respaldo != null) {
            ok = RepositorioFinanzas.usarCodigoRespaldo(cred.usuarioId(), Recuperacion.hashCorto(respaldo));
            if (ok) {
                RepositorioFinanzas.reiniciarIntentos(cred.usuarioId());
            }
        } else {
            String secreto = CifradoTotp.descifrarONull(cred.totpSecreto());
            Long paso = Totp.verificar(secreto, codigo, cred.totpUltimoPaso());
            ok = paso != null && RepositorioUsuarios.registrarExito(cred.usuarioId(), paso, motivoExito);
        }
        if (!ok) {
            RepositorioUsuarios.registrarFallo(cred.usuarioId(), "FALLO_TOTP");
        }
        return ok;
    }
}
