package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.prpagos.datos.Credenciales;
import com.prpagos.datos.RepositorioUsuarios;
import com.prpagos.datos.Sesion;
import com.prpagos.seguridad.CifradoTotp;
import com.prpagos.seguridad.Seguridad;
import com.prpagos.seguridad.Totp;

/**
 * "Seguridad": misma pantalla que seguridad.html de la web. Estado de la
 * verificación en dos pasos, cambiar de autenticador y cambiar contraseña;
 * las dos acciones piden un código vigente del autenticador.
 */
class PanelSeguridad extends JPanel {

    private final VentanaPrincipal ventana;
    private final CardLayout vistas = new CardLayout();
    private final Ui.Aviso aviso = new Ui.Aviso();
    private final JLabel estado = new JLabel();
    private final JTextField codigoCambio = Ui.campoCodigo();
    private final JPasswordField actual = Ui.clave();
    private final JPasswordField nueva = Ui.clave();
    private final JPasswordField confirmacion = Ui.clave();
    private final JTextField codigoClave = Ui.campoCodigo();
    // Nuevo autenticador
    private final PanelQr qr = new PanelQr();
    private final JLabel claveManual = new JLabel();
    private final JTextField codigoNuevo = Ui.campoCodigo();
    private final Ui.Aviso avisoNuevo = new Ui.Aviso();
    private String secretoNuevo;
    private boolean totpActivo;

    PanelSeguridad(VentanaPrincipal ventana) {
        this.ventana = ventana;
        setOpaque(false);
        setLayout(vistas);
        add(vistaPrincipal(), "principal");
        add(vistaNuevoAutenticador(), "nuevo");
        Tema.alCambiar(this::pintarEstado);
    }

    private JPanel vistaPrincipal() {
        JPanel p = Ui.columna();
        p.add(Ui.titulo("Seguridad", 26f));
        p.add(Ui.espacio(18));
        p.add(aviso);
        p.add(Ui.espacio(6));

        JPanel rejilla = new JPanel(new GridLayout(1, 2, 16, 0));
        rejilla.setOpaque(false);
        rejilla.setAlignmentX(LEFT_ALIGNMENT);

        JButton guardar = Ui.primario("Guardar contraseña", e -> cambiarContrasena());
        guardar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JButton cambiar = Ui.secundario("Cambiar autenticador", e -> iniciarCambio());
        cambiar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JPanel totp = Ui.tarjeta();
        estado.setFont(estado.getFont().deriveFont(Font.BOLD, 12.5f));
        estado.setOpaque(true);
        estado.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
        JTextArea ayudaTotp = Ui.suave("Cada ingreso pide, además de la contraseña, el código de 6 dígitos de "
                + "tu app autenticadora. Es el mismo en la web y en el escritorio.");
        ayudaTotp.setFont(ayudaTotp.getFont().deriveFont(12.5f));
        JTextArea paraCambiar = Ui.suave("Para cambiar de teléfono o de app, escribe un código del "
                + "autenticador actual");
        paraCambiar.setFont(paraCambiar.getFont().deriveFont(12.5f));
        agregar(totp, Ui.titulo("Verificación en dos pasos", 17f), Ui.espacio(10), estado, Ui.espacio(10),
                ayudaTotp, Ui.espacio(12), paraCambiar, Ui.espacio(6), codigoCambio, Ui.espacio(16),
                cambiar);

        JPanel clave = Ui.tarjeta();
        agregar(clave, Ui.titulo("Cambiar contraseña", 17f),
                Ui.etiqueta("Contraseña actual"), actual,
                Ui.etiqueta("Nueva contraseña (mínimo 8 caracteres)"), nueva,
                Ui.etiqueta("Repite la nueva contraseña"), confirmacion,
                Ui.etiqueta("Código del autenticador"), codigoClave,
                Ui.espacio(18), guardar);

        rejilla.add(arriba(totp));
        rejilla.add(arriba(clave));
        p.add(rejilla);
        p.add(Ui.espacio(16));
        JTextArea nota = Ui.suave("Las contraseñas se guardan con PBKDF2-SHA256 y la clave de tu autenticador, "
                + "cifrada con AES-256-GCM. Tras 5 intentos fallidos seguidos la cuenta se bloquea 5 minutos.");
        nota.setFont(nota.getFont().deriveFont(12f));
        p.add(nota);
        return p;
    }

