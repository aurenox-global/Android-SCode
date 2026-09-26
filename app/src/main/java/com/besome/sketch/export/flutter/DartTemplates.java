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
                                 String fontsYaml) {
        String flutterSection = "flutter:\n  uses-material-design: true\n"
                + (assetsYaml == null ? "" : assetsYaml)
                + (fontsYaml == null ? "" : fontsYaml);
        String yaml = """
                # pubspec.yaml generado por Android-SCode (Export Project -> Flutter project).
                #
                # El proyecto esta listo para `flutter run` desde la raiz del zip:
                #   flutter pub get
                #   flutter run
                #
                # Dependencia externa: shared_preferences (persistencia de los bloques
                # `fileGetData`/`fileSetData`). `flutter pub get` la descarga de pub.dev.
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

                dev_dependencies:
                  flutter_test:
                    sdk: flutter
                  flutter_lints: ^4.0.0

                __FLUTTER__
                """;
        yaml = fill(yaml, "__NAME__", pubspecName);
        yaml = fill(yaml, "__DESCRIPTION__", description == null ? "" : description);
        return fill(yaml, "__FLUTTER__", flutterSection);
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
                """;
    }

    public static String readme(String projectName,
                                String applicationName,
                                String packageName,
                                String screenMappings,
                                String blockMappings,
                                String todoSummary,
                                String assetsSummary,
                                String fontsSummary) {
        String template = """
                # __APP__ - proyecto Flutter generado por Android-SCode

                Exportado desde el proyecto Sketchware Pro **__PROJECT__** (paquete Android
                `__PACKAGE__`). El zip contiene un proyecto Flutter autonomo, listo para:

                ```bash
                flutter pub get
                flutter run
                ```

                ## Estructura

                | Ruta | Contenido |
                | --- | --- |
                | `pubspec.yaml` | nombre del proyecto, SDK de Dart/Flutter y assets/fuentes |
                | `lib/main.dart` | `MaterialApp` + navegacion inicial |
                | `lib/theme.dart` | tema Material 3 compartido |
                | `lib/screens/<pantalla>.dart` | una pantalla por cada layout/Activity del proyecto |
                | `lib/runtime/sk.dart` | runtime Dart propio (variables, listas/mapas, prefs, timers, dialogos, Toast, navegacion) |
                | `assets/images/...` | imagenes del proyecto usadas por los layouts |
                | `assets/fonts/...` | fuentes `.ttf`/`.otf` usadas por los textos |
                | `README.md` | este documento |

                Unica dependencia externa: `shared_preferences` (persistencia).

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

                - La traduccion cubre layouts + logica de bloques (Fases 1 y 2). Los componentes
                  con plugin de plataforma (Firebase, camara, Bluetooth, media, sensores, GPS,
                  `AdView`, `MapView`, `WebView`) siguen como `// TODO:`.
                - `listSetCustomViewData`/adapters personalizados no se traducen: el `ListView`
                  queda vacio + TODO.
                - Un `SharedPreferences` se emula con un unico almacen de `shared_preferences`
                  prefijando las claves con el nombre de fichero; los datos previos de Android no
                  se migran automaticamente.
                - `dialogDismiss` cierra el dialogo mostrado por `dialogShow` (no reproduce el
                  ciclo de vida exacto de `Dialog.dismiss()` de Android).
                - `timerAfter`/`timerEvery` crean timers Dart (`Timer`); `timerCancel` los cancela.
                - No se copian `.svg`/vectores `.xml` ni 9-patch a `assets/`; tampoco
                  `Drawer`, `FAB`, `Toolbar`/menus, `TabLayout`/`BottomNavigation`, `ViewPager`,
                  cadenas a `l10n`, ni `More Blocks` personalizados.

                ## Que queda pendiente (Fase 3)

                - Emisores de componentes: Firebase, camara/galeria, Bluetooth, media player,
                  sensores, GPS, `AdView`, `MapView`.
                - Adapters personalizados (`custom view data`) -> widgets propios.
                - `.svg`/vectores y cadenas de recursos (`@string`, `getResStr`).
                - `Drawer`, `FAB`, `Toolbar`/menus, `TabLayout`/`BottomNavigation`, `ViewPager`.
                - `onCreate`/`initializeLogic` completos y `More Blocks` personalizados.
                """;
        template = fill(template, "__APP__", applicationName);
        template = fill(template, "__PROJECT__", projectName);
        template = fill(template, "__PACKAGE__", packageName);
        template = fill(template, "__SCREENS__", screenMappings);
        template = fill(template, "__BLOCKS__", blockMappings);
        template = fill(template, "__TODOS__", todoSummary);
        template = fill(template, "__ASSETS__", assetsSummary);
        return fill(template, "__FONTS__", fontsSummary);
    }
}
