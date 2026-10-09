# Changelog — Android SCode

Las notas de cada release, con lo que quedó **arreglado y verificado** y lo que **NO se pudo cerrar**
(para no prometer de más). El detalle técnico y la matriz de invariantes viven en
[`REGRESSION-NOTES.md`](REGRESSION-NOTES.md).

Formato: por release, primero lo que funciona, después los límites honestos.

---

## v1.0.42.1 — TTS de la v1.0.42 + fixes de compilación, apps generadas y tema (2026-10-09)

versionCode 56 · versionName `v1.0.42.1` · APK universal (arm64-v8a, armeabi-v7a, x86_64, x86).

Esta release **rebasea** el trabajo de hoy sobre el `origin/main` actual y **conserva AMBOS** conjuntos
de fixes: los 22 commits de TTS de la v1.0.42 (release `722e5b8`, versionCode 55) y los arreglos de
v1.0.41.1. El código de `app/src/**` se integró sin conflictos; solo hubo que fusionar a mano
`app/build.gradle`, `README.md`, `README.es.md`, `docs/index.html` y `docs/es.html`.

### Arreglado y verificado

- **TTS del WebView (v1.0.42).** Se conserva el puente `AndroidBridge` sin duplicados (dedup a nombres
  cualificados) y garantizado en `onProgressChanged` y `onPageFinished`; la migración actualiza el helper
  antiguo de los proyectos ya parcheados; se retiró el motor interno Piper y se limpian sus restos en cada
  compilación; el manifiesto propio del proyecto conserva la visibilidad TTS (`queries TTS_SERVICE`); el
  motor del sistema elige voz por idioma, avisa si faltan datos y hace fallback, y expone diagnóstico JS.
- **Voz en las apps compiladas (`@JavascriptInterface`).** `AnnotationPromoter` (ASM) promueve la
  anotación a **RuntimeVisible** tras ECJ, sobre la salida del shrinker y antes de R8; `getRJavaRules()`
  añade siempre `-keepattributes *Annotation*` + `-keepclassmembers`; `BuiltInLibraries` re-extrae el
  `android.jar` en cada build.
- **D8 en el dispositivo.** `app/proguard-rules.pro` conserva **todo** `com.android.tools.r8.**`.
- **Versión del proyecto.** `versionCode`/`versionName` se releen del metadata en cada build y
  `stripManifestVersionAttributes()` los quita del manifiesto (aapt2 ignora `--version-code/--version-name`
  si el manifiesto ya los trae).
- **Tema Material3 (barra superior).** `yq#getXMLStyle` pasa los colores del proyecto al tema; **NUNCA**
  emite `colorSurfaceTint` (`material-1.13.0` no lo declara y aapt2 falla el enlace).
- **Toggle Toolbar con AndroidManifest personalizado.** `yq#applyGeneratedActivityThemes()` re-aplica el
  tema por-Activity del generador (Toolbar OFF → `@style/NoActionBar`) sin tocar temas del usuario.
- **Previsualización del editor sincronizada.** `DesignActivity` + `ViewEditorFragment` reconstruyen la
  preview desde el bean almacenado (`jC.b(sc_id)`).
- **Botón de copiar** en el registro de compilación (`compile_log.xml` + `CompileLogActivity`).

### NO cerrado / límites honestos

- **Verificación de la matriz TTS en dispositivo.** Sigue pendiente el `dexdump` de
  `JavascriptInterface` en un APK compilado en el emulador por cada combinación (misma limitación que
  v1.0.41.1: no se puede inyectar una clase de prueba porque el IDE limpia `app/src/main/java` y `gen/`
  en cada build). La cadena causal está probada en el host con el mismo R8 8.11.18.
- **Coexistencia TTS ↔ fixes.** El rebase integró `yq.java` y `ProjectBuilder.java` automáticamente
  (regiones distintas), pero **no** se re-ejecutó la matriz de 7 casos Toolbar/tema/versión/preview
  sobre este árbol fusionado; ver `REGRESSION-NOTES.md`.
- **`colorSurfaceTint`:** no volver a emitirlo nunca.

---

## v1.0.41.1 — compilación, apps generadas y tema (2026-10-09)

versionCode 48 · versionName `v1.0.41.1` · APK universal (arm64-v8a, armeabi-v7a, x86_64, x86).

### Arreglado y verificado

- **Voz en las apps compiladas (`@JavascriptInterface`).** El puente del WebView
  (`AndroidBridge.speak/stop`) llegaba al dex como anotación de compilación (RuntimeInvisible), así
  que el WebView no exponía el puente y la app compilada quedaba **muda**. Nuevo
  `mod/jbk/build/compiler/AnnotationPromoter.java` (ASM) que la promueve a **RuntimeVisible**:
  tras ECJ (`ProjectBuilder#compileJavaCode`), sobre la salida del shrinker
  (`createDexFilesFromClasses`) y antes de R8 (`runR8`, vía `promoteJar`). Además
  `getRJavaRules()` añade **siempre** (se regenera en cada build)
  `-keepattributes *Annotation*` + `-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }`,
  y `BuiltInLibraries.maybeExtractAndroidJar()` **re-extrae el `android.jar` en cada build** (un stub
  con longitud coincidente no se reemplazaba y dejaba anotaciones CLASS-retention → puente vacío).
