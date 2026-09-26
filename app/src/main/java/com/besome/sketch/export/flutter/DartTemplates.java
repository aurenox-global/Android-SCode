package com.besome.sketch.export.flutter;

/**
 * Plantillas de texto (Dart/YAML/Markdown) del exportador "Export Project as Flutter project".
 *
 * <p>Todas las plantillas se rellenan con {@link #fill(String, String, String)} para no tener que
 * escapar los {@code %} de {@link String#format} ni los {@code $} de Dart.</p>
 */
public final class DartTemplates {

    private DartTemplates() {
    }

    /**
     * @return {@code template} con todas las apariciones de {@code placeholder} sustituidas.
     */
    public static String fill(String template, String placeholder, String value) {
        return template.replace(placeholder, value);
    }

    public static String pubspec(String pubspecName, String description, String assetsYaml,
                                 String fontsYaml, String extraDependencies) {
        String flutterSection = "flutter:\n  uses-material-design: true\n"
                + (assetsYaml == null ? "" : assetsYaml)
                + (fontsYaml == null ? "" : fontsYaml);
        String yaml = """
                # pubspec.yaml generado por Android-SCode (Export Project -> Flutter project).
                #
                # El proyecto esta listo para compilarse EN LOCAL (no hace falta ningun servicio
                # en la nube) desde la raiz del zip:
                #   flutter pub get
                #   flutter build apk --release
                # o, en desarrollo:
                #   flutter run
                #
                # Dependencia fija: shared_preferences (persistencia de los bloques
                # `fileGetData`/`fileSetData`).
                name: __NAME__
                description: __DESCRIPTION__
                publish_to: "none"
                version: 1.0.0+1

                environment:
                  sdk: '>=3.0.0 <4.0.0'

                dependencies:
                  flutter:
                    sdk: flutter
                  shared_preferences: ^2.2.3
                __DEPS__
                dev_dependencies:
                  flutter_test:
                    sdk: flutter
                  flutter_lints: ^4.0.0

                __FLUTTER__
                """;
        yaml = fill(yaml, "__NAME__", pubspecName);
        yaml = fill(yaml, "__DEPS__",
                extraDependencies == null || extraDependencies.isEmpty() ? "" : extraDependencies);
        yaml = fill(yaml, "__DESCRIPTION__", description == null ? "" : description);
        return fill(yaml, "__FLUTTER__", flutterSection);
    }

