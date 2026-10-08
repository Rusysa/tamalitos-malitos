# Instalación en tu teléfono

## APK

El APK de desarrollo está en `entregables/Tamalitos-Malitos-debug.apk`. Android mínimo: 8.1. Copiar el archivo al teléfono, abrirlo y permitir temporalmente instalar desde la aplicación que lo abrió (Archivos, por ejemplo). No hace falta acceso a contactos, ubicación, cámara ni almacenamiento general: los archivos de respaldo se eligen con el selector de Android.

Esta versión no está publicada en Play Store. Está firmada con el certificado de desarrollo de esta máquina. Para distribuir públicamente se requiere preparar la firma de producción; no reutilizar el certificado debug como firma definitiva.

## Con Android Studio

Abrir `/home/rudys/proyectos/App gestion kt` como proyecto. Seleccionar JDK17 en la configuración de Gradle y SDK Android35. `local.properties` contiene la ruta local instalada aquí; en otra computadora Android Studio debe regenerarlo. Las bibliotecas Kotlin/Android están fijadas para que la compilación sea reproducible.

En esta máquina las herramientas están instaladas sin permisos de administrador en:

    /home/rudys/.local/share/tamalitos-tools

Comandos desde la raíz del proyecto:

    source tools/env.sh
    ./gradlew assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

## Google Drive: datos públicos de esta compilación

Paquete Android:

    com.tamalitos.malitos

SHA-1 real del certificado debug, obtenido con `apksigner verify --print-certs`:

    28:33:F6:7C:F1:13:1C:B3:DE:79:A9:73:01:F9:E1:58:97:67:7B:15

Registrar ese paquete y esa huella como cliente OAuth de tipo Android en tu proyecto de Google Cloud. Habilitar Google Drive API, preparar el consentimiento y agregar tu cuenta como usuario de prueba. Seguir `docs/GOOGLE_DRIVE.md`.

Si otra computadora firma el APK con otro certificado debug, la huella cambia y debes registrar también ese nuevo cliente. No se solicita ni incrusta contraseña de Google, token de acceso o client secret.

## Antes de usar con información real

1. Practicar con un cliente y un pedido de prueba. Confirmar precio, fecha, punto de entrega y anticipo.
2. Exportar un respaldo local a una ubicación privada fuera de la app.
3. Verificar los saldos y registrar un abono de prueba.
4. Probar la restauración con datos de prueba; restaura todos los datos, no fusiona dispositivos.
5. Configurar Drive y completar su prueba real de ida y vuelta antes de confiar en el respaldo automático.
6. Proteger el teléfono y la cuenta Google con bloqueo y autenticación segura.

No desinstalar la app ni borrar sus datos sin una exportación externa: los archivos privados y la base SQLite se eliminan con esas acciones. Los archivos JSON exportados no tienen cifrado adicional de la aplicación.