- **D8 en el dispositivo.** El propio R8 del IDE minificaba `com.android.tools.r8.**` y D8 (elegir
  Dexer = D8 + Java 1.8) reventaba por las búsquedas por reflexión. `app/proguard-rules.pro` conserva
  ahora **todo** `com.android.tools.r8.**` (nombres + miembros). D8 ya no cae a Dx ni peta.
- **Versión del proyecto (versionCode/versionName).** Se **relee del metadata** en cada build y
  `stripManifestVersionAttributes()` elimina `android:versionCode/Name` del manifiesto (aapt2 ignora
  `--version-code/--version-name` si el manifiesto ya los trae). La versión sube con Material3 ON y OFF.
- **Tema Material3 (barra superior).** `yq#getXMLStyle` pasa los colores del proyecto al tema
  (`colorPrimary`, `colorSurface*`, `colorOnSurface*`, overlays AppBar/Popup) para que el Toolbar deje
  de salir lila. **Nunca** se emite `colorSurfaceTint`: `material-1.13.0` no lo declara y aapt2 aborta
  el enlace de recursos con `style attribute 'attr/colorSurfaceTint' not found`.
- **Toggle Toolbar con AndroidManifest personalizado.** `yq#applyGeneratedActivityThemes()` re-aplica
  el tema por-Activity del generador sobre un manifiesto hecho a mano (Toolbar OFF → `@style/NoActionBar`),
  sin tocar nunca un tema del usuario.
- **Previsualización del editor sincronizada.** `DesignActivity` + `ViewEditorFragment`: al cambiar
  Toolbar/opciones de pantalla y volver, la preview se reconstruye desde el bean almacenado
  (`jC.b(sc_id)`), no desde una referencia cacheada.
- **Botón de copiar** en el registro de compilación (`res/layout/compile_log.xml` +
  `CompileLogActivity#copyCompileLogToClipboard`).

### NO cerrado / límites honestos

- **`dexdump` del `JavascriptInterface` en un APK compilado en el emulador.** No se pudo inyectar una
  clase de prueba con `@JavascriptInterface`: el IDE regenera/limpia `app/src/main/java` y `gen/` en cada
  build y no se pudo añadir un WebView por UI (los datos de `view` están cifrados). La **cadena causal sí
  está probada en el host** con el **mismo R8 8.11.18** y las **mismas reglas** (R8 sin `-keepattributes`
  = anotación perdida; con `-keepattributes` = `VISIBILITY_RUNTIME`). Para cerrarlo en dispositivo:
  compilar un proyecto con WebView (puente TTS ON) y comprobar
  `dexdump -a classes.dex | grep -A1 JavascriptInterface` → `VISIBILITY_RUNTIME`.
- **El "pegado" genérico de versión/barra** no se reprodujo con el código previo en los proyectos
  601/602; el fallo real corregido es el **estado Material3 cacheado en `yq`** más la **regeneración
  forzada** del manifest/styles (`invalidateGeneratedManifestAndValues()`).
- **`colorSurfaceTint`:** no volver a emitirlo nunca (ver arriba).

---

## v1.0.41 — Visor de código (2026-10-06)

versionCode 47 · versionName `v1.0.41`.

- **Guardar en el Visor de código.** Con el modo edición activo, guardar `AndroidManifest.xml` ya no da
  *"This source cannot be saved from Code Viewer."*: se escribe en
  `.AndroidSCode/data/<scId>/files/AndroidManifest.xml`, ese fichero pasa a ser el manifiesto del
  proyecto (el generador lo prefiere sobre el generado al vuelo) y los avisos del visor quedan
  localizados en inglés, español y portugués. Web y READMEs actualizados.

## v1.0.40 — TTS en el WebView y ajustes de WebView (2026-10-06)

versionCode 46 · versionName `v1.0.40`.

- Puente `AndroidBridge` (`@JavascriptInterface` con `speak`/`stop`) + shim de
  `window.speechSynthesis` para que las apps compiladas con WebView hablen; ajustes de WebView por
  proyecto; copiar diseño y lógica entre pantallas; chat de IA localizado (EN/ES/PT); botón Compilar
  en el inicio; modo claro arreglado.

## v1.0.39 — Tema con la paleta de la web (2026-10-05)

versionCode 45 · versionName `v1.0.39`.

- Tema visual con la paleta exacta de la web (#3DDC84 / #00BFA5 sobre azul marino) en claro y oscuro,
  sin morados heredados; toolchain de Flutter 3.13.5; botón Ejecutar en verde.

<!-- Releases anteriores: ver https://github.com/aurenox-global/Android-SCode/releases -->
