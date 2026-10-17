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
import java.util.Map;

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

import com.prpagos.datos.Credenciales;
import com.prpagos.datos.RepositorioFinanzas;
import com.prpagos.datos.RepositorioLlaves;
import com.prpagos.datos.RepositorioUsuarios;
import com.prpagos.datos.Sesion;
import com.prpagos.finanzas.Finanzas;
import com.prpagos.seguridad.CifradoTotp;
import com.prpagos.seguridad.LlavesAcceso;
import com.prpagos.seguridad.WindowsHello;
import com.prpagos.seguridad.Recuperacion;
import com.prpagos.seguridad.Seguridad;
import com.prpagos.seguridad.Totp;
import com.prpagos.seguridad.Verificacion;

/**
 * "Perfil": misma pantalla y mismas reglas que perfil.html y routers/perfil.py
 * de la web. Tus datos y los resúmenes por correo, el plan 50/30/20, tus
 * categorías, la verificación en dos pasos, los códigos de respaldo y la
 * contraseña. Las acciones de seguridad piden un código de la app o de respaldo.
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
    private String secretoNuevo;
    private boolean totpActivo;
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

    private Sesion sesion() {
        return ventana.sesion();
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

    private static JPanel arriba(JPanel tarjeta) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(tarjeta, BorderLayout.NORTH);
        return p;
    }

    private static JPanel par(JPanel izquierda, JPanel derecha) {
        JPanel fila = new JPanel(new GridLayout(1, 2, 16, 0));
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
        JPanel nuevaCategoria = new JPanel(new GridLayout(1, 4, 12, 0));
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
        agregar(totp, Ui.titulo("Verificación en dos pasos", 17f), Ui.espacio(10), estado, Ui.espacio(10),
                ayuda("Cada ingreso pide, además de la contraseña, el código de 6 dígitos de tu app autenticadora. "
                        + "Es el mismo en la web y en el escritorio."),
                Ui.espacio(10), ayuda("Para cambiar o restablecer tu autenticador (teléfono nuevo o perdido), escribe un "
                        + "código de la app o uno de respaldo"),
                Ui.espacio(6), codigoCambio, Ui.espacio(16),
                ancho(Ui.secundario("Cambiar autenticador", e -> iniciarCambio())));

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
        JPanel columnas = new JPanel(new GridLayout(1, 2, 16, 0));
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
        JTextArea nota = ayuda("Las contraseñas se guardan con PBKDF2-SHA256 y la clave de tu autenticador, cifrada con "
                + "AES-256-GCM. Tras 5 intentos fallidos seguidos la cuenta se bloquea 5 minutos.");
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
                    secretoNuevo = null;
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

    private record Estado(RepositorioFinanzas.Perfil perfil, boolean totp, Map<String, Double> pcts,
                          List<RepositorioFinanzas.Categoria> categorias, List<RepositorioLlaves.Llave> llaves) {
    }

    void recargar() {
        aviso.ocultar();
        secretoNuevo = null;
        vistas.show(this, "principal");
        long id = sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            Credenciales cred = RepositorioUsuarios.porId(id);
            return new Estado(RepositorioFinanzas.perfil(id), cred != null && cred.totpActivo(),
                    RepositorioFinanzas.plan(id), RepositorioFinanzas.categorias(id), RepositorioLlaves.deUsuario(id));
        }, e -> {
            nombre.setText(e.perfil().nombre());
            correo.setText(e.perfil().usuario());
            notificaciones.setSelected(e.perfil().notificaciones());
            necesidades.setText(Finanzas.textoPct(e.pcts().get("NECESIDADES")));
            deseos.setText(Finanzas.textoPct(e.pcts().get("DESEOS")));
            ahorro.setText(Finanzas.textoPct(e.pcts().get("AHORRO")));
            totpActivo = e.totp();
            respaldo = e.perfil().respaldoRestantes();
            pintarEstados();
            llenarCategorias(e.categorias());
            llenarLlaves(e.llaves());
        }, e -> aviso.error(Ui.mensajeError("leer el perfil").apply(e)));
    }

    private void pintarEstados() {
        Tema.Paleta p = Tema.c();
        estado.setText(totpActivo ? "Activa" : "Sin configurar");
        estado.setBackground(totpActivo ? p.exitoFondo() : p.errorFondo());
        estado.setForeground(totpActivo ? p.exitoTexto() : p.acento());
        restantes.setText(respaldo + " disponibles");
        restantes.setBackground(respaldo > 2 ? p.exitoFondo() : p.errorFondo());
        restantes.setForeground(respaldo > 2 ? p.exitoTexto() : p.acento());
    }

    private void llenarCategorias(List<RepositorioFinanzas.Categoria> lista) {
        listaCategorias.removeAll();
        Tema.Paleta p = Tema.c();
        for (String e : new String[] {"Categoría", "Tipo", "Grupo", ""}) {
            JLabel l = new JLabel(e);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 12.5f));
            l.setForeground(p.textoSuave());
            l.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
            listaCategorias.add(l);
        }
        for (RepositorioFinanzas.Categoria c : lista) {
            listaCategorias.add(fila(c.nombre()));
            listaCategorias.add(fila("INGRESO".equals(c.tipo()) ? "Ingreso" : "Gasto"));
            listaCategorias.add(fila(c.grupo() == null ? "—" : Finanzas.NOMBRE_GRUPO.get(c.grupo())));
            if (c.propia()) {
                listaCategorias.add(celdaEnlace(Ui.enlace("Eliminar", ev -> eliminarCategoria(c.id()))));
            } else {
                JLabel sugerida = fila("Sugerida");
                sugerida.setForeground(p.textoSuave());
                listaCategorias.add(sugerida);
            }
        }
        listaCategorias.revalidate();
        listaCategorias.repaint();
    }

    private void llenarLlaves(List<RepositorioLlaves.Llave> lista) {
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
        for (RepositorioLlaves.Llave llave : lista) {
            listaLlaves.add(fila(llave.nombre()));
            listaLlaves.add(fila(llave.creada()));
            listaLlaves.add(fila(llave.ultimoUso() == null ? "—" : llave.ultimoUso()));
            listaLlaves.add(celdaEnlace(Ui.enlace("Eliminar", ev -> eliminarLlave(llave.id()))));
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

    // ───────────────────────── Acciones (mismas reglas y textos que la web) ─────────────────────────

    private void resultado(String error, String exito) {
        if (error != null) {
            aviso.error(error);
        } else {
            recargar();
            aviso.exito(exito);
        }
    }

    private void guardarDatos() {
        String texto = nombre.getText().trim();
        boolean recibir = notificaciones.isSelected();
        if (texto.isEmpty()) {
            aviso.error("Escribe tu nombre completo");
            return;
        }
        long id = sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            RepositorioFinanzas.guardarDatosPerfil(id, texto.substring(0, Math.min(100, texto.length())), recibir);
            return null;
        }, r -> resultado(null, "Datos guardados"), e -> aviso.error(Ui.mensajeError("guardar los datos").apply(e)));
    }

    private void guardarPlan() {
        double[] v = new double[3];
        JTextField[] campos = {necesidades, deseos, ahorro};
        try {
            for (int i = 0; i < 3; i++) {
                v[i] = new java.math.BigDecimal(campos[i].getText().trim().replace(",", "."))
                        .setScale(2, java.math.RoundingMode.HALF_EVEN).doubleValue();
            }
        } catch (NumberFormatException e) {
            aviso.error("Los porcentajes deben ser números");
            return;
        }
        boolean fueraDeRango = v[0] < 0 || v[0] > 100 || v[1] < 0 || v[1] > 100 || v[2] < 0 || v[2] > 100;
        if (fueraDeRango || Math.round((v[0] + v[1] + v[2]) * 100) != 10000) {
            aviso.error("Los tres porcentajes deben sumar 100");
            return;
        }
        long id = sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            RepositorioFinanzas.guardarPlan(id, v[0], v[1], v[2]);
            return null;
        }, r -> resultado(null, "Plan actualizado"), e -> aviso.error(Ui.mensajeError("guardar el plan").apply(e)));
    }

    private void crearCategoria() {
        String texto = nombreCategoria.getText().trim();
        if (texto.length() > 60) {
            texto = texto.substring(0, 60);
        }
        if (texto.isEmpty()) {
            aviso.error("Escribe el nombre de la categoría");
            return;
        }
        String tipo = tipoCategoria.getSelectedIndex() == 0 ? "GASTO" : "INGRESO";
        String grupo = "GASTO".equals(tipo) ? GRUPOS[grupoCategoria.getSelectedIndex()] : null;
        long id = sesion().usuarioId();
        String nombreFinal = texto;
        Tarea.ejecutar(this, () -> RepositorioFinanzas.crearCategoria(id, nombreFinal, tipo, grupo), creada -> {
            if (creada) {
                nombreCategoria.setText("");
            }
            resultado(creada ? null : "Ya tienes una categoría con ese nombre", "Categoría creada");
        }, e -> aviso.error(Ui.mensajeError("crear la categoría").apply(e)));
    }

    private void eliminarCategoria(long categoriaId) {
        long id = sesion().usuarioId();
        Tarea.ejecutar(this, () -> RepositorioFinanzas.eliminarCategoria(id, categoriaId),
                motivo -> resultado(motivo, "Categoría eliminada"),
                e -> aviso.error(Ui.mensajeError("eliminar la categoría").apply(e)));
    }

    private String verificarCuentaYCodigo(String codigo) throws Exception {
        Credenciales cred = RepositorioUsuarios.porId(sesion().usuarioId());
        if (cred == null || cred.bloqueada()) {
            return "Cuenta bloqueada temporalmente; espera 5 minutos";
        }
        return Verificacion.segundoFactor(cred, codigo, "VERIFICA_TOTP") ? null : "Código incorrecto o ya usado";
    }

    private List<String> nuevosCodigos(long usuarioId) throws Exception {
        List<String> lista = Recuperacion.generarCodigosRespaldo();
        List<String> hashes = new ArrayList<>();
        for (String c : lista) {
            hashes.add(Recuperacion.hashCorto(c.replace("-", "")));
        }
        RepositorioFinanzas.reemplazarCodigosRespaldo(usuarioId, hashes);
        return lista;
    }

    private void mostrarCodigosNuevos(List<String> lista) {
        mostrarCodigos(codigos, lista);
        vistas.show(this, "codigos");
    }

    private void regenerarCodigos() {
        String codigo = codigoRespaldo.getText();
        long id = sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            String error = verificarCuentaYCodigo(codigo);
            return error != null ? (Object) error : nuevosCodigos(id);
        }, r -> {
            codigoRespaldo.setText("");
            if (r instanceof String error) {
                aviso.error(error);
            } else {
                @SuppressWarnings("unchecked")
                List<String> lista = (List<String>) r;
                mostrarCodigosNuevos(lista);
            }
        }, e -> aviso.error(Ui.mensajeError("generar los códigos").apply(e)));
    }

    private void iniciarCambio() {
        String codigo = codigoCambio.getText();
        Tarea.ejecutar(this, () -> verificarCuentaYCodigo(codigo), error -> {
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
        long id = sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            Long paso = Totp.verificar(secreto, codigo, 0);
            if (paso == null) {
                RepositorioUsuarios.registrarFallo(id, "FALLO_TOTP");
                return null;
            }
            RepositorioUsuarios.guardarTotp(id, CifradoTotp.cifrar(secreto), paso);
            return nuevosCodigos(id);
        }, lista -> {
            if (lista == null) {
                codigoNuevo.setText("");
                avisoNuevo.error("El código no coincide con el nuevo autenticador");
                return;
            }
            secretoNuevo = null;
            mostrarCodigosNuevos(lista);
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
            if (!Verificacion.segundoFactor(cred, codigo, "VERIFICA_TOTP")) {
                return "Código incorrecto o ya usado";
            }
            RepositorioUsuarios.cambiarPassword(cred.usuarioId(), Seguridad.hashPassword(claveNueva), "CAMBIO_CLAVE");
            return null;
        }, error -> {
            codigoClave.setText("");
            if (error == null) {
                actual.setText("");
                nueva.setText("");
                confirmacion.setText("");
            }
            resultado(error, "Contraseña actualizada");
        }, e -> aviso.error(Ui.mensajeError("cambiar la contraseña").apply(e)));
    }

    // ───────────────────────── Llaves de acceso (mismas reglas que /perfil/llaves de la web) ─────────────────────────

    private void agregarLlave() {
        String nombreDispositivo = LlavesAcceso.nombreValido(nombreLlave.getText());
        String codigo = codigoLlave.getText();
        avisoLlaves.ocultar();
        if (nombreDispositivo == null) {
            avisoLlaves.error("Escribe un nombre para este dispositivo");
            return;
        }
        Sesion s = sesion();
        java.awt.Window ventanaActual = javax.swing.SwingUtilities.getWindowAncestor(this);
        Tarea.ejecutar(this, () -> {
            String error = verificarCuentaYCodigo(codigo);
            if (error != null) {
                return error;
            }
            List<byte[]> existentes = new ArrayList<>();
            for (RepositorioLlaves.Llave llave : RepositorioLlaves.deUsuario(s.usuarioId())) {
                existentes.add(LlavesAcceso.deB64(llave.credencialId()));
            }
            byte[] datosCliente = LlavesAcceso.datosCliente("webauthn.create", LlavesAcceso.reto());
            try {
                byte[] datos = WindowsHello.crear(ventanaActual, LlavesAcceso.idUsuario(s.usuarioId()), s.correo(),
                        s.nombre(), datosCliente, existentes);
                LlavesAcceso.Registrada nueva = LlavesAcceso.verificarRegistro(datos);
                if (!RepositorioLlaves.guardar(s.usuarioId(), nueva.credencialId(), nueva.clavePublica(),
                        nueva.contador(), nombreDispositivo)) {
                    return "Esa llave de acceso ya está registrada";
                }
            } catch (LlavesAcceso.ErrorLlave e) {
                return e.getMessage();
            }
            return null;
        }, error -> {
            codigoLlave.setText("");
            if (error != null) {
                avisoLlaves.error(error);
            } else {
                nombreLlave.setText("");
                resultado(null, "Llave de acceso guardada");
            }
        }, e -> avisoLlaves.error(Ui.mensajeError("guardar la llave de acceso").apply(e)));
    }

    private void eliminarLlave(long llaveId) {
        long id = sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            RepositorioLlaves.eliminar(id, llaveId);
            return null;
        }, r -> resultado(null, "Llave de acceso eliminada"),
                e -> aviso.error(Ui.mensajeError("eliminar la llave de acceso").apply(e)));
    }
}