    /**
     * @return contenido de {@code lib/strings.dart}: mapa {@code clave -> valor} de las cadenas
     * usadas por la logica/los layouts y la funcion {@code str()} que las resuelve.
     */
    public static String stringsDart(java.util.Map<String, String> values) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("// strings.dart generado por Android-SCode desde res/values/strings.xml.\n");
        sb.append("//\n");
        sb.append("// Solo se incluyen las cadenas que usa el codigo generado. Los bloques\n");
        sb.append("// `getResStr`/`getResString` y las referencias `@string/...` de los layouts\n");
        sb.append("// se resuelven con `Sk.resStr('clave')`.\n\n");
        sb.append("/// Cadenas del proyecto Android original.\n");
        sb.append("const Map<String, String> kStrings = <String, String>{\n");
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            sb.append("  '").append(FlutterStrings.escape(entry.getKey())).append("': '")
                    .append(FlutterStrings.escape(entry.getValue())).append("',\n");
        }
        sb.append("};\n\n");
        sb.append("/// Devuelve la cadena `key` (o la propia clave si no existe), como `getString()`.");
        sb.append("\nString str(String key) => kStrings[key] ?? key;\n");
        return sb.toString();
    }

    public static String mainDart(String title, String homeScreenClass, String imports) {
        String template = """
                // main.dart generado por Android-SCode.
                //
                // Punto de entrada de la app Flutter. La pantalla inicial es la primera
                // Activity del proyecto (__HOME__); el resto se alcanza con los bloques
                // `startActivity` / `Intent` traducidos a `Sk.go(...)`.

                import 'package:flutter/material.dart';

                import 'runtime/sk.dart';
                import 'theme.dart';
                __IMPORTS__

                Future<void> main() async {
                  WidgetsFlutterBinding.ensureInitialized();
                  await Sk.initPreferences();
                  runApp(const AsCodeApp());
                }

                class AsCodeApp extends StatelessWidget {
                  const AsCodeApp({super.key});

                  @override
                  Widget build(BuildContext context) {
                    return MaterialApp(
                      title: '__TITLE__',
                      debugShowCheckedModeBanner: false,
                      theme: appTheme(),
                      home: const __HOME__(),
                    );
                  }
                }
                """;
        template = fill(template, "__HOME__", homeScreenClass);
        template = fill(template, "__IMPORTS__", imports);
        return fill(template, "__TITLE__", title);
    }

    public static String themeDart(int seedColor) {
        return fill("""
                // Tema compartido por todas las pantallas generadas (equivale al
                // `res/values/styles.xml` + colores de la app Android original).

                import 'package:flutter/material.dart';

                /// Color semilla derivado del color primario del proyecto Android.
                const Color kSeedColor = Color(0xFF__SEED__);

                ThemeData appTheme() {
                  return ThemeData(
                    useMaterial3: true,
                    colorScheme: ColorScheme.fromSeed(seedColor: kSeedColor),
                  );
                }
                """, "__SEED__", String.format("%06X", seedColor & 0xFFFFFF));
    }

    public static String skRuntime() {
        return """
                // Runtime Dart propio de Android-SCode para proyectos exportados a Flutter.
                //
                // Este fichero no existe en el proyecto Android original: es el equivalente en
                // Dart de la clase utilitaria `AscodeUtil` del runtime Android (Toast, dip,
                // random, helpers matematicos y de texto) mas la maquinaria de variables
                // globales y de textos de widgets que usan los bloques traducidos.
                //
                // Mapa Android -> Dart (detalle completo en README.md):
                //   variables globales        -> Sk.getVar / Sk.setVar
                //   doToast                   -> Sk.toast (ScaffoldMessenger + SnackBar)
                //   startActivity(intent)     -> Sk.go (Navigator.push)
                //   finish()                  -> Sk.finish (Navigator.pop)
                //   setText / getText         -> Sk.setText / Sk.getText
                //   operadores +,-,*,/,%,>,<  -> Sk.add/Sk.sub/... (tolerantes a tipo)
                //
                // Los bloques sin equivalente se emiten como `// TODO:` en las pantallas.

                import 'dart:async';
                import 'dart:convert';
                import 'dart:math' as math;

                import 'package:flutter/material.dart';
                import 'package:shared_preferences/shared_preferences.dart';

                import '../strings.dart';

                /// Equivalente Dart del runtime de Android generado por Android-SCode.
                class Sk {
                  Sk._();

                  /// Variables globales del proyecto (las "variables" de Sketchware).
                  static final Map<String, dynamic> variables = <String, dynamic>{};

                  /// Botones de navegacion registrados por las pantallas (`finish`).
                  static dynamic getVar(String name) => variables[name];

                  static void setVar(String name, dynamic value) {
                    variables[name] = value;
                  }

                  // --- Coerciones tolerantes (los bloques de Sketchware no son estrictos) ---

                  static double toNumber(dynamic value) {
                    if (value is num) return value.toDouble();
                    return double.tryParse('$value') ?? 0.0;
                  }

                  static String toText(dynamic value) => value == null ? '' : '$value';

                  static bool toBool(dynamic value) {
                    if (value is bool) return value;
                    if (value is num) return value != 0;
                    return '$value'.toLowerCase() == 'true';
                  }

                  static String boolText(bool value) => value ? 'true' : 'false';

                  /// Placeholder de los bloques de la Fase 1 sin equivalente: deja rastro en consola
                  /// y no rompe la app. El comentario `// TODO: ...` se emite justo antes.
                  static dynamic todo(String opcode, List<dynamic> params) {
                    if (kDebugMode) {
                      debugPrint('Android-SCode: bloque no soportado: ' +
                          opcode +
                          (params.isEmpty ? '' : ' ' + params.join(', ')));
                    }
                    return null;
                  }

                  // --- Operadores de los bloques ---

                  /// `+` de Sketchware: concatena si algun operando es texto, suma si no.
                  static dynamic add(dynamic a, dynamic b) {
                    if (a is String || b is String) return toText(a) + toText(b);
                    return toNumber(a) + toNumber(b);
                  }

                  static num sub(dynamic a, dynamic b) => toNumber(a) - toNumber(b);

                  static num mul(dynamic a, dynamic b) => toNumber(a) * toNumber(b);

                  static num div(dynamic a, dynamic b) {
                    final double right = toNumber(b);
                    if (right == 0) return 0;
                    return toNumber(a) / right;
                  }

                  static num mod(dynamic a, dynamic b) {
                    final double right = toNumber(b);
                    if (right == 0) return 0;
                    return toNumber(a) % right;
                  }

                  static bool gt(dynamic a, dynamic b) {
                    if (a is String && b is String) return a.compareTo(b) > 0;
                    return toNumber(a) > toNumber(b);
                  }

                  static bool lt(dynamic a, dynamic b) {
                    if (a is String && b is String) return a.compareTo(b) < 0;
                    return toNumber(a) < toNumber(b);
                  }

                  static bool eq(dynamic a, dynamic b) {
                    if (a is num || b is num) return toNumber(a) == toNumber(b);
                    return toText(a) == toText(b);
                  }

                  static bool and(dynamic a, dynamic b) => toBool(a) && toBool(b);

                  static bool or(dynamic a, dynamic b) => toBool(a) || toBool(b);

                  static bool not(dynamic a) => !toBool(a);

                  // --- Textos de widgets (setText / getText) ---

                  static final Map<String, TextEditingController> _controllers =
                      <String, TextEditingController>{};
                  static final Map<String, ValueNotifier<String>> _texts =
                      <String, ValueNotifier<String>>{};

                  static TextEditingController controller(String id) =>
                      _controllers.putIfAbsent(id, () => TextEditingController());

                  static ValueNotifier<String> textValue(String id) =>
                      _texts.putIfAbsent(id, () => ValueNotifier<String>(''));

                  static void setText(String id, dynamic value) {
                    final String text = toText(value);
                    textValue(id).value = text;
                    if (_controllers.containsKey(id)) {
                      _controllers[id]!.text = text;
                    }
                  }

                  static String getText(String id) {
                    if (_controllers.containsKey(id)) return _controllers[id]!.text;
                    return _texts[id]?.value ?? '';
                  }

                  /// Envuelve el texto de un widget en un `ValueListenableBuilder` para que
                  /// `setText` sobre su id se refleje en pantalla.
                  static Widget bindText(String id, Widget Function(BuildContext, String) builder) {
                    return ValueListenableBuilder<String>(
                      valueListenable: textValue(id),
                      builder: (context, value, _) => builder(context, value),
                    );
                  }

                  // --- Toast / navegacion ---

                  /// `doToast` -> SnackBar de Material.
                  static void toast(BuildContext context, dynamic message) {
                    ScaffoldMessenger.of(context)
                        .showSnackBar(SnackBar(content: Text(toText(message))));
                  }

                  /// `startActivity(intent)` -> Navigator.push.
                  static void go(BuildContext context, Widget screen) {
                    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
                  }

                  /// `finish()` -> Navigator.pop.
                  static void finish(BuildContext context) {
                    Navigator.of(context).maybePop();
                  }

                  // --- Listas y mapas de los bloques (Fase 2) ---

                  /// Devuelve (creando si hace falta) la variable de lista `name`.
                  static List<dynamic> list(String name) {
                    final dynamic current = variables[name];
                    if (current is List) return current;
                    final List<dynamic> created = <dynamic>[];
                    variables[name] = created;
                    return created;
                  }

                  /// Devuelve (creando si hace falta) la variable de mapa `name`.
                  static Map<String, dynamic> map(String name) {
                    final dynamic current = variables[name];
                    if (current is Map) {
                      final Map<String, dynamic> typed = <String, dynamic>{};
                      current.forEach((key, value) => typed[toText(key)] = value);
                      variables[name] = typed;
                      return typed;
                    }
                    final Map<String, dynamic> created = <String, dynamic>{};
                    variables[name] = created;
                    return created;
                  }

                  static List<dynamic> asList(dynamic value) {
                    if (value is List) return value;
                    if (value == null) return <dynamic>[];
                    if (value is String) return value.isEmpty ? <dynamic>[] : value.split(',');
                    return <dynamic>[value];
                  }

                  static List<dynamic> keysOf(dynamic source) {
                    if (source is Map) return source.keys.map(toText).toList();
                    return <dynamic>[];
                  }

                  static void setKeys(String listName, dynamic source) {
                    setVar(listName, keysOf(source));
                  }

                  // --- Persistencia: SharedPreferences (Fase 2) ---

                  static SharedPreferences? _prefs;
                  static final Map<String, String> _prefFiles = <String, String>{};
                  static final Map<String, dynamic> _prefCache = <String, dynamic>{};

                  /// Carga las preferencias compartidas. Se llama una vez desde `main()`.
                  static Future<void> initPreferences() async {
                    _prefs = await SharedPreferences.getInstance();
                    for (final String key in _prefs!.getKeys()) {
                      _prefCache[key] = _prefs!.get(key);
                    }
                  }

                  static String _prefFileName(String variable) => _prefFiles[variable] ?? variable;

                  static String _prefKey(String variable, dynamic key) =>
                      _prefFileName(variable) + '::' + toText(key);

                  /// Equivale a `fileSetFileName` (asocia la variable a un fichero de prefs).
                  static void setPrefFileName(String variable, dynamic fileName) {
                    final String name = toText(fileName);
                    _prefFiles[variable] = name;
                    final String prefix = name + '::';
                    final SharedPreferences? prefs = _prefs;
                    if (prefs != null) {
                      for (final String existing in prefs.getKeys()) {
                        if (existing.startsWith(prefix)) _prefCache[existing] = prefs.get(existing);
                      }
                    }
                  }

                  static String getPrefString(String variable, dynamic key) =>
                      toText(_prefCache[_prefKey(variable, key)]);

                  static void setPrefString(String variable, dynamic key, dynamic value) {
                    final String storageKey = _prefKey(variable, key);
                    final String text = toText(value);
                    _prefCache[storageKey] = text;
                    _prefs?.setString(storageKey, text);
                  }

                  static void removePref(String variable, dynamic key) {
                    final String storageKey = _prefKey(variable, key);
                    _prefCache.remove(storageKey);
                    _prefs?.remove(storageKey);
                  }

                  // --- JSON <-> mapas/listas (Fase 2) ---

                  static String jsonEncode(dynamic value) => json.encode(value);

                  static dynamic _decodeJson(dynamic text) {
                    try {
                      return json.decode(toText(text));
                    } catch (_) {
                      return null;
                    }
                  }

                  static Map<String, dynamic> jsonToMap(dynamic text) {
                    final dynamic decoded = _decodeJson(text);
                    if (decoded is Map) return Map<String, dynamic>.from(decoded);
                    return <String, dynamic>{};
                  }

                  static List<dynamic> jsonToListMap(dynamic text) {
                    final dynamic decoded = _decodeJson(text);
                    if (decoded is List) return decoded;
                    return <dynamic>[];
                  }

                  // --- ListView con datos (Fase 2) ---

                  static final Map<String, ValueNotifier<List<dynamic>>> _lists =
                      <String, ValueNotifier<List<dynamic>>>{};

                  static ValueNotifier<List<dynamic>> listValue(String id) => _lists.putIfAbsent(
                      id, () => ValueNotifier<List<dynamic>>(<dynamic>[]));

                  static void setListData(String id, dynamic data) {
                    listValue(id).value = asList(data);
                  }

                  static void refreshList(String id) {
                    listValue(id).value = List<dynamic>.from(listValue(id).value);
                  }

                  static Widget bindList(
                      String id, Widget Function(BuildContext, List<dynamic>) builder) {
                    return ValueListenableBuilder<List<dynamic>>(
                      valueListenable: listValue(id),
                      builder: (context, items, _) => builder(context, items),
                    );
                  }

                  static final Map<String, Set<int>> _checkedItems = <String, Set<int>>{};

                  static Set<int> _checked(String id) =>
                      _checkedItems.putIfAbsent(id, () => <int>{});

                  static void setListItemChecked(String id, int index, bool checked) {
                    final Set<int> current = _checked(id);
                    if (checked) {
                      current.add(index);
                    } else {
                      current.remove(index);
                    }
                    refreshList(id);
                  }

                  static int getCheckedPosition(String id) {
                    final Set<int> current = _checked(id);
                    return current.isEmpty ? -1 : current.first;
                  }

                  static List<dynamic> getCheckedPositions(String id) {
                    final List<int> sorted = _checked(id).toList()..sort();
                    return sorted.map((index) => index as dynamic).toList();
                  }

                  static int getCheckedCount(String id) => _checked(id).length;

                  // --- Spinner/Dropdown con datos (Fase 2) ---

                  static final Map<String, ValueNotifier<List<dynamic>>> _spinners =
                      <String, ValueNotifier<List<dynamic>>>{};
                  static final Map<String, int> _spinnerIndex = <String, int>{};

                  static ValueNotifier<List<dynamic>> spinnerValue(String id) => _spinners.putIfAbsent(
                      id, () => ValueNotifier<List<dynamic>>(<dynamic>[]));

                  static void setSpinnerData(String id, dynamic data) {
                    spinnerValue(id).value = asList(data);
                  }

                  static void refreshSpinner(String id) {
                    spinnerValue(id).value = List<dynamic>.from(spinnerValue(id).value);
                  }

                  static void setSpinnerIndex(String id, int index) {
                    _spinnerIndex[id] = index;
                    refreshSpinner(id);
                  }

                  static int getSpinnerIndex(String id) => _spinnerIndex[id] ?? -1;

                  /// Elementos sin duplicados: `DropdownButton` exige valores unicos.
                  static List<dynamic> distinct(List<dynamic> items) {
                    final List<dynamic> result = <dynamic>[];
                    final Set<String> seen = <String>{};
                    for (final dynamic item in items) {
                      if (seen.add(toText(item))) result.add(item);
                    }
                    return result;
                  }

                  static Widget bindSpinner(String id,
                      Widget Function(BuildContext, List<dynamic>, int) builder) {
                    return ValueListenableBuilder<List<dynamic>>(
                      valueListenable: spinnerValue(id),
                      builder: (context, items, _) => builder(context, items, getSpinnerIndex(id)),
                    );
                  }

                  // --- Timers (Fase 2) ---

                  static final Map<String, Timer> _timers = <String, Timer>{};

                  static void timerAfter(String name, dynamic delayMs, void Function() body) {
                    timerCancel(name);
                    _timers[name] = Timer(Duration(milliseconds: toNumber(delayMs).toInt()), body);
                  }

                  static void timerEvery(
                      String name, dynamic delayMs, dynamic periodMs, void Function() body) {
                    timerCancel(name);
                    _timers[name] =
                        Timer.periodic(Duration(milliseconds: toNumber(periodMs).toInt()), (_) => body());
                  }

                  static void timerCancel(String name) {
                    _timers.remove(name)?.cancel();
                  }

                  // --- Dialogos (Fase 2) ---

                  static final Map<String, SkDialog> _dialogs = <String, SkDialog>{};

                  static SkDialog dialog(String name) =>
                      _dialogs.putIfAbsent(name, () => SkDialog());

                  static void dialogSetTitle(String name, dynamic title) {
                    dialog(name).title = toText(title);
                  }

                  static void dialogSetMessage(String name, dynamic message) {
                    dialog(name).message = toText(message);
                  }

                  static void dialogShow(BuildContext context, String name) {
                    final SkDialog current = dialog(name);
                    showDialog<void>(
                      context: context,
                      builder: (BuildContext dialogContext) {
                        return AlertDialog(
                          title: current.title.isEmpty ? null : Text(current.title),
                          content: current.message.isEmpty ? null : Text(current.message),
                          actions: <Widget>[
                            if (current.cancelText != null)
                              TextButton(
                                onPressed: () {
                                  Navigator.of(dialogContext).pop();
                                  current.onCancel?.call();
                                },
                                child: Text(current.cancelText!),
                              ),
                            if (current.neutralText != null)
                              TextButton(
                                onPressed: () {
                                  Navigator.of(dialogContext).pop();
                                  current.onNeutral?.call();
                                },
                                child: Text(current.neutralText!),
                              ),
                            if (current.okText != null)
                              TextButton(
                                onPressed: () {
                                  Navigator.of(dialogContext).pop();
                                  current.onOk?.call();
                                },
                                child: Text(current.okText!),
                              ),
                          ],
                        );
                      },
                    );
                  }

                  static void dialogDismiss(BuildContext context) {
                    Navigator.of(context).maybePop();
                  }

                  /// `toStringFormat` de Sketchware (`new DecimalFormat(pattern)`).
                  static String decimalFormat(String pattern, dynamic value) {
                    final int dot = pattern.indexOf('.');
                    final int decimals = dot < 0 ? 0 : pattern.length - dot - 1;
                    final double parsed = toNumber(value);
                    if (decimals <= 0) return parsed.round().toString();
                    return parsed.toStringAsFixed(decimals);
                  }

                  // --- Helpers matematicos (espejo de AscodeUtil) ---

                  static final double pi = math.pi;
                  static final double e = math.e;

                  static num random(num min, num max) {
                    if (max <= min) return min;
                    return min + (DateTime.now().microsecondsSinceEpoch % (max - min + 1));
                  }

                  /// En Flutter el layout ya usa pixeles logicos, asi que dip == valor.
                  static num getDip(BuildContext context, num value) => value;

                  static num getDisplayWidth(BuildContext context) =>
                      MediaQuery.of(context).size.width;

                  static num getDisplayHeight(BuildContext context) =>
                      MediaQuery.of(context).size.height;

                  static num pow(num a, num b) => math.pow(a, b);
                  static num min(num a, num b) => math.min(a, b);
                  static num max(num a, num b) => math.max(a, b);
                  static num sqrt(num a) => math.sqrt(a);
                  static num abs(num a) => a.abs();
                  static num round(num a) => a.round();
                  static num ceil(num a) => a.ceil();
                  static num floor(num a) => a.floor();
                  static num sin(num a) => math.sin(a);
                  static num cos(num a) => math.cos(a);
                  static num tan(num a) => math.tan(a);
                  static num asin(num a) => math.asin(a);
                  static num acos(num a) => math.acos(a);
                  static num atan(num a) => math.atan(a);
                  static num exp(num a) => math.exp(a);
                  static num log(num a) => math.log(a);
                  static num log10(num a) => math.log(a) / math.ln10;
                  static num toRadian(num a) => a * math.pi / 180;
                  static num toDegree(num a) => a * 180 / math.pi;

                  // --- Cadenas de recursos: strings.xml (Fase 3) ---

                  /// `getResStr`/`getResString` y `@string/...` -> `strings.dart`.
                  static String resStr(String key) => str(key);

                  // --- Drawer (Fase 3) ---

                  static bool isDrawerOpen(BuildContext context) =>
                      Scaffold.of(context).isDrawerOpen;

                  static void openDrawer(BuildContext context) =>
                      Scaffold.of(context).openDrawer();

                  static void closeDrawer(BuildContext context) =>
                      Scaffold.of(context).closeDrawer();

                  // --- FAB: icono y visibilidad (Fase 3) ---

                  static final Map<String, ValueNotifier<IconData>> _fabIcons =
                      <String, ValueNotifier<IconData>>{};
                  static final Map<String, ValueNotifier<bool>> _fabVisible =
                      <String, ValueNotifier<bool>>{};

                  static ValueNotifier<IconData> _fabIconValue(String id) =>
                      _fabIcons.putIfAbsent(id, () => ValueNotifier<IconData>(Icons.add));

                  static void setFabIcon(String id, dynamic name) {
                    _fabIconValue(id).value = resolveIcon(toText(name));
                  }

                  /// `fabIcon` en el layout: icono que se puede cambiar con `setFabIcon`.
                  static Widget fabIcon(String id) => ValueListenableBuilder<IconData>(
                        valueListenable: _fabIconValue(id),
                        builder: (context, icon, _) => Icon(icon),
                      );

                  static void setFabVisible(String id, dynamic visible) {
                    _fabVisible
                        .putIfAbsent(id, () => ValueNotifier<bool>(true))
                        .value = toBool(visible);
                  }

                  /// Envuelve el FAB para que `fabVisibility` lo oculte/muestre.
                  static Widget bindFab(String id, Widget fab) => ValueListenableBuilder<bool>(
                        valueListenable: _fabVisible.putIfAbsent(
                            id, () => ValueNotifier<bool>(true)),
                        builder: (context, visible, _) =>
                            visible ? fab : const SizedBox.shrink(),
                      );

                  /// Traduce el nombre de un `drawable` a un `IconData` de Material.
                  static IconData resolveIcon(String name) {
                    final String key = name.toLowerCase().replaceAll('-', '_');
                    if (key.contains('add')) return Icons.add;
                    if (key.contains('edit')) return Icons.edit;
                    if (key.contains('delete')) return Icons.delete;
                    if (key.contains('search')) return Icons.search;
                    if (key.contains('settings')) return Icons.settings;
                    if (key.contains('home')) return Icons.home;
                    if (key.contains('menu')) return Icons.menu;
                    if (key.contains('arrow_back')) return Icons.arrow_back;
                    if (key.contains('check')) return Icons.check;
                    if (key.contains('close') || key.contains('cancel')) return Icons.close;
                    if (key.contains('favorite') || key.contains('star')) return Icons.favorite;
                    if (key.contains('share')) return Icons.share;
                    if (key.contains('call')) return Icons.call;
                    if (key.contains('send')) return Icons.send;
                    if (key.contains('next')) return Icons.arrow_forward;
                    if (key.contains('previous')) return Icons.arrow_back;
                    return Icons.add;
                  }

                  // --- Menu de opciones -> PopupMenuButton (Fase 3) ---

                  static final List<String> menuItems = <String>[];

                  static void addMenuItem(dynamic title) {
                    menuItems.add(toText(title));
                  }

                  /// `onCreateOptionsMenu` + `menuAddItem` -> acciones del AppBar.
                  static Widget menuButton(BuildContext context) => PopupMenuButton<int>(
                        icon: const Icon(Icons.more_vert),
                        onSelected: (int index) => todo('menu item seleccionado', <dynamic>[index]),
                        itemBuilder: (BuildContext context) => <PopupMenuEntry<int>>[
                          for (int i = 0; i < menuItems.length; i++)
                            PopupMenuItem<int>(value: i, child: Text(menuItems[i])),
                        ],
                      );

                  // --- TabLayout -> TabBar (Fase 3) ---

                  static final Map<String, ValueNotifier<List<String>>> _tabs =
                      <String, ValueNotifier<List<String>>>{};
                  static final Map<String, int> tabIndex = <String, int>{};

                  static ValueNotifier<List<String>> tabValue(String id) => _tabs.putIfAbsent(
                      id, () => ValueNotifier<List<String>>(<String>[]));

                  static void addTab(String id, dynamic title) {
                    tabValue(id).value = <String>[...tabValue(id).value, toText(title)];
                  }

                  /// `TabLayout` del layout -> `TabBar` alimentado por los bloques `addTab`.
                  static Widget tabBar(String id) => _SkTabBar(id: id);

                  static final Map<String, Color> _tabIndicator = <String, Color>{};
                  static final Map<String, Color> _tabTextSelected = <String, Color>{};
                  static final Map<String, Color> _tabTextNormal = <String, Color>{};

                  static void setTabIndicatorColor(String id, Color color) {
                    _tabIndicator[id] = color;
                    tabValue(id).value = List<String>.from(tabValue(id).value);
                  }

                  static void setTabTextColors(String id, Color normal, Color selected) {
                    _tabTextNormal[id] = normal;
                    _tabTextSelected[id] = selected;
                    tabValue(id).value = List<String>.from(tabValue(id).value);
                  }

                  // --- BottomNavigationView -> BottomNavigationBar (Fase 3) ---

                  static final Map<String, ValueNotifier<List<String>>> _bottomItems =
                      <String, ValueNotifier<List<String>>>{};
                  static final Map<String, List<IconData>> _bottomIcons =
                      <String, List<IconData>>{};
                  static final Map<String, int> bottomIndex = <String, int>{};

                  static ValueNotifier<List<String>> bottomValue(String id) => _bottomItems
                      .putIfAbsent(id, () => ValueNotifier<List<String>>(<String>[]));

                  static void addBottomItem(String id, dynamic title, dynamic icon) {
                    bottomValue(id).value = <String>[...bottomValue(id).value, toText(title)];
                    _bottomIcons
                        .putIfAbsent(id, () => <IconData>[])
                        .add(resolveIcon(toText(icon)));
                  }

                  /// `BottomNavigationView` del layout -> `BottomNavigationBar`.
                  static Widget bottomNav(String id) => _SkBottomNav(id: id);

                  // --- ViewPager -> PageView (Fase 3) ---

                  static final Map<String, PageController> _pageControllers =
                      <String, PageController>{};
                  static final Map<String, ValueNotifier<int>> _pageCounts =
                      <String, ValueNotifier<int>>{};

                  static PageController pageController(String id) =>
                      _pageControllers.putIfAbsent(id, () => PageController());

                  static ValueNotifier<int> pageCountValue(String id) => _pageCounts.putIfAbsent(
                      id, () => ValueNotifier<int>(1));

                  static void setPageCount(String id, dynamic count) {
                    pageCountValue(id).value = toNumber(count).toInt();
                  }

                  static void setPage(String id, dynamic index) {
                    pageController(id).jumpToPage(toNumber(index).toInt());
                  }

                  static int currentPage(String id) =>
                      pageController(id).page?.toInt() ?? 0;

                  /// `ViewPager` del layout -> `PageView`.
                  static Widget pageView(String id) => _SkPageView(id: id);
                }

                /// Estado de un dialogo de Sketchware (`dialogSetTitle`, `dialogOkButton`, ...).
                class SkDialog {
                  String title = '';
                  String message = '';
                  String? okText;
                  String? cancelText;
                  String? neutralText;
                  void Function()? onOk;
                  void Function()? onCancel;
                  void Function()? onNeutral;
                }

                /// `TabBar` de la Fase 3: sigue la lista de pestanas de `Sk.tabValue(id)`.
                class _SkTabBar extends StatefulWidget {
                  const _SkTabBar({required this.id});

                  final String id;

                  @override
                  State<_SkTabBar> createState() => _SkTabBarState();
                }

                class _SkTabBarState extends State<_SkTabBar>
                    with SingleTickerProviderStateMixin {
                  TabController? _controller;
                  int _length = -1;

                  @override
                  void dispose() {
                    _controller?.dispose();
                    super.dispose();
                  }

                  @override
                  Widget build(BuildContext context) {
                    return ValueListenableBuilder<List<String>>(
                      valueListenable: Sk.tabValue(widget.id),
                      builder: (context, items, _) {
                        if (items.isEmpty) return const SizedBox.shrink();
                        if (_controller == null || _length != items.length) {
                          _controller?.dispose();
                          _length = items.length;
                          _controller = TabController(length: items.length, vsync: this);
                          _controller!.addListener(() {
                            Sk.tabIndex[widget.id] = _controller!.index;
                          });
                        }
                        return TabBar(
                          controller: _controller,
                          indicatorColor: Sk._tabIndicator[widget.id],
                          labelColor: Sk._tabTextSelected[widget.id],
                          unselectedLabelColor: Sk._tabTextNormal[widget.id],
                          isScrollable: true,
                          tabs: <Widget>[
                            for (final String item in items) Tab(text: item),
                          ],
                        );
                      },
                    );
                  }
                }

                /// `BottomNavigationBar` de la Fase 3.
                class _SkBottomNav extends StatefulWidget {
                  const _SkBottomNav({required this.id});

                  final String id;

                  @override
                  State<_SkBottomNav> createState() => _SkBottomNavState();
                }

                class _SkBottomNavState extends State<_SkBottomNav> {
                  @override
                  Widget build(BuildContext context) {
                    return ValueListenableBuilder<List<String>>(
                      valueListenable: Sk.bottomValue(widget.id),
                      builder: (context, items, _) {
                        if (items.isEmpty) return const SizedBox.shrink();
                        final List<IconData> icons =
                            Sk._bottomIcons[widget.id] ?? <IconData>[];
                        int selected = Sk.bottomIndex[widget.id] ?? 0;
                        if (selected < 0 || selected >= items.length) selected = 0;
                        return BottomNavigationBar(
                          items: <BottomNavigationBarItem>[
                            for (int i = 0; i < items.length; i++)
                              BottomNavigationBarItem(
                                icon: Icon(
                                    i < icons.length ? icons[i] : Icons.circle),
                                label: items[i],
                              ),
                          ],
                          currentIndex: selected,
                          onTap: (int index) => Sk.bottomIndex[widget.id] = index,
                        );
                      },
                    );
                  }
                }

                /// `PageView` de la Fase 3 (una pagina por cada fragmento declarado;
                /// el contenido de cada pagina queda como TODO).
                class _SkPageView extends StatefulWidget {
                  const _SkPageView({required this.id});

                  final String id;

                  @override
                  State<_SkPageView> createState() => _SkPageViewState();
                }

                class _SkPageViewState extends State<_SkPageView> {
                  @override
                  Widget build(BuildContext context) {
                    return ValueListenableBuilder<int>(
                      valueListenable: Sk.pageCountValue(widget.id),
                      builder: (context, count, _) => PageView(
                        controller: Sk.pageController(widget.id),
                        children: <Widget>[
                          for (int i = 0; i < count; i++)
                            Center(child: Text('Pagina ${i + 1}')),
                        ],
                      ),
                    );
                  }
                }
                """;
    }

    public static String readme(String projectName,
                                String applicationName,
                                String packageName,
                                String screenMappings,
                                String blockMappings,
                                String todoSummary,
                                String assetsSummary,
                                String fontsSummary,
                                String componentDependencies,
                                String componentsSummary,
                                String patternsSummary,
                                String adaptersSummary,
                                String stringsSummary) {
        String template = """
                # __APP__ - proyecto Flutter generado por Android-SCode

                Exportado desde el proyecto Sketchware Pro **__PROJECT__** (paquete Android
                `__PACKAGE__`). El zip contiene un proyecto Flutter autonomo y se compila
                **en local** (no usa ningun servicio en la nube).

                ## Como compilar en local (PC con Flutter)

                1. Instala el SDK de Flutter (<https://docs.flutter.dev/get-started/install>) y
                   comprueba que funciona:

                   ```bash
                   flutter --version
                   flutter doctor
                   ```

                2. Descomprime el zip y, desde la carpeta del proyecto:

                   ```bash
                   flutter pub get
                   ```

                   Esto descarga de pub.dev las dependencias que aparecen en `pubspec.yaml`.

                3. Para generar un APK de release:

                   ```bash
                   flutter build apk --release
                   ```

                   El APK queda en `build/app/outputs/flutter-apk/app-release.apk`.
                   (Para probar rapido en un movil/emulador: `flutter run`.)

                > Nota: `flutter build apk` necesita Android SDK + JDK 17; el proyecto Android
                genera el `android/` la primera vez que compiles. Si pub.dev esta detras de un
                proxy, configura `PUB_HOSTED_URL` antes de `flutter pub get`.

                ## Estructura

                | Ruta | Contenido |
                | --- | --- |
                | `pubspec.yaml` | nombre del proyecto, SDK de Dart/Flutter, dependencias y assets/fuentes |
                | `lib/main.dart` | `MaterialApp` + navegacion inicial |
                | `lib/theme.dart` | tema Material 3 compartido |
                | `lib/strings.dart` | cadenas de `res/values/strings.xml` usadas por el codigo |
                | `lib/screens/<pantalla>.dart` | una pantalla por cada layout/Activity del proyecto |
                | `lib/runtime/sk.dart` | runtime Dart propio (variables, listas/mapas, prefs, timers, dialogos, Toast, navegacion, drawer/FAB/menus/tabs/bottom-nav/pager) |
                | `lib/runtime/sk_components.dart` | runtime de componentes con plugins (solo si el proyecto los usa) |
                | `assets/images/...` | imagenes del proyecto usadas por los layouts |
                | `assets/fonts/...` | fuentes `.ttf`/`.otf` usadas por los textos |
                | `README.md` | este documento |

                Dependencias anadidas al `pubspec.yaml` (todas open source, de pub.dev):

                __COMPDEPS__

                Ademas se usa siempre `shared_preferences` (persistencia).

                ## Mapeo de layouts (Android -> Flutter)

                | Android | Flutter |
                | --- | --- |
                | `LinearLayout` (vertical) | `Column` |
                | `LinearLayout` (horizontal) | `Row` |
                | `RelativeLayout` / `FrameLayout` / `ConstraintLayout` | `Stack` (+ `Align` segun gravedad) |
                | `ScrollView` | `SingleChildScrollView` |
                | `HorizontalScrollView` | `SingleChildScrollView(scrollDirection: horizontal)` |
                | `TextView` | `Text` |
                | `Button` / `MaterialButton` | `ElevatedButton` |
                | `EditText` | `TextField` (+ `TextEditingController`) |
                | `ImageView` | `Image.asset` del recurso copiado a `assets/` (o `Image.network`) |
                | `CheckBox` | `Checkbox` |
                | `Switch` | `Switch` |
                | `Spinner` | `DropdownButton` alimentado con `spnSetData` |
                | `ListView` | `ListView` o `ListView.builder` alimentado con `listSetData` |
                | `ProgressBar` | `CircularProgressIndicator` / `LinearProgressIndicator` |
                | `SeekBar` | `Slider` |

                Se conservan los `id` de cada vista como `Key` de widget y como clave del runtime
                (`Sk.setText('id', ...)`, `Sk.getText('id')`). `padding`, `margin`, ancho/alto,
                gravedad, color de fondo, color/tamano/estilo/familia de texto y `hint` se traducen a
                `Padding`, `SizedBox`/`Expanded`, `Align`, `TextStyle` y `InputDecoration`.

                ## Recursos copiados a `assets/`

                Imagenes:

                __ASSETS__

                Fuentes:

                __FONTS__

                ## Mapeo de pantallas

                __SCREENS__

                ## Mapeo de bloques (Fase 1 + Fase 2)

                __BLOCKS__

                ## Bloques no soportados (marcados como `// TODO:` en el codigo)

                __TODOS__

                Cada bloque no soportado se emite como un comentario
                `// TODO: <opcode> <parametros>` para no perder informacion.

                ## Cobertura de la Fase 2

                - **Recursos**: los `drawable`/imagenes raster (png/jpg/webp/gif) y las fuentes
                  `.ttf`/`.otf` referenciadas por los layouts se copian a `assets/images` y
                  `assets/fonts`, y se referencian como `Image.asset('assets/...')` y
                  `TextStyle(fontFamily: ...)`. Los `.svg`/vectores `.xml` y los 9-patch no se
                  pueden cargar directamente: placeholder + TODO.
                - **Listas y adapters**: `listSetData` -> `ListView.builder` ligado a
                  `Sk.bindList(id)`; `spnSetData` -> `DropdownButton` ligado a `Sk.bindSpinner(id)`.
                  Tambien `listRefresh`, `listSetItemChecked`, `listGetCheckedPosition(s)`,
                  `listGetCheckedCount`, `spnRefresh`, `spnSetSelection` y `spnGetSelection`.
                  Los adapters personalizados (`*SetCustomViewData`) siguen como TODO explicito.
                - **Persistencia**: `fileSetFileName`/`fileGetData`/`fileSetData`/`fileRemoveData`
                  se apoyan en `Sk` + `shared_preferences`. Los mapas y listas simples se
                  serializan a JSON con `strToMap`, `strToListMap`, `mapToStr` y `listMapToStr`.
                - **Mas bloques**: listas (`addListInt`/`addListStr`/`insertList*`/`getAtList*`/
                  `indexList*`/`containList*`/`deleteList`/`lengthList`/`clearList`),
                  mapas (`mapCreateNew`/`mapPut`/`mapGet`/`mapContainKey`/`mapRemoveKey`/
                  `mapSize`/`mapIsEmpty`/`mapClear`/`mapGetAllKeys`), listas de mapas
                  (`addListMap`/`insertListMap`/`getAtListMap`/`setListMap`/`containListMap`),
                  dialogos (`dialogSetTitle`/`dialogSetMessage`/`dialogShow`/`dialogOkButton`/
                  `dialogCancelButton`/`dialogNeutralButton`/`dialogDismiss` -> `AlertDialog`),
                  timers (`timerAfter`/`timerEvery`/`timerCancel` -> `Timer`), cadenas/fecha
                  (`toStringFormat`, `currentTime`) y `finish()` (`finishActivity` -> `Sk.finish`).

                ## Limitaciones conocidas

                - La traduccion cubre layouts + logica de bloques (Fases 1, 2 y 3). Los
                  componentes con plugin se traducen a plugins open source (ver mas arriba);
                  `AdView`/`InterstitialAd`/`RewardedVideoAd` quedan como `// TODO:` porque
                  requieren un SDK de anuncios (no hay equivalente open source).
                - Los adapters personalizados generan un `ListView.builder` esbozado con `TODO`
                  explicito: hay que enlazar a mano los campos del layout del item.
                - Un `SharedPreferences` se emula con un unico almacen de `shared_preferences`
                  prefijando las claves con el nombre de fichero; los datos previos de Android no
                  se migran automaticamente.
                - `dialogDismiss` cierra el dialogo mostrado por `dialogShow` (no reproduce el
                  ciclo de vida exacto de `Dialog.dismiss()` de Android).
                - `timerAfter`/`timerEvery` crean timers Dart (`Timer`); `timerCancel` los cancela.
                - Los bloques de los plugins que en Android devuelven un valor de forma **sincrona**
                  (URL/estado del WebView, `mediaplayerGetCurrent`/`GetDuration`, duracion del
                  video, `isBluetoothEnabled`) son **asincronos** en Flutter: se emiten como TODO
                  para no romper la compilacion.
                - No se copian `.svg`/vectores `.xml` ni 9-patch a `assets/`.
                - `Menu` (opciones): se usa un `PopupMenuButton` en el AppBar; los submenus y el
                  `menuInflater` (menu XML) quedan como TODO.
                - `ViewPager`: `PageView` con una pagina por fragmento declarado; el contenido de
                  cada fragmento queda como TODO.
                - `More Blocks` personalizados y el codigo Java de `addSourceDirectly` no se
                  traducen (se emiten como TODO).

                ## Cobertura de la Fase 3

                ### Componentes Android -> plugins Flutter (open source)

                __COMPONENTS__

                ### Patrones de UI

                __PATTERNS__

                ### Adapters personalizados

                __ADAPTERS__

                ### Cadenas y recursos

                __STRINGS__
                """;
        template = fill(template, "__APP__", applicationName);
        template = fill(template, "__PROJECT__", projectName);
        template = fill(template, "__PACKAGE__", packageName);
        template = fill(template, "__SCREENS__", screenMappings);
        template = fill(template, "__BLOCKS__", blockMappings);
        template = fill(template, "__TODOS__", todoSummary);
        template = fill(template, "__ASSETS__", assetsSummary);
        template = fill(template, "__COMPDEPS__", componentDependencies);
        template = fill(template, "__COMPONENTS__", componentsSummary);
        template = fill(template, "__PATTERNS__", patternsSummary);
        template = fill(template, "__ADAPTERS__", adaptersSummary);
        template = fill(template, "__STRINGS__", stringsSummary);
        return fill(template, "__FONTS__", fontsSummary);
    }
}
