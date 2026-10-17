package com.prpagos.datos;

/** Datos de acceso de una cuenta activa (procedimiento obtener_credenciales). */
public record Credenciales(
        long usuarioId,
        String correo,
        String nombre,
        String password,
        String totpSecreto,
        boolean totpActivo,
        long totpUltimoPaso,
        boolean bloqueada) {
}
