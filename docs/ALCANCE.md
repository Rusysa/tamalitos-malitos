# Alcance y decisiones

Fuente: Tamalitos_Malitos_Proyecto.docx, conservado sin modificaciones. La aclaración del usuario prioriza directorio de clientes, pedidos y gastos. Los pedidos permiten pago completo, mitad, sin pago y abonos posteriores. El inventario del Word se conserva como catálogo opcional de productos con control de existencias; no se añade receta/consumo de ingredientes sin requerimientos.

## Requisitos y criterios de aceptación

- Android 8.1 (API27) como mínimo; Kotlin, almacenamiento local SQLite. No depende de un servidor para trabajar.
- Clientes: nombre, teléfono, dirección/punto de encuentro y notas. Consultar y modificar; conservar referencias históricas.
- Pedidos: cliente, varios conceptos, cantidades/precios, fecha de entrega, ubicación, notas y estados pendiente/en preparación/entregado/cancelado.
- Dinero: cantidades exactas en centavos; registro inicial completo/mitad/adeudo y opcional personalizado; historial de abonos, saldo automático y rechazo de sobrepagos.
- Gastos: concepto, categoría, fecha, importe y notas. Se separan de las ventas y de la cobranza.
- Reportes: ventas, cobros efectivos, gastos, flujo de caja, cuentas por cobrar y pedidos pendientes. Las ventas no equivalen a dinero cobrado.
- Productos: catálogo opcional, precio y existencias opcionales. La cancelación no debe devolver inventario dos veces.
- Respaldo: JSON local exportable/restaurable; Drive con autorización, archivos propios del appDataFolder y verificación de la carga. La restauración sustituye los datos y requiere confirmación y copia de seguridad previa.
- Respaldo programado: sujeto a autorización, conexión y planificación de Android. No equivale a sincronización de varios dispositivos ni a tiempo real.

## Supuestos explícitos

- Moneda inicial: pesos mexicanos (MXN). Puede cambiarse tras validación del negocio.
- Una instalación administra el negocio. No se ofrece fusión concurrente entre teléfonos.
- Un pedido entregado puede seguir adeudado: entrega y pago son estados independientes.
- Si el total tiene un centavo impar, el anticipo de mitad se redondea hacia arriba un centavo y el saldo conserva la suma exacta.
- Cancelaciones con dinero recibido se bloquean: el flujo de reembolsos exige definición adicional, no se borran cobros silenciosamente.
- Sin datos precargados de clientes, ventas ni cuentas de Google.
- El propietario debe validar la experiencia con pedidos reales antes de producción.

## Fuera de alcance

Portal para clientes, recepción automática de WhatsApp, facturación fiscal, pagos con tarjeta, recetas de ingredientes, varios empleados con permisos, sincronización concurrente y publicación en Play Store.

## Google Drive

El usuario indicó que todavía no tiene configurado un proyecto en Google Cloud. Se entregan la integración y la guía de configuración, pero el éxito con la cuenta real y la recuperación tras pérdida del dispositivo requieren validación posterior. No se usa una cuenta ajena ni credenciales inventadas.
