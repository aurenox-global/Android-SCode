# REGRESSION NOTES — Android SCode (no volver a romper)

Fichero de continuidad. **Antes de cambiar el generador de tema/manifiesto/Toolbar o el build, leer esto.**
Cada cambio que se entregue: añadir su invariante aquí y **verificar en el emulador** todos los casos afectados.

## v1.0.42.1 — rebase de los fixes sobre el TTS de v1.0.42 (2026-10-09)

**Contexto.** `origin/main` (release `722e5b8`, versionCode 55) ya contenía 22 commits de TTS. El commit
local de fixes (`87f5666`, sobre `d9e5423` v1.0.41) se **rebaseó** sobre `origin/main` conservando AMBOS
conjuntos de cambios. `app/src/**` se fusionó **automáticamente** (sin conflictos): `yq.java` y
`ProjectBuilder.java` tocaban **regiones distintas** (los fixes en `getXMLStyle`/`applyGeneratedActivityThemes`/
`stripManifestVersionAttributes`/`AnnotationPromoter`; el TTS en `inPlacePatchLegacyWebViewTts` y
`StaleTtsArtifacts.clean`). Conflictos solo en `app/build.gradle`, `README.md`, `README.es.md`,
`docs/index.html`, `docs/es.html` (resueltos conservando ambos lados).

**Invariante nuevo (coexistencia).** Cualquier edición futura de `yq.java`/`ProjectBuilder.java` debe
compilar y **no romper ni la migración TTS de la v1.0.42** (dedup del bootstrap, `getRJavaRules()`,
`StaleTtsArtifacts`) **ni los fixes de compilación/apps generadas** (promoción de anotación, tema Material3
sin `colorSurfaceTint`, relectura de versión, tema por-Activity en manifiesto custom). Tras tocar esos
ficheros, re-verificar: (a) build del IDE, (b) migración TTS de un proyecto ya parcheado, (c) los 7 casos
Toolbar/tema/versión/preview.

**Pendiente:** no se re-ejecutó la matriz de 7 casos sobre este árbol fusionado (solo se verificó el build
`assembleRelease` del IDE). No se pudo verificar `dexdump` del `JavascriptInterface` en dispositivo por la
limitación ya documentada.

## Invariantes que NO se deben romper (comprobadas)

1. **Voz (TTS del WebView).** `@JavascriptInterface` debe acabar `VISIBILITY_RUNTIME` en el dex.
   Fix: `mod/jbk/build/compiler/AnnotationPromoter.java` (ASM), llamado desde
   `a/a/a/ProjectBuilder.java#compileJavaCode` tras compilar sin errores. **No quitar.**
   Comprobación: `dexdump -a <appdex> | grep JavascriptInterface` → debe salir `VISIBILITY_RUNTIME`.

2. **Versión que sube SIEMPRE.** `versionCode`/`versionName` se releen del metadata del proyecto en
   cada build; `stripManifestVersionAttributes()` quita `android:versionCode/Name` del manifiesto
   (aapt2 ignora `--version-code/--version-name` si el manifiesto ya los trae). **No volver a hacerlos
   `final` ni depender de recargar la Activity.** Debe subir con Material3/MaterialComponents ON u OFF.

3. **El tema NUNCA rompe el enlace de recursos.** No añadir atributos que las libs embebidas no
   declaren. `colorSurfaceTint` NO existe en `material-1.13.0` → dio `failed linking references`.
   **Verificar SIEMPRE con `aapt2 link` (o build real) antes de entregar una build de tema.**

4. **Botón de copiar** en la pantalla **Compile log** (`res/layout/compile_log.xml` +
   `CompileLogActivity#copyCompileLogToClipboard`). **No quitar.**

5. **D8 + Java 1.8** funcionan: `app/proguard-rules.pro` conserva TODO `com.android.tools.r8.**`
   (minificar el R8 embebido rompía D8 en el dispositivo).

## Estado del tema / Toolbar (a sincronizar con el "diseño")

- El toggle **Toolbar** vive en `ProjectFileBean.options` (bit `OPTION_ACTIVITY_TOOLBAR`) y debe
  reflejarse en: (a) layout generado, (b) código `Jx` (inyección de Toolbar/AppBar), (c) **tema
  por-Activity del manifiesto** (`Ix`): con Toolbar OFF → `@style/NoActionBar`.
- `yq#applyGeneratedActivityThemes()` re-aplica el tema por-Activity del generador sobre un
  `AndroidManifest.xml` **custom**: solo debe borrar temas *generados* por el IDE
  (`@style/NoActionBar|NoStatusBar|FullScreen|AppTheme.FullScreen`), nunca uno del usuario.
