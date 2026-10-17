package com.prpagos.datos;

import java.time.LocalDate;

/**
 * Un pago. duenoCorreo y duenoNombre solo vienen llenos en la consulta del
 * administrador (pagos de todos los usuarios), igual que en la web.
 */
public record Pago(long idPago, double monto, LocalDate fecha, String concepto,
                   String duenoCorreo, String duenoNombre) {

    public Pago(long idPago, double monto, LocalDate fecha, String concepto) {
        this(idPago, monto, fecha, concepto, null, null);
    }
}
