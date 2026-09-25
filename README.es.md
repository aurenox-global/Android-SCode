# Android SCode

**Android SCode** es un IDE para crear aplicaciones Android directamente desde el móvil: editor
visual, bloques, recursos, librerías y compilación a APK/AAB firmado, todo dentro del teléfono.

Este repositorio es un fork independiente, ya rebrandeado por completo (nombre, paquete, icono,
firma y menús).

## Descargar

- APK universal (todas las arquitecturas): [Releases](https://github.com/aurenox-global/Android-SCode/releases)
- Web del proyecto: https://aurenox-global.github.io/Android-SCode/

## Qué incluye esta versión (v1.0.0)

- Nombre de app: **Android SCode** · paquete `com.ascode.android`.
- Icono nuevo estilo Android Studio (robot Android verde).
- Firma propia (`ascode.keystore`, alias `ascode`).
- Menú sin Discord, sin Telegram, sin SwAssist, sin "About the team" ni "Changelog".
- **App information**: solo **Docs** (apunta a la web del proyecto) y **System Information**.
- Almacenamiento interno renombrado de `.sketchware` a `.ascode`.
- Una sola **APK universal** (arm64-v8a, armeabi-v7a, x86_64, x86).

## Compilar

```bash
./gradlew :app:assembleRelease
```

Salida en `app/build/outputs/apk/release/` (`app-universal-release.apk` = APK universal).

La firma de release es la del proyecto (`ascode.keystore`). Solo se sobrescribe con variables de
entorno `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` y
`RELEASE_KEY_PASSWORD`.

## Créditos

Creado por **Andrés Mag**.

## Contribuir

Este repositorio tiene **un único mantenedor** y **toda contribución externa necesita su
aprobación (Andrés Mag) antes de fusionarse**. `main` está protegida: los cambios entran por
*pull request* y el PR necesita la revisión del mantenedor. Detalles en
[CONTRIBUTING.md](CONTRIBUTING.md).

## Licencia

Ver [LICENSE.md](LICENSE.md). Se conservan los avisos y atribuciones exigidos por la licencia de la
base de código original.

## Aviso

Al cambiar el nombre de paquete y la firma, esta app se instala como una **aplicación nueva**: no
actualiza por encima de instalaciones antiguas de otros paquetes. Los proyectos de la carpeta
antigua `.sketchware` se importan automáticamente la primera vez que abres la app.
