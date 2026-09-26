package com.besome.sketch.export.flutter;

import android.util.Log;

import com.besome.sketch.beans.ProjectResourceBean;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import a.a.a.jC;
import a.a.a.kC;
import a.a.a.wq;

/**
 * Recoge los recursos (imagenes y fuentes) que usan los layouts del proyecto y los prepara para
 * copiarlos al {@code assets/} del proyecto Flutter exportado (Fase 2).
 *
 * <p>Espejo de la gestion de recursos del editor ({@code pu.java} para imagenes y
 * {@code kC} para el resto): los {@link ProjectResourceBean} se leen del gestor de recursos del
 * proyecto ({@link jC#d(String)}) y los ficheros de disco de las carpetas
 * {@code .AndroidSCode/resources/images/<scId>} (imagenes) y
 * {@code .AndroidSCode/resources/fonts/<scId>} (fuentes). Si un recurso no se encuentra en disco
 * se devuelve {@code null} y el bloque que lo referencia sigue emitiendo un placeholder + TODO.</p>
 *
 * <p>Los recursos se registran de forma perezosa: solo se copian los que algun layout usa de
 * verdad, porque {@link DartWidgets} llama a {@link #imageAsset(String)} /
 * {@link #fontFamily(String)} durante la generacion de cada pantalla.</p>
 */
public class FlutterAssets {

    private static final String TAG = "FlutterAssets";

