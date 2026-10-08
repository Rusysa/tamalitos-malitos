# Manual de uso — Tamalitos Malitos

## Antes de empezar

- Android 8.1 o posterior. La aplicación funciona sin servidor y sin internet para clientes, pedidos, pagos, gastos, catálogo e informes.
- No se precargan clientes ni pedidos de ejemplo. Tus registros viven en SQLite, dentro de este dispositivo.
- Se asume **peso mexicano (MXN)**. Los importes se almacenan como centavos enteros; escribe `25`, `25.50` o `25,50`, sin símbolo de moneda ni separadores de miles. No se aceptan más de dos decimales ni importes negativos.
- Los campos con `*` son obligatorios. Si un formulario tiene un error, permanece abierto y conserva lo escrito; el aviso explica qué corregir. Los formularios se desplazan verticalmente y las acciones tienen etiquetas de texto.

## Inicio

El resumen muestra ventas registradas, dinero efectivamente cobrado, gastos, saldo por cobrar y flujo de efectivo **de todo el historial**. También permite crear un pedido, registrar un gasto o agregar un cliente y consultar las próximas cinco entregas pendientes.

Usa la barra horizontal superior para abrir **Inicio, Clientes, Pedidos, Gastos, Informes, Productos y Respaldo**. Deslízala si no caben todas las opciones. El botón Atrás regresa del detalle a su lista; desde una sección vuelve a Inicio.

## Clientes

1. En **Clientes → Agregar cliente**, escribe el nombre. Teléfono, dirección habitual y notas son opcionales.
2. Busca por nombre o teléfono. La lista se actualiza mientras escribes, sin eliminar lo que estás buscando.
3. Abre **Ver cliente** para ver contacto, dirección, notas, pedidos e importe pendiente de cobro.
4. **Nuevo pedido para este cliente** lo selecciona directamente en el formulario.
5. **Editar cliente** cambia sus datos sin borrar el historial.
6. **Eliminar cliente** pide confirmación. Solo se permite si no tiene ningún pedido, incluso si sus pedidos están cancelados. Esto protege los vínculos contables.

## Crear pedidos

Necesitas al menos un cliente. Si no hay ninguno, la aplicación te ofrece agregarlo; no inventa un cliente provisional.

1. Abre **Pedidos → Nuevo pedido** o usa el acceso en Inicio.
2. Selecciona el cliente. La dirección habitual se propone como punto de entrega; puedes cambiarla sin modificar el directorio.
3. Elige la fecha con el calendario y escribe la dirección de entrega o el punto de encuentro. Agrega notas si hace falta.
4. Cada artículo puede ser **personalizado** o del **catálogo**. Seleccionar un producto propone su nombre y precio. Revisa descripción, cantidad entera mayor que cero y precio unitario mayor que cero.
5. Usa **Agregar otro artículo** para combinar varios productos o **Quitar este artículo** para retirarlo. Se admiten hasta 100 renglones por formulario.
6. Selecciona el anticipo:
   - **Pago completo**: registra el total como pago inicial.
   - **La mitad (50 %)**: registra la mitad; cuando sobra un centavo, redondea el anticipo hacia arriba.
   - **Sin anticipo**: no registra ningún pago inicial.
   - **Otro importe**: escribe un importe entre cero y el total.
7. Revisa el total, anticipo y saldo calculados y pulsa **Crear pedido**.

Los artículos de catálogo con stock controlado reservan/descuentan existencias al crear el pedido. Si no alcanza el stock, no se crea un pedido parcial y el formulario muestra el motivo. Un artículo personalizado no afecta al inventario.

## Detalle, abonos y entrega

**Ver pedido** muestra cliente, fecha y lugar, notas, estado, artículos, total, cobrado y saldo. El historial de pagos incluye anticipos y abonos con la fecha/hora reales de registro y su nota.

- **Registrar abono** acepta un importe mayor que cero y no mayor que el saldo. La nota/referencia es opcional. El pago se registra con la fecha y hora actuales del dispositivo. No hay una función de deshacer o editar pagos.
- **Cambiar estado** permite Pendiente, En preparación o Entregado. Entregar no implica que el cliente ya pagó todo; el saldo se conserva.
- **Cancelar pedido** pide confirmación. Es irreversible y recupera las existencias reservadas una sola vez. Un cancelado queda en el historial, pero no cuenta como venta ni deuda.
- No se puede cancelar un pedido con pagos: todavía no existe gestión de reembolsos. Tampoco se puede reactivar un pedido cancelado.
- No se editan artículos de un pedido ya creado. Verifica los datos antes de guardarlo; los pedidos sin pagos se pueden cancelar y crear nuevamente.

## Gastos

