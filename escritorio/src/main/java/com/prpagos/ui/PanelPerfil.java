package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.google.gson.JsonObject;
import com.prpagos.cliente.Api;
import com.prpagos.cliente.Json;
import com.prpagos.seguridad.WindowsHello;

/**
 * "Perfil": misma pantalla que perfil.html de la web. Tus datos y los
 * resúmenes por correo, el plan 50/30/20, tus categorías, la verificación en
 * dos pasos, los códigos de respaldo, las llaves de acceso y la contraseña.
 * Cada acción va a la API: el servidor valida y responde con el mismo mensaje
 * que en la web.
 */
class PanelPerfil extends JPanel {

    private static final String[] GRUPOS = {"NECESIDADES", "DESEOS", "AHORRO"};

    private final VentanaPrincipal ventana;
    private final CardLayout vistas = new CardLayout();
    private final Ui.Aviso aviso = new Ui.Aviso();
    // Datos
    private final JTextField nombre = Ui.campo(null);
    private final JTextField correo = Ui.campo(null);
    private final JCheckBox notificaciones =
            new JCheckBox("Recibir el resumen semanal (viernes) y el general (día 29) por correo");
    // Plan
    private final JTextField necesidades = Ui.campo(null);
    private final JTextField deseos = Ui.campo(null);
    private final JTextField ahorro = Ui.campo(null);
    // Categorías
    private final JPanel listaCategorias = new JPanel(new GridLayout(0, 4, 0, 0));
    private final JTextField nombreCategoria = Ui.campo(null);
    private final JComboBox<String> tipoCategoria = new JComboBox<>(new String[] {"Gasto", "Ingreso"});
    private final JComboBox<String> grupoCategoria = new JComboBox<>(new String[] {"Necesidades", "Deseos", "Ahorro y deudas"});
    // Verificación y respaldo
    private final JLabel estado = pastilla();
    private final JLabel estadoCorreo = pastilla();
    private final JLabel etiquetaApp = new JLabel();
    private final JLabel etiquetaCorreo = new JLabel();
    private final JTextField codigoCorreoNuevo = Ui.campoCodigo();
    private final JButton botonPreferido = ancho(Ui.secundario("Pedir el correo por defecto", e -> cambiarPreferido()));
    private final JButton botonAgregarCorreo = ancho(Ui.secundario("Agregar código por correo", e -> agregarCorreo()));
    private final JButton botonActivarCorreo = ancho(Ui.secundario("Activar código por correo", e -> activarCorreo()));
    private final JButton botonEnviarCodigo = ancho(Ui.secundario("Enviarme un código por correo", e -> enviarCodigo()));
    private final JButton botonCambio = ancho(Ui.secundario("Cambiar autenticador", e -> iniciarCambio()));
    private final JTextArea ayudaCambio = ayuda("");
    private final JComponent bloqueAgregarCorreo = Ui.columna();
    private final JLabel restantes = pastilla();
    private final JTextField codigoCambio = Ui.campoCodigo();
    private final JTextField codigoRespaldo = Ui.campoCodigo();
    // Llaves de acceso
    private final JPanel listaLlaves = new JPanel(new GridLayout(0, 4, 0, 0));
    private final JTextField nombreLlave = Ui.campo("Portátil, celular…");
    private final JTextField codigoLlave = Ui.campoCodigo();
    private final Ui.Aviso avisoLlaves = new Ui.Aviso();
    private JButton agregarLlave;
    // Contraseña
    private final JPasswordField actual = Ui.clave();
    private final JPasswordField nueva = Ui.clave();
    private final JPasswordField confirmacion = Ui.clave();
    private final JTextField codigoClave = Ui.campoCodigo();
    // Nuevo autenticador y códigos
    private final PanelQr qr = new PanelQr();
    private final JLabel claveManual = new JLabel();
    private final JTextField codigoNuevo = Ui.campoCodigo();
    private final Ui.Aviso avisoNuevo = new Ui.Aviso();
    private final JPanel codigos = new JPanel(new GridLayout(0, 2, 24, 6));
    /** Token del cambio de autenticador (el secreto nuevo viaja cifrado en él). */
    private String tokenNuevo;
    private boolean totpActivo;
    private boolean correoActivo;
    private String preferido = "app";
    private int respaldo;