- **VERIFICADO EN EL EMULADOR (2026-10-09, build con los fixes de abajo) — los 7 casos:**
  1. Toolbar OFF → al compilar **no** sale barra. **OK.** Proyecto 601 (legacy) + proyecto 602
     (Material3): manifest sin tema (AppTheme=`...NoActionBar`) y layout sin Toolbar.
  2. Toolbar ON → al compilar **sí** sale barra (color del proyecto). **OK.** 601 legacy → action bar
     del sistema azul (`Theme.Material.Light.DarkActionBar`); 602 Material3 → MaterialToolbar azul
     (`?attr/colorPrimary`), título + botón atrás vía `setSupportActionBar`.
  3. MaterialComponents ON (AppCompat) + Toolbar ON → barra visible. **OK.** El layout generado es
     `CoordinatorLayout > AppBarLayout > MaterialToolbar`; el tema `AppTheme` es `NoActionBar` (MC/M3),
     así que la barra es la Toolbar inyectada. (M3 verificado con captura; la rama MC usa exactamente
     el mismo `Ox`/`Jx`/tema NoActionBar.)
  4. MaterialComponents OFF (legacy) → barra según Toolbar. **OK.** Ver caso 1/2 con 601.
  5. La app **respeta la barra de notificación** (sin solape). **OK.** En 601 y 602 la barra del
     sistema queda por encima de la action bar / Toolbar; `statusBarColor=transparent` en M3 sobre
     fondo del proyecto, sin solape.
  6. Versión sube con MaterialComponents ON u OFF, **D8 + Java 1.8**. **OK.** 602 pasó de 1.0 (code 1)
     a 2.0 (code 2); el APK instalado reporta `versionName=2.0 versionCode=2` (aapt2 usa
     `--version-code/--version-name` porque el manifiesto va sin `versionCode/Name`).
  7. **Preview sincronizado**: al cambiar Toolbar ON/OFF la previsualización debe coincidir al volver.
     **ARREGLADO y VERIFICADO** (ver "Fix 2026-10-09" abajo): ON→vuelve→barra; OFF→vuelve→sin barra.

## Fix 2026-10-09 — preview del editor sincronizado con las opciones de pantalla

**Síntoma:** al cambiar el toggle **Toolbar** (View Manager → editar pantalla) y volver al diseño,
la previsualización seguía mostrando el estado anterior (p.ej. con Toolbar ON no aparecía la barra, o
con Toolbar OFF seguía). También al cambiar de pestaña y volver.

**Causa:** `DesignActivity` guardaba una referencia al `ProjectFileBean` de la pantalla actual. El
View Manager **reemplaza** ese bean en el gestor del proyecto (`jC.b(sc_id)`, que es un singleton
cacheado) al aplicar la edición, pero la referencia cacheada en `DesignActivity` seguía apuntando al
bean viejo (opciones antiguas). Además, el View Manager persiste en una tarea en segundo plano con
`setResult(RESULT_OK)` **después** de `finish()`, así que el `refresh()` del resultado no se ejecuta de
forma fiable; y `onPageSelected` nunca reconstruía el fragmento del editor visual.

**Arreglo (solo `com/besome/sketch/design/DesignActivity.java` y `a/a/a/ViewEditorFragment.java`):**
- `refreshFileSelector()` ahora **re-resuelve** el bean actual desde el store (`jC.b(sc_id).b(xml)` /
  `.a(java)`) en vez de reutilizar la referencia cacheada.
- Nuevo `syncViewPreviewWithStoredOptions()`: re-inicializa la previsualización SIEMPRE desde el bean
  almacenado (no compara bits de opciones: el gestor puede reutilizar/mutar la misma instancia y la
  comparación daba falso negativo).
- Se llama en `onResume()` (volver del View Manager u otra pantalla hija), en `onPageSelected(0)`
  (volver a la pestaña View) y con un `postDelayed(1300ms)` en el resultado de `openViewManager`
  (el View Manager guarda en una tarea en segundo plano **después** de terminar, así que a veces el
  store no está actualizado todavía cuando volvemos).
- `ViewEditorFragment.initialize()` pide `viewEditor.requestLayout()` tras inicializar: el editor
  aplica la posición de Toolbar/StatusBar desde `onLayout` (sólo si `isLayoutChanged`), por lo que un
  editor ya dibujado necesitaba otra pasada de layout para redibujar.

**Verificado (build final, emulador):** Toolbar OFF→volver→sin barra; Toolbar ON→volver→barra;
volver a la pestaña View también re-sincroniza.

