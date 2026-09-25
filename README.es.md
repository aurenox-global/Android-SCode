<div align="center">

<img src="assets/Android-SCode.png" width="132" alt="Android SCode">

# Android SCode

**Crea apps Android reales directamente desde tu móvil.**

Diseña, programa, compila y firma aplicaciones Android sin necesidad de un ordenador.

[![Última versión](https://img.shields.io/github/v/release/aurenox-global/Android-SCode?label=versión&color=3DDC84)](https://github.com/aurenox-global/Android-SCode/releases/latest)
[![Descargas](https://img.shields.io/github/downloads/aurenox-global/Android-SCode/total?color=3DDC84)](https://github.com/aurenox-global/Android-SCode/releases)
[![Plataforma](https://img.shields.io/badge/plataforma-Android%208.0%2B-3DDC84)](#requisitos)
[![Telegram](https://img.shields.io/badge/telegram-unirse%20al%20grupo-2CA5E0?logo=telegram&logoColor=white)](https://t.me/AndroidSCode)

[Web](https://aurenox-global.github.io/Android-SCode/) · [Releases](https://github.com/aurenox-global/Android-SCode/releases) · [English](README.md)

</div>

---

## Descripción

**Android SCode** es un IDE de Android que funciona en el propio dispositivo: editor visual de
pantallas, editor de lógica por bloques, edición de código Java/Kotlin, gestión de recursos,
resolución de dependencias y un pipeline de compilación completo que genera un **APK o AAB
firmado**, todo sin salir del teléfono.

| | |
|---|---|
| **Paquete** | `com.ascode.android` |
| **Android mínimo** | 8.0 (API 26) |
| **Target / Compile SDK** | 35 / 36 |
| **Distribución** | Una **APK universal** (todas las arquitecturas) |

## Características

- **Editor de diseño visual** — arrastra widgets, edita el XML y previsualiza las pantallas.
- **Editor de lógica** — bloques para prototipar rápido y Java/Kotlin cuando necesitas control
  total.
- **Gestor de recursos** — imágenes, sonidos, fuentes, iconos, colecciones y bloques propios por
  proyecto.
- **Librerías y dependencias** — añade tus `.jar`/`.aar` o resuelve artefactos de Maven.
- **Compilar y firmar** — genera un APK o AAB firmado, gestiona tus keystores e instala el
  resultado desde la propia app.
- **Copias de seguridad** — exporta y restaura proyectos en ficheros portables `.swb`.
- **Extras** — compilador de Kotlin, soporte de Flutter y gestor de IA local.

## Descargar

Descarga la última **APK universal** (compatible con `arm64-v8a`, `armeabi-v7a`, `x86_64` y `x86`):

**[→ Última versión](https://github.com/aurenox-global/Android-SCode/releases/latest)**

> **Nota:** la app usa su propio nombre de paquete y su propia firma, así que se instala como una
> **aplicación nueva**: no actualiza por encima de instalaciones de otros paquetes. La primera vez
> que la abres importa automáticamente los proyectos que tuvieras en la carpeta antigua
> `.sketchware` a `.AndroidSCode`.

## Requisitos

**Para instalarla:** Android 8.0 o superior y permiso para instalar apps de origen desconocido.

**Para compilar desde el código:**

- JDK **17**
- SDK de Android con **compileSdk 36** y las build-tools correspondientes
- (El wrapper de Gradle viene incluido: no hace falta instalar Gradle aparte.)

## Compilar desde el código

```bash
git clone https://github.com/aurenox-global/Android-SCode.git
cd Android-SCode

# Indica tu SDK (o define ANDROID_HOME)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# Una sola APK universal
./gradlew :app:assembleRelease
```

Salida:

```
app/build/outputs/apk/release/
├── app-universal-release.apk   # una APK con todas las arquitecturas
├── app-arm64-v8a-release.apk
├── app-armeabi-v7a-release.apk
├── app-x86-release.apk
└── app-x86_64-release.apk
```

### Firma

Las builds de release se firman con el keystore incluido en este repositorio (`ascode.keystore`,
alias `ascode`), de modo que las versiones se instalan una encima de otra. Para firmar con tu
propia clave, define estas variables de entorno antes de compilar:

```bash
export RELEASE_STORE_FILE=/ruta/a/tu.keystore
export RELEASE_STORE_PASSWORD=****
export RELEASE_KEY_ALIAS=tu-alias
export RELEASE_KEY_PASSWORD=****
```

> Las variables de entorno son el único modo de sobrescribirla: `gradle.properties` se ignora a
> propósito para que una configuración global no cambie la firma de release por accidente.

## Estructura del proyecto

| Ruta | Descripción |
|---|---|
| `app/` | Módulo Android (código, recursos, librerías incluidas) |
| `app/src/main/java/com/ascode/android/` | Código de la aplicación |
| `app/src/main/java/com/besome/sketch/` | Interfaz del editor (actividades, adaptadores, vistas) |
| `app/src/main/java/mod/`, `a/a/a/` | Compilador, modelo de proyecto y código de soporte |
| `docs/` | Web del proyecto (GitHub Pages) y notas de desarrollo |
| `gradle/libs.versions.toml` | Catálogo de versiones: SDK, plugins y dependencias |
| `scripts/` | Scripts auxiliares de mantenimiento y compilación |
| `ascode.keystore` | Clave de firma por defecto de las releases |

## Contribuir

Este repositorio tiene **un único mantenedor** y **toda contribución externa necesita su
aprobación antes de fusionarse**.

- Los cambios entran por **pull request**; `main` está protegida y el PR necesita la revisión del
  mantenedor. Abrir un PR no garantiza que se fusione.
- Envía PR **pequeños y concretos** e indica siempre **cómo lo has probado**.
- Lee **[CONTRIBUTING.md](CONTRIBUTING.md)** antes de abrir un PR.

## Comunidad

Dudas, ideas y avisos de errores:

- **Grupo de Telegram:** https://t.me/AndroidSCode
- **Issues:** https://github.com/aurenox-global/Android-SCode/issues

## Reconocimiento

Android SCode es un **trabajo derivado de Sketchware** (y de sus forks de la comunidad),
mantenido de forma independiente. **No está afiliado, patrocinado ni respaldado** por el proyecto
original. Todas las marcas pertenecen a sus respectivos titulares y se conservan los avisos y
atribuciones que exige la licencia original — ver [LICENSE.md](LICENSE.md).

## Créditos

Creado y mantenido por **Andrés Mag**.

## Licencia

Ver **[LICENSE.md](LICENSE.md)**. Este proyecto deriva de una base de código abierto
(*source-available*); se conservan todos los avisos y atribuciones exigidos.

<div align="center"><sub>Android SCode · crea apps Android desde tu móvil</sub></div>
