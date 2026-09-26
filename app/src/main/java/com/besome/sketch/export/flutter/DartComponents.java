package com.besome.sketch.export.flutter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registro de los componentes Android que la Fase 3 del export Flutter traduce a plugins
 * <b>open source</b> de pub.dev (nada de servicios de pago).
 *
 * <p>Cada componente aporta tres cosas:</p>
 * <ul>
 *   <li>las dependencias que hay que anadir al {@code pubspec.yaml} generado (solo si el proyecto
 *       las usa),</li>
 *   <li>las importaciones del fichero de runtime,</li>
 *   <li>el codigo Dart del runtime ({@code lib/runtime/sk_components.dart}).</li>
 * </ul>
 *
 * <p>Los bloques de logica y los widgets de layout llaman a las clases {@code SkC*}
 * ({@code SkCWebView}, {@code SkCAudio}, ...). Todo el uso de las APIs de los plugins se concentra
 * aqui: si una version del plugin cambia una firma, solo hay que retocar este fichero
 * ({@code sk_components.dart}) en el proyecto exportado.</p>
 *
 * <p>{@code AdView}/{@code InterstitialAd}/{@code RewardedVideoAd} quedan como TODO explicito:
 * los anuncios exigen un SDK de anuncios (no hay equivalente open source) y el bloque emite
 * {@code Sk.todo(...)} para no romper la compilacion.</p>
 */
public class DartComponents {

    public static final String WEBVIEW = "webview";
    public static final String CAMERA = "camera";
    public static final String IMAGE_PICKER = "image_picker";
    public static final String BLUETOOTH = "bluetooth";
    public static final String SENSORS = "sensors";
    public static final String GEOLOCATOR = "geolocator";
    public static final String AUDIO = "audio";
    public static final String VIDEO = "video";
    public static final String MAP = "map";
    public static final String ADS = "ads";

    private final LinkedHashSet<String> used = new LinkedHashSet<>();

    /**
     * @return el componente (id de {@link DartComponents}) que cubre un opcode, o {@code null} si
     * no es un opcode de componente.
     */
    public static String componentForOpcode(String opCode) {
        if (opCode == null) {
            return null;
        }
        if (opCode.startsWith("webView")) {
            return WEBVIEW;
        }
        if (opCode.startsWith("camerastart")) {
            return CAMERA;
        }
        if (opCode.startsWith("filepicker")) {
            return IMAGE_PICKER;
        }
        if (opCode.startsWith("bluetooth")) {
            return BLUETOOTH;
        }
        if (opCode.startsWith("gyroscope")) {
            return SENSORS;
        }
        if (opCode.startsWith("locationManager")) {
            return GEOLOCATOR;
        }
        if (opCode.startsWith("mediaplayer") || opCode.startsWith("soundpool")) {
            return AUDIO;
        }
        if (opCode.startsWith("videoview")) {
            return VIDEO;
        }
        if (opCode.startsWith("mapView")) {
            return MAP;
        }
        if ("adViewLoadAd".equals(opCode) || opCode.startsWith("interstitialad")
                || opCode.startsWith("rewardedVideoAd")) {
            return ADS;
        }
        return null;
    }

    /**
     * Registra el componente que corresponde a un opcode (si lo hay).
     *
     * @return {@code true} si el opcode pertenece a un componente conocido.
     */
    public boolean useForOpcode(String opCode) {
        String component = componentForOpcode(opCode);
        if (component == null) {
            return false;
        }
        used.add(component);
        return true;
    }

    /**
     * Registra explicitamente un componente.
     */
    public void use(String component) {
        used.add(component);
    }

    public boolean isEmpty() {
        return used.isEmpty();
    }

    public Set<String> getUsed() {
        return used;
    }