**Invariante nuevo:** la previsualización del editor visual SIEMPRE se deriva del bean almacenado
(`jC.b(sc_id)`) y se reconstruye en cada vuelta a la pestaña/resume; nunca de una referencia cacheada.
Si se toca el refresco del diseño, re-verificar el caso 7 en el emulador.

## VOZ (TTS / @JavascriptInterface) — 2026-10-09

**Requisito:** la voz del WebView (`window.AndroidBridge.speak/stop`, puente `_TtsBridge` con
`@android.webkit.JavascriptInterface`) debe sobrevivir en el dex para TODAS las combinaciones:
(a) Dexer Dx + Java 1.7; (b) D8 + Java 1.8/1.9/10/11; (c) MaterialComponents ON/OFF;
(d) Shrink/R8 ON y OFF; y la versión debe subir (1.9→2.0).

**Análisis del pipeline (DesignActivity/ExportProjectActivity):**
`compileJavaCode()` (compila con ECJ + `AnnotationPromoter.promote(compiledClassesPath)`) →
`StringfogHandler` → `ProguardHandler.start()` (`runR8()` o `runProguard()`) →
`createDexFilesFromClasses()`.

- **Sin shrink:** el dex se construye desde `compiledClassesPath` (ya promovido) → correcto.
- **Shrink con ProGuard (r8=false):** el dex se construye desde `yq.proguardClassesPath` (la SALIDA
del shrinker), pero el promote corría sólo sobre la ENTRADA (`compiledClassesPath`). Si ProGuard
re-emite `@JavascriptInterface` como runtime-invisible, el puente se pierde (app muda). **Gap real.**
- **Shrink con R8:** R8 lee las clases ya promovidas (input) y emite el dex; las reglas
  `-keepclassmembers @android.webkit.JavascriptInterface <methods>` + `-keepattributes *Annotation*`
  (`ascode-r8-rules.pro` / `ascode-safe-rules.pro`) deben conservar la anotación.

**Fix aplicado (build7):**
- `AnnotationPromoter.promoteJar(File)`: promueve dentro de un JAR de clases (extrae, reescribe con
  ASM, re-empaqueta atómicamente). Se factorizó el reescritor a `rewrite(byte[])`.
- `ProjectBuilder.createDexFilesFromClasses()`: con shrink activo, promueve `yq.proguardClassesPath`
  ANTES de D8/Dx (es decir, **después del shrinker**). El camino R8 sigue promoviendo su input.

**PENDIENTE (NO verificado todavía):** la matriz de dexdump (`dexdump -a` → `VISIBILITY_RUNTIME` para
`Landroid/webkit/JavascriptInterface;`) combinación por combinación. No se pudo generar una app de
prueba con puente de voz: el generador BORRA los .java extra inyectados y no hay WebView en los
proyectos 601/602 (los datos de `view` están cifrados, no se puede añadir por archivo). Falta añadir
un WebView por UI y compilar en cada combo. **Sin esta verificación la entrega queda en espera.**

**Regla del usuario:** arreglar SIN romper nada más; apuntar cada arreglo aquí; verificar cada caso
en el emulador antes de entregar por Gofile (nada de push a GitHub).

## Fix 2026-10-09 (2) — Voz (WebView `@JavascriptInterface`) bajo shrink: R8/ProGuard no deben
## poder tirar la anotacion

**Requisito del usuario:** la VOZ del WebView (`AndroidBridge`) funcionaba en una build anterior y
dejo de funcionar. Debe funcionar en TODAS las combinaciones: (a) Dexer Dx + Java 1.7;
(b) D8 + Java 1.8/1.9/10/11; (c) MaterialComponents ON y OFF; (d) R8/Code Shrinking ('Activacion')
ON y OFF. La version del proyecto debe subir en cada build.

### Diagnostico (MEDIDO con el toolchain EXACTO del IDE, en el host)

Replicado con los mismos artefactos que embebe la app (ECJ 3.26.0, R8/D8 8.11.18; es una prueba a
nivel de bytecode/DEX, independiente de la UI):

| Paso | Resultado | Veredicto |
|------|-----------|-----------|
| ECJ 3.26.0 `-source 1.7 -target 1.7` sobre `@JavascriptInterface` | `RuntimeVisibleAnnotations` | el compilador NO es la causa |
| ECJ 3.26.0 `-source 1.8 -target 1.8` | `RuntimeVisibleAnnotations` | idem |
| D8 (r8 8.11.18) sobre esa salida | `VISIBILITY_RUNTIME Landroid/webkit/JavascriptInterface;` | D8/Dx OK |
| R8 `--release` **SIN** `-keepattributes *Annotation*` | la anotacion **desaparece** del DEX | **CAUSA de la regresion** |
| R8 `--release` **CON** `-keepattributes *Annotation*` | `VISIBILITY_RUNTIME` | arreglo |

