# Verificación del rediseño 1.1.0 — Compose / Material 3

## Estado

Snapshot de desarrollo del rediseño, no una certificación de producción. Las revisiones independientes detectaron tres bloqueos iniciales y dos posteriores en `deleg_39de9e89`. El segundo ciclo añadió regresiones para el refresh tras escritura fallida y para el cierre de otro editor desde una finalización antigua. La rama de éxito del guardado comprueba la identidad del editor, pero su callback de error todavía asigna `draftError` sin esa comprobación; puede mostrar el error de un formulario anterior en un borrador nuevo. Este caso queda pendiente y no se afirma que todos los bloqueos estén resueltos. La revisión suplementaria `deleg_392f2ca2` aprobó únicamente el fondo Material, los selectores de prueba acotados al editor y el empaquetado portable. No hay un dictamen independiente final aprobando todo el código posterior al segundo ciclo.

## Compilación y pruebas

- APK `com.tamalitos.malitos`, versión 1.1.0 / versionCode 2; minSdk27, targetSdk34, compileSdk35.
- Recompilación completa, instrumentación compilada, pruebas JVM y lint con Gradle8.11.1 y JDK17: exit0. `build-final.log` y `toolchain.log`.
- Conteo de XML más reciente: 125 pruebas JVM, 29 suites, 0 fallos, 0 errores y 0 omitidas. Incluye las regresiones de concurrencia de lecturas/escrituras, refresh después de escritura rechazada, identidad del editor en la rama de éxito, selección de copia, búsqueda, calendario y confirmación Compose Drive. `review-results.json` conserva el conteo histórico de 123 anterior al segundo ciclo.
- Lint: 0 errores y 29 advertencias pendientes. No se afirma que esté libre de advertencias.
- Instrumentación ejecutada: `OK (6 tests)` en Android8.1/API27, emulador desechable. Las comprobaciones de ventanas amplias usan también ajustes de tamaño/densidad. `android-final.log`.
- El flujo completo ejecuta alta/edición de cliente, pedido HALF, liquidación, entrega y gasto sobre SQLite real. También comprueba deuda después de entregar, persistencia, recreación del borrador, dos paneles y formulario con IME.
- La primera ejecución en tableta expuso un selector de prueba ambiguo (filtro de estado y selector del editor). Se acotó al editor, sin cambiar reglas ni controles de producción. `android-tablet-selector-red.log` conserva el fallo.
- Una inspección visual detectó transparencia negra debajo del rail. Se añadió comprobación del píxel real de Compose, se observó RED y se aplicó el fondo Material a la fila raíz. `tablet-background-red.log`; la suite final comprueba el fondo y pasa.

## Ventanas y apariencia

`windows.json` y `windows.log` registran comprobaciones con Android WindowManager y toque real por `adb input`, no solo acciones semánticas:

- Siete destinos accesibles con navegación lateral.
- Directorio/ficha de cliente y lista/detalle de pedido visibles simultáneamente a 1600×1000 @160.
- Viewport compacto 720×1280 @320.
- Viewport horizontal corto 1280×720 @320: borrador retenido y Guardar alcanzable con el teclado abierto; se exige la ficha del registro efectivamente guardado.
- Tamaño y densidad originales restaurados al terminar.

`appearance.json` y `appearance.log`: modo oscuro y fuente1.3, siete destinos y dos paneles visibles, formulario compacto con acciones visibles y teclado. Capturas inspeccionadas: contraste y etiquetas legibles, sin solapamiento observado de Guardar/Volver. El formulario se cancela y no añade un registro. No se declara cumplimiento completo de accesibilidad ni pruebas con todas las escalas de fuente.

Las capturas proceden de emuladores desechables y ajustes de viewport; la revisión de apariencia usó también un AVD con pantalla física 1600×1000. Pueden incluir márgenes negros fuera del viewport y no son fotografías de dispositivos físicos. Los nombres y datos mostrados son fixtures sintéticos, solo en el emulador, no precargados en la aplicación.

Capturas en `docs/capturas/compose/`:

- `tableta-clientes.png`, `tableta-pedidos.png`.
- `telefono-formulario.png`, `telefono-horizontal-teclado.png`.
- `tableta-oscura-fuente-ampliada.png`, `telefono-oscuro-fuente-ampliada.png`.

## Respaldo real

`local-saf.json` y `local-saf.log`: interfaz Compose + DocumentsUI del sistema → exportar → confirmar importación → exportar de nuevo. Se descargan los archivos realmente seleccionados y se compara todo el negocio salvo el timestamp `exportedAt`; igualdad exacta y confirmación de copia previa. No se confunde este flujo local con una transferencia real a Google.

Se conservan SQLite, centavos, transacciones, stock y las protecciones por generación. Las pruebas incluyen rechazo de formularios y callbacks anteriores a una restauración y de IDs reutilizados. Un proceso nuevo descarta borradores anteriores por seguridad; la rotación sí los conserva.

## Firma, configuración y archivo de código

- Firma debug verificada por `apksigner`: `signature.log`. Los metadatos reales están en `apk-metadata.log`.
- `test_config_safety.py` verifica que el respaldo/extracción de plataforma esté desactivado.
- ZIP de código generado por `tools/package_delivery.py`, con comprobación de integridad, nombres únicos y relectura de cada entrada. Excluye credenciales, claves, SQLite, cachés, `local.properties` y `tools/env.sh`.
- Única transformación de contenido: omitir `org.gradle.java.home` en el gradle.properties del ZIP para no forzar la ruta absoluta del equipo de desarrollo. No modifica esa preferencia en la carpeta de trabajo. Regresión RED/GREEN: `package-portability-red.log` / `package-portability-green.log`.
- Esta publicación en Git contiene el código, pruebas, documentación y evidencia como un snapshot de desarrollo. Los APK/ZIP, credenciales y ajustes locales permanecen excluidos.

## Límites pendientes

- Callback de error de guardado pendiente de comprobar la identidad del editor; falta una regresión de fallo después de cerrar y abrir otro formulario. No se presenta este snapshot como una versión libre de errores.
- Google Drive necesita proyecto OAuth y cuenta autorizada; se probaron su lógica y confirmaciones, no un respaldo/restauración con una cuenta Google real.
- El APK es debug; una publicación de producción requiere una clave de firma propia y pruebas de aceptación del usuario.
- No se ejecutaron pruebas destructivas ni se instaló esta versión en el teléfono del usuario. Las pruebas Android se limitaron a un emulador desechable.
- Antes de actualizar una instalación con datos reales, exportar un respaldo externo; instalar con el mismo paquete y certificado sin desinstalar ni borrar datos.

## Reproducir

Con herramientas preparadas y desde la raíz:

    source tools/env.sh
    ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --no-daemon --max-workers=1

Instalar los dos APK en un emulador desechable y ejecutar la instrumentación. Revisar el contenido de la salida: `adb am instrument` puede devolver exit0 aunque una prueba haya fallado. Después:

    python3 tools/native_saf_roundtrip.py
    python3 tools/compose_window_qa.py

Estas pruebas generan fixtures y cambian temporalmente la geometría; nunca ejecutarlas contra datos del negocio.
