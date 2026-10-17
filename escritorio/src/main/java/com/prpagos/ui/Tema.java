package com.prpagos.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Colores y aspecto de la aplicación. Como todo proyecto no destacado del
 * portafolio, combina el color del motor de base de datos (rojo Oracle:
 * acciones principales, marca, sello) con el de la plataforma donde se
 * despliega la web (azul de Google Cloud Run: enlaces, menú, foco, acciones
 * secundarias). Es la misma paleta de la web
 * (web/app/static/estilos.css y acceso.css), en tema claro y oscuro, para
 * que las dos interfaces se vean como un solo producto. Si se cambia un
 * color allá, hay que cambiarlo aquí.
 *
 * El aspecto moderno (bordes redondeados, campos, tablas) lo da FlatLaf; los
 * colores se le pasan como "extra defaults" y como clases de estilo
 * (botones primario y secundario, menú).
 */
public final class Tema {

    /** Tokens de color: mismos nombres que las variables CSS de la web. */
    public record Paleta(
            Color fondo, Color superficie, Color texto, Color textoSuave, Color borde,
            Color acento, Color acentoSuave, Color enlace, Color plataforma, Color plataformaSuave,
            Color errorFondo, Color errorBorde, Color errorTexto,
            Color exitoFondo, Color exitoBorde, Color exitoTexto,
            Color accesoFondo, Color marcaFondo, Color marcaTexto, Color marcaSuave,
            Color trama, Color papel, Color papelBorde) {
    }

    public static final Paleta CLARO = new Paleta(
            hex("#f5f6fa"), hex("#ffffff"), hex("#1f2430"), hex("#5a6072"), hex("#dde1ea"),
            hex("#c74634"), hex("#fbe7e4"), hex("#a83a2b"), hex("#1a73e8"), hex("#e8f0fe"),
            hex("#fdeaea"), hex("#e0a1a1"), hex("#a8221a"),
            hex("#ecfdf3"), hex("#9bd8b6"), hex("#067647"),
            hex("#f7f5f4"), hex("#fbeeeb"), hex("#2a1714"), hex("#7b5a55"),
            new Color(199, 70, 52, 18), hex("#ffffff"), hex("#ebd9d5"));

    public static final Paleta OSCURO = new Paleta(
            hex("#14161f"), hex("#1d2130"), hex("#e9ebf3"), hex("#a3a9bd"), hex("#2c3145"),
            hex("#c74634"), hex("#3a1f1c"), hex("#f08a7a"), hex("#8ab4f8"), hex("#1c2b45"),
            hex("#3a2222"), hex("#6b3535"), hex("#fda29b"),
            hex("#12291f"), hex("#2c6a4c"), hex("#75e0a7"),
            hex("#14161f"), hex("#1a1218"), hex("#f6edeb"), hex("#b9a3a0"),
            new Color(240, 138, 122, 15), hex("#231c24"), hex("#3a2c33"));

    private static final Preferences PREFERENCIAS = Preferences.userNodeForPackage(Tema.class);
    private static final List<Runnable> OYENTES = new ArrayList<>();
    private static boolean oscuro;

    private Tema() {
    }

    /** Aplica el tema guardado (o el claro la primera vez). Llamar antes de crear ventanas. */
    public static void iniciar() {
        oscuro = PREFERENCIAS.getBoolean("oscuro", false);
        aplicar();
    }

    public static Paleta c() {
        return oscuro ? OSCURO : CLARO;
    }

    public static boolean esOscuro() {
        return oscuro;
    }

    /** El botón 🌓: cambia de tema en todas las ventanas abiertas y lo recuerda. */
    public static void alternar() {
        oscuro = !oscuro;
        PREFERENCIAS.putBoolean("oscuro", oscuro);
        aplicar();
        FlatLaf.updateUI();
        for (Runnable oyente : new ArrayList<>(OYENTES)) {
            oyente.run();
        }
    }

    /** Para componentes con colores puestos a mano: se vuelven a pintar al cambiar de tema. */
    public static void alCambiar(Runnable oyente) {
        OYENTES.add(oyente);
        oyente.run();
    }

