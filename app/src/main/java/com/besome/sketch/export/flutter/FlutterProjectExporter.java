package com.besome.sketch.export.flutter;

import android.content.Context;
import android.util.Log;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import com.ascode.android.managers.inject.InjectRootLayoutManager;

/**
 * Exporta un proyecto de Sketchware como proyecto Flutter ({@code .zip} listo para
 * {@code flutter run}).
 *
 * <p>Espejo de la exportacion "Android Studio" de {@code ExportProjectActivity}: recorre los
 * mismos ficheros de proyecto ({@link hC}), los mismos layouts ({@link eC#d(String)}) y los mismos
 * bloques de logica ({@link eC#b(String)}), pero emitiendo Dart ({@link DartWidgets} y
 * {@link DartBlocks}) en vez de Java.</p>
 *
 * <p>El zip se escribe directamente (sin directorio temporal) con
 * {@link ZipOutputStream} y contiene {@code pubspec.yaml}, {@code lib/main.dart},
 * {@code lib/theme.dart}, {@code lib/runtime/sk.dart}, {@code lib/screens/*.dart} y
 * {@code README.md}.</p>
 */
public class FlutterProjectExporter {

    private static final String TAG = "FlutterExporter";

    private final Context context;
    private final String scId;
    private final String projectName;
    private final String applicationName;
    private final String packageName;

    private final ArrayList<String> screenMappings = new ArrayList<>();
    private final ArrayList<String> blockMappings = new ArrayList<>();
    private final ArrayList<String> todos = new ArrayList<>();
    private final FlutterAssets assets;
    /** Cadenas de recursos del proyecto (Fase 3). */
    private final FlutterStrings strings;
    /** Componentes/plugins que usa el proyecto (Fase 3). */
    private final DartComponents components = new DartComponents();
    /** Pantallas que usan patrones de UI (Drawer/FAB/menu/tabs/bottom-nav/pager). */
    private final ArrayList<String> patternMappings = new ArrayList<>();
    /** Pantallas con adapters personalizados. */
    private final ArrayList<String> adapterMappings = new ArrayList<>();

    public FlutterProjectExporter(Context context, String scId, String projectName,
                                  String applicationName, String packageName) {
        this.context = context;
        this.scId = scId;
        this.projectName = projectName;
        this.applicationName = applicationName;
        this.packageName = packageName;
        this.assets = new FlutterAssets(scId);
        this.strings = new FlutterStrings(scId, applicationName);
    }

    /**
     * @return linea "Android -> Flutter" por pantalla generada.
     */
    public ArrayList<String> getScreenMappings() {
        return screenMappings;
    }

    /**
     * @return bloques de Fase 1 cubiertos.
     */
    public ArrayList<String> getBlockMappings() {
        return blockMappings;
    }

    /**
     * @return bloques/vistas sin equivalente (los que quedan como TODO).
     */
    public ArrayList<String> getTodos() {
        return todos;
    }

