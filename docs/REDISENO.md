# Rediseño Compose / Material Design 3

## Alcance

La solicitud de usar Compose y tabletas horizontales sustituye la decisión inicial de construir controles Android clásicos. Se migra la interfaz, no la base de datos. Se conserva `com.tamalitos.malitos`, la Activity de entrada, SQLite, importes en centavos, formato JSON de respaldos y reglas de clientes, pedidos, stock, pagos, gastos e informes. No se introduce sincronización entre dispositivos, KMP, datos precargados ni un servidor.

Versiones fijadas para conservar el toolchain del proyecto: Kotlin/Compose compiler 2.1.20, BOM Compose 2025.04.01 (Compose 1.8.0, Material3 1.3.2), Activity Compose 1.9.3 y Lifecycle Compose 2.8.7. No se presentan como las versiones más recientes. Android mínimo sigue siendo 8.1/API27, compileSdk35 y targetSdk34.

## Criterios de aceptación

- Todas las pantallas y editores del negocio son Compose con Material3; los selectores de archivos y consentimiento de Google siguen siendo interfaces del sistema.
- Los siete destinos son accesibles en teléfono y tableta, incluso con poca altura.
- En ancho compacto se muestra una lista o su detalle; en ancho expandido se pueden mostrar simultáneamente el directorio y ficha del cliente, o los pedidos y su detalle.
- La adaptación depende del ancho disponible de la ventana, no de una orientación bloqueada ni de una etiqueta de dispositivo.
- Formularios desplazables, teclado sin cubrir acciones, importes MXN, errores comprensibles y blancos táctiles de al menos 48dp.
- Navegación, selección y borradores se conservan en la recreación por rotación; los borradores anteriores a una restauración se descartan, no se reasignan a IDs reutilizados. Un proceso completamente nuevo obtiene otra generación y descarta las referencias/borradores anteriores por seguridad; no se promete recuperación de un formulario tras la muerte del proceso.
- Una confirmación de borrado, pago, estado o restauración antigua no debe cambiar datos restaurados posteriormente.
- Exportación local verificada por relectura del destino; restauración confirmada, validada y protegida por copia previa. Las copias previas locales y de Drive pueden exportarse.
- No se reemplaza una prueba por inspección de controles clásicos que ya no forman parte de la interfaz visible.

## Matriz de comprobación

Estas configuraciones son objetivos de QA, no resultados obtenidos por escribir este documento. Los resultados ejecutados se registran en `VERIFICACION-COMPOSE.md` cuando existan.

| Ventana de prueba | Comprobaciones |
| --- | --- |
| Teléfono vertical | Navegación compacta, alta/edición de cliente, pedido con varios conceptos, anticipo, abono, gasto y validación |
| Teléfono horizontal de poca altura | Navegación accesible, formulario desplazable y teclado sin bloquear Guardar |
| Tableta horizontal | Navegación lateral, directorio/ficha y lista/detalle de pedido simultáneos |
| Cambio de orientación/recreación | Borrador y selección conservados; referencia obsoleta tras restauración rechazada |
| Modo oscuro / fuente ampliada | Contraste, legibilidad y desplazamiento sin perder acciones |
| Respaldo local SAF | Exportar → confirmar importación → reexportar; comparar todo el negocio salvo `exportedAt` |

## Límites de la verificación

Un build verde no demuestra por sí solo la interfaz adaptable. Las pruebas de Compose y las ejecuciones en un emulador son complementarias. Las pruebas crean registros solo en un dispositivo de QA; nunca se deben ejecutar contra los datos reales del negocio.

Google Drive conserva su integración real, pero no se declara validado con una cuenta real sin configuración OAuth y consentimiento del propietario. La firma debug tampoco sustituye una firma de distribución.

## Referencias de plataforma

- Configuración del compilador Compose: https://developer.android.com/develop/ui/compose/compiler
- BOM y versiones compatibles: https://developer.android.com/develop/ui/compose/bom/bom-mapping
- Anchos de ventana adaptables: https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