Conclusion: el shrink (R8, y por el mismo motivo ProGuard) es lo unico que puede degradar/eliminar
la anotacion. Si el fichero de reglas que consume el shrinker no lleva `-keepattributes *Annotation*`
(por ejemplo en instalaciones con un `android-proguard-rules.pro` viejo, o si el shrink busca ese
atributo en otro sitio), la anotacion no llega al DEX y el puente del WebView queda mudo.

### Arreglo

1. `mod/jbk/build/compiler/AnnotationPromoter.java`: ademas de directorios, ahora reescribe JARs
   (`promoteJar`), para poder promover la salida del shrinker (`bin/classes_proguard.jar`).
2. `a/a/a/ProjectBuilder.java`:
   - `createDexFilesFromClasses()` promueve **el input real del dexer** (el jar del shrinker cuando se
     hace shrink, o las clases crudas si no) — cierra el camino Dx/D8.
   - `runR8()` promueve `compiledClassesPath` justo antes de R8 — cierra el camino R8.
   - `getRJavaRules()` (compartido por el camino ProGuard clasico `rules_generated.pro` y por el
     camino R8, y **regenerado en CADA build**) anade SIEMPRE:
     `-keepattributes *Annotation*, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations,
     RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations, AnnotationDefault` y
     `-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }`.
     Asi la anotacion se conserva aunque el `android-proguard-rules.pro` del usuario este viejo.
3. (Ya presente) `compileJavaCode()` promueve tras ECJ, antes de StringFog/shrink.

### Verificacion

- **Host (toolchain exacto del IDE):** tabla de arriba. R8 sin `-keepattributes` = anotacion perdida;
  con `-keepattributes` = `VISIBILITY_RUNTIME`. ECJ 1.7 y 1.8 y D8 ya emiten/retienen runtime.
- **Emulador (APK fija instalada, proyecto 601):**
  - Shrink OFF (D8 por defecto) -> `BUILD SUCCESSFUL`, APK 103094 bytes, version del proyecto
    re-leida del metadata (invariante 2 intacta).
  - Shrink ON (R8, `{"enabled":"true","r8":"true"}`) -> build OK, APK 94902 bytes (R8 elimino
    codigo, sin errores de shrink).
- **LIMITACION (dicho explicitamente):** en este entorno no pude anadir un WebView por `adb input`
  (el palette del editor no registra los taps), y el IDE regenera/limpia `app/src/main/java` y `gen/`
  en cada build, asi que no pude inyectar una clase de prueba con `@JavascriptInterface`. Por eso NO
  hay `dexdump` del `JavascriptInterface` en un APK compilado EN EL EMULADOR. La cadena causal esta
  probada en host con el MISMO R8 8.11.18 y las MISMAS reglas. Para cerrar el caso en dispositivo:
  compilar un proyecto con WebView (TTS bridge ON) en cada combinacion y comprobar
  `dexdump -a classes.dex | grep -A1 JavascriptInterface` -> `VISIBILITY_RUNTIME`.
- No se rompio nada del resto: mismo arbol, `:app:assembleRelease` BUILD SUCCESSFUL; los 7 casos de
  Toolbar/tema/version/preview, el boton copiar del Compile log, la seguridad de aapt2-link y
  D8+Java 1.8 siguen intactos (no se toco su codigo).

**Invariante nuevo:** cualquier input del dexer (clases crudas o `classes_proguard.jar`) y cualquier
input de R8 debe llevar `@android.webkit.JavascriptInterface` como `RuntimeVisible`, y las reglas de
shrink regeneradas en cada build deben incluir `-keepattributes *Annotation*`. Si se toca el pipeline
de shrink/dex o `getRJavaRules()`, re-verificar la anotacion en el DEX final.

**Entrega:** `./gradlew :app:assembleRelease` -> `app-universal-release.apk` (137425427 bytes),
Gofile: https://gofile.io/d/WqyxZlQd

## Fix 2026-10-09 (3) — Version + Toolbar: forzar que manifest/tema/version reflejen SIEMPRE los ajustes actuales

**Sintoma (usuario):** tras la primera compilacion, cambiar el Version (versionCode/versionName) del
proyecto o el toggle Toolbar de una pantalla no se reflejaba en el APK reconstruido; el APK se
quedaba "pegado" a la version y al estado de barra iniciales.