    /**
     * @param outputDirectory directorio donde dejar el zip (normalmente
     *                        {@code /sdcard/ascode/export_src}).
     * @return el {@code .zip} generado.
     */
    public File export(File outputDirectory) throws IOException {
        hC projectFileManager = jC.b(scId);
        eC projectDataManager = jC.a(scId);
        if (projectFileManager == null || projectDataManager == null) {
            throw new IOException("No se pudieron leer los datos del proyecto " + scId);
        }

        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        ArrayList<ProjectFileBean> screens = new ArrayList<>(projectFileManager.b());
        screens.addAll(projectFileManager.c());

        /* Primera pasada: nombres de clase/fichero de todas las pantallas, para poder importar
         * unas desde otras (los bloques `startActivity` generan `Sk.go(context, const XScreen())`). */
        LinkedHashMap<ProjectFileBean, String> screenClassNames = new LinkedHashMap<>();
        ArrayList<String> imports = new ArrayList<>();
        ArrayList<String> mainImports = new ArrayList<>();
        String homeScreen = null;
        for (ProjectFileBean file : screens) {
            String className = screenClassNameForFile(file);
            screenClassNames.put(file, className);
            imports.add("import '" + snakeCase(className) + ".dart';");
            mainImports.add("import 'screens/" + snakeCase(className) + ".dart';");
            if (homeScreen == null && file.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                homeScreen = className;
            }
        }
        if (homeScreen == null && !screenClassNames.isEmpty()) {
            homeScreen = screenClassNames.values().iterator().next();
        }

        /* Fase 3: pre-escaneo de componentes y cadenas. Asi el pubspec, el runtime de componentes
         * y strings.dart se generan completos antes de escribir las pantallas. */
        scanComponentUsage(projectDataManager, screens);

        for (Map.Entry<ProjectFileBean, String> screen : screenClassNames.entrySet()) {
            ProjectFileBean file = screen.getKey();
            String className = screen.getValue();
            String dartFileName = snakeCase(className) + ".dart";
            try {
                boolean isActivity = file.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY;
                String source = generateScreen(projectDataManager, file, className, isActivity, imports);
                files.put("lib/screens/" + dartFileName, source);
                screenMappings.add("`" + file.getXmlName() + "` -> `lib/screens/" + dartFileName
                        + "` (" + className + ")");
            } catch (Throwable throwable) {
                Log.e(TAG, "No se pudo generar la pantalla " + file.getXmlName(), throwable);
                todos.add("pantalla " + file.getXmlName() + " no generada: " + throwable);
            }
        }

        String screenImports = String.join("\n", mainImports);
        files.put("lib/main.dart", DartTemplates.mainDart(applicationName, homeScreen, screenImports));
        files.put("lib/theme.dart", DartTemplates.themeDart(0xFF008DCD));
        files.put("lib/runtime/sk.dart", DartTemplates.skRuntime());
        files.put("lib/strings.dart", DartTemplates.stringsDart(strings.usedStrings()));
        String componentsRuntime = components.runtimeFile();
        if (!componentsRuntime.isEmpty()) {
            files.put("lib/runtime/sk_components.dart", componentsRuntime);
        }
        files.put("pubspec.yaml", DartTemplates.pubspec(pubspecName(), applicationName,
                assets.assetsYaml(), assets.fontsYaml(), components.pubspecDependencies()));
        for (String copiedImage : assets.getCopiedImages()) {
            blockMappings.add("recurso `" + copiedImage + "` copiado a `assets/`");
        }
        for (String missingImage : assets.getMissingImages()) {
            if (!todos.contains("imagen `" + missingImage + "` no copiada (placeholder + TODO)")) {
                todos.add("imagen `" + missingImage + "` no copiada (placeholder + TODO)");
            }
        }
        for (String missingFont : assets.getMissingFonts()) {
            if (!todos.contains("fuente `" + missingFont + "` no copiada (se usa la fuente por defecto)")) {
                todos.add("fuente `" + missingFont + "` no copiada (se usa la fuente por defecto)");
            }
        }
        files.put("README.md", DartTemplates.readme(projectName, applicationName, packageName,
                markdownList(screenMappings), markdownList(blockMappings), markdownList(todos),
                markdownList(assets.getCopiedImages()), markdownList(assets.getCopiedFonts()),
                components.readmeDependencies(), markdownList(componentSummary()),
                markdownList(patternMappings), markdownList(adapterMappings),
                stringsSummary()));

        File output = new File(outputDirectory, projectName + "_flutter.zip");
        writeZip(output, files, assets.getAssetFiles());
        return output;
    }

    // ---------------------------------------------------------------- pantalla