En **Gastos → Registrar gasto**, captura concepto, importe positivo, categoría y fecha. El selector ofrece categorías frecuentes, pero también puedes escribir una propia. Las notas son opcionales.

Cada registro ofrece **Editar gasto** y **Eliminar gasto**. La eliminación pide confirmación, no puede deshacerse y cambia el informe correspondiente.

Usa **Desde / Hasta → Aplicar periodo**, **Mes actual** o **Todo el historial** para filtrar. Las fechas son inclusivas; la fecha inicial no puede ser posterior a la final.

## Informes: ventas no son cobros

**Informes** abre el mes actual hasta hoy. Puedes elegir otro periodo o todo el historial:

| Cifra | Qué seleccionan las fechas |
| --- | --- |
| Ventas | Total de pedidos no cancelados según **fecha de entrega** |
| Cobros | Anticipos y abonos según **fecha real del pago**, aunque el pedido se entregue fuera del periodo |
| Gastos | Fecha registrada en cada gasto |
| Flujo de efectivo | Cobros menos gastos; **no es utilidad contable** |
| Saldo por cobrar | Saldo **actual** de los pedidos con entrega en el periodo, no deuda reconstruida al cierre histórico |
| Pendientes / entregados | Estado actual de los pedidos con entrega en el periodo |

Debajo aparecen gastos agrupados por categoría y pedidos con saldo pendiente para ese periodo. Abre un pedido para consultar o registrar sus abonos. Cancelados no suman a ventas, deuda ni pendientes.

## Catálogo opcional

**Productos → Agregar producto** registra nombre, precio positivo, existencias disponibles y si está activo:

- Existencias vacías: sin control de stock.
- Existencias `0`: producto controlado sin unidades disponibles.
- Editar las existencias ajusta las unidades disponibles, no las ya reservadas por pedidos.
- Desactivar un producto lo oculta en pedidos nuevos, pero conserva el historial. No hay eliminación de catálogo.
- Cambiar un precio no modifica artículos ni totales de pedidos anteriores.

## Respaldos locales — sin Google

### Exportar

1. Abre **Respaldo → Exportar respaldo local**.
2. El selector de archivos de Android te permite elegir carpeta/proveedor y nombre `.json`.
3. La aplicación escribe en segundo plano y vuelve a leer **el archivo exacto** para verificarlo. Solo entonces muestra que fue exportado y verificado.
4. Cancelar el selector no cambia los datos. Un proveedor sin permiso de lectura/escritura o una verificación fallida muestra un error; no se declara éxito.

El archivo incluye datos personales y financieros, **sin cifrado propio**. Guárdalo con controles de acceso, no lo publiques y conserva una copia fuera del dispositivo.

### Importar

1. Abre **Importar respaldo local** y elige un archivo creado por la aplicación.
2. Confirma que deseas **reemplazar todos los datos**. Importar no mezcla cuentas ni sincroniza dos teléfonos.
3. Antes de aplicar el archivo, la aplicación guarda el estado actual en una copia privada `respaldos-seguridad/seguridad-….json`, con escritura sincronizada y publicación del archivo completa. La instantánea, escritura de seguridad y sustitución comparten un bloqueo con todos los cambios y con la restauración de Drive; un cambio en espera de una generación anterior se rechaza, no se pierde silenciosamente.
4. Si la copia previa no puede guardarse, no se importa. Si el archivo entrante no pasa la validación del almacén, la sustitución no se aplica y los registros actuales quedan intactos.
5. Al terminar vuelve a Inicio y cierra formularios/confirmaciones anteriores. **Exportar copia de seguridad previa** permite elegir cualquier copia de `respaldos-seguridad` (local) o `drive-pre-restore` (Drive), exportar el archivo elegido fuera de la app y luego importarlo para recuperar ese estado.

La interfaz rechaza archivos mayores de **16 MB** o UTF-8 inválido. El almacén impone además sus propias validaciones de formato, versión, relaciones e importes. Si la instantánea actual supera 16 MB, se rechaza la importación para no crear una copia de seguridad que esta interfaz no pudiera volver a leer.

Las copias privadas se borran al desinstalar la aplicación o borrar sus datos. **Exporta primero**. No se borran automáticamente las copias locales de seguridad: vigila el espacio del dispositivo. Durante una operación local se bloquean nuevas acciones para evitar cambios simultáneos.

## Google Drive

La autorización y las transferencias se delegan al controlador real de Drive, fuera del hilo principal. La Activity reenvía los resultados de autorización al controlador.

