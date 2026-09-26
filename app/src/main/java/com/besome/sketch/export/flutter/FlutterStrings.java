package com.besome.sketch.export.flutter;

import android.util.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.wq;

/**
 * Cadenas del proyecto Android ({@code res/values/strings.xml}) para el export a Flutter
 * (Fase 3).
 *
 * <p>Lee el fichero {@code .AndroidSCode/data/<scId>/files/resource/values/strings.xml} del
 * proyecto y lo expone como un mapa {@code clave -> valor}. Las cadenas que de verdad usa la
 * logica o los layouts se registran con {@link #dart(String)} y acaban en {@code lib/strings.dart}
 * (una sola vez por clave).</p>
 *
 * <p>Cubre los bloques {@code getResStr} (Sketchware de serie) y {@code getResString} (addon), y
 * las referencias {@code @string/<clave>} que el editor guarda en textos/hints de los layouts.</p>
 */
public class FlutterStrings {

    private static final String TAG = "FlutterStrings";

    /**
     * Prefijo de las referencias a recursos de tipo cadena guardadas en las propiedades de vista
     * ({@code ViewBean.text.text == "@string/foo"}).
     */
    public static final String REFERENCE_PREFIX = "@string/";

    private static final Pattern STRING_TAG = Pattern.compile(
            "<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL);

    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();
    private final LinkedHashSet<String> used = new LinkedHashSet<>();

    /**
     * @param scId       id del proyecto.
     * @param appName    nombre de la app; se registra siempre como {@code app_name} (igual que
     *                   hace la exportacion Android, que a\u00f1ade esa cadena al {@code strings.xml}).
     */
    public FlutterStrings(String scId, String appName) {
        values.put("app_name", appName == null ? "" : appName);
        try {
            File file = new File(wq.b(scId), "files/resource/values/strings.xml");
            if (file.isFile()) {
                String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                Matcher matcher = STRING_TAG.matcher(xml);
                while (matcher.find()) {
                    values.put(matcher.group(1), unescapeXml(matcher.group(2)));
                }
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo leer strings.xml del proyecto " + scId, throwable);
        }
    }

    /**
     * @return {@code true} si el valor es una referencia {@code @string/<clave>}.
     */
    public boolean isReference(String value) {
        return value != null && value.startsWith(REFERENCE_PREFIX);
    }

    /**
     * @return la clave de una referencia {@code @string/<clave>}.
     */
    public String referenceKey(String value) {
        return value.substring(REFERENCE_PREFIX.length());
    }

    /**
     * @return el valor de una clave, o {@code null} si no existe.
     */
    public String value(String key) {
        return values.get(key);
    }

    /**
     * Registra el uso de una clave y devuelve el codigo Dart equivalente.
     *
     * @param key clave de {@code strings.xml} (puede venir de una referencia {@code @string/...}).
     * @return {@code Sk.resStr('<clave>')}.
     */
    public String dart(String key) {
        String name = key == null ? "" : key;
        used.add(name);
        return "Sk.resStr('" + escape(name) + "')";
    }

    /**
     * Registra el uso de una clave sin devolver codigo (para textos que se resuelven en Java).
     */
    public void use(String key) {
        if (key != null) {
            used.add(key);
        }
    }

    /**
     * @return mapa (en orden de uso) de las claves realmente usadas con su valor resuelto.
     */
    public LinkedHashMap<String, String> usedStrings() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String key : used) {
            String value = values.get(key);
            result.put(key, value == null ? key : value);
        }
        return result;
    }

    /**
     * @return todas las claves conocidas del {@code strings.xml}.
     */
    public Map<String, String> all() {
        return values;
    }

    // ---------------------------------------------------------------- utilidades

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '$' -> sb.append("\\$");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replaceAll("\\\\@", "@")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }
}
