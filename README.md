# Tamalitos Malitos — Android

Aplicación Android en Kotlin con Jetpack Compose y Material Design 3 para clientes, pedidos, pagos parciales, gastos, catálogo e informes; funciona sin internet con SQLite. Android mínimo 8.1. Interfaz adaptable a teléfonos y tabletas horizontales. Ver docs/ALCANCE.md para los requisitos del Word y docs/REDISENO.md para la migración de interfaz.

## Estado del proyecto

Rediseño 1.1.0 para publicación como versión de desarrollo, no como versión certificada para producción. Las pruebas JVM más recientes registran 125 casos en 29 suites, sin fallos, errores ni omitidas. Hay evidencia de seis pruebas instrumentadas API27, respaldo SAF real y guardado mediante toque Android con el teclado abierto. Lint: cero errores y 29 advertencias. Se corrigieron la admisión de escrituras, el orden de lecturas, la selección SAF, la búsqueda, el refresh tras escritura fallida y el cierre de otro editor desde un guardado anterior. Sigue pendiente proteger el callback de error del guardado: puede asignar a un formulario nuevo el error de otro formulario ya cerrado. Las pruebas verdes no equivalen a una aprobación independiente final de todo el rediseño. Google Drive requiere configuración de Google Cloud y pruebas con una cuenta real.

## Interfaz adaptable

- Las siete secciones y sus formularios usan Compose con componentes Material3.
- En teléfonos: Inicio, Clientes, Pedidos y Más para Gastos, Informes, Productos y Respaldo.
- Desde 600dp de ancho disponible: navegación lateral desplazable.
- Desde 840dp: directorio/ficha de cliente y lista/detalle de pedido simultáneos.
- Formularios con ancho limitado, desplazamiento, calendario Material y ajuste a teclado y poca altura.
- Tema claro/oscuro y colores dinámicos en Android12+; se conservan SQLite, centavos MXN y el formato de respaldos.
- La rotación conserva navegación y borradores. Un inicio de proceso nuevo descarta referencias/borradores de una generación anterior por seguridad.

Los APK, el ZIP de entrega, las credenciales y los ajustes locales de Android Studio no se incluyen en Git. Después de clonar, configurar el SDK local y JDK17 en Android Studio.

## Abrir y compilar

Abrir esta carpeta como proyecto en Android Studio. Usar JDK17, SDK Android35 y aceptar las licencias del SDK. Gradle wrapper incluido; no es necesario instalar Gradle global.

En Linux, el instalador reproducible de herramientas propias (sin sudo) es:

    bash tools/bootstrap.sh
    source tools/env.sh
    ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" assembleDebug testDebugUnitTest lintDebug --no-daemon --max-workers=1

El instalador descarga JDK17, Gradle8.11.1 y las herramientas oficiales del SDK a ~/.local/share/tamalitos-tools. No modifica el JDK del sistema. local.properties y tools/env.sh son específicos de esta máquina y se ignoran en Git.

El ZIP de código omite el override local `org.gradle.java.home` de gradle.properties para respetar el JDK seleccionado en la otra computadora. La carpeta de trabajo conserva ese ajuste histórico; la opción `-D` anterior permite verificar explícitamente con JDK17 sin modificarlo.

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
- docs/VERIFICACION-COMPOSE.md: resultados ejecutados para el rediseño 1.1.0.
- docs/VERIFICACION.md: evidencia histórica del APK anterior.
- docs/REDISENO.md: criterios y matriz de pruebas de Compose y ventanas adaptables.

No se necesita Google para registrar operaciones. Drive requiere configuración y una cuenta autorizada. Los respaldos locales contienen datos personales sin cifrado adicional: guardarlos en una ubicación privada y proteger el teléfono con bloqueo de pantalla. No hay token, contraseña ni secreto de Google en el repositorio.

La copia de Drive es un respaldo/restauración de un solo dispositivo; no es un sistema de sincronización simultánea entre teléfonos. WorkManager respeta las restricciones de batería y conectividad de Android y no garantiza una hora exacta.

## Pruebas

    source tools/env.sh
    ./gradlew testDebugUnitTest --no-daemon --max-workers=1
    ./gradlew connectedDebugAndroidTest --no-daemon --max-workers=1

La segunda orden necesita emulador/dispositivo. Las pruebas instrumentadas crean registros identificados como prueba, de modo que deben ejecutarse solo en dispositivos de pruebas, no con datos del negocio. No prueban Google en vivo sin configuración y cuenta autorizada.

QA de archivos y ventanas, solo contra un emulador desechable encendido con el APK instalado:

    python3 tools/native_saf_roundtrip.py
    python3 tools/compose_window_qa.py

La segunda prueba cambia temporalmente tamaño/densidad, abre el teclado y guarda un cliente sintético mediante `adb input`; restaura la geometría al terminar. No basta una acción semántica de Compose para demostrar que Android no tapa el botón con el teclado.

## Estructura

    app/src/main/java/com/tamalitos/malitos/   UI, reglas, SQLite y Drive
    app/src/test/                            pruebas JVM/Robolectric
    app/src/androidTest/                     pruebas en Android real/emulado
    docs/                                   documentación en español
    tools/                                  preparación y verificación
    Tamalitos_Malitos_Proyecto.docx           documento original intacto