    private JPanel vistaNuevoAutenticador() {
        JPanel tarjeta = Ui.tarjeta();
        tarjeta.setBorder(BorderFactory.createEmptyBorder(30, 32, 30, 32));
        JTextArea pasos = Ui.suave("1. Escanea este código QR con la app nueva, o escribe la clave a mano.\n"
                + "2. Escribe el código de 6 dígitos que muestra.");
        pasos.setFont(pasos.getFont().deriveFont(13f));
        JPanel cajaQr = new JPanel(new GridBagLayout());
        cajaQr.setOpaque(false);
        cajaQr.add(qr);
        cajaQr.setAlignmentX(LEFT_ALIGNMENT);
        JPanel filaClave = new JPanel(new GridBagLayout());
        filaClave.setOpaque(false);
        filaClave.add(claveManual);
        filaClave.setAlignmentX(LEFT_ALIGNMENT);
        JButton confirmar = Ui.primario("Confirmar autenticador", e -> confirmarNuevo());
        confirmar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        codigoNuevo.addActionListener(e -> confirmarNuevo());
        JTextArea nota = Ui.suave("Hasta que confirmes, el autenticador anterior sigue siendo el válido.");
        nota.setFont(nota.getFont().deriveFont(12f));
        agregar(tarjeta, Ui.titulo("Nuevo autenticador", 26f), Ui.espacio(10), avisoNuevo, pasos,
                Ui.espacio(14), cajaQr, Ui.espacio(6), filaClave,
                Ui.etiqueta("Código de 6 dígitos"), codigoNuevo, Ui.espacio(20), confirmar,
                Ui.espacio(12), nota, Ui.espacio(8),
                Ui.enlace("Cancelar", e -> {
                    secretoNuevo = null;
                    vistas.show(this, "principal");
                }));
        JPanel centro = new JPanel(new BorderLayout());
        centro.setOpaque(false);
        JPanel envoltura = new JPanel(new GridBagLayout());
        envoltura.setOpaque(false);
        tarjeta.setPreferredSize(new Dimension(440, tarjeta.getPreferredSize().height));
        envoltura.add(tarjeta);
        centro.add(envoltura, BorderLayout.NORTH);
        return centro;
    }

    private static void agregar(JPanel destino, Component... partes) {
        for (Component c : partes) {
            if (c instanceof javax.swing.JComponent j) {
                j.setAlignmentX(LEFT_ALIGNMENT);
            }
            destino.add(c);
        }
    }

