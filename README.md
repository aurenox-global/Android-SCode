<p align="center">
  <img src="assets/Android-SCode.png" width="160" alt="Android SCode">
</p>

# Android SCode

**Android SCode** es un IDE para crear aplicaciones Android directamente desde el móvil, sin ordenador y sin escribir todo el código a mano: interfaz por bloques y por XML, editor de diseño, editor de lógica, recursos (imágenes, sonidos, fuentes), librerías, compilación a APK/AAB y firma, todo dentro del teléfono.

Esta app es un fork independiente y **ya no mantiene ninguna referencia a la marca del proyecto original**.

## Características

- Editor visual de pantallas (arrastrar y soltar) y edición de XML.
- Programación por bloques y por código (Java/Kotlin).
- Gestor de recursos: imágenes, sonidos, fuentes, iconos y colecciones.
- Librerías locales y descarga de dependencias.
- Compilación a APK/AAB firmado, con firma propia.
- Importar/exportar proyectos y copias de seguridad.
- Integración Flutter y compilador Kotlin.

## Descargar

La APK universal (todas las arquitecturas: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) está en la sección de **Releases** de este repositorio.

- Web: https://aurenox-global.github.io/Android-SCode/
- Releases: https://github.com/aurenox-global/Android-SCode/releases

## Compilar desde el código

Requisitos: JDK 17 y el SDK de Android (compileSdk 36).

```bash
./gradlew :app:assembleRelease
```

La salida queda en `app/build/outputs/apk/release/`:

- `app-release-universal.apk` — una sola APK con todas las ABIs.
- `app-release-<abi>.apk` — APKs por arquitectura.

La firma por defecto usa `ascode.keystore` (alias `ascode`). Para firmar con otra clave, define
`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD`
(variables de entorno o `~/.gradle/gradle.properties`).

## Estructura

- `app/` — módulo Android (código fuente, recursos, librerías propias).
- `docs/` — notas de desarrollo y web del proyecto.
- `gradle/libs.versions.toml` — catálogo de versiones y dependencias.

## Licencia

Ver [LICENSE.md](LICENSE.md). Este proyecto deriva de una base de código abierta; se conservan
los avisos de licencia y las atribuciones exigidas por la misma.

## Aviso

Cambiar el nombre del paquete (`com.ascode.android`) o la firma hace que esta app se instale como
una aplicación nueva: no actualiza por encima de instalaciones antiguas de otros paquetes.
