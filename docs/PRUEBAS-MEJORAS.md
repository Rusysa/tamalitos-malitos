# Mejoras de operación — pendientes de probar en Android Studio

Punto de respaldo previo: `cc5cbab` (árbol del proyecto sin cambios pendientes).

No se ejecutaron Gradle, compilaciones, APK, emuladores ni pruebas JVM para estos cambios, por petición del usuario. La revisión realizada es de código, XML y diferencias de Git. La evidencia anterior en `verification-evidence` corresponde a versiones anteriores.

## Prueba manual

Usar un dispositivo/emulador de pruebas y abrir este proyecto en Android Studio.

1. Abrir la aplicación en modo claro y oscuro, entrar a un formulario y volver. Comprobar que la barra superior de Android ya no tiene el verde personalizado. Repetir en Android 8.1 y una versión reciente disponible.
2. Crear pedidos con entregas ayer, hoy y mañana. Marcar uno como entregado y cancelar otro sin pagos. En Inicio, comprobar que hoy/vencidos solo incluyen pendientes y en preparación. Abrir cada acceso y comprobar la lista.
3. Combinar estado, búsqueda, entrega y cobro. Comprobar que entregados con deuda aparecen al filtrar saldo y que cancelados/pagados no aparecen. Revisar el orden por fecha y saldo, el conteo, el caso sin coincidencias y Restablecer filtros. Girar el dispositivo y verificar que conserva los filtros.
4. Crear productos activos con stock 0, 5, 6 y sin control, y uno inactivo con stock 0. Inicio debe avisar solo por los dos primeros. Editar existencias y volver a Inicio para verificar la actualización.
5. Abrir un cliente con teléfono y dirección. Marcar debe abrir el marcador sin iniciar una llamada. Mapas debe abrir la dirección correcta. Un cliente sin estos datos no muestra esos accesos. Comprobar el aviso en un emulador sin aplicación compatible.
6. Abrir un pedido con anticipo y abono. Compartir debe mostrar el selector de Android; revisar el texto en la aplicación elegida sin enviarlo. Confirmar importes, artículos, dirección y ausencia de notas privadas/referencias. Cancelar el selector y verificar que el pedido sigue igual. Revisar también el resumen de un cancelado.
7. Revisar Inicio, filtros y detalle en teléfono, tableta horizontal y con fuente ampliada; comprobar que los controles se pueden desplazar y pulsar.

## Regresiones preparadas, sin ejecutar

- `UiOrderToolsTest`: agenda por fecha/estado, saldo, búsqueda combinada, orden e información del resumen.
- `UiActivityTest`: conservación y limpieza de filtros; acceso a la agenda desde Inicio con filtros previos incompatibles.
- `ComposeReviewEditorIdentityTest`: un fallo de guardado anterior no modifica el formulario abierto posteriormente.

Se conserva el esquema SQLite, el formato de respaldos y los permisos existentes. No se agregaron dependencias.

## Diseño visual y apariencia — también sin compilar

- Tarjetas con iconos, cabecera con degradado, etiquetas de estado y columnas adaptables.
- Gráficos nativos de Compose: comparación de ventas/cobros/gastos, distribución de estados y gastos por categoría. Sin bibliotecas adicionales ni datos de ejemplo.
- Selector claro/oscuro/sistema persistido en preferencias locales. Paleta propia en todas las versiones de Android compatibles.

Pruebas adicionales en Android Studio:

1. Abrir **Cambiar apariencia** (icono de paleta superior), seleccionar oscuro, cerrar y abrir la app. Verificar que conserva la elección. Repetir con claro y con seguir el sistema; en este último caso cambiar el tema del dispositivo.
2. Revisar textos, tarjetas, formularios, teclado, menús, gráficos y barra inferior en ambos modos. Comprobar que la barra de notificaciones no recupera el verde.
3. Probar una ventana horizontal baja y fuente ampliada: el selector de apariencia debe desplazarse, las tarjetas reorganizarse y las etiquetas de gráficos seguir siendo legibles.
4. Comparar cifras de gráficos con tarjetas e informes. Probar periodos sin registros, cobros de pedidos entregados fuera del periodo, cancelados, pagos parciales y flujo negativo. Las gráficas no representan utilidad.
5. Registrar gastos en más de cinco categorías; verificar que las cinco mayores más Otras categorías suman el total. Comparar con el desglose completo debajo.
6. Usar TalkBack: los gráficos deben tener equivalentes en texto para importes, cantidades y estados, sin depender del color.

Regresiones nuevas preparadas, sin ejecutar: `UiChartsTest` (sumas y agrupación sin pérdida de centavos) y `UiAppearanceTest` (selección desde interfaz, persistencia y valor desconocido).
