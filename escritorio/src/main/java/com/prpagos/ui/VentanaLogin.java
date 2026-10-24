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

import com.google.gson.JsonObject;
import com.prpagos.cliente.Api;
import com.prpagos.cliente.Json;
import com.prpagos.datos.Sesion;
import com.prpagos.seguridad.WindowsHello;

/**
 * Portada de acceso: ingresar (contraseña y luego código), configurar el
 * autenticador, crear cuenta y recuperar contraseña. Misma estructura,
 * textos y reglas que la web (web/app/routers/auth.py y acceso_base.html):
 * formulario a la izquierda y panel de marca a la derecha.
 *
 * Toda la lógica está en el servidor, la misma de la web: cada paso llama a la
 * API y muestra su respuesta. La verificación en dos pasos es obligatoria y
 * compartida con la web: el método que se elija aquí (app autenticadora o
 * código por correo) sirve allá, y desde el perfil se agrega el otro.
 */
public class VentanaLogin extends JFrame {


    private final CardLayout tarjetas = new CardLayout();
    private final JPanel formularios = new JPanel(tarjetas);

    // Ingreso
    private final JTextField correo = Ui.campo("nombre@dominio.com");
    private final JPasswordField contrasena = Ui.clave();
    private final Ui.Aviso avisoIngreso = new Ui.Aviso();
    private final Ui.Aviso avisoLlave = new Ui.Aviso();
    // Código
    private final JTextField codigo = Ui.campoCodigo();
    private final JTextArea subtituloCodigo = Ui.suave("");
    private final Ui.Aviso avisoCodigo = new Ui.Aviso();
    private final JButton enlacePerdi = Ui.enlace("Perdí mi teléfono", e -> perdiTelefono());
    private final JButton enlaceCambiar = Ui.enlace("Usar un código por correo", e -> cambiarMetodo());
    // Primer ingreso: elegir el método de la verificación en dos pasos
    private final JTextArea subtituloElegir = Ui.suave("");
    private final Ui.Aviso avisoElegir = new Ui.Aviso();
    // Activar el código por correo (primer ingreso)
    private final JTextField codigoActivar = Ui.campoCodigo();
    private final JTextArea subtituloActivar = Ui.suave("");
    private final Ui.Aviso avisoActivar = new Ui.Aviso();
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

