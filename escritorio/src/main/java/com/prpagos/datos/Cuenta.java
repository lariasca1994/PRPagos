package com.prpagos.datos;

/** Fila del panel de administración de usuarios. */
public record Cuenta(long id, String usuario, String nombre, String rol, boolean activo) {

    public boolean esAdmin() {
        return "admin".equalsIgnoreCase(rol);
    }
}