1. Configura Google Cloud siguiendo [GOOGLE_DRIVE.md](GOOGLE_DRIVE.md): cliente OAuth Android, paquete y SHA-1 reales. No se incluye un identificador falso ni se simula una conexión.
2. Pulsa **Autorizar Google Drive y respaldar**. El estado de conexión debe aparecer solo después de completar y verificar un respaldo real.
3. Una vez conectado, activa **Respaldo automático cada 12 horas**. Android puede retrasar la ejecución; requiere red y autorización vigente. Si la autorización necesita interacción, vuelve a conectar manualmente.
4. **Restaurar último respaldo de Drive** autoriza si corresponde. El controlador confirma antes de reemplazar y crea su copia de seguridad local.
5. **Desconectar Google Drive** detiene las tareas automáticas y elimina la conexión local; no elimina registros ni archivos remotos. Sin conexión verificada aparece **Restablecer autorización de Google Drive**, también disponible si la primera autorización quedó interrumpida. Limpia la acción pendiente e invalida sus resultados antes de volver a autorizar.

Sin configurar Google Cloud, sin internet, ante una autorización rechazada o una transferencia fallida, se muestra el estado/error real. **No se afirma que hubo una copia remota.** Drive se usa para respaldo/restauración, no para edición simultánea entre dispositivos.

## Rotación y límites

Se conservan la sección, detalle abierto, búsqueda de clientes/pedidos, estado filtrado, periodos aplicados, posición de desplazamiento y borradores de los formularios principales al recrear la Activity. Los borradores incluyen múltiples artículos y anticipo. La confirmación de importación local también se recupera. El trabajo local de respaldo usa un ejecutor independiente de la Activity y no conserva referencias fuertes a una pantalla destruida.

Los borradores y confirmaciones quedan vinculados a la generación de datos que los abrió: después de una restauración no pueden guardar, abonar ni borrar registros con IDs reutilizados. Los borradores de una generación anterior se descartan al recrear la pantalla; un proceso nuevo también los invalida por seguridad. Los cambios no guardados no sobreviven a un cierre forzado o a descartar el formulario. Los diálogos de calendario/confirmación de eliminación y el estado aún no aplicado del selector de periodo pueden necesitar abrirse de nuevo tras una rotación. Verifica el reloj del dispositivo: determina la fecha real de cobros.

## Evidencia y pruebas de la interfaz

- Se escribieron pruebas Robolectric de pantalla vacía, directorio/búsqueda/deuda, errores de validación sin cerrar formulario, detalle de pedido, borrador con artículos después de recreación, gastos/catálogo, informes y respaldo local. Se usa SDK 28 y una `Application` sin inicializar trabajos de Drive para pruebas de pantalla.
- `UiWorkflowTest.kt` añade recorridos con controles nativos y SQLite real: alta/edición de cliente y gasto, varios artículos y anticipo HALF, abono excesivo rechazado y abono válido, precio cero rechazado, confirmación de borrado cancelada y selección SAF de exportación. Estas pruebas también quedan pendientes de la ejecución Robolectric coordinada.
- La primera tentativa de RED no pudo ejecutar Gradle: todavía no existían `tools/env.sh` y el wrapper ejecutable. Después, el agente coordinador reservó Gradle al trabajador DATA por el límite de RAM. Por eso **este trabajador no ejecutó las pruebas Robolectric** ni declara un RED/GREEN de esas pantallas; quedan para la integración del coordinador.
- Sí se compiló código Kotlin puro real con el compilador 2.1.20 disponible y se ejecutó JUnit 4.13.2, sin Gradle paralelo: validación de periodos, límites de lectura, UTF-8 y archivos de seguridad.
- Salida inicial real: `OK (8 tests)`.
- Se agregó una prueba que exige rechazar una copia de seguridad mayor de 16 MB. RED real: `Tests run: 9, Failures: 1`, `Expected exception: java.lang.IllegalArgumentException`.
- Después de implementar el límite antes de escribir, GREEN real: `Kotlin compiler exit: 0`, `OK (9 tests)`.
- Las pruebas puras se ejecutaron con límites de memoria de 256 MB para compilar y 128 MB para JUnit. El lanzador temporal quedó en el directorio de scratch del agente; las pruebas permanentes están en `UiPeriodTest.kt` y `UiBackupFilesTest.kt`.
- Una comprobación adicional con el compilador Kotlin y el `android.jar` real detectó un sombreado de `id` en el selector de categoría; se corrigió con `this.id`. La compilación completa de la UI todavía dependía de que el otro trabajador aportara `DriveController`, ausente en ese momento; no se usó un controlador ficticio para declarar que la aplicación compilaba.

La autorización real de Drive y los proveedores de documentos SAF deben probarse también en un dispositivo/emulador con servicios Google y con la configuración OAuth indicada. No se usaron datos de negocio ficticios para declarar transferencias exitosas.