    private static JPanel arriba(JPanel tarjeta) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(tarjeta, BorderLayout.NORTH);
        return p;
    }

    private Sesion sesion() {
        return ventana.sesion();
    }

    void recargar() {
        aviso.ocultar();
        secretoNuevo = null;
        vistas.show(this, "principal");
        Tarea.ejecutar(this, () -> RepositorioUsuarios.porId(sesion().usuarioId()), cred -> {
            totpActivo = cred != null && cred.totpActivo();
            pintarEstado();
        }, e -> aviso.error(Ui.mensajeError("leer el estado de la cuenta").apply(e)));
    }

    /** La pastilla "Activa" / "Sin configurar" (.estado-totp). */
    private void pintarEstado() {
        Tema.Paleta p = Tema.c();
        estado.setText(totpActivo ? "Activa" : "Sin configurar");
        estado.setBackground(totpActivo ? p.exitoFondo() : p.errorFondo());
        estado.setForeground(totpActivo ? p.exitoTexto() : p.acento());
    }

    /** Verifica un código del autenticador ACTUAL y lo marca como usado (igual que la web). */
    private static String verificarCodigoVigente(Credenciales cred, String codigo) throws Exception {
        Long paso = Totp.verificar(CifradoTotp.descifrarONull(cred.totpSecreto()), codigo, cred.totpUltimoPaso());
        if (paso == null || !RepositorioUsuarios.registrarExito(cred.usuarioId(), paso, "VERIFICA_TOTP")) {
            RepositorioUsuarios.registrarFallo(cred.usuarioId(), "FALLO_TOTP");
            return "Código del autenticador incorrecto o ya usado";
        }
        return null;
    }

    private void iniciarCambio() {
        String codigo = codigoCambio.getText();
        Tarea.ejecutar(this, () -> {
            Credenciales cred = RepositorioUsuarios.porId(sesion().usuarioId());
            if (cred == null || cred.bloqueada()) {
                return "Cuenta bloqueada temporalmente; espera 5 minutos";
            }
            return verificarCodigoVigente(cred, codigo);
        }, error -> {
            codigoCambio.setText("");
            if (error != null) {
                aviso.error(error);
                return;
            }
            secretoNuevo = Totp.generarSecreto();
            qr.mostrar(Totp.uri(secretoNuevo, sesion().correo()));
            claveManual.setText("Clave:  " + Totp.legible(secretoNuevo));
            codigoNuevo.setText("");
            avisoNuevo.ocultar();
            vistas.show(this, "nuevo");
            codigoNuevo.requestFocusInWindow();
        }, e -> aviso.error(Ui.mensajeError("verificar el código").apply(e)));
    }

    private void confirmarNuevo() {
        if (secretoNuevo == null) {
            vistas.show(this, "principal");
            return;
        }
        String codigo = codigoNuevo.getText();
        String secreto = secretoNuevo;
        Tarea.ejecutar(this, () -> {
            Long paso = Totp.verificar(secreto, codigo, 0);
            if (paso == null) {
                RepositorioUsuarios.registrarFallo(sesion().usuarioId(), "FALLO_TOTP");
                return "El código no coincide con el nuevo autenticador";
            }
            RepositorioUsuarios.guardarTotp(sesion().usuarioId(), CifradoTotp.cifrar(secreto), paso);
            return null;
        }, error -> {
            if (error != null) {
                codigoNuevo.setText("");
                avisoNuevo.error(error);
                return;
            }
            recargar();
            aviso.exito("Autenticador actualizado");
        }, e -> avisoNuevo.error(Ui.mensajeError("guardar el autenticador").apply(e)));
    }

    private void cambiarContrasena() {
        String claveActual = new String(actual.getPassword());
        String claveNueva = new String(nueva.getPassword());
        String claveConfirmacion = new String(confirmacion.getPassword());
        String codigo = codigoClave.getText();
        Tarea.ejecutar(this, () -> {
            Credenciales cred = RepositorioUsuarios.porId(sesion().usuarioId());
            if (cred == null || cred.bloqueada()) {
                return "Cuenta bloqueada temporalmente; espera 5 minutos";
            }
            if (!Seguridad.verificarPassword(claveActual, cred.password())) {
                RepositorioUsuarios.registrarFallo(cred.usuarioId(), "FALLO_CLAVE");
                return "La contraseña actual no es correcta";
            }
            String problema = Seguridad.problemaContrasena(claveNueva);
            if (problema != null) {
                return problema;
            }
            if (!claveNueva.equals(claveConfirmacion)) {
                return "Las contraseñas nuevas no coinciden";
            }
            String error = verificarCodigoVigente(cred, codigo);
            if (error != null) {
                return error;
            }
            RepositorioUsuarios.cambiarPassword(cred.usuarioId(), Seguridad.hashPassword(claveNueva), "CAMBIO_CLAVE");
            return null;
        }, error -> {
            codigoClave.setText("");
            if (error != null) {
                aviso.error(error);
                return;
            }
            actual.setText("");
            nueva.setText("");
            confirmacion.setText("");
            aviso.exito("Contraseña actualizada");
        }, e -> aviso.error(Ui.mensajeError("cambiar la contraseña").apply(e)));
    }
}
