package com.prpagos.datos;

import java.time.LocalDate;

/**
 * Un registro (ingreso o gasto). duenoCorreo y duenoNombre solo vienen llenos
 * en la consulta del administrador (registros de todos), igual que en la web.
 */
public record Pago(long idPago, String tipo, double monto, LocalDate fecha, String concepto,
                   Long categoriaId, String categoria, String duenoCorreo, String duenoNombre) {

    public boolean esIngreso() {
        return "INGRESO".equals(tipo);
    }

    public String tipoTexto() {
        return esIngreso() ? "Ingreso" : "Gasto";
    }
}
