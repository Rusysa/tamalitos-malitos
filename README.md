# Tamalitos Malitos — Android

Aplicación nativa en Kotlin para clientes, pedidos, pagos parciales, gastos, catálogo e informes; funciona sin internet con SQLite. Android mínimo 8.1. Ver docs/ALCANCE.md para los requisitos del Word y la aclaración del usuario.

## Estado del proyecto

Versión en desarrollo. La aplicación compila con `assembleDebug`, pero la suite de pruebas tiene errores de compilación pendientes en `BoundaryAllocationTest.kt` (uso de `p.id` cuando `saveProduct` devuelve un `Long`). Los resultados históricos de `docs/VERIFICACION.md` y `docs/verification-evidence/` no certifican las últimas modificaciones. Google Drive aún requiere configurar Google Cloud y realizar pruebas con una cuenta autorizada. No se presenta como una versión lista para producción.

Los APK, el ZIP de entrega, las credenciales y los ajustes locales de Android Studio no se incluyen en Git. Después de clonar, configurar el SDK local y JDK17 en Android Studio.

## Abrir y compilar

Abrir esta carpeta como proyecto en Android Studio. Usar JDK17, SDK Android35 y aceptar las licencias del SDK. Gradle wrapper incluido; no es necesario instalar Gradle global.

En Linux, el instalador reproducible de herramientas propias (sin sudo) es:

    bash tools/bootstrap.sh
    source tools/env.sh
    ./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --max-workers=1

El instalador descarga JDK17, Gradle8.11.1 y las herramientas oficiales del SDK a ~/.local/share/tamalitos-tools. No modifica el JDK del sistema. local.properties y tools/env.sh son específicos de esta máquina y se ignoran en Git.

APK de desarrollo generado por Gradle:

    app/build/outputs/apk/debug/app-debug.apk

Para instalar con depuración USB activada:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

La entrega local contiene entregables/Tamalitos-Malitos-debug.apk, pero esa carpeta no se publica en Git. Para obtener el APK del código actual, ejecutar `assembleDebug` y usar app/build/outputs/apk/debug/app-debug.apk. Es una compilación de desarrollo; para distribución pública hace falta una clave de firma de producción que no se genera ni publica sin tu decisión.

## Uso y respaldo

- docs/MANUAL.md: flujo de clientes, pedidos, abonos y gastos.
- docs/GOOGLE_DRIVE.md: proyecto de Google Cloud, OAuth Android y autorización.
- docs/DATA.md: modelo de datos y reglas de integridad.
- docs/ALCANCE.md: alcance, supuestos y limitaciones.
- docs/VERIFICACION.md: evidencia de compilación/pruebas al entregar.

No se necesita Google para registrar operaciones. Drive requiere configuración y una cuenta autorizada. Los respaldos locales contienen datos personales sin cifrado adicional: guardarlos en una ubicación privada y proteger el teléfono con bloqueo de pantalla. No hay token, contraseña ni secreto de Google en el repositorio.

La copia de Drive es un respaldo/restauración de un solo dispositivo; no es un sistema de sincronización simultánea entre teléfonos. WorkManager respeta las restricciones de batería y conectividad de Android y no garantiza una hora exacta.

## Pruebas

    source tools/env.sh
    ./gradlew testDebugUnitTest --no-daemon --max-workers=1
    ./gradlew connectedDebugAndroidTest --no-daemon --max-workers=1

La segunda orden necesita emulador/dispositivo. Las pruebas instrumentadas crean registros identificados como prueba, de modo que deben ejecutarse solo en dispositivos de pruebas, no con datos del negocio. No prueban Google en vivo sin configuración y cuenta autorizada.

## Estructura

    app/src/main/java/com/tamalitos/malitos/   UI, reglas, SQLite y Drive
    app/src/test/                            pruebas JVM/Robolectric
    app/src/androidTest/                     pruebas en Android real/emulado
    docs/                                   documentación en español
    tools/                                  preparación y verificación
    Tamalitos_Malitos_Proyecto.docx           documento original intacto