    /**
     * @return las lineas {@code nombre: version} que hay que anadir a {@code dependencies:} del
     * {@code pubspec.yaml} generado (solo de los componentes usados).
     */
    public String pubspecDependencies() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> dependency : dependencies().entrySet()) {
            if (isDependencyUsed(dependency.getKey())) {
                sb.append("  ").append(dependency.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    private boolean isDependencyUsed(String key) {
        if (MAP_LATLONG.equals(key)) {
            return used.contains(MAP);
        }
        return used.contains(key);
    }

    /**
     * @return las lineas {@code - nombre: version} documentadas en el README (solo usadas).
     */
    public String readmeDependencies() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> dependency : dependencies().entrySet()) {
            if (isDependencyUsed(dependency.getKey())) {
                sb.append("- `").append(dependency.getValue()).append("`\n");
            }
        }
        if (sb.length() == 0) {
            return "_ninguna_";
        }
        return sb.toString();
    }

    /**
     * @return dependencias del pubspec por componente (clave = id de componente). El mapa se
     * declara como dos entradas porque {@code flutter_map} necesita {@code latlong2}.
     */
    private Map<String, String> dependencies() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put(WEBVIEW, "webview_flutter: ^4.9.0");
        result.put(CAMERA, "camera: ^0.11.0");
        result.put(IMAGE_PICKER, "image_picker: ^1.1.2");
        result.put(BLUETOOTH, "flutter_blue_plus: ^1.35.5");
        result.put(SENSORS, "sensors_plus: ^6.0.1");
        result.put(GEOLOCATOR, "geolocator: ^13.0.2");
        result.put(AUDIO, "audioplayers: ^6.1.0");
        result.put(VIDEO, "video_player: ^2.9.2");
        result.put(MAP, "flutter_map: ^7.0.2");
        result.put(MAP_LATLONG, "latlong2: ^0.9.1");
        return result;
    }

    /** Clave interna: {@code latlong2} viaja con {@link #MAP}. */
    private static final String MAP_LATLONG = MAP + "_latlong2";

    /**
     * @return el contenido completo de {@code lib/runtime/sk_components.dart}, o cadena vacia si el
     * proyecto no usa ningun componente.
     */
    public String runtimeFile() {
        if (used.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(8192);
        sb.append("""
                // Runtime de componentes de Android-SCode para proyectos exportados a Flutter
                // (Fase 3). Concentra todo el uso de los plugins de pub.dev; el resto del codigo
                // generado solo llama a las clases SkC*.
                //
                // Dependencias de este fichero (ver pubspec.yaml): se anaden solo las que el
                // proyecto usa de verdad.
                // ignore_for_file: unused_import, avoid_print, library_private_types_in_public_api

                import 'dart:async';

                import 'package:flutter/material.dart';

                import 'sk.dart';

                """);
        if (used.contains(WEBVIEW)) {
            sb.append("import 'package:webview_flutter/webview_flutter.dart';\n");
        }
        if (used.contains(CAMERA)) {
            sb.append("import 'package:camera/camera.dart';\n");
        }
        if (used.contains(IMAGE_PICKER)) {
            sb.append("import 'package:image_picker/image_picker.dart';\n");
        }
        if (used.contains(BLUETOOTH)) {
            sb.append("import 'package:flutter_blue_plus/flutter_blue_plus.dart';\n");
        }
        if (used.contains(SENSORS)) {
            sb.append("import 'package:sensors_plus/sensors_plus.dart';\n");
        }
        if (used.contains(GEOLOCATOR)) {
            sb.append("import 'package:geolocator/geolocator.dart';\n");
        }
        if (used.contains(AUDIO)) {
            sb.append("import 'package:audioplayers/audioplayers.dart';\n");
        }
        if (used.contains(VIDEO)) {
            sb.append("import 'package:video_player/video_player.dart';\n");
        }
        if (used.contains(MAP)) {
            sb.append("import 'package:flutter_map/flutter_map.dart';\n");
            sb.append("import 'package:latlong2/latlong.dart';\n");
        }
        sb.append('\n');

        if (used.contains(WEBVIEW)) {
            sb.append(WEBVIEW_CODE);
        }
        if (used.contains(CAMERA)) {
            sb.append(CAMERA_CODE);
        }
        if (used.contains(IMAGE_PICKER)) {
            sb.append(IMAGE_PICKER_CODE);
        }
        if (used.contains(AUDIO)) {
            sb.append(AUDIO_CODE);
        }
        if (used.contains(VIDEO)) {
            sb.append(VIDEO_CODE);
        }
        if (used.contains(GEOLOCATOR)) {
            sb.append(GEOLOCATOR_CODE);
        }
        if (used.contains(SENSORS)) {
            sb.append(SENSORS_CODE);
        }
        if (used.contains(BLUETOOTH)) {
            sb.append(BLUETOOTH_CODE);
        }
        if (used.contains(MAP)) {
            sb.append(MAP_CODE);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- codigo Dart

    private static final String WEBVIEW_CODE = """
            // ---------------------------------------------------------------- WebView
            /// `webViewLoadUrl`, `webViewGoBack`, ... -> `webview_flutter`.
            /// El widget de layout se pinta con `SkCWebView.build('id')`.
            class SkCWebView {
              static final Map<String, WebViewController> _controllers =
                  <String, WebViewController>{};

              static WebViewController controller(String id) => _controllers.putIfAbsent(
                  id, () => WebViewController()..setJavaScriptMode(JavaScriptMode.unrestricted));

              static Widget build(String id, {Key? key}) =>
                  WebViewWidget(key: key, controller: controller(id));

              static void loadUrl(String id, dynamic url) =>
                  controller(id).loadRequest(Uri.parse(Sk.toText(url)));

              static void goBack(String id) => controller(id).goBack();

              static void goForward(String id) => controller(id).goForward();

              static void reload(String id) => controller(id).reload();

              static void clearCache(String id) => controller(id).clearCache();
            }

            """;

    private static final String CAMERA_CODE = """
            // ---------------------------------------------------------------- Camara
            /// `camerastarttakepicture` -> `camera`.
            /// La vista previa del layout se pinta con `SkCWebView`-like
            /// `SkCCamera` (ver README); en las pantallas sin preview se abre la camara del sistema
            /// con `image_picker` si el proyecto tambien usa galeria.
            class SkCCamera {
              static final Map<String, CameraController> _controllers =
                  <String, CameraController>{};

              static Future<CameraController> controller(String id) async {
                final CameraController? existing = _controllers[id];
                if (existing != null) return existing;
                final List<CameraDescription> cameras = await availableCameras();
                final CameraDescription camera = cameras.firstWhere(
                  (CameraDescription description) =>
                      description.lensDirection == CameraLensDirection.back,
                  orElse: () => cameras.first,
                );
                final CameraController controller =
                    CameraController(camera, ResolutionPreset.high);
                await controller.initialize();
                _controllers[id] = controller;
                return controller;
              }

              static Future<void> start(String id) => controller(id);

              static Future<void> takePicture(String id, dynamic path) async {
                final CameraController controller = await SkCCamera.controller(id);
                final XFile file = await controller.takePicture();
                final String target = Sk.toText(path);
                if (target.isNotEmpty) {
                  await file.saveTo(target);
                }
              }
            }

            """;

    private static final String IMAGE_PICKER_CODE = """
            // ---------------------------------------------------------------- Galeria / ficheros
            /// `filepickerstartpickfiles` -> `image_picker` (galeria de imagenes).
            class SkCPicker {
              static final ImagePicker _picker = ImagePicker();

              /// Ultimos ficheros elegidos (equivale a la salida del FilePicker de Android).
              static final List<XFile> pickedFiles = <XFile>[];

              static Future<void> pickFiles(String id, dynamic mimeType) async {
                final List<XFile> files = await _picker.pickMultiImage();
                pickedFiles
                  ..clear()
                  ..addAll(files);
              }
            }

            """;

    private static final String AUDIO_CODE = """
            // ---------------------------------------------------------------- Audio
            /// `mediaplayerCreate`/`mediaplayerStart`/... -> `audioplayers`.
            /// `mediaplayerCreate` toma el nombre del recurso `R.raw.<nombre>`; en Flutter se
            /// resuelve como asset `audio/<nombre>` (copia tus audios a `assets/audio/` y anadelos
            /// al pubspec) o como URL si empieza por `http`.
            class SkCAudio {
              static final Map<String, AudioPlayer> _players = <String, AudioPlayer>{};
              static final Map<String, String> _sources = <String, String>{};

              static AudioPlayer player(String id) =>
                  _players.putIfAbsent(id, () => AudioPlayer());

              static Source _source(String path) => path.startsWith('http')
                  ? UrlSource(path)
                  : (path.contains('/') ? AssetSource(path) : AssetSource('audio/' + path));

              static Future<void> create(String id, dynamic source) async {
                final String path = Sk.toText(source);
                _sources[id] = path;
                await player(id).setSource(_source(path));
              }

              static Future<void> start(String id) async {
                final String? path = _sources[id];
                if (path == null) return;
                await player(id).play(_source(path));
              }

              static Future<void> pause(String id) => player(id).pause();

              static Future<void> stop(String id) => player(id).stop();

              static Future<void> reset(String id) => player(id).stop();

              static Future<void> release(String id) async {
                await player(id).dispose();
                _players.remove(id);
              }

              static Future<void> seek(String id, dynamic ms) =>
                  player(id).seek(Duration(milliseconds: Sk.toNumber(ms).toInt()));

              static Future<void> setLooping(String id, dynamic looping) => player(id)
                  .setReleaseMode(Sk.toBool(looping) ? ReleaseMode.loop : ReleaseMode.release);
            }

            """;

    private static final String VIDEO_CODE = """
            // ---------------------------------------------------------------- Video
            /// `videoviewSetVideoUri`/`videoviewStart`/... -> `video_player`.
            /// El widget de layout se pinta con `SkCVideo.build('id')`.
            class SkCVideo {
              static final Map<String, VideoPlayerController> controllers =
                  <String, VideoPlayerController>{};

              static VideoPlayerController controllerFor(String id) => controllers[id]!;

              static Future<void> setUri(String id, dynamic url) async {
                final String path = Sk.toText(url);
                final VideoPlayerController controller = path.startsWith('http')
                    ? VideoPlayerController.networkUrl(Uri.parse(path))
                    : VideoPlayerController.asset(path);
                await controller.initialize();
                await controllers[id]?.dispose();
                controllers[id] = controller;
              }

              static Future<void> start(String id) => controllerFor(id).play();

              static Future<void> pause(String id) => controllerFor(id).pause();

              static Future<void> stop(String id) async {
                final VideoPlayerController controller = controllerFor(id);
                await controller.pause();
                await controller.seekTo(Duration.zero);
              }

              static Widget build(String id, {Key? key}) =>
                  _SkCVideoView(id: id, widgetKey: key);
            }

            class _SkCVideoView extends StatelessWidget {
              const _SkCVideoView({required this.id, this.widgetKey});

              final String id;
              final Key? widgetKey;

              @override
              Widget build(BuildContext context) {
                final VideoPlayerController? controller = SkCVideo.controllers[id];
                if (controller == null || !controller.value.isInitialized) {
                  return Container(
                    key: widgetKey,
                    height: 200,
                    color: Colors.black12,
                    child: const Center(child: Icon(Icons.videocam, size: 48)),
                  );
                }
                return AspectRatio(
                  key: widgetKey,
                  aspectRatio: controller.value.aspectRatio,
                  child: VideoPlayer(controller),
                );
              }
            }

            """;

    private static final String GEOLOCATOR_CODE = """
            // ---------------------------------------------------------------- Ubicacion
            /// `locationManagerRequestLocationUpdates`/`RemoveUpdates` -> `geolocator`.
            /// La posicion mas reciente queda en `SkCLocation.latitude` / `.longitude`.
            class SkCLocation {
              static StreamSubscription<Position>? _subscription;
              static Position? lastPosition;

              static Future<bool> ensurePermission() async {
                if (!await Geolocator.isLocationServiceEnabled()) return false;
                LocationPermission permission = await Geolocator.checkPermission();
                if (permission == LocationPermission.denied) {
                  permission = await Geolocator.requestPermission();
                }
                return permission != LocationPermission.denied &&
                    permission != LocationPermission.deniedForever;
              }

              static Future<void> startUpdates(
                  String id, dynamic provider, dynamic minTime, dynamic minDistance) async {
                if (!await ensurePermission()) return;
                await _subscription?.cancel();
                _subscription = Geolocator.getPositionStream(
                  locationSettings: LocationSettings(
                    accuracy: LocationAccuracy.high,
                    distanceFilter: Sk.toNumber(minDistance).toInt(),
                  ),
                ).listen((Position position) => lastPosition = position);
              }

              static Future<void> stopUpdates(String id) async {
                await _subscription?.cancel();
                _subscription = null;
              }

              static double get latitude => lastPosition?.latitude ?? 0;
              static double get longitude => lastPosition?.longitude ?? 0;
            }

            """;

    private static final String SENSORS_CODE = """
            // ---------------------------------------------------------------- Sensores
            /// `gyroscopeStartListen`/`gyroscopeStopListen` -> `sensors_plus`.
            /// El ultimo valor queda en `SkCSensors.lastGyroscope`.
            class SkCSensors {
              static StreamSubscription<GyroscopeEvent>? _gyroscope;
              static GyroscopeEvent? lastGyroscope;

              static Future<void> startGyroscope(String id) async {
                await _gyroscope?.cancel();
                _gyroscope = gyroscopeEventStream()
                    .listen((GyroscopeEvent event) => lastGyroscope = event);
              }

              static Future<void> stopGyroscope(String id) async {
                await _gyroscope?.cancel();
                _gyroscope = null;
              }
            }

            """;

    private static final String BLUETOOTH_CODE = """
            // ---------------------------------------------------------------- Bluetooth
            /// `bluetoothConnectStartConnection`/`SendData`/... -> `flutter_blue_plus` (BLE).
            /// Nota: `bluetoothConnectIsBluetoothEnabled` y `getPairedDevices` son asincronos en
            /// Flutter; los bloques que los usan como valor quedan como TODO.
            class SkCBluetooth {
              static final Map<String, BluetoothDevice> _devices =
                  <String, BluetoothDevice>{};
              static final List<BluetoothDevice> bondedDevices = <BluetoothDevice>[];

              static BluetoothDevice device(String address) =>
                  _devices.putIfAbsent(address, () => BluetoothDevice.fromId(address));

              static Future<bool> isBluetoothEnabled() async {
                final BluetoothAdapterState state =
                    await FlutterBluePlus.adapterState.first;
                return state == BluetoothAdapterState.on;
              }

              static Future<void> activateBluetooth() => FlutterBluePlus.turnOn();

              static Future<void> getPairedDevices(String id, dynamic out) async {
                bondedDevices
                  ..clear()
                  ..addAll(await FlutterBluePlus.bondedDevices);
              }

              static Future<void> startConnection(
                      String id, dynamic address, dynamic uuid) =>
                  device(Sk.toText(address)).connect();

              static Future<void> stopConnection(String id, dynamic address) =>
                  device(Sk.toText(address)).disconnect();

              static Future<void> sendData(
                      String id, dynamic address, dynamic data) =>
                  device(Sk.toText(address)).write(Sk.toText(data).codeUnits);
            }

            """;

    private static final String MAP_CODE = """
            // ---------------------------------------------------------------- Mapa
            /// `mapViewMoveCamera`/`mapViewAddMarker`/... -> `flutter_map` + tiles de
            /// OpenStreetMap (libre). El widget de layout se pinta con `SkCMap.build('id')`.
            class SkCMap {
              static final Map<String, SkCMapState> _states = <String, SkCMapState>{};

              static SkCMapState state(String id) =>
                  _states.putIfAbsent(id, () => SkCMapState());

              static Widget build(String id, {Key? key}) =>
                  _SkCMapView(id: id, widgetKey: key);
            }

            class SkCMapState extends ChangeNotifier {
              double latitude = 0;
              double longitude = 0;
              double zoom = 15;
              final List<SkCMarker> markers = <SkCMarker>[];

              void move(dynamic lat, dynamic lng, dynamic newZoom) {
                latitude = Sk.toNumber(lat);
                longitude = Sk.toNumber(lng);
                zoom = Sk.toNumber(newZoom);
                notifyListeners();
              }

              void zoomTo(dynamic newZoom) {
                zoom = Sk.toNumber(newZoom);
                notifyListeners();
              }

              void addMarker(dynamic markerId, dynamic lat, dynamic lng) {
                markers.add(SkCMarker(Sk.toNumber(lat), Sk.toNumber(lng), Sk.toText(markerId)));
                notifyListeners();
              }
            }

            class SkCMarker {
              SkCMarker(this.latitude, this.longitude, this.markerId);

              final double latitude;
              final double longitude;
              final String markerId;
            }

            class _SkCMapView extends StatefulWidget {
              const _SkCMapView({required this.id, this.widgetKey});

              final String id;
              final Key? widgetKey;

              @override
              State<_SkCMapView> createState() => _SkCMapViewState();
            }

            class _SkCMapViewState extends State<_SkCMapView> {
              @override
              Widget build(BuildContext context) {
                final SkCMapState map = SkCMap.state(widget.id);
                return AnimatedBuilder(
                  animation: map,
                  builder: (BuildContext context, Widget? child) => FlutterMap(
                    key: widget.widgetKey,
                    options: MapOptions(
                      initialCenter: LatLng(map.latitude, map.longitude),
                      initialZoom: map.zoom,
                    ),
                    children: <Widget>[
                      TileLayer(
                        urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                        userAgentPackageName: 'com.example.app',
                      ),
                      MarkerLayer(
                        markers: <Marker>[
                          for (final SkCMarker marker in map.markers)
                            Marker(
                              point: LatLng(marker.latitude, marker.longitude),
                              width: 40,
                              height: 40,
                              child: const Icon(Icons.location_on,
                                  color: Colors.red, size: 40),
                            ),
                        ],
                      ),
                    ],
                  ),
                );
              }
            }

            """;
}