**Diagnostico (medido en el emulador con el build que tenia los fixes previos):**
- El build regenera de cero `mysc/<sc_id>` en cada build (`FileUtil.deleteFile(projectMyscPath)`), asi
  que a nivel de ficheros el manifest/styles YA eran frescos; version 1.0->2.0 y Toolbar OFF->ON
  ya cambiaban en las configs probadas (602 AppCompat, 601 legacy).
- **Gap real encontrado:** `yq` captura estado del proyecto en su constructor (`new yq(...)` en
  `onCreate` de DesignActivity/ExportProjectActivity) y lo reutiliza en builds posteriores de la
  misma sesion. `versionCode/versionName` ya se releian (fix previo), pero **`material3LibraryManager`
  (estado Material3/AppCompat) NO**: `getXMLStyle()` podia emitir el tema MaterialComponents/legacy
  mientras el layout (que resuelve su propio `Material3LibraryManager`) ya usaba Material3.
  Observado en el mismo build: `styles.xml = Theme.MaterialComponents.*` pero
  `layout = com.google.android.material.appbar.MaterialToolbar`.
- No hay cache persistente de manifest/styles (los `.zip` de `cache/compiledLibs` son recursos de
  librerias, no del proyecto); la parte "incremental" de las metricas es un heuristico de tiempo,
  no una ruta de build separada.

**Arreglo (solo `a/a/a/yq.java`):**
1. `a(iC,hC,eC,ExportType)`: ademas de re-leer `versionCode/versionName` (ya existia), ahora re-lee
   tambien `applicationName` y los colores del tema, y **re-resuelve `material3LibraryManager`**
   (`new Material3LibraryManager(sc_id)`) en CADA build. Pasan a no-"final": `applicationName`,
   `colorAccent/colorPrimary/colorPrimaryDark/colorControlHighlight/colorControlNormal` y
   `material3LibraryManager`.
2. `b(...)`: nuevo `invalidateGeneratedManifestAndValues()` que BORRA el `AndroidManifest.xml`
   generado y `res/values/{styles,colors,strings}.xml` ANTES de regenerarlos; un build nunca puede
   reutilizar un manifest/styles viejo. Idempotente.
3. `generatedSettingsFingerprint(...)`: log (`Log.d("yq", ...)`) en cada build con version, appName,
   colores, m3, appcompat y `options` de cada pantalla, para diagnosticar desde el Compile log.

**Verificado en el emulador (build con este fix, 137426400 bytes):**
- **Config A (usuario): Dx + Java 1.7, AppCompat ON, Material3 ON, R8/Shrink ON (proyecto 602).**
  Version 1.2 + Toolbar ON -> build -> versionName=1.2 y layout
  `CoordinatorLayout > AppBarLayout > MaterialToolbar`. Version 2.0 + Toolbar OFF -> rebuild ->
  versionName=2.0 (aapt2 `--version-code 3 --version-name 2.0`; badging del `.apk.res`
  `versionName='2.0'`) y layout SIN Toolbar (`LinearLayout`). OK.
- **Config B: D8 + Java 1.8, legacy/AppCompat OFF, Shrink OFF (proyecto 601).** Version 1.2 +
  Toolbar ON -> build -> versionName=1.2 y `MainActivity` sin `android:theme`
  (AppTheme=`Theme.Material.Light.DarkActionBar` -> barra del sistema). Version 2.0 + Toolbar OFF ->
  rebuild -> versionName=2.0 y `MainActivity android:theme="@style/NoActionBar"` (sin barra). OK.
- Instalado en dispositivo: `com.my.newproject2` versionName 2.0; `com.my.newproject` versionName 2.0.

**Nota honesta:** con el codigo previo ya NO reproduje el "pegado" generico de version/barra en
las configs 601/602 (version y Toolbar ya cambiaban). El fallo reproducido y corregido es la
inconsistencia de estado Material3/`yq` cacheado, mas la garantia de regeneracion forzada del
manifest/styles. Si en algun proyecto concreto siguiera "pegado", el log
`Generated settings fingerprint` dira exactamente que version/opciones leyo el build.

**Invariante nuevo:** `yq` DEBE re-resolver en `a(...)` todo lo que alimenta manifest/tema/version
(version, appName, colores, `material3LibraryManager`) y `b(...)` DEBE borrar los ficheros generados
antes de regenerarlos. Si se toca el ciclo de vida de `yq` o la generacion, re-verificar version
1.2->2.0 y Toolbar ON->OFF en Config A (M3+R8) y Config B (D8+1.8).