    /**
     * Extensiones que {@code Image.asset} puede mostrar directamente.
     */
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "webp", "gif", "bmp"));

    /**
     * Extensiones de fuentes validas como asset de Flutter.
     */
    private static final Set<String> FONT_EXTENSIONS = new HashSet<>(Arrays.asList("ttf", "otf"));

    /**
     * Un fichero a copiar dentro del zip: ruta relativa en el proyecto Flutter y origen en disco.
     */
    public static final class AssetFile {
        public final String zipPath;
        public final File source;

        AssetFile(String zipPath, File source) {
            this.zipPath = zipPath;
            this.source = source;
        }
    }

    private final Map<String, ProjectResourceBean> imagesByName = new LinkedHashMap<>();
    private final Map<String, ProjectResourceBean> fontsByName = new LinkedHashMap<>();
    private final Map<String, String> imageAssets = new LinkedHashMap<>();
    private final Map<String, String> fontFamilies = new LinkedHashMap<>();
    private final Map<String, String> fontAssets = new LinkedHashMap<>();
    private final LinkedHashMap<String, AssetFile> assetFiles = new LinkedHashMap<>();
    private final Set<String> missingImages = new LinkedHashSet<>();
    private final Set<String> missingFonts = new LinkedHashSet<>();
    private String imagesDirectory = "";
    private String fontsDirectory = "";

    /**
     * @param scId id del proyecto (los mismos datos que usan {@code eC}, {@code hC} y {@code kC}).
     */
    public FlutterAssets(String scId) {
        try {
            kC resources = jC.d(scId);
            if (resources == null) {
                return;
            }
            imagesDirectory = nullToEmpty(resources.l());
            fontsDirectory = nullToEmpty(resources.j());
            if (resources.b != null) {
                for (ProjectResourceBean bean : resources.b) {
                    if (bean != null && bean.resName != null) {
                        imagesByName.put(bean.resName, bean);
                    }
                }
            }
            if (resources.d != null) {
                for (ProjectResourceBean bean : resources.d) {
                    if (bean != null && bean.resName != null) {
                        fontsByName.put(bean.resName, bean);
                    }
                }
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudieron leer los recursos del proyecto " + scId, throwable);
        }
    }

    /**
     * Resuelve una imagen del proyecto a una ruta dentro de {@code assets/}.
     *
     * @param resName nombre del recurso tal y como lo guarda el layout ({@code ViewBean.image.resName}).
     * @return {@code assets/images/<fichero>} o {@code null} si el recurso no existe o no es una
     * imagen que Flutter pueda cargar (svg/vector/9-patch).
     */
    public String imageAsset(String resName) {
        if (resName == null || resName.isEmpty()) {
            return null;
        }
        if (imageAssets.containsKey(resName)) {
            return imageAssets.get(resName);
        }
        ProjectResourceBean bean = imagesByName.get(resName);
        if (bean == null || !isRasterImage(bean.resFullName)) {
            missingImages.add(resName);
            return null;
        }
        File source = findImageFile(bean);
        if (source == null) {
            missingImages.add(resName);
            return null;
        }
        String relative = uniquePath("assets/images/" + assetFileName(resName, bean.resFullName));
        assetFiles.put(relative, new AssetFile(relative, source));
        imageAssets.put(resName, relative);
        return relative;
    }

    /**
     * Resuelve una fuente del proyecto a un nombre de familia de Flutter, registrando su fichero.
     *
     * @param fontName nombre del recurso de fuente ({@code TextBean.textFont}).
     * @return la familia a usar en {@code TextStyle(fontFamily: ...)} o {@code null} si no existe.
     */
    public String fontFamily(String fontName) {
        if (fontName == null || fontName.isEmpty() || "default_font".equals(fontName)) {
            return null;
        }
        if (fontFamilies.containsKey(fontName)) {
            return fontFamilies.get(fontName);
        }
        ProjectResourceBean bean = fontsByName.get(fontName);
        if (bean == null) {
            missingFonts.add(fontName);
            return null;
        }
        File source = findFontFile(bean);
        if (source == null) {
            missingFonts.add(fontName);
            return null;
        }
        String family = sanitizeIdentifier(fontName);
        String relative = uniquePath("assets/fonts/" + assetFileName(family, bean.resFullName));
        assetFiles.put(relative, new AssetFile(relative, source));
        fontFamilies.put(fontName, family);
        fontAssets.put(family, relative);
        return family;
    }

    /**
     * @return ficheros de recursos a anadir al zip (imagenes y fuentes registradas).
     */
    public List<AssetFile> getAssetFiles() {
        return new ArrayList<>(assetFiles.values());
    }

    /**
     * @return lineas YAML ({@code assets:}) para el bloque {@code flutter:} de {@code pubspec.yaml}.
     */
    public String assetsYaml() {
        if (assetFiles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("  assets:\n");
        for (AssetFile asset : assetFiles.values()) {
            sb.append("    - ").append(asset.zipPath).append('\n');
        }
        return sb.toString();
    }

    /**
     * @return lineas YAML ({@code fonts:}) para el bloque {@code flutter:} de {@code pubspec.yaml}.
     */
    public String fontsYaml() {
        if (fontAssets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("  fonts:\n");
        for (Map.Entry<String, String> font : fontAssets.entrySet()) {
            sb.append("    - family: ").append(font.getKey()).append('\n');
            sb.append("      fonts:\n");
            sb.append("        - asset: ").append(font.getValue()).append('\n');
        }
        return sb.toString();
    }

    public List<String> getCopiedImages() {
        return new ArrayList<>(imageAssets.values());
    }

    public List<String> getCopiedFonts() {
        List<String> fonts = new ArrayList<>();
        for (Map.Entry<String, String> font : fontAssets.entrySet()) {
            fonts.add(font.getKey() + " -> " + font.getValue());
        }
        return fonts;
    }

    public List<String> getMissingImages() {
        return new ArrayList<>(missingImages);
    }

    public List<String> getMissingFonts() {
        return new ArrayList<>(missingFonts);
    }

    // ---------------------------------------------------------------- busqueda en disco

    private File findImageFile(ProjectResourceBean bean) {
        List<File> candidates = new ArrayList<>();
        if (!imagesDirectory.isEmpty()) {
            addCandidate(candidates, imagesDirectory, bean.resFullName);
            addCandidate(candidates, imagesDirectory, bean.resName);
            addCandidate(candidates, imagesDirectory, bean.resName + ".png");
            addCandidate(candidates, imagesDirectory, bean.resName + ".jpg");
            addCandidate(candidates, imagesDirectory, bean.resName + ".jpeg");
            addCandidate(candidates, imagesDirectory, bean.resName + ".webp");
        }
        /* Coleccion global de imagenes (por si el proyecto solo guarda la referencia). */
        addCandidate(candidates, wq.a() + File.separator + "image" + File.separator + "data",
                bean.resFullName);
        return firstExisting(candidates);
    }

    private File findFontFile(ProjectResourceBean bean) {
        List<File> candidates = new ArrayList<>();
        if (!fontsDirectory.isEmpty()) {
            addCandidate(candidates, fontsDirectory, bean.resFullName);
            addCandidate(candidates, fontsDirectory, bean.resName);
            addCandidate(candidates, fontsDirectory, bean.resName + ".ttf");
            addCandidate(candidates, fontsDirectory, bean.resName + ".otf");
        }
        addCandidate(candidates, wq.a() + File.separator + "font" + File.separator + "data",
                bean.resFullName);
        return firstExisting(candidates);
    }

    private static void addCandidate(List<File> candidates, String directory, String name) {
        if (directory == null || directory.isEmpty() || name == null || name.isEmpty()) {
            return;
        }
        candidates.add(new File(directory, name));
    }

    private static File firstExisting(List<File> candidates) {
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- nombres

    private static boolean isRasterImage(String resFullName) {
        if (resFullName == null) {
            return false;
        }
        String lower = resFullName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".svg") || lower.endsWith(".xml") || lower.endsWith(".9.png")) {
            return false;
        }
        String extension = extensionOf(lower);
        return extension.isEmpty() || IMAGE_EXTENSIONS.contains(extension);
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static String assetFileName(String resName, String resFullName) {
        String extension = extensionOf(nullToEmpty(resFullName).toLowerCase(Locale.ROOT));
        if (!IMAGE_EXTENSIONS.contains(extension) && !FONT_EXTENSIONS.contains(extension)) {
            extension = "png";
        }
        return sanitizeIdentifier(resName) + "." + extension;
    }

    private String uniquePath(String candidate) {
        if (!assetFiles.containsKey(candidate)) {
            return candidate;
        }
        int dot = candidate.lastIndexOf('.');
        String base = dot < 0 ? candidate : candidate.substring(0, dot);
        String extension = dot < 0 ? "" : candidate.substring(dot);
        int index = 2;
        while (assetFiles.containsKey(base + "_" + index + extension)) {
            index++;
        }
        return base + "_" + index + extension;
    }

    private static String sanitizeIdentifier(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c == '-' ? '_' : c);
            } else {
                sb.append('_');
            }
        }
        if (sb.length() == 0) {
            sb.append("resource");
        }
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