    /** Token del paso a medio camino (lo entrega la API y vence en 10 minutos). */
    private String tokenAcceso;
    /** Sesión abierta, lista para pasar a la ventana principal. */
    private Sesion sesionLista;

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
        formularios.add(formElegir(), "elegir");
        formularios.add(formActivarCorreo(), "activar");
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
                regClave, regConfirmacion, recCorreo, recNombre, recCodigo, recClave, recConfirmacion,
                codigoCorreo, codigoActivar}) {
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
        // En HTML con ancho fijo: los títulos largos pasan a la línea siguiente, como en la web.
        p.add(Ui.titulo("<html><body style='width:300px'>" + titulo + "</body></html>", 27f));
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
                separador(), avisoLlave, botonLlave(), ayudaLlave(),
                enlaces(conTexto("¿No tienes cuenta?", Ui.enlace("Regístrate", e -> mostrar("registro"))),
                        Ui.enlace("Olvidé mi contraseña", e -> mostrar("recuperar"))));
    }

    /** Línea con una "o" en el centro, como .separador-acceso de la web. */
    private JPanel separador() {
        JPanel p = new JPanel(new java.awt.GridBagLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 0, 4, 0));
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.fill = java.awt.GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        javax.swing.JSeparator izquierda = new javax.swing.JSeparator();
        javax.swing.JSeparator derecha = new javax.swing.JSeparator();
        JLabel o = new JLabel("o");
        o.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        Tema.alCambiar(() -> o.setForeground(Tema.c().textoSuave()));
        p.add(izquierda, g);
        g.weightx = 0;
        p.add(o, g);
        g.weightx = 1;
        p.add(derecha, g);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return p;
    }

    private JButton botonLlave() {
        JButton b = botonAncho(Ui.secundario("Ingresar con llave de acceso", e -> ingresarConLlave()));
        if (!WindowsHello.disponible()) {
            b.setEnabled(false);
            avisoLlave.error("Este equipo no tiene Windows Hello configurado.");
        }
        return b;
    }

    private JComponent ayudaLlave() {
        JLabel l = new JLabel("Windows Hello, huella, rostro o PIN del dispositivo", javax.swing.SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(12f));
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        l.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        Tema.alCambiar(() -> l.setForeground(Tema.c().textoSuave()));
        return l;
    }

    private JPanel formCodigo() {
        JButton ingresar = botonAncho(Ui.primario("Ingresar", e -> verificarCodigo()));
        codigo.addActionListener(e -> verificarCodigo());
        return formulario(
                encabezado("Acceso · Paso 2 de 2", "Código de verificación", subtituloCodigo),
                avisoCodigo,
                Ui.etiqueta("Código de 6 dígitos (o un código de respaldo)"), codigo,
                (JComponent) Ui.espacio(20), ingresar,
                enlaces(Ui.enlace("Usar otra cuenta", e -> reiniciar(null)), enlacesCodigo()));
    }

    /** "Perdí mi teléfono" (solo app) o "Usar el otro método" (con los dos activos), como en la web. */
    private JPanel enlacesCodigo() {
        JPanel p = Ui.fila(12);
        p.add(enlaceCambiar);
        p.add(enlacePerdi);
        return p;
    }

    private JPanel formElegir() {
        return formulario(
                encabezado("Acceso · Paso 2 de 2", "Verificación en dos pasos", subtituloElegir),
                avisoElegir,
                botonAncho(Ui.primario("App autenticadora (recomendado)", e -> elegir("app"))),
                (JComponent) Ui.espacio(10),
                botonAncho(Ui.secundario("Código por correo", e -> elegir("correo"))),
                enlaces(Ui.enlace("Usar otra cuenta", e -> reiniciar(null))));
    }

    private JPanel formActivarCorreo() {
        JButton activar = botonAncho(Ui.primario("Activar e ingresar", e -> activarCorreo()));
        codigoActivar.addActionListener(e -> activarCorreo());
        return formulario(
                encabezado("Acceso · Código por correo", "Revisa tu correo", subtituloActivar),
                avisoActivar,
                Ui.etiqueta("Código del correo"), codigoActivar,
                (JComponent) Ui.espacio(20), activar,
                enlaces(Ui.enlace("Usar otra cuenta", e -> reiniciar(null))));
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
            avisoCorreo, avisoLlave, avisoElegir, avisoActivar}) {
            a.ocultar();
        }
        tarjetas.show(formularios, tarjeta);
    }

    /** Vuelve al paso 1 olvidando el ingreso a medio camino (como "Usar otra cuenta"). */
    private void reiniciar(String mensajeExito) {
        tokenAcceso = null;
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

    private void ingresar() {
        String email = correo.getText().trim();
        String clave = new String(contrasena.getPassword());
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/ingresar", Json.de("usuario", email, "contrasena", clave)),
                this::siguientePaso, e -> avisoIngreso.error(Ui.mensajeError("ingresar").apply(e)));
    }

    /**
     * Respuesta con "etapa" (las mismas de la web): código de la app ("codigo") o del correo
     * ("codigo_correo"), elegir el método ("elegir"), configurar la app ("configurar") o
     * activar el correo ("activar_correo").
     */
    private void siguientePaso(JsonObject paso) {
        tokenAcceso = Json.texto(paso, "token");
        String etapa = Json.texto(paso, "etapa");
        String mensaje = Json.texto(paso, "mensaje");
        if ("codigo".equals(etapa) || "codigo_correo".equals(etapa)) {
            boolean porCorreo = "codigo_correo".equals(etapa);
            JsonObject metodos = Json.objeto(paso, "metodos");
            boolean ambos = Json.logico(metodos, "app") && Json.logico(metodos, "correo");
            subtituloCodigo.setText(porCorreo
                    ? "Escribe el código de 6 dígitos que te enviamos a " + Json.texto(paso, "correo") + "."
                    : "Abre tu app autenticadora y escribe el código de 6 dígitos de PRPagos ("
                            + Json.texto(paso, "correo") + ").");
            enlaceCambiar.setText(porCorreo ? "Usar la app autenticadora" : "Usar un código por correo");
            enlaceCambiar.setVisible(ambos);
            enlacePerdi.setVisible(!porCorreo && !Json.logico(metodos, "correo"));
            codigo.setText("");
            mostrar("codigo");
            if (!mensaje.isEmpty()) {
                avisoCodigo.exito(mensaje);
            }
            codigo.requestFocusInWindow();
        } else if ("elegir".equals(etapa)) {
            subtituloElegir.setText("Es obligatoria para todas las cuentas (" + Json.texto(paso, "correo")
                    + "). Elige cómo recibir el código; después puedes agregar el otro método desde tu perfil.");
            mostrar("elegir");
        } else if ("activar_correo".equals(etapa)) {
            subtituloActivar.setText(mensaje);
            codigoActivar.setText("");
            mostrar("activar");
            codigoActivar.requestFocusInWindow();
        } else {
            qr.mostrar(Json.texto(paso, "uri"));
            claveManual.setText("Clave:  " + Json.texto(paso, "clave"));
            codigoConfig.setText("");
            mostrar("configurar");
            if (!mensaje.isEmpty()) {
                avisoConfig.exito(mensaje);
            }
            codigoConfig.requestFocusInWindow();
        }
    }

    /** Si el ingreso expiró o la cuenta quedó bloqueada, se vuelve al paso 1 con el mensaje. */
    private boolean volverAlInicio(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.startsWith("Tu ingreso expiró") || m.startsWith("Demasiados intentos")) {
            reiniciar(null);
            avisoIngreso.error(m);
            return true;
        }
        return false;
    }

    // ───────────────────── Paso 2a: código del autenticador ─────────────────────

    private void verificarCodigo() {
        String texto = codigo.getText();
        String token = tokenAcceso;
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/codigo", Json.de("token", token, "codigo", texto)),
                this::sesionAbierta, e -> {
                    codigo.setText("");
                    if (!volverAlInicio(e)) {
                        avisoCodigo.error(Ui.mensajeError("verificar el código").apply(e));
                    }
                });
    }

    /** Con los dos métodos activos: usar el otro en este ingreso. */
    private void cambiarMetodo() {
        String token = tokenAcceso;
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/cambiar-metodo", Json.de("token", token)),
                this::siguientePaso, e -> {
                    if (!volverAlInicio(e)) {
                        avisoCodigo.error(Ui.mensajeError("cambiar de método").apply(e));
                    }
                });
    }

    // ───────────── Primer ingreso: elegir y activar el método ─────────────

    private void elegir(String metodo) {
        String token = tokenAcceso;
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/elegir", Json.de("token", token, "metodo", metodo)),
                this::siguientePaso, e -> {
                    if (!volverAlInicio(e)) {
                        avisoElegir.error(Ui.mensajeError("elegir el método").apply(e));
                    }
                });
    }

    private void activarCorreo() {
        String texto = codigoActivar.getText();
        String token = tokenAcceso;
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/correo", Json.de("token", token, "codigo", texto)),
                respuesta -> {
                    sesionLista = guardarSesion(respuesta);
                    // Con el primer método llegan los 10 códigos de respaldo (se muestran una sola vez).
                    PanelPerfil.mostrarCodigos(rejillaRespaldo, Json.textos(respuesta, "codigos"));
                    mostrar("respaldo");
                }, e -> {
                    codigoActivar.setText("");
                    if (!volverAlInicio(e)) {
                        avisoActivar.error(Ui.mensajeError("verificar el código").apply(e));
                    }
                });
    }

    // ─────────────── Ingresar con una llave de acceso (Windows Hello) ───────────────

    /** Reemplaza los dos pasos: Windows Hello firma el reto y el servidor lo verifica. */
    private void ingresarConLlave() {
        avisoLlave.ocultar();
        avisoIngreso.ocultar();
        Tarea.ejecutar(this, () -> {
            JsonObject o = Api.post("/api/acceso/llave/opciones", null);
            JsonObject opciones = Json.objeto(o, "opciones");
            byte[] cliente = WindowsHello.datosCliente("webauthn.get", Json.texto(opciones, "challenge"),
                    Json.texto(o, "origen"));
            WindowsHello.Asercion a = WindowsHello.obtener(this, Json.texto(o, "sitio"), cliente);
            return Api.post("/api/acceso/llave", Json.de("reto", Json.texto(o, "reto"),
                    "credencial", WindowsHello.credencialIngreso(a, cliente)));
        }, this::sesionAbierta, e -> avisoLlave.error(Ui.mensajeError("ingresar con la llave de acceso").apply(e)));
    }

    // ─────────────────── Paso 2b: configurar el autenticador ───────────────────

    private void activarAutenticador() {
        String texto = codigoConfig.getText();
        String token = tokenAcceso;
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/configurar", Json.de("token", token, "codigo", texto)),
                respuesta -> {
                    sesionLista = guardarSesion(respuesta);
                    // Con el autenticador llegan los 10 códigos de respaldo (se muestran una sola vez).
                    PanelPerfil.mostrarCodigos(rejillaRespaldo, Json.textos(respuesta, "codigos"));
                    mostrar("respaldo");
                }, e -> {
                    codigoConfig.setText("");
                    if (!volverAlInicio(e)) {
                        avisoConfig.error(Ui.mensajeError("guardar el autenticador").apply(e));
                    }
                });
    }

    // ─────────────────── Perdí mi teléfono (código por correo) ───────────────────

    /** Solo después de la contraseña: el servidor envía un código de 6 dígitos al correo. */
    private void perdiTelefono() {
        String token = tokenAcceso;
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/perdi-telefono", Json.de("token", token)), respuesta -> {
            tokenAcceso = Json.texto(respuesta, "token");
            subtituloCorreo.setText(Json.texto(respuesta, "mensaje"));
            codigoCorreo.setText("");
            mostrar("correo");
            codigoCorreo.requestFocusInWindow();
        }, e -> {
            if (!volverAlInicio(e)) {
                avisoCodigo.error(Ui.mensajeError("enviar el correo").apply(e));
            }
        });
    }

    private void verificarCodigoCorreo() {
        String texto = codigoCorreo.getText();
        String token = tokenAcceso;
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/correo", Json.de("token", token, "codigo", texto)),
                this::siguientePaso, e -> {
                    codigoCorreo.setText("");
                    if (!volverAlInicio(e)) {
                        avisoCorreo.error(Ui.mensajeError("verificar el código").apply(e));
                    }
                });
    }

    // ───────────────────────── Sesión ─────────────────────────

    private static Sesion guardarSesion(JsonObject respuesta) {
        Api.usarSesion(Json.texto(respuesta, "sesion"));
        return Sesion.de(Json.objeto(respuesta, "usuario"));
    }

    private void sesionAbierta(JsonObject respuesta) {
        sesionLista = guardarSesion(respuesta);
        abrirPrincipal();
    }

    private void abrirPrincipal() {
        Sesion sesion = sesionLista;
        tokenAcceso = null;
        sesionLista = null;
        new VentanaPrincipal(sesion).setVisible(true);
        dispose();
    }

    // ───────────────────────── Registro ─────────────────────────

    private void registrar() {
        String email = regCorreo.getText().trim();
        String nombre = regNombre.getText();
        String clave = new String(regClave.getPassword());
        String confirmacion = new String(regConfirmacion.getPassword());
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/registro", Json.de("usuario", email, "nombre", nombre,
                "contrasena", clave, "confirmacion", confirmacion)), respuesta -> {
                    for (JTextField t : new JTextField[] {regCorreo, regNombre, regClave, regConfirmacion}) {
                        t.setText("");
                    }
                    correo.setText(email);
                    reiniciar(Json.texto(respuesta, "mensaje"));
                }, e -> avisoRegistro.error(Ui.mensajeError("crear la cuenta").apply(e)));
    }

    // ───────────────────────── Recuperación ─────────────────────────

    private void recuperar() {
        String email = recCorreo.getText().trim();
        String nombre = recNombre.getText();
        String textoCodigo = recCodigo.getText();
        String clave = new String(recClave.getPassword());
        String confirmacion = new String(recConfirmacion.getPassword());
        Tarea.ejecutar(this, () -> Api.post("/api/acceso/recuperar", Json.de("correo", email,
                "nombre_registrado", nombre, "codigo", textoCodigo, "nueva", clave, "confirmacion", confirmacion)),
                respuesta -> {
                    for (JTextField t : new JTextField[] {recNombre, recCodigo, recClave, recConfirmacion}) {
                        t.setText("");
                    }
                    correo.setText(email);
                    reiniciar(Json.texto(respuesta, "mensaje"));
                }, e -> avisoRecuperar.error(Ui.mensajeError("actualizar la contraseña").apply(e)));
    }

    public static void main(String[] args) {
        Tema.iniciar();
        SwingUtilities.invokeLater(() -> new VentanaLogin().setVisible(true));
        // Actualización automática (solo en la aplicación instalada con PRPagos.exe).
        com.prpagos.actualizaciones.Comprobador.iniciar();
    }
}