    PanelPerfil(VentanaPrincipal ventana) {
        this.ventana = ventana;
        setOpaque(false);
        setLayout(vistas);
        add(vistaPrincipal(), "principal");
        add(vistaNuevoAutenticador(), "nuevo");
        add(vistaCodigos(), "codigos");
        Tema.alCambiar(this::pintarEstados);
    }

    // ───────────────────────── Vistas ─────────────────────────

    private static JLabel pastilla() {
        JLabel l = new JLabel();
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12.5f));
        l.setOpaque(true);
        l.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
        return l;
    }

    private static JTextArea ayuda(String texto) {
        JTextArea a = Ui.suave(texto);
        a.setFont(a.getFont().deriveFont(12.5f));
        return a;
    }

    private static JButton ancho(JButton b) {
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return b;
    }

    private static void agregar(JPanel destino, Component... partes) {
        for (Component c : partes) {
            if (c instanceof JComponent j) {
                j.setAlignmentX(LEFT_ALIGNMENT);
            }
            destino.add(c);
        }
    }

    private static JPanel filaEstado(JLabel texto, JLabel pastilla) {
        JPanel fila = Ui.fila(8);
        fila.add(texto);
        fila.add(pastilla);
        return fila;
    }

    private static JPanel arriba(JPanel tarjeta) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(tarjeta, BorderLayout.NORTH);
        return p;
    }

    private static JPanel par(JPanel izquierda, JPanel derecha) {
        JPanel fila = Ui.adaptable(new JPanel(new GridLayout(1, 2, 16, 0)), 2, 300);
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        fila.add(arriba(izquierda));
        fila.add(arriba(derecha));
        return fila;
    }

    private JPanel vistaPrincipal() {
        JPanel p = Ui.columna();
        p.add(Ui.titulo("Perfil", 26f));
        p.add(Ui.espacio(18));
        p.add(aviso);
        p.add(Ui.espacio(6));

        correo.setEnabled(false);
        notificaciones.setOpaque(false);
        JPanel datos = Ui.tarjeta();
        agregar(datos, Ui.titulo("Tus datos", 17f), Ui.etiqueta("Nombre completo"), nombre, Ui.etiqueta("Correo"), correo,
                Ui.espacio(10), notificaciones, Ui.espacio(16), ancho(Ui.primario("Guardar datos", e -> guardarDatos())));

        JPanel plan = Ui.tarjeta();
        agregar(plan, Ui.titulo("Plan 50/30/20", 17f), Ui.espacio(6),
                ayuda("Cómo repartir tus ingresos del mes. Los tres porcentajes deben sumar 100."),
                Ui.etiqueta("Necesidades (%)"), necesidades, Ui.etiqueta("Deseos (%)"), deseos,
                Ui.etiqueta("Ahorro y deudas (%)"), ahorro, Ui.espacio(16),
                ancho(Ui.primario("Guardar plan", e -> guardarPlan())));
        p.add(par(datos, plan));
        p.add(Ui.espacio(16));

        JPanel categorias = Ui.tarjeta();
        listaCategorias.setOpaque(false);
        tipoCategoria.addActionListener(e -> grupoCategoria.setEnabled(tipoCategoria.getSelectedIndex() == 0));
        JPanel nuevaCategoria = Ui.adaptable(new JPanel(new GridLayout(1, 4, 12, 0)), 4, 140);
        nuevaCategoria.setOpaque(false);
        nuevaCategoria.add(conEtiqueta("Nombre", nombreCategoria));
        nuevaCategoria.add(conEtiqueta("Tipo", tipoCategoria));
        nuevaCategoria.add(conEtiqueta("Grupo (gastos)", grupoCategoria));
        JPanel boton = new JPanel(new BorderLayout());
        boton.setOpaque(false);
        boton.setBorder(BorderFactory.createEmptyBorder(30, 0, 0, 0));
        boton.add(Ui.secundario("Agregar categoría", e -> crearCategoria()), BorderLayout.NORTH);
        nuevaCategoria.add(boton);
        agregar(categorias, Ui.titulo("Categorías", 17f), Ui.espacio(6),
                ayuda("Las sugeridas sirven para todos; puedes crear las tuyas y elegir su grupo del plan."),
                Ui.espacio(8), listaCategorias, Ui.espacio(12), nuevaCategoria);
        p.add(categorias);
        p.add(Ui.espacio(16));

        JPanel totp = Ui.tarjeta();
        agregar((JPanel) bloqueAgregarCorreo, Ui.espacio(10), botonAgregarCorreo,
                Ui.etiqueta("Código que te enviamos para activar el correo"), codigoCorreoNuevo, Ui.espacio(8),
                botonActivarCorreo);
        agregar(totp, Ui.titulo("Verificación en dos pasos", 17f), Ui.espacio(6),
                ayuda("Obligatoria en cada ingreso, igual en la web y en el escritorio. Tener los dos métodos te "
                        + "permite entrar aunque no tengas el teléfono a mano."),
                Ui.espacio(10), filaEstado(etiquetaApp, estado), Ui.espacio(6), filaEstado(etiquetaCorreo, estadoCorreo),
                Ui.espacio(10), botonPreferido, bloqueAgregarCorreo, botonEnviarCodigo,
                Ui.espacio(10), ayudaCambio,
                Ui.espacio(6), codigoCambio, Ui.espacio(16), botonCambio);

        JPanel respaldoTarjeta = Ui.tarjeta();
        agregar(respaldoTarjeta, Ui.titulo("Códigos de respaldo", 17f), Ui.espacio(10), restantes, Ui.espacio(10),
                ayuda("Sirven, una sola vez cada uno, si no tienes el teléfono a mano. Generar nuevos anula los anteriores."),
                Ui.etiqueta("Código de la app o de respaldo"), codigoRespaldo, Ui.espacio(16),
                ancho(Ui.secundario("Generar códigos nuevos", e -> regenerarCodigos())));
        p.add(par(totp, respaldoTarjeta));
        p.add(Ui.espacio(16));

        JPanel llaves = Ui.tarjeta();
        listaLlaves.setOpaque(false);
        JPanel nuevaLlave = new JPanel(new GridLayout(1, 3, 12, 0));
        nuevaLlave.setOpaque(false);
        nuevaLlave.add(conEtiqueta("Nombre del dispositivo", nombreLlave));
        nuevaLlave.add(conEtiqueta("Código de la app o de respaldo", codigoLlave));
        JPanel botonLlave = new JPanel(new BorderLayout());
        botonLlave.setOpaque(false);
        botonLlave.setBorder(BorderFactory.createEmptyBorder(30, 0, 0, 0));
        agregarLlave = Ui.secundario("Agregar este dispositivo", e -> agregarLlave());
        botonLlave.add(agregarLlave, BorderLayout.NORTH);
        nuevaLlave.add(botonLlave);
        agregar(llaves, Ui.titulo("Llaves de acceso", 17f), Ui.espacio(6),
                ayuda("Ingresa sin contraseña ni código con Windows Hello, la huella, el rostro o el PIN de este "
                        + "dispositivo. Tu huella nunca sale del dispositivo: PRPagos solo guarda una clave pública."),
                Ui.espacio(8), listaLlaves, Ui.espacio(8), avisoLlaves, Ui.espacio(4), nuevaLlave);
        p.add(llaves);
        p.add(Ui.espacio(16));

        JPanel clave = Ui.tarjeta();
        JPanel columnas = Ui.adaptable(new JPanel(new GridLayout(1, 2, 16, 0)), 2, 260);
        columnas.setOpaque(false);
        JPanel izq = Ui.columna();
        agregar(izq, Ui.etiqueta("Contraseña actual"), actual, Ui.etiqueta("Nueva contraseña (mínimo 8 caracteres)"), nueva);
        JPanel der = Ui.columna();
        agregar(der, Ui.etiqueta("Repite la nueva contraseña"), confirmacion,
                Ui.etiqueta("Código de la app o de respaldo"), codigoClave);
        columnas.add(arriba(izq));
        columnas.add(arriba(der));
        agregar(clave, Ui.titulo("Cambiar contraseña", 17f), columnas, Ui.espacio(16),
                Ui.primario("Guardar contraseña", e -> cambiarContrasena()));
        p.add(clave);
        p.add(Ui.espacio(16));
        JTextArea nota = ayuda("Tu contraseña y tu autenticador se guardan protegidos. Tras varios intentos fallidos seguidos la cuenta se bloquea unos minutos.");
        nota.setFont(nota.getFont().deriveFont(12f));
        p.add(nota);
        return p;
    }

    private static JPanel conEtiqueta(String texto, JComponent campo) {
        JPanel p = Ui.columna();
        campo.setAlignmentX(LEFT_ALIGNMENT);
        campo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        p.add(Ui.etiqueta(texto));
        p.add(campo);
        return p;
    }

    private JPanel tarjetaCentrada(JPanel tarjeta) {
        JPanel centro = new JPanel(new BorderLayout());
        centro.setOpaque(false);
        JPanel envoltura = new JPanel(new GridBagLayout());
        envoltura.setOpaque(false);
        tarjeta.setPreferredSize(new Dimension(460, tarjeta.getPreferredSize().height));
        envoltura.add(tarjeta);
        centro.add(envoltura, BorderLayout.NORTH);
        return centro;
    }

    private JPanel vistaNuevoAutenticador() {
        JPanel tarjeta = Ui.tarjeta();
        tarjeta.setBorder(BorderFactory.createEmptyBorder(30, 32, 30, 32));
        JPanel cajaQr = new JPanel(new GridBagLayout());
        cajaQr.setOpaque(false);
        cajaQr.add(qr);
        JPanel filaClave = new JPanel(new GridBagLayout());
        filaClave.setOpaque(false);
        filaClave.add(claveManual);
        codigoNuevo.addActionListener(e -> confirmarNuevo());
        agregar(tarjeta, Ui.titulo("Nuevo autenticador", 26f), Ui.espacio(10), avisoNuevo,
                ayuda("1. Escanea este código QR con la app nueva, o escribe la clave a mano.\n"
                        + "2. Escribe el código de 6 dígitos que muestra."),
                Ui.espacio(14), cajaQr, Ui.espacio(6), filaClave, Ui.etiqueta("Código de 6 dígitos"), codigoNuevo,
                Ui.espacio(20), ancho(Ui.primario("Confirmar autenticador", e -> confirmarNuevo())), Ui.espacio(12),
                ayuda("Hasta que confirmes, el autenticador anterior sigue siendo el válido. Al confirmar recibirás "
                        + "códigos de respaldo nuevos."),
                Ui.espacio(8), Ui.enlace("Cancelar", e -> {
                    tokenNuevo = null;
                    vistas.show(this, "principal");
                }));
        return tarjetaCentrada(tarjeta);
    }

    private JPanel vistaCodigos() {
        JPanel tarjeta = Ui.tarjeta();
        tarjeta.setBorder(BorderFactory.createEmptyBorder(30, 32, 30, 32));
        codigos.setOpaque(false);
        agregar(tarjeta, Ui.titulo("Tus códigos de respaldo", 26f), Ui.espacio(14), codigos, Ui.espacio(12),
                ayuda(TEXTO_RESPALDO), Ui.espacio(16),
                Ui.primario("Ya los guardé", e -> recargar()));
        return tarjetaCentrada(tarjeta);
    }

    static final String TEXTO_RESPALDO = "Guárdalos en un lugar seguro (por ejemplo, impresos o en tu gestor de "
            + "contraseñas). Cada uno sirve una sola vez para ingresar, recuperar la contraseña o cambiar de "
            + "autenticador si no tienes el teléfono. No se volverán a mostrar.";

    /** Llena una rejilla con los 10 códigos (también la usa la portada de acceso). */
    static void mostrarCodigos(JPanel rejilla, List<String> lista) {
        rejilla.removeAll();
        for (int i = 0; i < lista.size(); i++) {
            JLabel l = new JLabel((i + 1) + ".  " + lista.get(i));
            l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
            rejilla.add(l);
        }
        rejilla.revalidate();
    }

    // ───────────────────────── Carga ─────────────────────────

    void recargar() {
        aviso.ocultar();
        tokenNuevo = null;
        vistas.show(this, "principal");
        Tarea.ejecutar(this, () -> Api.get("/api/perfil"), d -> {
            JsonObject perfil = Json.objeto(d, "perfil");
            nombre.setText(Json.texto(perfil, "nombre"));
            correo.setText(Json.texto(perfil, "usuario"));
            notificaciones.setSelected(Json.logico(perfil, "notificaciones"));
            JsonObject pcts = Json.objeto(d, "textos_pct");
            necesidades.setText(Json.texto(pcts, "NECESIDADES"));
            deseos.setText(Json.texto(pcts, "DESEOS"));
            ahorro.setText(Json.texto(pcts, "AHORRO"));
            JsonObject dosPasos = Json.objeto(d, "dos_pasos");
            totpActivo = Json.logico(dosPasos, "app");
            correoActivo = Json.logico(dosPasos, "correo");
            preferido = Json.texto(dosPasos, "preferido");
            respaldo = (int) Json.entero(perfil, "respaldo_restantes");
            pintarEstados();
            llenarCategorias(Json.lista(d, "categorias"), Json.objeto(d, "grupos"));
            llenarLlaves(Json.lista(d, "llaves"));
        }, e -> aviso.error(Ui.mensajeError("leer el perfil").apply(e)));
    }

    private void pintarEstados() {
        Tema.Paleta p = Tema.c();
        estado.setText(totpActivo ? "Activa" : "Sin configurar");
        estado.setBackground(totpActivo ? p.exitoFondo() : p.errorFondo());
        estado.setForeground(totpActivo ? p.exitoTexto() : p.acento());
        estadoCorreo.setText(correoActivo ? "Activo" : "Sin configurar");
        estadoCorreo.setBackground(correoActivo ? p.exitoFondo() : p.errorFondo());
        estadoCorreo.setForeground(correoActivo ? p.exitoTexto() : p.acento());
        boolean ambos = totpActivo && correoActivo;
        etiquetaApp.setText("App autenticadora" + (ambos && "app".equals(preferido) ? " · se pide por defecto" : "") + ":");
        etiquetaCorreo.setText("Código por correo" + (ambos && "correo".equals(preferido) ? " · se pide por defecto" : "") + ":");
        etiquetaApp.setForeground(p.texto());
        etiquetaCorreo.setForeground(p.texto());
        botonPreferido.setText("app".equals(preferido) ? "Pedir el correo por defecto" : "Pedir la app por defecto");
        botonPreferido.setVisible(ambos);
        bloqueAgregarCorreo.setVisible(!correoActivo);
        botonEnviarCodigo.setVisible(correoActivo);
        ayudaCambio.setText((totpActivo ? "Para cambiar o restablecer tu autenticador (teléfono nuevo o perdido)"
                : "Para agregar la app autenticadora") + ", escribe un código vigente (de la app, del correo o de respaldo)");
        botonCambio.setText(totpActivo ? "Cambiar autenticador" : "Agregar app autenticadora");
        restantes.setText(respaldo + " disponibles");
        restantes.setBackground(respaldo > 2 ? p.exitoFondo() : p.errorFondo());
        restantes.setForeground(respaldo > 2 ? p.exitoTexto() : p.acento());
    }

    private void llenarCategorias(List<JsonObject> lista, JsonObject grupos) {
        listaCategorias.removeAll();
        Tema.Paleta p = Tema.c();
        for (String e : new String[] {"Categoría", "Tipo", "Grupo", ""}) {
            JLabel l = new JLabel(e);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 12.5f));
            l.setForeground(p.textoSuave());
            l.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
            listaCategorias.add(l);
        }
        for (JsonObject c : lista) {
            String grupo = Json.textoONulo(c, "grupo");
            listaCategorias.add(fila(Json.texto(c, "nombre")));
            listaCategorias.add(fila("INGRESO".equals(Json.texto(c, "tipo")) ? "Ingreso" : "Gasto"));
            listaCategorias.add(fila(grupo == null ? "—" : Json.texto(grupos, grupo)));
            if (Json.logico(c, "propia")) {
                long id = Json.entero(c, "id");
                listaCategorias.add(celdaEnlace(Ui.enlace("Eliminar", ev -> eliminarCategoria(id))));
            } else {
                JLabel sugerida = fila("Sugerida");
                sugerida.setForeground(p.textoSuave());
                listaCategorias.add(sugerida);
            }
        }
        listaCategorias.revalidate();
        listaCategorias.repaint();
    }

    private void llenarLlaves(List<JsonObject> lista) {
        listaLlaves.removeAll();
        Tema.Paleta p = Tema.c();
        for (String e : new String[] {"Dispositivo", "Agregada", "Último uso", ""}) {
            JLabel l = new JLabel(e);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 12.5f));
            l.setForeground(p.textoSuave());
            l.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
            listaLlaves.add(l);
        }
        if (lista.isEmpty()) {
            JLabel vacio = fila("Todavía no tienes llaves de acceso.");
            vacio.setForeground(p.textoSuave());
            listaLlaves.add(vacio);
            for (int i = 0; i < 3; i++) {
                listaLlaves.add(new JLabel());
            }
        }
        for (JsonObject llave : lista) {
            String uso = Json.textoONulo(llave, "ultimo_uso");
            long id = Json.entero(llave, "id");
            listaLlaves.add(fila(Json.texto(llave, "nombre")));
            listaLlaves.add(fila(Json.texto(llave, "creada")));
            listaLlaves.add(fila(uso == null ? "—" : uso));
            listaLlaves.add(celdaEnlace(Ui.enlace("Eliminar", ev -> eliminarLlave(id))));
        }
        avisoLlaves.ocultar();
        boolean hello = WindowsHello.disponible();
        agregarLlave.setEnabled(hello);
        if (!hello) {
            avisoLlaves.error("Este equipo no tiene Windows Hello configurado.");
        }
        listaLlaves.revalidate();
        listaLlaves.repaint();
    }

    /** Enlace a su tamaño dentro de una celda de la rejilla (como en la tabla de la web). */
    private static JPanel celdaEnlace(JButton enlace) {
        JPanel celda = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 3));
        celda.setOpaque(false);
        celda.add(enlace);
        return celda;
    }

    private static JLabel fila(String texto) {
        JLabel l = new JLabel(texto);
        l.setBorder(BorderFactory.createEmptyBorder(5, 2, 5, 2));
        return l;
    }

    // ───────────────── Acciones: la API valida y responde con el mensaje (el de la web) ─────────────────

    /** Llama a la API; si sale bien recarga y muestra su mensaje, si no, el error del servidor. */
    private void accion(String que, java.util.concurrent.Callable<JsonObject> llamada, Runnable alTerminar) {
        Tarea.ejecutar(this, llamada, r -> {
            if (alTerminar != null) {
                alTerminar.run();
            }
            recargar();
            aviso.exito(Json.texto(r, "mensaje"));
        }, e -> aviso.error(Ui.mensajeError(que).apply(e)));
    }

    private void guardarDatos() {
        JsonObject datos = Json.de("nombre", nombre.getText(), "notificaciones", notificaciones.isSelected());
        accion("guardar los datos", () -> Api.post("/api/perfil/datos", datos), null);
    }

    private void guardarPlan() {
        JsonObject datos = Json.de("necesidades", necesidades.getText(), "deseos", deseos.getText(),
                "ahorro", ahorro.getText());
        accion("guardar el plan", () -> Api.post("/api/perfil/plan", datos), null);
    }

    private void crearCategoria() {
        String tipo = tipoCategoria.getSelectedIndex() == 0 ? "GASTO" : "INGRESO";
        JsonObject datos = Json.de("nombre", nombreCategoria.getText(), "tipo", tipo,
                "grupo", "GASTO".equals(tipo) ? GRUPOS[grupoCategoria.getSelectedIndex()] : "");
        accion("crear la categoría", () -> Api.post("/api/perfil/categorias", datos), () -> nombreCategoria.setText(""));
    }

    private void eliminarCategoria(long categoriaId) {
        accion("eliminar la categoría", () -> Api.delete("/api/perfil/categorias/" + categoriaId), null);
    }

    // ───────────── Verificación en dos pasos: métodos (los mismos de la web) ─────────────

    private void cambiarPreferido() {
        String otro = "app".equals(preferido) ? "correo" : "app";
        accion("cambiar el método por defecto", () -> Api.post("/api/perfil/dos-pasos/preferido", Json.de("metodo", otro)),
                null);
    }

    private void agregarCorreo() {
        Tarea.ejecutar(this, () -> Api.post("/api/perfil/dos-pasos/correo", null),
                r -> aviso.exito(Json.texto(r, "mensaje")), e -> aviso.error(Ui.mensajeError("enviar el código").apply(e)));
    }

    private void activarCorreo() {
        String texto = codigoCorreoNuevo.getText();
        accion("activar el código por correo",
                () -> Api.post("/api/perfil/dos-pasos/correo/confirmar", Json.de("codigo", texto)),
                () -> codigoCorreoNuevo.setText(""));
    }

    private void enviarCodigo() {
        Tarea.ejecutar(this, () -> Api.post("/api/perfil/dos-pasos/codigo", null),
                r -> aviso.exito(Json.texto(r, "mensaje")), e -> aviso.error(Ui.mensajeError("enviar el código").apply(e)));
    }

    private void mostrarCodigosNuevos(List<String> lista) {
        mostrarCodigos(codigos, lista);
        vistas.show(this, "codigos");
    }

    private void regenerarCodigos() {
        String codigo = codigoRespaldo.getText();
        Tarea.ejecutar(this, () -> Api.post("/api/perfil/respaldo", Json.de("codigo", codigo)), r -> {
            codigoRespaldo.setText("");
            mostrarCodigosNuevos(Json.textos(r, "codigos"));
        }, e -> {
            codigoRespaldo.setText("");
            aviso.error(Ui.mensajeError("generar los códigos").apply(e));
        });
    }

    private void iniciarCambio() {
        String codigo = codigoCambio.getText();
        Tarea.ejecutar(this, () -> Api.post("/api/perfil/autenticador", Json.de("codigo", codigo)), r -> {
            codigoCambio.setText("");
            tokenNuevo = Json.texto(r, "token");
            qr.mostrar(Json.texto(r, "uri"));
            claveManual.setText("Clave:  " + Json.texto(r, "clave"));
            codigoNuevo.setText("");
            avisoNuevo.ocultar();
            vistas.show(this, "nuevo");
            codigoNuevo.requestFocusInWindow();
        }, e -> {
            codigoCambio.setText("");
            aviso.error(Ui.mensajeError("verificar el código").apply(e));
        });
    }

    private void confirmarNuevo() {
        if (tokenNuevo == null) {
            vistas.show(this, "principal");
            return;
        }
        JsonObject datos = Json.de("token", tokenNuevo, "codigo", codigoNuevo.getText());
        Tarea.ejecutar(this, () -> Api.post("/api/perfil/autenticador/confirmar", datos), r -> {
            tokenNuevo = null;
            mostrarCodigosNuevos(Json.textos(r, "codigos"));
        }, e -> {
            codigoNuevo.setText("");
            avisoNuevo.error(Ui.mensajeError("guardar el autenticador").apply(e));
        });
    }

    private void cambiarContrasena() {
        JsonObject datos = Json.de("actual", new String(actual.getPassword()), "nueva", new String(nueva.getPassword()),
                "confirmacion", new String(confirmacion.getPassword()), "codigo", codigoClave.getText());
        codigoClave.setText("");
        accion("cambiar la contraseña", () -> Api.post("/api/perfil/contrasena", datos), () -> {
            actual.setText("");
            nueva.setText("");
            confirmacion.setText("");
        });
    }

    // ───────────────────────── Llaves de acceso (Windows Hello) ─────────────────────────

    /** El servidor da el reto, Windows Hello crea la llave y el servidor la verifica y la guarda. */
    private void agregarLlave() {
        avisoLlaves.ocultar();
        JsonObject pedido = Json.de("nombre", nombreLlave.getText(), "codigo", codigoLlave.getText());
        java.awt.Window ventanaActual = javax.swing.SwingUtilities.getWindowAncestor(this);
        Tarea.ejecutar(this, () -> {
            JsonObject o = Api.post("/api/perfil/llaves/opciones", pedido);
            JsonObject op = Json.objeto(o, "opciones");
            JsonObject sitio = Json.objeto(op, "rp");
            JsonObject usuario = Json.objeto(op, "user");
            List<byte[]> excluidas = new ArrayList<>();
            for (JsonObject c : Json.lista(op, "excludeCredentials")) {
                excluidas.add(WindowsHello.deB64(Json.texto(c, "id")));
            }
            byte[] cliente = WindowsHello.datosCliente("webauthn.create", Json.texto(op, "challenge"),
                    Json.texto(o, "origen"));
            WindowsHello.Creada creada = WindowsHello.crear(ventanaActual, Json.texto(sitio, "id"),
                    Json.texto(sitio, "name"), WindowsHello.deB64(Json.texto(usuario, "id")), Json.texto(usuario, "name"),
                    Json.texto(usuario, "displayName"), cliente, excluidas);
            return Api.post("/api/perfil/llaves", Json.de("reto", Json.texto(o, "reto"),
                    "credencial", WindowsHello.credencialRegistro(creada, cliente)));
        }, r -> {
            codigoLlave.setText("");
            nombreLlave.setText("");
            recargar();
            aviso.exito(Json.texto(r, "mensaje"));
        }, e -> {
            codigoLlave.setText("");
            avisoLlaves.error(Ui.mensajeError("guardar la llave de acceso").apply(e));
        });
    }

    private void eliminarLlave(long llaveId) {
        accion("eliminar la llave de acceso", () -> Api.delete("/api/perfil/llaves/" + llaveId), null);
    }
}
