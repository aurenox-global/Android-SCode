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

    public static String pubspec(String pubspecName, String description) {
        return fill(fill("""
                # pubspec.yaml generado por Android-SCode (Export Project -> Flutter project).
                #
                # El proyecto esta listo para `flutter run` desde la raiz del zip:
                #   flutter pub get
                #   flutter run
                #
                # Solo usa el SDK de Flutter (sin dependencias externas) para que funcione
                # sin acceso a pub.dev.
                name: __NAME__
                description: __DESCRIPTION__
                publish_to: "none"
                version: 1.0.0+1

                environment:
                  sdk: '>=3.0.0 <4.0.0'

                dependencies:
                  flutter:
                    sdk: flutter

                dev_dependencies:
                  flutter_test:
                    sdk: flutter
                  flutter_lints: ^4.0.0

                flutter:
                  uses-material-design: true
                """, "__NAME__", pubspecName), "__DESCRIPTION__", description);
    }

    public static String mainDart(String title, String homeScreenClass, String imports) {
        String template = """
                // main.dart generado por Android-SCode.
                //
                // Punto de entrada de la app Flutter. La pantalla inicial es la primera
                // Activity del proyecto (__HOME__); el resto se alcanza con los bloques
                // `startActivity` / `Intent` traducidos a `Sk.go(...)`.

                import 'package:flutter/material.dart';

                import 'theme.dart';
                __IMPORTS__

                void main() {
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

                import 'dart:math' as math;

                import 'package:flutter/material.dart';

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
                """;
    }

    public static String readme(String projectName,
                                String applicationName,
                                String packageName,
                                String screenMappings,
                                String blockMappings,
                                String todoSummary) {
        String template = """
                # __APP__ - proyecto Flutter generado por Android-SCode

                Exportado desde el proyecto Sketchware Pro **__PROJECT__** (paquete Android
                `__PACKAGE__`). El zip contiene un proyecto Flutter autonomo, listo para:

                ```bash
                flutter pub get
                flutter run
                ```

                No depende de ningun paquete de pub.dev (solo el SDK de Flutter), asi que
                `flutter pub get` funciona tambien sin red.

                ## Estructura

                | Ruta | Contenido |
                | --- | --- |
                | `pubspec.yaml` | nombre del proyecto y SDK de Dart/Flutter |
                | `lib/main.dart` | `MaterialApp` + navegacion inicial |
                | `lib/theme.dart` | tema Material 3 compartido |
                | `lib/screens/<pantalla>.dart` | una pantalla por cada layout/Activity del proyecto |
                | `lib/runtime/sk.dart` | runtime Dart propio (variables, Toast, navegacion, helpers) |
                | `README.md` | este documento |

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
                | `ImageView` | `Image` (placeholder si el recurso no es una URL) |
                | `CheckBox` | `Checkbox` |
                | `Switch` | `Switch` |
                | `Spinner` | `DropdownButton` |
                | `ListView` | `ListView` |
                | `ProgressBar` | `CircularProgressIndicator` / `LinearProgressIndicator` |
                | `SeekBar` | `Slider` |

                Se conservan los `id` de cada vista como `Key` de widget y como clave del runtime
                (`Sk.setText('id', ...)`, `Sk.getText('id')`). `padding`, `margin`, ancho/alto,
                gravedad, color de fondo, color/tamano/estilo de texto y `hint` se traducen a
                `Padding`, `SizedBox`/`Expanded`, `Align` y `TextStyle`.

                ## Mapeo de pantallas

                __SCREENS__

                ## Mapeo de bloques (Fase 1)

                __BLOCKS__

                ## Bloques no soportados (marcados como `// TODO:` en el codigo)

                __TODOS__

                Cada bloque no soportado se emite como un comentario
                `// TODO: <opcode> <parametros>` para no perder informacion.

                ## Limitaciones conocidas

                - **Fase 1 (esta exportacion)**: la traduccion es estructural + logica basica.
                  Componentes (Firebase, camara, Bluetooth, media, sensores...), menus, `Drawer`,
                  `FAB`, `AdView`, `MapView`, `WebView` y las APIs que necesitan plugins de
                  plataforma quedan como `// TODO:`.
                - Un `ListView`/`Spinner` con adapter se genera vacio: los datos (`listSetData`,
                  `spnSetData`, adapters personalizados) quedan como TODO.
                - Los `EditText` conservan su texto en controladores globales por id: dos
                  instancias simultaneas de la misma pantalla comparten estado.
                - `RelativeLayout` se traduce a `Stack`: las reglas de posicionamiento relativas
                  (`layout_toRightOf`, etc.) no se conservan.
                - Los recursos (`@drawable/...`, `@string/...`, fuentes `.ttf`) no se copian: las
                  imagenes se sustituyen por un placeholder y se anota un TODO.
                - El idioma de las cadenas queda como en el proyecto Android (los literales de los
                  bloques y los textos de las vistas se copian tal cual).

                ## Que hara falta para la Fase 2

                - Emisores de componentes: Firebase, camara/galeria, Bluetooth, media player,
                  sensores, GPS, `AdView`, `MapView`.
                - Adapters de lista/spinner (incluido "custom view data") -> `ListView.builder` y
                  `DropdownButton` con datos.
                - `Drawer`, `FAB`, `Toolbar`/menus, `TabLayout`/`BottomNavigation` (navegacion),
                  `ViewPager`.
                - Persistencia: `fileGetData`/`fileSetData` (SharedPreferences -> `shared_preferences`
                  o `Sk` en memoria), Gson/mapas/listas completos.
                - Recursos: copiar `drawable`/`mipmap`/fuentes a `assets/` y generar el mapa de
                  imagenes, y las cadenas a un `AppStrings`/`l10n`.
                - `onCreate`/`initializeLogic` completos, `More Blocks` y bloques personalizados
                  (los `ExtraBlockInfo` con `getCode()` propio no tienen equivalente Dart directo).
                """;
        template = fill(template, "__APP__", applicationName);
        template = fill(template, "__PROJECT__", projectName);
        template = fill(template, "__PACKAGE__", packageName);
        template = fill(template, "__SCREENS__", screenMappings);
        template = fill(template, "__BLOCKS__", blockMappings);
        return fill(template, "__TODOS__", todoSummary);
    }
}
