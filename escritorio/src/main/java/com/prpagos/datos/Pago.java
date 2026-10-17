package com.prpagos.datos;

import java.time.LocalDate;

public record Pago(long idPago, double monto, LocalDate fecha, String concepto) {
}
