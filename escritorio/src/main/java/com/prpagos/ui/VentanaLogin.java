package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.prpagos.datos.Credenciales;
import com.prpagos.datos.RepositorioUsuarios;
import com.prpagos.datos.Sesion;
import com.prpagos.seguridad.CifradoTotp;
import com.prpagos.seguridad.Seguridad;
import com.prpagos.seguridad.Recuperacion;
import com.prpagos.seguridad.Totp;
import com.prpagos.seguridad.Verificacion;
import com.prpagos.datos.RepositorioFinanzas;
import com.prpagos.finanzas.Correo;
import com.prpagos.finanzas.Finanzas;
import java.util.ArrayList;
import java.util.List;

/**
 * Portada de acceso: ingresar (contraseña y luego código), configurar el
 * autenticador, crear cuenta y recuperar contraseña. Misma estructura,
 * textos y reglas que la web (web/app/routers/auth.py y acceso_base.html):
 * formulario a la izquierda y panel de marca a la derecha.
 *
 * La verificación en dos pasos es obligatoria y compartida con la web: el
 * autenticador que se configure aquí sirve allá, y al revés.
 */
public class VentanaLogin extends JFrame {

    private static final String MENSAJE_BLOQUEO =
            "Demasiados intentos fallidos. Espera 5 minutos e inténtalo de nuevo.";
    private static final String HASH_RELLENO = Seguridad.hashPassword("relleno-para-igualar-tiempos");

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel formularios = new JPanel(tarjetas);

    // Ingreso
    private final JTextField correo = Ui.campo("nombre@dominio.com");
    private final JPasswordField contrasena = Ui.clave();
    private final Ui.Aviso avisoIngreso = new Ui.Aviso();
    // Código
    private final JTextField codigo = Ui.campoCodigo();
    private final JTextArea subtituloCodigo = Ui.suave("");
    private final Ui.Aviso avisoCodigo = new Ui.Aviso();
    // Configurar autenticador
    private final PanelQr qr = new PanelQr();
    private final JLabel claveManual = new JLabel();
    private final JTextField codigoConfig = Ui.campoCodigo();
    private final Ui.Aviso avisoConfig = new Ui.Aviso();
    // Registro
    private final JTextField regCorreo = Ui.campo("nombre@dominio.com");
    private final JTextField regNombre = Ui.campo(null);
    private final JPasswordField regClave = Ui.clave();
    private final JPasswordField regConfirmacion = Ui.clave();
    private final Ui.Aviso avisoRegistro = new Ui.Aviso();
    // Recuperación
    private final JTextField recCorreo = Ui.campo("nombre@dominio.com");
    private final JTextField recNombre = Ui.campo(null);
    private final JTextField recCodigo = Ui.campoCodigo();
    private final JPasswordField recClave = Ui.clave();
    private final JPasswordField recConfirmacion = Ui.clave();
    private final Ui.Aviso avisoRecuperar = new Ui.Aviso();
    // Perdí mi teléfono (código por correo)
    private final JTextField codigoCorreo = Ui.campoCodigo();
    private final JTextArea subtituloCorreo = Ui.suave("");
    private final Ui.Aviso avisoCorreo = new Ui.Aviso();
    // Códigos de respaldo al activar el autenticador
    private final JPanel rejillaRespaldo = new JPanel(new java.awt.GridLayout(0, 2, 24, 6));

    /** Contraseña ya verificada; falta el código o configurar el autenticador. */
    private Credenciales pendiente;
    private String secretoPendiente;
    private boolean pendienteAdmin;

    /** Portada con el mensaje de cierre de sesión (los mismos textos que la web). */
    public VentanaLogin(String mensaje, boolean exito) {
        this();
        if (exito) {
            avisoIngreso.exito(mensaje);
        } else {
            avisoIngreso.error(mensaje);
        }
    }

