package com.ascode.android.utility;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
                || contains(inject, ATTR_STROKE_COLOR);
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
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        if (radiusPx > 0) {
            drawable.setCornerRadius(radiusPx);
        }
        if (strokeWidthPx > 0 && Color.alpha(strokeColor) != 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
        drawable.setColor(fillColor);
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
        String radius = get(bean, ATTR_CORNER_RADIUS);
        String strokeWidth = get(bean, ATTR_STROKE_WIDTH);
        String strokeColor = get(bean, ATTR_STROKE_COLOR);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    android:shape=\"rectangle\">\n");

        // solid (relleno) a partir del fondo del bean.
        if (layout != null) {
            if (layout.backgroundResColor != null && !layout.backgroundResColor.isEmpty()) {
                sb.append("    <solid android:color=\"").append(formatColorReference(layout.backgroundResColor)).append("\" />\n");
            } else if (isColorDefined(layout.backgroundColor)) {
                sb.append("    <solid android:color=\"").append(formatHex(layout.backgroundColor)).append("\" />\n");
            } else {
                sb.append("    <solid android:color=\"@android:color/transparent\" />\n");
            }
        } else {
            sb.append("    <solid android:color=\"@android:color/transparent\" />\n");
        }

        if (!radius.trim().isEmpty()) {
            sb.append("    <corners android:radius=\"").append(normalizeDimen(radius)).append("\" />\n");
        }
        if (!strokeWidth.trim().isEmpty() && !strokeColor.trim().isEmpty()) {
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
