# Verificación de la entrega

AVISO AL PUBLICAR EN GITHUB: este documento y sus evidencias son históricos y no certifican el estado del código actual. La comprobación más reciente alcanza `assembleDebug`, pero las pruebas JVM no pueden compilar: `BoundaryAllocationTest.kt:90–91` usa `p.id` aunque `saveProduct` devuelve un `Long`. La última corrección no tiene aprobación final independiente. Consultar también el estado del proyecto en README.md; esta publicación es una versión en desarrollo, no una entrega validada para producción.

## Resultado de ejecución real

- Compilación del APK completo con Gradle8.11.1/JDK17: `BUILD SUCCESSFUL`.
- 92 pruebas JUnit/Robolectric: 0 fallos, 0 errores, 0 omitidas. Se ejecutaron las pruebas originales y las regresiones de revisión, no versiones ficticias de los servicios de producción.
- 3 pruebas instrumentadas: `OK (3 tests)` en el emulador Android8.1.0/API27. Incluyen arranque de la aplicación real con el manifiesto fusionado, SQLite persistente, mitad/adeudo y el flujo completo por controles nativos: cliente → pedido → abono → entrega → gasto.
- 2 comprobaciones de configuración: respaldo de plataforma desactivado y exclusiones explícitas de extracción para Android12+.
- Android Lint: 0 errores y 32 advertencias. Entre ellas versiones fijadas de bibliotecas, targetSdk34, uso de APIs nativas de navegación y preferencias síncronas. No se presenta como análisis sin advertencias ni como app lista para Play Store.
- Prueba real de Storage Access Framework: exportar a Descargas → importar con confirmación → exportar de nuevo. El contenido de negocio fue idéntico, ignorando exclusivamente la hora de exportación. Se confirmó la existencia de la copia privada previa. Evidencia: `native-saf-final.json`.
- APK con firma debug válida verificada mediante `apksigner`; minSdk27 confirmado con `aapt`. Sin datos de negocio precargados: los registros de pruebas existen solamente en el emulador, no dentro del APK.
- Word original intacto, comprobado por SHA-256.

Resumen legible por máquina: `docs/verification-evidence/summary.json`.

## Evidencia durable

- `build-current.log`: compilación, suite completa y lint.
- `android-delivery.log`: resultados nativos del APK final.
- `config-current.log`: comprobaciones de configuración.
- `native-saf-delivery.log` y `native-saf-final.json`: ida/vuelta real de archivos del APK final.
- `review-*.log`: fallos reproducidos y verificaciones de correcciones.
- `docs/capturas/inicio-final.png` y `respaldo-final-verificado.png`: capturas del emulador con datos identificados de prueba.

Estos archivos están dentro de `docs/verification-evidence/`, salvo las capturas. Los logs con `red` reflejan intentos anteriores fallidos, no el resultado final. Un intento de integración detectó problemas del entorno de Robolectric: inicialización WorkManager fuera del arranque nativo y callbacks pendientes de diálogos; se corrigió la configuración del test y se avanzó el looper, sin sustituir servicios de producción.

## Correcciones derivadas de revisión

1. Restauración coordinada entre todas las instancias de almacenamiento: exportación de seguridad, publicación durable e importación quedan dentro del mismo cerco de mutaciones. Un escritor o restaurador encolado con la generación anterior se rechaza explícitamente.
2. Formularios, botones y confirmaciones capturan la generación de los datos. No pueden guardar sobre IDs reutilizados tras una restauración; se limpian diálogos y borradores antiguos y se valida la recreación de la Activity.
3. Recuperación de autorizaciones pendientes: hay una acción accesible aun desconectado para restablecer el flujo. Se rechazan resultados antiguos de autorización y fallos de operaciones invalidadas.
4. Se rechazan IDs importados que agotan los límites admitidos. El límite de importación es 999999000000, separado del límite de protección de asignación de 1000000000000. En el límite aceptado se asigna explícitamente un ID libre inferior: los IDs existentes se conservan, los nuevos siguen siendo exportables/restaurables y se invalidan referencias antiguas solamente tras confirmar la transacción. Los IDs nuevos se verifican dentro de las transacciones, conservando filas, stock, secuencias y generación al fallar. `boundary-red.log` reproduce fallos; `boundary-green.log` confirma su corrección.
5. Las copias privadas previas a restauraciones locales y de Drive se pueden seleccionar y exportar desde Respaldo.

Las pruebas de regresión están en `Review*Test.kt`. La ejecución final completa fue repetida por el agente principal después de una interrupción del trabajador de correcciones; no se interpretó una operación inconclusa como éxito.

Estado de la segunda revisión independiente: pendiente de su dictamen al momento de escribir este documento.

## Qué no está verificado

- Autorización con un proyecto Google Cloud real y una cuenta del negocio.
- Subida/recuperación real en Google Drive ni una ejecución periódica prolongada del respaldo automático.
- Pruebas de aceptación con el propietario, teléfono físico, todos los fabricantes o versiones Android posteriores.
- Firma de producción, publicación en Play Store o funcionamiento simultáneo entre varios dispositivos.

El usuario indicó que todavía no tiene configurado Google Cloud. La integración y guía se entregan preparadas, pero no se promete el criterio de éxito del Word de 100% de pruebas reales de Drive sin haberlas ejecutado.

## Repetir las pruebas

Desde la raíz del proyecto:

    source tools/env.sh
    bash tools/verify.sh

Con un emulador encendido (no un teléfono con información del negocio):

    bash tools/verify.sh --emulator
    python3 tools/native_saf_roundtrip.py

Para este entorno, el emulador API27 requirió `-gpu swangle`: `swiftshader` se cerraba antes de arrancar Android. Las pruebas no arrancan ese emulador automáticamente. Ejemplo de arranque:

    source tools/env.sh
    emulator -avd Tamalitos_API27 -no-window -no-audio -no-snapshot -gpu swangle -cores 2

TDD: hay registros reales RED→GREEN de comportamientos y regresiones. Algunas primeras partes se escribieron con tests antes del código pero sin poder ejecutar el test aún por falta de herramientas/componentes; no se afirma que todo el proyecto haya seguido TDD estricto.