    public VentanaLogin() {
        super("PRPagos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(900, 640));
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        formularios.setOpaque(false);
        formularios.add(formIngreso(), "ingreso");
        formularios.add(formCodigo(), "codigo");
        formularios.add(formConfigurar(), "configurar");
        formularios.add(formRegistro(), "registro");
        formularios.add(formRecuperar(), "recuperar");
        formularios.add(formCorreo(), "correo");
        formularios.add(formRespaldo(), "respaldo");

        JPanel lado = new JPanel(new BorderLayout());
        lado.setBorder(BorderFactory.createEmptyBorder(26, 48, 22, 48));
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);
        JPanel marca = Ui.fila(12);
        marca.add(Ui.marca());
        marca.add(Ui.botonTema());
        barra.add(marca, BorderLayout.WEST);
        JPanel centro = new JPanel(new GridBagLayout());
        centro.setOpaque(false);
        centro.add(formularios);
        JTextArea pie = Ui.suave("PRPagos — proyecto de portafolio de Luis Felipe Arias.");
        pie.setFont(pie.getFont().deriveFont(12f));
        lado.add(barra, BorderLayout.NORTH);
        lado.add(centro, BorderLayout.CENTER);
        lado.add(pie, BorderLayout.SOUTH);
        Tema.alCambiar(() -> {
            lado.setBackground(Tema.c().accesoFondo());
            // En la portada los campos van sobre blanco (.formulario-acceso input).
            for (JTextField t : new JTextField[] {correo, contrasena, codigo, codigoConfig, regCorreo, regNombre,
                regClave, regConfirmacion, recCorreo, recNombre, recCodigo, recClave, recConfirmacion}) {
                t.setBackground(Tema.c().superficie());
            }
        });

        JPanel portada = new JPanel(new GridLayout(1, 2));
        portada.add(lado);
        portada.add(new PanelMarca());
        setContentPane(portada);
        getRootPane().setDefaultButton(null);
    }

    // ───────────────────────── Formularios ─────────────────────────

    private JPanel encabezado(String paso, String titulo, Component subtitulo) {
        JPanel p = Ui.columna();
        p.add(Ui.paso(paso));
        p.add(Ui.espacio(8));
        p.add(Ui.titulo(titulo, 27f));
        p.add(Ui.espacio(6));
        p.add(subtitulo);
        p.add(Ui.espacio(18));
        return p;
    }

