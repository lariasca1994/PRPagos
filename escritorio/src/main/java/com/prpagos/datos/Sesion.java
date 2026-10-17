package com.prpagos.datos;

/** Usuario con el ingreso ya verificado (contraseña + código TOTP). */
public record Sesion(long usuarioId, String correo, String nombre) {

    public static Sesion de(Credenciales c) {
        return new Sesion(c.usuarioId(), c.correo(), c.nombre());
    }
}
