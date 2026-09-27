package com.ascode.android.utility;

import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

import androidx.cardview.widget.CardView;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ViewBean;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Gx;
import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Atributos de forma (corner radius + stroke) para widgets que NO los soportan de forma nativa
 * (p.ej. LinearLayout o ImageView).
 *
 * <p>Los widgets de las familias CardView / MaterialButton / CircleImageView / TabLayout ya tienen
 * sus propios appliers en los tres motores; esta clase NO los toca. Para el resto la unica via es
 * un <b>shape drawable</b>: en el XML compilado se escribe en los recursos del proyecto y se
 * referencia con {@code android:background="@drawable/..."}; el lienzo y la vista previa construyen
 * el {@link GradientDrawable} equivalente en memoria para que los tres motores coincidan.
 *
 * <p>Los valores se guardan en el mismo campo que el resto de atributos "extra" del bean
 * ({@link ViewBean#inject}), reutilizando los nombres que ya usan MaterialButton/CardView
 * ({@code app:cornerRadius}, {@code app:strokeWidth}, {@code app:strokeColor}) para no inventar
 * atributos nuevos. Cuando un valor no esta definido simplemente NO se escribe (asi el centinela de
 * "color sin definir" del resto de motores se mantiene intacto).
 */
public final class DesignShapeAttrs {

    public static final String ATTR_CORNER_RADIUS = "cornerRadius";
    public static final String ATTR_STROKE_WIDTH = "strokeWidth";
    public static final String ATTR_STROKE_COLOR = "strokeColor";

    // Fase 2: gradiente de 2 colores (mismo sitio que el resto de la forma).
    public static final String ATTR_GRADIENT_START = "gradientStart";
    public static final String ATTR_GRADIENT_END = "gradientEnd";
    public static final String ATTR_GRADIENT_ORIENTATION = "gradientOrientation";

    /** Valores canonicos de orientacion del gradiente (los mismos que ofrece el panel). */
    public static final String GRADIENT_VERTICAL = "vertical";
    public static final String GRADIENT_HORIZONTAL = "horizontal";
    /** Diagonal de arriba-izquierda a abajo-derecha (\u2198). */
    public static final String GRADIENT_DIAGONAL_DOWN = "diagonal_down";
    /** Diagonal de arriba-derecha a abajo-izquierda (\u2199). */
    public static final String GRADIENT_DIAGONAL_UP = "diagonal_up";
    public static final String[] GRADIENT_ORIENTATIONS = {
            GRADIENT_VERTICAL, GRADIENT_HORIZONTAL, GRADIENT_DIAGONAL_DOWN, GRADIENT_DIAGONAL_UP};

    // Modo glass: preset translucido + borde fino claro + esquinas redondeadas (+ blur del propio
    // widget en API >= 31). El desenfoque de lo que hay DETRAS de la vista no es posible de forma
    // nativa: aqui solo se difumina el propio widget.
    public static final String ATTR_GLASS = "glass";
    public static final String ATTR_GLASS_ALPHA = "glassAlpha";
    public static final String ATTR_GLASS_BLUR = "glassBlur";

    /** Alfa por defecto del fondo glass (0..255): ~20% (blanco translucido). */
    public static final int DEFAULT_GLASS_ALPHA = 0x33;
    public static final int DEFAULT_GLASS_CORNER_DP = 16;
    public static final int DEFAULT_GLASS_STROKE_DP = 1;
    /** Borde por defecto del glass: blanco al 40%. */
    public static final int DEFAULT_GLASS_STROKE_COLOR = 0x66FFFFFF;
    public static final int DEFAULT_GLASS_BLUR_DP = 16;

    /** Centinela de "color de fondo sin definir" del bean (mismo que usa Ox/ViewPane). */
    public static final int COLOR_NOT_SET = 0xffffff;

    private DesignShapeAttrs() {
    }

    // ---------------------------------------------------------------- nombres de atributo

    /** Nombre del atributo "corner radius" para este tipo de widget (fase 1: mismo para todos). */
    public static String cornerRadiusAttribute(ViewBean bean) {
        return ATTR_CORNER_RADIUS;
    }

    public static String strokeWidthAttribute(ViewBean bean) {
        return ATTR_STROKE_WIDTH;
    }

    public static String strokeColorAttribute(ViewBean bean) {
        return ATTR_STROKE_COLOR;
    }

    // ---------------------------------------------------------------- lectura / escritura en inject

    /** ¿El bean define alguno de los tres atributos de forma? */
    public static boolean hasAnyShape(String inject) {
        if (inject == null || inject.isEmpty()) {
            return false;
        }
        return contains(inject, ATTR_CORNER_RADIUS)
                || contains(inject, ATTR_STROKE_WIDTH)
                || contains(inject, ATTR_STROKE_COLOR)
                || contains(inject, ATTR_GRADIENT_START)
                || contains(inject, ATTR_GRADIENT_END)
                || contains(inject, ATTR_GRADIENT_ORIENTATION)
                || contains(inject, ATTR_GLASS)
                || contains(inject, ATTR_GLASS_ALPHA)
                || contains(inject, ATTR_GLASS_BLUR);
    }

    /** Todos los atributos de forma que NO deben acabar como atributos XML del widget. */
    private static final String[] SHAPE_ATTRIBUTES = {
            ATTR_CORNER_RADIUS, ATTR_STROKE_WIDTH, ATTR_STROKE_COLOR,
            ATTR_GRADIENT_START, ATTR_GRADIENT_END, ATTR_GRADIENT_ORIENTATION,
            ATTR_GLASS, ATTR_GLASS_ALPHA, ATTR_GLASS_BLUR};

    /** Entero del inject (0 si no esta definido o no es un numero). */
    public static int getInt(String inject, String name, int fallback) {
        String raw = inject == null ? "" : raw(inject, name);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String raw(String inject, String name) {
        Matcher matcher = pattern(name).matcher(inject);
        return matcher.find() ? matcher.group(1) : "";
    }

    // ---------------------------------------------------------------- gradiente

    /** ¿El bean define un gradiente completo (los dos colores)? */
    public static boolean hasGradient(ViewBean bean) {
        return bean != null
                && !get(bean, ATTR_GRADIENT_START).trim().isEmpty()
                && !get(bean, ATTR_GRADIENT_END).trim().isEmpty();
    }

    public static String gradientOrientation(ViewBean bean) {
        String value = get(bean, ATTR_GRADIENT_ORIENTATION).trim();
        return value.isEmpty() ? GRADIENT_VERTICAL : value;
    }

    /**
     * Angulo {@code android:angle} equivalente a la orientacion elegida. Android exige multiplos de
     * 45: 0 = izquierda->derecha, 45 = abajo-izq->arriba-der, 135 = abajo-der->arriba-izq,
     * 270 = arriba->abajo.
     */
    public static int gradientAngle(String orientation) {
        String value = orientation == null ? "" : orientation.trim().toLowerCase(Locale.US);
        return switch (value) {
            case GRADIENT_HORIZONTAL -> 0;
            case GRADIENT_DIAGONAL_DOWN -> 45;
            case GRADIENT_DIAGONAL_UP -> 135;
            default -> 270;
        };
    }

    /** Orientacion equivalente del {@link GradientDrawable} del lienzo/vista previa. */
    public static GradientDrawable.Orientation gradientDrawableOrientation(String orientation) {
        return switch (gradientAngle(orientation)) {
            case 0 -> GradientDrawable.Orientation.LEFT_RIGHT;
            case 45 -> GradientDrawable.Orientation.BL_TR;
            case 135 -> GradientDrawable.Orientation.BR_TL;
            default -> GradientDrawable.Orientation.TOP_BOTTOM;
        };
    }

    // ---------------------------------------------------------------- glass

    /** ¿El widget lleva activado el modo glass? */
    public static boolean isGlass(String inject) {
        if (inject == null || inject.isEmpty() || !contains(inject, ATTR_GLASS)) {
            return false;
        }
        String value = raw(inject, ATTR_GLASS).trim();
        return !"false".equalsIgnoreCase(value) && !"0".equals(value) && !"none".equalsIgnoreCase(value);
    }

    /** Alfa (0..255) del fondo glass; por defecto ~20%. */
    public static int glassAlpha(String inject) {
        return clamp(getInt(inject, ATTR_GLASS_ALPHA, DEFAULT_GLASS_ALPHA), 0, 255);
    }

    /** Radio de blur (dp) del glass; 0 desactiva el blur aunque el modo siga activo. */
    public static int glassBlurDp(String inject) {
        return Math.max(0, getInt(inject, ATTR_GLASS_BLUR, DEFAULT_GLASS_BLUR_DP));
    }

    /** Color de fondo translucido del glass (#AARRGGBB con el alfa configurado). */
    public static int glassFillColor(String inject) {
        return (glassAlpha(inject) << 24) | 0x00FFFFFF;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Aplica el blur del PROPIO widget en API >= 31 (RenderEffect). En API < 31 no hace nada y
     * limpia cualquier efecto previo: el glass queda como translucido + borde + esquinas, sin
     * excepciones. Devuelve {@code true} si el blur quedo aplicado.
     */
    public static boolean applyGlassBlur(View view, String inject) {
        if (view == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        try {
            int blurDp = glassBlurDp(inject);
            if (blurDp <= 0) {
                view.setRenderEffect(null);
                return false;
            }
            float radius = blurDp * view.getResources().getDisplayMetrics().density;
            view.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            return true;
        } catch (Throwable throwable) {
            try {
                view.setRenderEffect(null);
            } catch (Throwable ignored) {
                // sin efecto previo que limpiar
            }
            return false;
        }
    }

    /** Limpia el blur del widget si el modo glass ya no esta activo (API < 31: no hace nada). */
    public static void clearGlassBlur(View view) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        try {
            view.setRenderEffect(null);
        } catch (Throwable ignored) {
            // nada que limpiar
        }
    }

    private static boolean contains(String inject, String name) {
        return pattern(name).matcher(inject).find();
    }

    /** Valor actual de un atributo de forma ("" si no esta definido). */
    public static String get(ViewBean bean, String name) {
        if (bean == null || bean.inject == null) {
            return "";
        }
        Matcher matcher = pattern(name).matcher(bean.inject);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Fija (o elimina, con {@code value} vacio) un atributo de forma en el {@code inject} del bean,
     * conservando el resto de atributos. Idempotente.
     */
    public static void set(ViewBean bean, String name, String value) {
        if (bean == null) {
            return;
        }
        String inject = bean.inject == null ? "" : bean.inject;
        inject = pattern(name).matcher(inject).replaceAll("");
        inject = inject.replaceAll("\\n{2,}", "\n").trim();
        if (value != null && !value.trim().isEmpty()) {
            if (!inject.isEmpty()) {
                inject = inject + "\n";
            }
            inject = inject + "app:" + name + "=\"" + value.trim() + "\"";
        }
        bean.inject = inject;
    }

    /** Elimina un atributo de forma del bean. */
    public static void remove(ViewBean bean, String name) {
        set(bean, name, "");
    }

    /**
     * Quita del texto de "inject" los atributos de forma. Se usa al generar el XML: en un widget
     * sin soporte nativo la forma se representa con el shape drawable (`android:background`), y
     * escribir ademas `app:cornerRadius` / `app:strokeWidth` / `app:strokeColor` rompe el link de
     * recursos en proyectos que no incluyen la libreria Material (donde esos atributos no existen).
     */
    public static String stripShapeAttributes(String inject) {
        if (inject == null || inject.isEmpty()) {
            return inject;
        }
        String result = inject;
        for (String name : SHAPE_ATTRIBUTES) {
            result = pattern(name).matcher(result).replaceAll("");
        }
        return result.replaceAll("\n{2,}", "\n").trim();
    }

    private static Pattern pattern(String name) {
        // "app:cornerRadius="..." o "android:cornerRadius="..." en cualquier posicion del inject.
        return Pattern.compile("(?:app|android)\\s*:\\s*" + Pattern.quote(name) + "\\s*=\\s*\"([^\"]*)\"");
    }

    // ---------------------------------------------------------------- que widgets lo usan

    /**
     * ¿Este widget puede llevar forma propia? Se excluyen las familias que ya la aplican con sus
     * propios appliers en los tres motores (CardView / MaterialButton / TabLayout / CircleImageView).
     */
    public static boolean supportsShapeBackground(View view) {
        return view != null
                && !(view instanceof CardView)
                && !(view instanceof MaterialButton)
                && !(view instanceof TabLayout)
                && !(view instanceof CircleImageView);
    }

    /** Version para el generador de XML, donde solo tenemos el bean (sin la vista instanciada). */
    public static boolean supportsShapeBackgroundType(ViewBean bean) {
        if (bean == null) {
            return false;
        }
        Gx info = bean.getClassInfo();
        if (info == null) {
            return false;
        }
        return !info.a("CardView")
                && !info.a("MaterialButton")
                && !info.a("TabLayout")
                && !info.a("CircleImageView");
    }

    /** Widgets que en la fase 1 exponen la UI dedicada de forma (LinearLayout / ImageView). */
    public static boolean isPhase1TargetType(ViewBean bean) {
        if (bean == null) {
            return false;
        }
        Gx info = bean.getClassInfo();
        if (info == null) {
            return false;
        }
        return info.a("LinearLayout") || info.a("ImageView");
    }

    // ---------------------------------------------------------------- dibujo en memoria

    /** Un color cuenta como "definido" si no es el centinela y no es transparente. */
    public static boolean isColorDefined(int color) {
        return color != COLOR_NOT_SET && Color.alpha(color) != 0;
    }

    /**
     * Construye el drawable equivalente al shape que se generara en el XML.
     *
     * @param radiusPx      radio de esquina en px (0 = sin esquinas)
     * @param strokeWidthPx grosor del borde en px (0 = sin borde)
     * @param strokeColor   color del borde (alfa 0 = sin borde)
     * @param fillColor     relleno; {@link Color#TRANSPARENT} para solo-borde
     */
    public static GradientDrawable buildDrawable(int radiusPx, int strokeWidthPx, int strokeColor, int fillColor) {
        return buildDrawable(radiusPx, strokeWidthPx, strokeColor, fillColor, COLOR_NOT_SET, COLOR_NOT_SET, null);
    }

    /**
     * Igual que {@link #buildDrawable(int, int, int, int)} pero con gradiente de dos colores
     * opcional. Si los dos colores estan definidos se pinta el gradiente; si solo hay uno, se usa
     * como color solido; si no hay ninguno, se usa {@code fillColor} (comportamiento actual).
     *
     * @param gradientStart color inicial ya resuelto por el motor que llama ({@code COLOR_NOT_SET}
     *                      = sin definir)
     * @param gradientEnd   color final ya resuelto ({@code COLOR_NOT_SET} = sin definir)
     */
    public static GradientDrawable buildDrawable(int radiusPx, int strokeWidthPx, int strokeColor, int fillColor,
                                                 int gradientStart, int gradientEnd, String gradientOrientation) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        if (radiusPx > 0) {
            drawable.setCornerRadius(radiusPx);
        }
        if (strokeWidthPx > 0 && Color.alpha(strokeColor) != 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
        boolean startDefined = isColorDefined(gradientStart);
        boolean endDefined = isColorDefined(gradientEnd);
        if (startDefined && endDefined) {
            drawable.setOrientation(gradientDrawableOrientation(gradientOrientation));
            drawable.setColors(new int[]{gradientStart, gradientEnd});
        } else if (startDefined) {
            drawable.setColor(gradientStart);
        } else if (endDefined) {
            drawable.setColor(gradientEnd);
        } else {
            drawable.setColor(fillColor);
        }
        return drawable;
    }

    // ---------------------------------------------------------------- XML del recurso

    /** Nombre estable del drawable generado para un widget (se usa como @drawable/&lt;name&gt;). */
    public static String drawableName(String viewId) {
        String safe = viewId == null ? "view" : viewId.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isEmpty()) {
            safe = "view";
        }
        if (Character.isDigit(safe.charAt(0))) {
            safe = "v" + safe;
        }
        return "designshape_" + safe.toLowerCase(Locale.US);
    }

    /**
     * XML del {@code <shape>} equivalente al bean. Devuelve {@code null} si el bean no tiene forma.
     *
     * <p>El relleno sale del fondo del propio bean (color literal o {@code @color/...}) para que el
     * shape no cambie el color que el usuario ya tenia; si no hay fondo definido, es transparente.
     */
    public static String buildShapeDrawableXml(ViewBean bean) {
        if (bean == null || !hasAnyShape(bean.inject)) {
            return null;
        }
        LayoutBean layout = bean.layout;
        boolean glass = isGlass(bean.inject);
        String gradientStart = get(bean, ATTR_GRADIENT_START).trim();
        String gradientEnd = get(bean, ATTR_GRADIENT_END).trim();
        String radius = get(bean, ATTR_CORNER_RADIUS).trim();
        String strokeWidth = get(bean, ATTR_STROKE_WIDTH).trim();
        String strokeColor = get(bean, ATTR_STROKE_COLOR).trim();

        // El glass rellena con corner/borde por defecto si el usuario no los eligio.
        if (glass) {
            if (radius.isEmpty()) {
                radius = DEFAULT_GLASS_CORNER_DP + "dp";
            }
            if (strokeWidth.isEmpty()) {
                strokeWidth = DEFAULT_GLASS_STROKE_DP + "dp";
            }
            if (strokeColor.isEmpty()) {
                strokeColor = formatHex(DEFAULT_GLASS_STROKE_COLOR);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    android:shape=\"rectangle\">\n");

        // Relleno: gradiente si hay dos colores; si no, un color solido (glass -> blanco translucido;
        // un unico color de gradiente -> ese color; si no, el fondo que ya tenia el bean).
        if (!gradientStart.isEmpty() && !gradientEnd.isEmpty()) {
            sb.append("    <gradient\n");
            sb.append("        android:startColor=\"").append(formatColorReference(gradientStart)).append("\"\n");
            sb.append("        android:endColor=\"").append(formatColorReference(gradientEnd)).append("\"\n");
            sb.append("        android:angle=\"").append(gradientAngle(gradientOrientation(bean))).append("\" />\n");
        } else {
            String solidColor = null;
            if (!gradientStart.isEmpty()) {
                solidColor = formatColorReference(gradientStart);
            } else if (!gradientEnd.isEmpty()) {
                solidColor = formatColorReference(gradientEnd);
            } else if (glass) {
                solidColor = formatHex(glassFillColor(bean.inject));
            } else if (layout != null) {
                if (layout.backgroundResColor != null && !layout.backgroundResColor.isEmpty()) {
                    solidColor = formatColorReference(layout.backgroundResColor);
                } else if (isColorDefined(layout.backgroundColor)) {
                    solidColor = formatHex(layout.backgroundColor);
                }
            }
            sb.append("    <solid android:color=\"")
                    .append(solidColor == null ? "@android:color/transparent" : solidColor)
                    .append("\" />\n");
        }

        if (!radius.isEmpty()) {
            sb.append("    <corners android:radius=\"").append(normalizeDimen(radius)).append("\" />\n");
        }
        if (!strokeWidth.isEmpty() && !strokeColor.isEmpty()) {
            sb.append("    <stroke\n");
            sb.append("        android:width=\"").append(normalizeDimen(strokeWidth)).append("\"\n");
            sb.append("        android:color=\"").append(formatColorReference(strokeColor)).append("\" />\n");
        }
        sb.append("</shape>\n");
        return sb.toString();
    }

    /**
     * Fichero del shape drawable en los recursos del PROYECTO (files/resource/drawable). Es el
     * mismo nombre que genera el compilador en el res del build, de modo que la vista previa y el
     * proyecto exportado usan exactamente el mismo fichero.
     */
    public static java.io.File projectDrawableFile(String scId, String layoutXmlName, ViewBean bean) {
        return new java.io.File(new FilePathUtil().getPathResource(scId) + "/drawable/"
                + drawableName(layoutXmlName + "_" + bean.id) + ".xml");
    }

    /**
     * Mantiene sincronizado el shape drawable del proyecto con el estado del bean (lo escribe, o lo
     * borra si el bean ya no tiene forma). Se llama al editar propiedades para que la vista previa
     * vea el mismo drawable que compilara el proyecto.
     */
    public static void syncProjectDrawable(String scId, String layoutXmlName, ViewBean bean) {
        if (bean == null || scId == null || layoutXmlName == null || !supportsShapeBackgroundType(bean)) {
            return;
        }
        java.io.File file = projectDrawableFile(scId, layoutXmlName, bean);
        String xml = hasAnyShape(bean.inject) ? buildShapeDrawableXml(bean) : null;
        if (xml == null) {
            if (file.exists()) {
                file.delete();
            }
            return;
        }
        java.io.File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        FileUtil.writeFile(file.getAbsolutePath(), xml);
    }

    // ---------------------------------------------------------------- utilidades de formato

    /** "#RRGGBB" (o "#AARRGGBB" si lleva alfa) desde un color int del bean, igual que Ox.formatColor. */
    public static String formatHex(int color) {
        int alpha = (color >> 24) & 0xff;
        if (alpha != 0xff) {
            return String.format(Locale.US, "#%08X", color);
        }
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    /**
     * Acepta "#RRGGBB" / "#AARRGGBB" / "@color/x" / "?attr/x" y devuelve un valor listo para el XML
     * del drawable (los hexadecimales se normalizan a 6/8 digitos).
     */
    public static String formatColorReference(String value) {
        String v = value == null ? "" : value.trim();
        if (v.startsWith("@") || v.startsWith("?")) {
            return v;
        }
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            if (hex.length() == 3) {
                StringBuilder expanded = new StringBuilder("#");
                for (char c : hex.toCharArray()) {
                    expanded.append(c).append(c);
                }
                return expanded.toString().toUpperCase(Locale.US);
            }
            return ("#" + hex).toUpperCase(Locale.US);
        }
        return v;
    }

    /** Anade "dp" a un numero suelto; deja intactas las unidades existentes. */
    public static String normalizeDimen(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return v;
        }
        if (v.matches("-?\\d+(\\.\\d+)?")) {
            return v + "dp";
        }
        return v;
    }
}