    public static void quitar(Runnable oyente) {
        OYENTES.remove(oyente);
    }

    private static void aplicar() {
        Paleta p = c();
        Map<String, String> d = new HashMap<>();
        d.put("@accentColor", css(p.plataforma()));
        d.put("@background", css(p.fondo()));
        d.put("@foreground", css(p.texto()));
        d.put("@disabledForeground", css(p.textoSuave()));
        d.put("Component.arc", "8");
        d.put("Button.arc", "8");
        d.put("TextComponent.arc", "8");
        d.put("Component.focusWidth", "1");
        d.put("Component.borderColor", css(p.borde()));
        d.put("Component.focusedBorderColor", css(p.plataforma()));
        d.put("Component.focusColor", css(p.plataformaSuave()));
        // Como la web: los campos llevan el fondo del tema (input { background: var(--fondo) });
        // en la portada de acceso van en blanco (ver VentanaLogin).
        d.put("TextField.background", css(p.fondo()));
        d.put("PasswordField.background", css(p.fondo()));
        d.put("FormattedTextField.background", css(p.fondo()));
        d.put("TextField.margin", "6,10,6,10");
        d.put("PasswordField.margin", "6,10,6,10");
        d.put("Table.background", css(p.superficie()));
        d.put("Table.alternateRowColor", css(p.superficie()));
        d.put("Table.gridColor", css(p.borde()));
        d.put("Table.showHorizontalLines", "true");
        d.put("Table.showVerticalLines", "false");
        d.put("Table.rowHeight", "36");
        d.put("Table.selectionBackground", css(p.plataformaSuave()));
        d.put("Table.selectionForeground", css(p.texto()));
        d.put("Table.selectionInactiveBackground", css(p.plataformaSuave()));
        d.put("Table.selectionInactiveForeground", css(p.texto()));
        d.put("TableHeader.background", css(p.superficie()));
        d.put("TableHeader.foreground", css(p.textoSuave()));
        d.put("TableHeader.separatorColor", css(p.superficie()));
        d.put("TableHeader.bottomSeparatorColor", css(p.borde()));
        d.put("TableHeader.height", "36");
        d.put("ScrollPane.background", css(p.superficie()));
        d.put("Viewport.background", css(p.superficie()));
        d.put("OptionPane.background", css(p.superficie()));
        d.put("Panel.background", css(p.fondo()));
        d.put("PopupMenu.background", css(p.superficie()));
        d.put("ToolTip.background", css(p.superficie()));
        d.put("ScrollBar.width", "10");
        // Clases de estilo: el mismo botón .boton y .boton-secundario de la web.
        d.put("[style]Button.primario",
                "background: " + css(p.acento()) + "; foreground: #ffffff; hoverBackground: #b23e2e;"
                + " pressedBackground: #a83a2b; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0;"
                + " margin: 8,18,8,18; font: bold");
        d.put("[style]Button.secundario",
                "background: " + css(p.superficie()) + "; foreground: " + css(p.plataforma()) + ";"
                + " borderColor: " + css(p.plataforma()) + "; hoverBorderColor: " + css(p.plataforma()) + ";"
                + " hoverBackground: " + css(p.plataformaSuave()) + "; focusWidth: 0; innerFocusWidth: 0;"
                + " margin: 8,18,8,18");
        d.put("[style]Button.enlace",
                "foreground: " + css(p.plataforma()) + "; background: null; borderWidth: 0; focusWidth: 0;"
                + " innerFocusWidth: 0; margin: 2,2,2,2; hoverBackground: null; pressedBackground: null");
        d.put("[style]Button.redondo",
                "background: " + css(p.fondo()) + "; borderColor: " + css(p.borde()) + "; arc: 999;"
                + " focusWidth: 0; innerFocusWidth: 0; margin: 4,4,4,4");
        FlatLaf.setGlobalExtraDefaults(d);
        if (oscuro) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }

    private static Color hex(String valor) {
        return Color.decode(valor);
    }

    private static String css(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