    private JPanel formulario(JComponent... partes) {
        JPanel f = Ui.columna();
        for (JComponent parte : partes) {
            parte.setAlignmentX(Component.LEFT_ALIGNMENT);
            f.add(parte);
        }
        JPanel ancho = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(384, super.getPreferredSize().height);
            }
        };
        ancho.setOpaque(false);
        ancho.add(f, BorderLayout.NORTH);
        return ancho;
    }

    private JPanel enlaces(Component... partes) {
        JPanel fila = new JPanel(new BorderLayout());
        fila.setOpaque(false);
        fila.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        fila.add(partes[0], BorderLayout.WEST);
        if (partes.length > 1) {
            fila.add(partes[1], BorderLayout.EAST);
        }
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return fila;
    }

    private JButton botonAncho(JButton b) {
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }

    private JPanel conTexto(String texto, JButton enlace) {
        JPanel p = Ui.fila(4);
        JLabel l = new JLabel(texto);
        Tema.alCambiar(() -> l.setForeground(Tema.c().textoSuave()));
        p.add(l);
        p.add(enlace);
        return p;
    }

    private JPanel formIngreso() {
        JButton continuar = botonAncho(Ui.primario("Continuar", e -> ingresar()));
        contrasena.addActionListener(e -> ingresar());
        correo.addActionListener(e -> contrasena.requestFocusInWindow());
        return formulario(
                encabezado("Acceso · Paso 1 de 2", "Iniciar sesión", Ui.suave(
                        "Ingresa con tu correo y contraseña; después te pediremos el código de tu autenticador.")),
                avisoIngreso,
                Ui.etiqueta("Correo"), correo,
                Ui.etiqueta("Contraseña"), contrasena,
                (JComponent) Ui.espacio(20), continuar,
                enlaces(conTexto("¿No tienes cuenta?", Ui.enlace("Regístrate", e -> mostrar("registro"))),
                        Ui.enlace("Olvidé mi contraseña", e -> mostrar("recuperar"))));
    }

    private JPanel formCodigo() {
        JButton ingresar = botonAncho(Ui.primario("Ingresar", e -> verificarCodigo()));
        codigo.addActionListener(e -> verificarCodigo());
        return formulario(
                encabezado("Acceso · Paso 2 de 2", "Código de verificación", subtituloCodigo),
                avisoCodigo,
                Ui.etiqueta("Código de 6 dígitos (o un código de respaldo)"), codigo,
                (JComponent) Ui.espacio(20), ingresar,
                enlaces(Ui.enlace("Usar otra cuenta", e -> reiniciar(null)),
                        Ui.enlace("Perdí mi teléfono", e -> perdiTelefono())));
    }

    private JPanel formCorreo() {
        JButton verificar = botonAncho(Ui.primario("Verificar y configurar autenticador", e -> verificarCodigoCorreo()));
        codigoCorreo.addActionListener(e -> verificarCodigoCorreo());
        return formulario(
                encabezado("Perdí mi teléfono", "Revisa tu correo", subtituloCorreo),
                avisoCorreo,
                Ui.etiqueta("Código del correo"), codigoCorreo,
                (JComponent) Ui.espacio(20), verificar,
                enlaces(Ui.enlace("Usar otra cuenta", e -> reiniciar(null))));
    }

    private JPanel formRespaldo() {
        rejillaRespaldo.setOpaque(false);
        JTextArea texto = Ui.suave(PanelPerfil.TEXTO_RESPALDO);
        texto.setFont(texto.getFont().deriveFont(12.5f));
        return formulario(
                encabezado("Verificación activada", "Guarda tus códigos de respaldo", Ui.suave(
                        "Si pierdes el teléfono, con uno de estos códigos podrás ingresar y configurar un "
                        + "autenticador nuevo.")),
                rejillaRespaldo, (JComponent) Ui.espacio(12), texto, (JComponent) Ui.espacio(20),
                botonAncho(Ui.primario("Ya los guardé, continuar", e -> abrirPrincipal())));
    }

    private JPanel formConfigurar() {
        JButton activar = botonAncho(Ui.primario("Activar e ingresar", e -> activarAutenticador()));
        codigoConfig.addActionListener(e -> activarAutenticador());
        JTextArea pasos = Ui.suave(
                "1. Instala una app autenticadora (Google Authenticator, Microsoft Authenticator, Authy…).\n"
                + "2. Escanea este código QR, o escribe la clave a mano.\n"
                + "3. Escribe el código de 6 dígitos que muestra la app.");
        pasos.setFont(pasos.getFont().deriveFont(13f));
        JPanel cajaQr = new JPanel(new GridBagLayout());
        cajaQr.setOpaque(false);
        cajaQr.add(qr);
        claveManual.setFont(claveManual.getFont().deriveFont(12.5f));
        JPanel filaClave = new JPanel(new GridBagLayout());
        filaClave.setOpaque(false);
        filaClave.add(claveManual);
        return formulario(
                encabezado("Acceso · Paso 2 de 2", "Verificación en dos pasos", Ui.suave(
                        "Es obligatoria para todas las cuentas. Configúrala una sola vez y sirve en la "
                        + "versión de escritorio y en la web.")),
                avisoConfig, pasos, (JComponent) Ui.espacio(12), cajaQr, (JComponent) Ui.espacio(6),
                filaClave,
                Ui.etiqueta("Código de 6 dígitos"), codigoConfig,
                (JComponent) Ui.espacio(20), activar,
                enlaces(Ui.enlace("Usar otra cuenta", e -> reiniciar(null))));
    }

    private JPanel formRegistro() {
        JButton crear = botonAncho(Ui.primario("Crear cuenta", e -> registrar()));
        return formulario(
                encabezado("Cuenta nueva", "Crear cuenta", Ui.suave(
                        "Usa un correo real: ahí te llegarán las notificaciones. Solo verás tus propios pagos.")),
                avisoRegistro,
                Ui.etiqueta("Correo"), regCorreo,
                Ui.etiqueta("Nombre completo"), regNombre,
                Ui.etiqueta("Contraseña (mínimo 8 caracteres)"), regClave,
                Ui.etiqueta("Repite la contraseña"), regConfirmacion,
                (JComponent) Ui.espacio(20), crear,
                enlaces(conTexto("¿Ya tienes cuenta?", Ui.enlace("Ingresa", e -> reiniciar(null)))));
    }

    private JPanel formRecuperar() {
        JButton actualizar = botonAncho(Ui.primario("Actualizar contraseña", e -> recuperar()));
        JTextArea ayuda = Ui.suave("Si todavía no configuraste tu autenticador, déjalo vacío.");
        ayuda.setFont(ayuda.getFont().deriveFont(12f));
        return formulario(
                encabezado("Recuperar acceso", "Recuperar contraseña", Ui.suave(
                        "Confirma tu correo, el nombre con el que te registraste y un código de tu "
                        + "autenticador (o de respaldo) para definir una contraseña nueva.")),
                avisoRecuperar,
                Ui.etiqueta("Correo"), recCorreo,
                Ui.etiqueta("Nombre registrado"), recNombre,
                Ui.etiqueta("Código del autenticador o de respaldo"), recCodigo, ayuda,
                Ui.etiqueta("Nueva contraseña (mínimo 8 caracteres)"), recClave,
                Ui.etiqueta("Repite la nueva contraseña"), recConfirmacion,
                (JComponent) Ui.espacio(20), actualizar,
                enlaces(Ui.enlace("Volver a ingresar", e -> reiniciar(null))));
    }

    private void mostrar(String tarjeta) {
        for (Ui.Aviso a : new Ui.Aviso[] {avisoIngreso, avisoCodigo, avisoConfig, avisoRegistro, avisoRecuperar,
            avisoCorreo}) {
            a.ocultar();
        }
        tarjetas.show(formularios, tarjeta);
    }

    /** Vuelve al paso 1 olvidando el ingreso a medio camino (como "Usar otra cuenta"). */
    private void reiniciar(String mensajeExito) {
        pendiente = null;
        secretoPendiente = null;
        contrasena.setText("");
        codigo.setText("");
        codigoConfig.setText("");
        mostrar("ingreso");
        if (mensajeExito != null) {
            avisoIngreso.exito(mensajeExito);
        }
        correo.requestFocusInWindow();
    }

    // ───────────────────────── Paso 1: contraseña ─────────────────────────

    private enum Siguiente { ERROR, BLOQUEADA, CODIGO, CONFIGURAR }

    private void ingresar() {
        String email = correo.getText().trim();
        String clave = new String(contrasena.getPassword());
        Tarea.ejecutar(this, () -> {
            Credenciales cred = RepositorioUsuarios.porCorreo(email);
            if (cred == null) {
                Seguridad.verificarPassword(clave, HASH_RELLENO);
                return Siguiente.ERROR;
            }
            if (cred.bloqueada()) {
                return Siguiente.BLOQUEADA;
            }
            if (!Seguridad.verificarPassword(clave, cred.password())) {
                RepositorioUsuarios.registrarFallo(cred.usuarioId(), "FALLO_CLAVE");
                return Siguiente.ERROR;
            }
            if (Seguridad.necesitaRehash(cred.password())) {
                RepositorioUsuarios.cambiarPassword(cred.usuarioId(), Seguridad.hashPassword(clave), "MIGRAR_HASH");
            }
            pendiente = cred;
            if (cred.totpActivo() && cred.totpSecreto() != null) {
                return Siguiente.CODIGO;
            }
            secretoPendiente = Totp.generarSecreto();
            return Siguiente.CONFIGURAR;
        }, siguiente -> {
            switch (siguiente) {
                case ERROR -> avisoIngreso.error("Usuario o contraseña incorrectos");
                case BLOQUEADA -> avisoIngreso.error(MENSAJE_BLOQUEO);
                case CODIGO -> {
                    subtituloCodigo.setText("Abre tu app autenticadora y escribe el código de 6 dígitos de "
                            + "PRPagos (" + pendiente.correo() + ").");
                    mostrar("codigo");
                    codigo.requestFocusInWindow();
                }
                case CONFIGURAR -> {
                    qr.mostrar(Totp.uri(secretoPendiente, pendiente.correo()));
                    claveManual.setText("Clave:  " + Totp.legible(secretoPendiente));
                    mostrar("configurar");
                    codigoConfig.requestFocusInWindow();
                }
                default -> { }
            }
        }, e -> avisoIngreso.error(Ui.mensajeError("conectar con la base de datos").apply(e)));
    }

    // ───────────────────── Paso 2a: código del autenticador ─────────────────────

    private void verificarCodigo() {
        if (pendiente == null) {
            reiniciar(null);
            return;
        }
        String texto = codigo.getText();
        long id = pendiente.usuarioId();
        Tarea.ejecutar(this, () -> {
            Credenciales cred = RepositorioUsuarios.porId(id);
            if (cred == null) {
                return "Usuario o contraseña incorrectos";
            }
            if (cred.bloqueada()) {
                return MENSAJE_BLOQUEO;
            }
            // Código de la app o de respaldo (igual que la web).
            if (!Verificacion.segundoFactor(cred, texto, "INGRESO")) {
                return "Código incorrecto o ya usado";
            }
            pendiente = cred;
            pendienteAdmin = esAdmin(id);
            return null;
        }, error -> {
            if (error != null) {
                codigo.setText("");
                avisoCodigo.error(error);
            } else {
                abrirPrincipal();
            }
        }, e -> avisoCodigo.error(Ui.mensajeError("verificar el código").apply(e)));
    }

    // ─────────────────── Paso 2b: configurar el autenticador ───────────────────

    private void activarAutenticador() {
        if (pendiente == null || secretoPendiente == null) {
            reiniciar(null);
            return;
        }
        String texto = codigoConfig.getText();
        long id = pendiente.usuarioId();
        String secreto = secretoPendiente;
        Tarea.ejecutar(this, () -> {
            Credenciales cred = RepositorioUsuarios.porId(id);
            if (cred == null) {
                return "Usuario o contraseña incorrectos";
            }
            if (cred.bloqueada()) {
                return MENSAJE_BLOQUEO;
            }
            Long paso = Totp.verificar(secreto, texto, 0);
            if (paso == null) {
                RepositorioUsuarios.registrarFallo(id, "FALLO_TOTP");
                return "El código no coincide. Revisa que la hora del teléfono sea la correcta.";
            }
            RepositorioUsuarios.guardarTotp(id, CifradoTotp.cifrar(secreto), paso);
            pendienteAdmin = esAdmin(id);
            // Con el autenticador se entregan los 10 códigos de respaldo (se muestran una sola vez).
            List<String> codigos = Recuperacion.generarCodigosRespaldo();
            List<String> hashes = new ArrayList<>();
            for (String c : codigos) {
                hashes.add(Recuperacion.hashCorto(c.replace("-", "")));
            }
            RepositorioFinanzas.reemplazarCodigosRespaldo(id, hashes);
            codigosNuevos = codigos;
            return null;
        }, error -> {
            if (error != null) {
                codigoConfig.setText("");
                avisoConfig.error(error);
            } else {
                PanelPerfil.mostrarCodigos(rejillaRespaldo, codigosNuevos);
                mostrar("respaldo");
            }
        }, e -> avisoConfig.error(Ui.mensajeError("guardar el autenticador").apply(e)));
    }

    private List<String> codigosNuevos = List.of();

    // ─────────────────── Perdí mi teléfono (código por correo) ───────────────────

    /** Solo después de la contraseña: envía un código de 6 dígitos al correo de la cuenta. */
    private void perdiTelefono() {
        if (pendiente == null) {
            reiniciar(null);
            return;
        }
        long id = pendiente.usuarioId();
        Tarea.ejecutar(this, () -> {
            Credenciales cred = RepositorioUsuarios.porId(id);
            if (cred == null || cred.bloqueada()) {
                return MENSAJE_BLOQUEO;
            }
            String codigoNuevo = Recuperacion.generarCodigoCorreo();
            RepositorioFinanzas.guardarCodigoCorreo(id, Recuperacion.hashCorto(codigoNuevo),
                    Recuperacion.MINUTOS_CODIGO_CORREO);
            try {
                Correo.enviar(cred.correo(), cred.nombre(), "Código para recuperar tu acceso a PRPagos",
                        Finanzas.htmlCodigo(cred.nombre(), codigoNuevo, Recuperacion.MINUTOS_CODIGO_CORREO));
            } catch (Correo.ErrorCorreo e) {
                return "No se pudo enviar el correo. Usa un código de respaldo o inténtalo más tarde.";
            }
            return null;
        }, error -> {
            if (error != null) {
                avisoCodigo.error(error);
                return;
            }
            subtituloCorreo.setText("Te enviamos un código de 6 dígitos a " + pendiente.correo() + ". Vence en "
                    + Recuperacion.MINUTOS_CODIGO_CORREO + " minutos.");
            codigoCorreo.setText("");
            mostrar("correo");
            codigoCorreo.requestFocusInWindow();
        }, e -> avisoCodigo.error(Ui.mensajeError("enviar el correo").apply(e)));
    }

    private void verificarCodigoCorreo() {
        if (pendiente == null) {
            reiniciar(null);
            return;
        }
        long id = pendiente.usuarioId();
        String limpio = codigoCorreo.getText().replaceAll("[^0-9]", "");
        Tarea.ejecutar(this, () -> {
            Credenciales cred = RepositorioUsuarios.porId(id);
            if (cred == null || cred.bloqueada()) {
                return MENSAJE_BLOQUEO;
            }
            if (limpio.length() != 6 || !RepositorioFinanzas.usarCodigoCorreo(id, Recuperacion.hashCorto(limpio))) {
                RepositorioUsuarios.registrarFallo(id, "FALLO_CORREO");
                return "Código incorrecto o vencido";
            }
            secretoPendiente = Totp.generarSecreto();
            return null;
        }, error -> {
            if (error != null) {
                codigoCorreo.setText("");
                avisoCorreo.error(error);
                return;
            }
            qr.mostrar(Totp.uri(secretoPendiente, pendiente.correo()));
            claveManual.setText("Clave:  " + Totp.legible(secretoPendiente));
            codigoConfig.setText("");
            mostrar("configurar");
            avisoConfig.exito("Código verificado: configura tu autenticador nuevo.");
            codigoConfig.requestFocusInWindow();
        }, e -> avisoCorreo.error(Ui.mensajeError("verificar el código").apply(e)));
    }

    private static boolean esAdmin(long usuarioId) throws java.sql.SQLException {
        com.prpagos.datos.Cuenta cuenta = RepositorioUsuarios.estado(usuarioId);
        return cuenta != null && cuenta.esAdmin();
    }

    private void abrirPrincipal() {
        Sesion sesion = Sesion.de(pendiente, pendienteAdmin);
        pendiente = null;
        secretoPendiente = null;
        new VentanaPrincipal(sesion).setVisible(true);
        dispose();
    }

    // ───────────────────────── Registro ─────────────────────────

    private void registrar() {
        String email = regCorreo.getText().trim();
        String nombre = regNombre.getText().trim();
        String clave = new String(regClave.getPassword());
        String confirmacion = new String(regConfirmacion.getPassword());
        if (!Seguridad.correoValido(email)) {
            avisoRegistro.error("Escribe un correo válido (por ejemplo, nombre@dominio.com)");
            return;
        }
        if (nombre.isEmpty()) {
            avisoRegistro.error("Escribe tu nombre completo");
            return;
        }
        String problema = Seguridad.problemaContrasena(clave);
        if (problema != null) {
            avisoRegistro.error(problema);
            return;
        }
        if (!clave.equals(confirmacion)) {
            avisoRegistro.error("Las contraseñas no coinciden");
            return;
        }
        Tarea.ejecutar(this, () -> RepositorioUsuarios.crear(email, nombre, Seguridad.hashPassword(clave)),
                creado -> {
                    if (!creado) {
                        avisoRegistro.error("Ese correo ya tiene una cuenta");
                        return;
                    }
                    for (JTextField t : new JTextField[] {regCorreo, regNombre, regClave, regConfirmacion}) {
                        t.setText("");
                    }
                    correo.setText(email);
                    reiniciar("Cuenta creada. Al ingresar configurarás tu verificación en dos pasos.");
                },
                e -> avisoRegistro.error(Ui.mensajeError("crear la cuenta").apply(e)));
    }

    // ───────────────────────── Recuperación ─────────────────────────

    private void recuperar() {
        String email = recCorreo.getText().trim();
        String nombre = recNombre.getText().trim();
        String textoCodigo = recCodigo.getText();
        String clave = new String(recClave.getPassword());
        String confirmacion = new String(recConfirmacion.getPassword());
        String errorGenerico = "Los datos no coinciden con una cuenta";
        Tarea.ejecutar(this, () -> {
            Credenciales cred = Seguridad.correoValido(email) ? RepositorioUsuarios.porCorreo(email) : null;
            if (cred == null) {
                return errorGenerico;
            }
            if (cred.bloqueada()) {
                return MENSAJE_BLOQUEO;
            }
            if (cred.nombre() == null || !cred.nombre().trim().equalsIgnoreCase(nombre)) {
                RepositorioUsuarios.registrarFallo(cred.usuarioId(), "FALLO_RECUPERAR");
                return errorGenerico;
            }
            // Con autenticador configurado, sin un código suyo (o de respaldo) no se puede tomar la cuenta.
            if (cred.totpActivo() && !Verificacion.segundoFactor(cred, textoCodigo, "RECUPERAR_TOTP")) {
                return errorGenerico;
            }
            String problema = Seguridad.problemaContrasena(clave);
            if (problema != null) {
                return problema;
            }
            if (!clave.equals(confirmacion)) {
                return "Las contraseñas no coinciden";
            }
            RepositorioUsuarios.cambiarPassword(cred.usuarioId(), Seguridad.hashPassword(clave), "RECUPERAR_CLAVE");
            return null;
        }, error -> {
            if (error != null) {
                avisoRecuperar.error(error);
                return;
            }
            for (JTextField t : new JTextField[] {recNombre, recCodigo, recClave, recConfirmacion}) {
                t.setText("");
            }
            correo.setText(email);
            reiniciar("Contraseña actualizada, ya puedes ingresar");
        }, e -> avisoRecuperar.error(Ui.mensajeError("actualizar la contraseña").apply(e)));
    }

    public static void main(String[] args) {
        Tema.iniciar();
        SwingUtilities.invokeLater(() -> new VentanaLogin().setVisible(true));
    }

}