    /**
     * Genera el fichero Dart de una pantalla: su layout (widgets) + los bloques de logica de sus
     * eventos.
     */
    private String generateScreen(eC projectDataManager, ProjectFileBean file, String className,
                                  boolean isActivity, ArrayList<String> allScreenImports) {
        String xmlName = file.getXmlName();
        String javaName = file.getJavaName();

        ArrayList<ViewBean> views = projectDataManager.d(xmlName);
        if (views == null) {
            views = new ArrayList<>();
        }

        HashMap<String, ArrayList<BlockBean>> logic = javaName == null || javaName.isEmpty()
                ? new HashMap<>()
                : projectDataManager.b(javaName);
        if (logic == null) {
            logic = new HashMap<>();
        }

        Map<String, String> intentScreens = scanIntentScreens(logic);
        Set<String> dynamicTextIds = scanDynamicTextIds(logic);
        Set<String> listDataIds = scanDataBindingIds(logic, "listSetData");
        Set<String> spinnerDataIds = scanDataBindingIds(logic, "spnSetData");
        Set<String> customViewDataIds = new LinkedHashSet<>();
        customViewDataIds.addAll(scanDataBindingIds(logic, "listSetCustomViewData"));
        customViewDataIds.addAll(scanDataBindingIds(logic, "recyclerSetCustomViewData"));
        customViewDataIds.addAll(scanDataBindingIds(logic, "gridSetCustomViewData"));
        customViewDataIds.addAll(scanDataBindingIds(logic, "spnSetCustomViewData"));
        customViewDataIds.addAll(scanDataBindingIds(logic, "pagerSetCustomViewData"));
        Set<String> viewIds = new LinkedHashSet<>();
        for (ViewBean view : views) {
            viewIds.add(view.id);
        }

        /* Fase 3: eventos del Drawer (se guardan con prefijo `_drawer_<id>`). */
        Map<String, Set<String>> drawerEventKeys = new LinkedHashMap<>();
        if (isActivity && file.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            for (String key : logic.keySet()) {
                int separator = key.lastIndexOf('_');
                if (separator > 0 && key.startsWith("_drawer_")) {
                    drawerEventKeys
                            .computeIfAbsent(key.substring(0, separator), ignored -> new LinkedHashSet<>())
                            .add(key);
                }
            }
        }

        Map<String, Map<String, String>> viewEvents = new LinkedHashMap<>();
        Map<String, Map<String, String>> drawerEvents = new LinkedHashMap<>();
        StringBuilder lifecycle = new StringBuilder();
        boolean hasOptionsMenu = false;
        ArrayList<String> unmappedEvents = new ArrayList<>();

        for (Map.Entry<String, ArrayList<BlockBean>> entry : logic.entrySet()) {
            String key = entry.getKey();
            String eventTarget = key;
            String eventName = "";
            int separator = key.lastIndexOf('_');
            if (separator > 0) {
                eventTarget = key.substring(0, separator);
                eventName = key.substring(separator + 1);
            }

            String body = new DartBlocks(entry.getValue(), intentScreens, components, strings).generate();
            if (body.isEmpty()) {
                continue;
            }

            if ("initializeLogic".equals(eventName) || "onCreate".equals(eventName)
                    || "onCreateOptionsMenu".equals(eventName)) {
                if ("onCreateOptionsMenu".equals(eventName)) {
                    /* El menu se construye en initState (menuAddItem -> Sk.menuItems) y se pinta
                     * como acciones del AppBar con Sk.menuButton(context). */
                    hasOptionsMenu = true;
                    if (lifecycle.length() > 0) {
                        lifecycle.append("\n");
                    }
                    lifecycle.append("Sk.menuItems.clear();\n").append(body);
                    patternMappings.add("`" + key + "` -> menu de opciones (PopupMenuButton en AppBar)");
                } else {
                    if (lifecycle.length() > 0) {
                        lifecycle.append("\n");
                    }
                    lifecycle.append("// ").append(key).append("\n").append(body);
                    blockMappings.add("`" + key + "` -> `initState()`");
                }
            } else if (key.startsWith("_drawer_") && drawerEventKeys.containsKey(eventTarget)) {
                String drawerViewId = key.substring("_drawer_".length(), separator);
                drawerEvents.computeIfAbsent(drawerViewId, ignored -> new LinkedHashMap<>())
                        .put(eventName, body);
                blockMappings.add("`" + key + "` -> `" + eventName + "` de Drawer");
            } else if (viewIds.contains(eventTarget)) {
                viewEvents.computeIfAbsent(eventTarget, ignored -> new LinkedHashMap<>())
                        .put(eventName, body);
                blockMappings.add("`" + key + "` -> `" + eventName + "` de `" + eventTarget + "`");
            } else {
                unmappedEvents.add(key);
                todos.add("evento `" + key + "` no traducido (Fase 3)");
            }
        }

        InjectRootLayoutManager.Root root =
                new InjectRootLayoutManager(scId).getLayoutByFileName(xmlName);
        DartWidgets widgets = new DartWidgets(views, viewEvents, dynamicTextIds, listDataIds,
                spinnerDataIds, customViewDataIds, strings, components, assets);
        String layout = widgets.build(root.getClassName(), root.getAttributes());
        for (String todo : widgets.getTodos()) {
            if (!todos.contains(todo)) {
                todos.add(todo);
            }
        }
        if (!customViewDataIds.isEmpty()) {
            adapterMappings.add("`" + xmlName + "` -> `ListView.builder` esbozado + TODO ("
                    + String.join(", ", customViewDataIds) + ")");
        }
        if (!widgets.getFloatingActionButton().isEmpty()) {
            patternMappings.add("`" + xmlName + "` -> FAB (`Scaffold.floatingActionButton`)");
        }
        if (!widgets.getBottomNavigationBar().isEmpty()) {
            patternMappings.add("`" + xmlName + "` -> `BottomNavigationBar`");
        }
        if (!widgets.getTabBar().isEmpty()) {
            patternMappings.add("`" + xmlName + "` -> `TabBar` (`AppBar.bottom`)");
        }
        String drawerLayout = drawerLayout(projectDataManager, file, isActivity, drawerEvents);
        if (!drawerLayout.isEmpty()) {
            patternMappings.add("`" + file.getDrawerXmlName() + "` -> `Drawer`");
        }

        StringBuilder sb = new StringBuilder(4096);
        sb.append("// Pantalla generada por Android-SCode desde `").append(xmlName).append("`.\n");
        sb.append("// No editar a mano: se regenera al exportar de nuevo.\n");
        sb.append("\n");
        sb.append("import 'package:flutter/material.dart';\n\n");
        sb.append("import '../runtime/sk.dart';\n");
        if (!components.isEmpty()) {
            sb.append("// ignore: unused_import\n");
            sb.append("import '../runtime/sk_components.dart';\n");
        }
        sb.append("import '../theme.dart';\n");
        /* El resto de pantallas se importan siempre (con `ignore: unused_import`) para que las
         * llamadas `Sk.go(context, const OtraScreen())` de los bloques resuelvan sin tener que
         * calcular el grafo de navegacion. */
        String ownImport = "import '" + snakeCase(className) + ".dart';";
        for (String screenImport : allScreenImports) {
            if (screenImport.equals(ownImport)) {
                continue;
            }
            sb.append("// ignore: unused_import\n");
            sb.append(screenImport).append('\n');
        }
        sb.append("\n");
        sb.append("class ").append(className).append(" extends StatefulWidget {\n");
        sb.append("  const ").append(className).append("({super.key});\n\n");
        sb.append("  @override\n");
        sb.append("  State<").append(className).append("> createState() => _")
                .append(className).append("State();\n");
        sb.append("}\n\n");
        sb.append("class _").append(className).append("State extends State<").append(className).append("> {\n");
        sb.append("  @override\n");
        sb.append("  void initState() {\n");
        sb.append("    super.initState();\n");
        for (String id : dynamicTextIds) {
            if (!viewIds.contains(id)) {
                continue;
            }
            if (strings.isReference(initialTextOf(views, id))) {
                strings.use(strings.referenceKey(initialTextOf(views, id)));
            }
            sb.append("    Sk.setText('").append(id).append("', ")
                    .append(initialLiteral(initialTextOf(views, id))).append(");\n");
        }
        if (lifecycle.length() > 0) {
            sb.append("\n").append(DartWidgets.indent(lifecycle.toString(), 2)).append("\n");
        }
        String fabIconResource = widgets.getFabIconResource();
        if (!fabIconResource.isEmpty()) {
            sb.append("    Sk.setFabIcon('_fab', '").append(fabIconResource).append("');\n");
        }
        sb.append("  }\n\n");
        sb.append("  @override\n");
        sb.append("  Widget build(BuildContext context) {\n");
        if (isActivity) {
            sb.append("    return Scaffold(\n");
            sb.append("      appBar: AppBar(\n");
            sb.append("        title: const Text(").append(literal(screenTitle(file))).append("),\n");
            if (hasOptionsMenu) {
                sb.append("        actions: <Widget>[Sk.menuButton(context)],\n");
            }
            if (!widgets.getTabBar().isEmpty()) {
                sb.append("        bottom: PreferredSize(\n");
                sb.append("          preferredSize: const Size.fromHeight(56),\n");
                sb.append("          child: ").append(widgets.getTabBar()).append(",\n");
                sb.append("        ),\n");
            }
            sb.append("      ),\n");
            if (!drawerLayout.isEmpty()) {
                sb.append("      drawer: Drawer(\n");
                sb.append("        child: Builder(\n");
                sb.append("          builder: (context) => ")
                        .append(DartWidgets.indent(drawerLayout, 5)).append(",\n");
                sb.append("        ),\n");
                sb.append("      ),\n");
            }
            if (!widgets.getFloatingActionButton().isEmpty()) {
                sb.append("      floatingActionButton: Builder(\n");
                sb.append("        builder: (context) => ")
                        .append(DartWidgets.indent(widgets.getFloatingActionButton(), 4)).append(",\n");
                sb.append("      ),\n");
            }
            if (!widgets.getBottomNavigationBar().isEmpty()) {
                sb.append("      bottomNavigationBar: ")
                        .append(widgets.getBottomNavigationBar()).append(",\n");
            }
            if (!drawerLayout.isEmpty()) {
                /* `Scaffold.of(context)` necesita un contexto por debajo del Scaffold: se envuelve
                 * el cuerpo en un Builder para que los bloques de Drawer/Toast resuelvan. */
                sb.append("      body: Builder(\n");
                sb.append("        builder: (context) => ")
                        .append(DartWidgets.indent(layout, 4)).append(",\n");
                sb.append("      ),\n");
            } else {
                sb.append("      body: ").append(DartWidgets.indent(layout, 3)).append(",\n");
            }
            sb.append("    );\n");
        } else {
            sb.append("    return ").append(DartWidgets.indent(layout, 2)).append(";\n");
        }
        sb.append("  }\n");
        sb.append("}\n");

        if (!unmappedEvents.isEmpty()) {
            sb.append("\n// Eventos sin equivalente (Fase 3):\n");
            for (String event : unmappedEvents) {
                sb.append("// TODO: evento `").append(event).append("`\n");
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- escaneo de bloques

    /**
     * @return mapa {@code variable de Intent -> Activity destino}, leido de los bloques
     * {@code intentSetScreen} de la pantalla (necesario para traducir {@code startActivity} a
     * {@code Navigator.push}).
     */
    private Map<String, String> scanIntentScreens(HashMap<String, ArrayList<BlockBean>> logic) {
        Map<String, String> result = new HashMap<>();
        for (ArrayList<BlockBean> blocks : logic.values()) {
            for (BlockBean block : blocks) {
                if ("intentSetScreen".equals(block.opCode) && block.parameters.size() >= 2) {
                    result.put(block.parameters.get(0), block.parameters.get(1));
                }
            }
        }
        return result;
    }

    /**
     * @return ids de vista cuyo texto se modifica con bloques {@code setText} (se pintan con
     * {@code Sk.bindText}).
     */
    private Set<String> scanDynamicTextIds(HashMap<String, ArrayList<BlockBean>> logic) {
        Set<String> result = new LinkedHashSet<>();
        for (ArrayList<BlockBean> blocks : logic.values()) {
            for (BlockBean block : blocks) {
                if ("setText".equals(block.opCode) && !block.parameters.isEmpty()) {
                    result.add(block.parameters.get(0));
                }
            }
        }
        return result;
    }

    /**
     * @return ids de vista usados por los bloques de datos de listas o spinners
     * ({@code listSetData}/{@code spnSetData}) para pintarlos con datos en vez de vacios.
     */
    private Set<String> scanDataBindingIds(HashMap<String, ArrayList<BlockBean>> logic, String opcode) {
        Set<String> result = new LinkedHashSet<>();
        for (ArrayList<BlockBean> blocks : logic.values()) {
            for (BlockBean block : blocks) {
                if (opcode.equals(block.opCode) && !block.parameters.isEmpty()) {
                    result.add(block.parameters.get(0));
                }
            }
        }
        return result;
    }

    private String initialTextOf(ArrayList<ViewBean> views, String id) {
        for (ViewBean view : views) {
            if (id.equals(view.id) && view.text != null) {
                return view.text.text == null ? "" : view.text.text;
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- Fase 3

    /**
     * Pre-escanea los componentes/plugins que usa el proyecto (Fase 3) para que el pubspec y el
     * runtime de componentes se generen completos antes de escribir las pantallas.
     */
    private void scanComponentUsage(eC projectDataManager, ArrayList<ProjectFileBean> screens) {
        for (ProjectFileBean file : screens) {
            try {
                scanViewsForComponents(projectDataManager.d(file.getXmlName()));
                String javaName = file.getJavaName();
                if (javaName != null && !javaName.isEmpty()) {
                    scanLogicForComponents(projectDataManager.b(javaName));
                }
                if (file.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
                    scanViewsForComponents(projectDataManager.d(file.getDrawerXmlName()));
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "No se pudo escanear componentes de " + file.getXmlName(), throwable);
            }
        }
    }

    private void scanViewsForComponents(ArrayList<ViewBean> views) {
        if (views == null) {
            return;
        }
        for (ViewBean view : views) {
            String name = view.convert != null && !view.convert.isEmpty()
                    ? view.convert
                    : (view.getClassInfo() == null ? "" : view.getClassInfo().getClassName());
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                name = name.substring(dot + 1);
            }
            switch (name) {
                case "WebView" -> components.use(DartComponents.WEBVIEW);
                case "VideoView" -> components.use(DartComponents.VIDEO);
                case "MapView" -> components.use(DartComponents.MAP);
                default -> {
                    // Sin componente de plugin.
                }
            }
        }
    }

    private void scanLogicForComponents(HashMap<String, ArrayList<BlockBean>> logic) {
        if (logic == null) {
            return;
        }
        for (ArrayList<BlockBean> blocks : logic.values()) {
            for (BlockBean block : blocks) {
                components.useForOpcode(block.opCode);
            }
        }
    }

    /**
     * @return descripcion "componente Android -> plugin Flutter" de los componentes usados.
     */
    private ArrayList<String> componentSummary() {
        ArrayList<String> lines = new ArrayList<>();
        for (String id : components.getUsed()) {
            switch (id) {
                case DartComponents.WEBVIEW ->
                        lines.add("`WebView` -> `webview_flutter` (widget `SkCWebView.build`)");
                case DartComponents.CAMERA ->
                        lines.add("`Camera` -> `camera` (`camerastarttakepicture`)");
                case DartComponents.IMAGE_PICKER ->
                        lines.add("`FilePicker`/galeria -> `image_picker` (`filepickerstartpickfiles`)");
                case DartComponents.BLUETOOTH ->
                        lines.add("`BluetoothConnect` -> `flutter_blue_plus` (BLE)");
                case DartComponents.SENSORS ->
                        lines.add("`Gyroscope` -> `sensors_plus`");
                case DartComponents.GEOLOCATOR ->
                        lines.add("`LocationManager` -> `geolocator`");
                case DartComponents.AUDIO ->
                        lines.add("`MediaPlayer`/`SoundPool` -> `audioplayers`");
                case DartComponents.VIDEO ->
                        lines.add("`VideoView` -> `video_player` (widget `SkCVideo.build`)");
                case DartComponents.MAP ->
                        lines.add("`MapView` -> `flutter_map` + tiles de OpenStreetMap (libre)");
                case DartComponents.ADS ->
                        lines.add("`AdView`/`InterstitialAd`/`RewardedVideoAd` -> TODO (requiere SDK de anuncios)");
                default -> lines.add("`" + id + "` -> plugin de pub.dev");
            }
        }
        if (lines.isEmpty()) {
            lines.add("_ninguno_: el proyecto no usa componentes de plugin");
        }
        return lines;
    }

    /**
     * @return resumen de las cadenas incluidas en {@code lib/strings.dart}.
     */
    private String stringsSummary() {
        LinkedHashMap<String, String> used = strings.usedStrings();
        StringBuilder sb = new StringBuilder();
        sb.append("Se genera `lib/strings.dart` con ").append(used.size())
                .append(" cadenas tomadas de `res/values/strings.xml` ")
                .append("(bloques `getResStr`/`getResString` y referencias `@string/...` de los ")
                .append("layouts). Se resuelven con `Sk.resStr('clave')`.\n\n");
        if (used.isEmpty()) {
            sb.append("_ninguna_\n");
        } else {
            for (String key : used.keySet()) {
                sb.append("- `").append(key).append("`\n");
            }
        }
        return sb.toString();
    }

    /**
     * @return el contenido del {@code Drawer} de una Activity (o cadena vacia si no tiene).
     */
    private String drawerLayout(eC projectDataManager, ProjectFileBean file, boolean isActivity,
                                Map<String, Map<String, String>> drawerEvents) {
        if (!isActivity || !file.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            return "";
        }
        ArrayList<ViewBean> views;
        try {
            views = projectDataManager.d(file.getDrawerXmlName());
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo leer el Drawer de " + file.getXmlName(), throwable);
            return "";
        }
        if (views == null || views.isEmpty()) {
            return "";
        }
        Set<String> empty = java.util.Collections.emptySet();
        DartWidgets widgets = new DartWidgets(views, drawerEvents, empty, empty, empty, empty,
                strings, components, assets);
        String layout;
        try {
            InjectRootLayoutManager.Root root =
                    new InjectRootLayoutManager(scId).getLayoutByFileName(file.getDrawerXmlName());
            layout = widgets.build(root.getClassName(), root.getAttributes());
        } catch (Throwable throwable) {
            layout = widgets.build("LinearLayout", new HashMap<>());
        }
        for (String todo : widgets.getTodos()) {
            if (!todos.contains(todo)) {
                todos.add(todo);
            }
        }
        return layout;
    }

    /**
     * @return el texto inicial de un widget como expresion Dart (resuelve {@code @string/...}).
     */
    private String initialLiteral(String value) {
        if (strings.isReference(value)) {
            return strings.dart(strings.referenceKey(value));
        }
        return literal(value);
    }

    // ---------------------------------------------------------------- zip

    private void writeZip(File output, LinkedHashMap<String, String> files,
                          java.util.List<FlutterAssets.AssetFile> binaryFiles) throws IOException {
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear " + parent.getAbsolutePath());
        }
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(output)))) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(System.currentTimeMillis());
                zip.putNextEntry(entry);
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (FlutterAssets.AssetFile asset : binaryFiles) {
                ZipEntry entry = new ZipEntry(asset.zipPath);
                entry.setTime(System.currentTimeMillis());
                zip.putNextEntry(entry);
                try (java.io.InputStream input = new java.io.BufferedInputStream(
                        new java.io.FileInputStream(asset.source))) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    // ---------------------------------------------------------------- nombres

    /**
     * @return el nombre de clase Dart de una pantalla, coherente con
     * {@link #screenClassNameForActivity(String)}.
     */
    public static String screenClassNameForFile(ProjectFileBean file) {
        return capitalize(camelCase(file.fileName)) + "Screen";
    }

    /**
     * @return el nombre de clase Dart de la pantalla que corresponde a una Activity Android
     * ({@code MainActivity} -> {@code MainScreen}).
     */
    public static String screenClassNameForActivity(String activityName) {
        String base = activityName == null ? "" : activityName;
        if (base.endsWith("Activity")) {
            base = base.substring(0, base.length() - "Activity".length());
        }
        return capitalize(base) + "Screen";
    }

    private String screenTitle(ProjectFileBean file) {
        String name = capitalize(camelCase(file.fileName));
        return name.isEmpty() ? applicationName : name;
    }

    private String pubspecName() {
        String name = packageName == null ? "" : packageName;
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        name = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (name.isEmpty() || Character.isDigit(name.charAt(0))) {
            name = "as" + name;
        }
        return name;
    }

    private static String camelCase(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                upper = true;
                continue;
            }
            if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String snakeCase(String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String literal(String value) {
        if (value == null) {
            value = "";
        }
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '$' -> sb.append("\\$");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }

    private static String markdownList(java.util.List<String> lines) {
        if (lines.isEmpty()) {
            return "_ninguno_";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
        return sb.toString();
    }
}
