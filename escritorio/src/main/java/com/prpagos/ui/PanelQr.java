package com.prpagos.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Map;

import javax.swing.JComponent;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * Código QR para configurar el autenticador (.qr-caja de la web): fondo
 * blanco también en tema oscuro, porque las cámaras leen mejor así.
 */
public class PanelQr extends JComponent {

    private static final int LADO = 220;
    private BitMatrix matriz;

    public PanelQr() {
        setPreferredSize(new Dimension(LADO, LADO));
        setMaximumSize(new Dimension(LADO, LADO));
        setMinimumSize(new Dimension(LADO, LADO));
    }

    public void mostrar(String texto) {
        try {
            matriz = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 2));
        } catch (WriterException e) {
            matriz = null;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, LADO - 1, LADO - 1, 24, 24);
        g2.setColor(Tema.c().borde());
        g2.drawRoundRect(0, 0, LADO - 1, LADO - 1, 24, 24);
        if (matriz != null) {
            int modulos = matriz.getWidth();
            int tamano = (LADO - 16) / modulos;
            int inicio = (LADO - tamano * modulos) / 2;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(Color.BLACK);
            for (int y = 0; y < modulos; y++) {
                for (int x = 0; x < modulos; x++) {
                    if (matriz.get(x, y)) {
                        g2.fillRect(inicio + x * tamano, inicio + y * tamano, tamano, tamano);
                    }
                }
            }
        }
        g2.dispose();
    }
}
