package com.prpagos.datos;

/**
 * Usuario con el ingreso ya verificado (contraseña + código TOTP). El rol se
 * vuelve a consultar al cambiar de pantalla (ver VentanaPrincipal), como la
 * web lo hace en cada petición: una suspensión o un cambio de rol tienen
 * efecto inmediato.
 */
public record Sesion(long usuarioId, String correo, String nombre, boolean admin) {

    public static Sesion de(Credenciales c, boolean admin) {
        return new Sesion(c.usuarioId(), c.correo(), c.nombre(), admin);
    }

    public Sesion conAdmin(boolean esAdmin) {
        return new Sesion(usuarioId, correo, nombre, esAdmin);
    }
}
