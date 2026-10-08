# Manual de uso — Tamalitos Malitos

## Antes de empezar

- Android 8.1 o posterior. La aplicación funciona sin servidor y sin internet para clientes, pedidos, pagos, gastos, catálogo e informes.
- No se precargan clientes ni pedidos de ejemplo. Tus registros viven en SQLite, dentro de este dispositivo.
- Se asume **peso mexicano (MXN)**. Los importes se almacenan como centavos enteros; escribe `25`, `25.50` o `25,50`, sin símbolo de moneda ni separadores de miles. No se aceptan más de dos decimales ni importes negativos.
- Los campos con `*` son obligatorios. Si un formulario tiene un error, permanece abierto y conserva lo escrito; el aviso explica qué corregir. Los formularios se desplazan verticalmente y las acciones tienen etiquetas de texto.

## Inicio

El resumen muestra ventas registradas, dinero efectivamente cobrado, gastos, saldo por cobrar y flujo de efectivo **de todo el historial**. También permite crear un pedido, registrar un gasto o agregar un cliente y consultar las próximas cinco entregas pendientes.

La interfaz usa **Jetpack Compose y Material 3**, con colores verde/crema/terracota, modo oscuro y colores dinámicos de Android 12 o posterior.

- En teléfono (ancho disponible menor de 600 dp), la barra inferior muestra **Inicio, Clientes, Pedidos y Más**. **Más** abre Gastos, Informes, Productos y Respaldo.
- Desde 600 dp aparece una **barra lateral** con las siete secciones; se puede desplazar en ventanas bajas.
- Desde 840 dp, **Clientes y Pedidos muestran lista y detalle simultáneamente**. La selección se mantiene al cambiar tamaño o girar. Se usa el ancho real de la ventana: también funciona en pantalla dividida, sin bloquear orientación.
- Los indicadores se distribuyen en tarjetas adaptables. Los formularios tienen ancho máximo, desplazamiento y espacio para el teclado; no es necesario completar un formulario horizontalmente.
- **Atrás** cierra primero el formulario, luego el detalle; desde una sección vuelve a Inicio.

## Clientes

1. En **Clientes → Agregar cliente**, escribe el nombre. Teléfono, dirección habitual y notas son opcionales.
2. Busca por nombre, teléfono, dirección o notas. La lista se actualiza mientras escribes, sin eliminar lo que estás buscando.
3. Abre **Ver cliente** para ver contacto, dirección, notas, pedidos e importe pendiente de cobro.
4. **Nuevo pedido para este cliente** lo selecciona directamente en el formulario.
5. **Editar cliente** cambia sus datos sin borrar el historial.
6. **Eliminar cliente** pide confirmación. Solo se permite si no tiene ningún pedido, incluso si sus pedidos están cancelados. Esto protege los vínculos contables.

## Crear pedidos

Necesitas al menos un cliente. Si no hay ninguno, la aplicación te ofrece agregarlo; no inventa un cliente provisional.

1. Abre **Pedidos → Nuevo pedido** o usa el acceso en Inicio.
2. Selecciona el cliente. La dirección habitual se propone como punto de entrega; puedes cambiarla sin modificar el directorio.
3. Escribe la fecha en formato `AAAA-MM-DD` o pulsa **Elegir fecha en calendario** (selector Material 3). Escribe la dirección de entrega o el punto de encuentro. Agrega notas si hace falta.
4. Cada artículo puede ser **personalizado** o del **catálogo**. Seleccionar un producto propone su nombre y precio. Revisa descripción, cantidad entera mayor que cero y precio unitario mayor que cero.
5. Usa **Agregar otro artículo** para combinar varios productos o **Quitar artículo N** para retirarlo. Se admiten hasta 100 renglones por formulario.
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

En **Gastos → Registrar gasto**, captura concepto, importe positivo, categoría y fecha. El selector **Categorías frecuentes** ofrece opciones, pero también puedes escribir una categoría propia. Las notas son opcionales.

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

La sección, selección, búsquedas, filtros, periodos y borradores pertenecen a un **ViewModel retenido**. Al girar el dispositivo se conserva incluso un guardado en curso: termina en segundo plano, cierra el editor y selecciona el registro guardado en la nueva pantalla. Los datos SQLite se cargan fuera de la composición mediante un repositorio con contexto de aplicación y se observan con `collectAsStateWithLifecycle`.

La Activity serializa navegación, campos y múltiples artículos. Los controles desplazables y calendarios usan estado guardable de Compose. La confirmación de importación local y la copia previa seleccionada se recuperan al recrear la Activity. Los trabajos locales de respaldo continúan sin conservar una Activity fuerte ni reabrir su almacén ya cerrado.

Todos los borradores, confirmaciones y trabajos capturan una generación antes de encolarse. Una restauración cierra editores y selección, y los callbacks antiguos no pueden modificar IDs reutilizados. **Un proceso completamente nuevo cambia el nonce de generación del almacén existente; por seguridad se descartan sus borradores de edición anteriores**, aunque la navegación se serializa. No se promete recuperar un borrador después de forzar detención o borrar datos. Los diálogos de eliminación/Drive deben abrirse de nuevo después de recreación. Verifica el reloj del dispositivo: determina la fecha real de cobros.

## Evidencia y pruebas de la interfaz

- Las regresiones UI se migraron de `android.widget`/diálogos nativos a **semántica Compose** y SQLite real: altas y edición, artículos múltiples, HALF, pagos excesivos, validación con borrador conservado, eliminación cancelada, búsqueda, informes y SAF.
- Se mantienen las verificaciones de generación, recuperación de copias locales y de Drive, y autorización/reset/epoch. El controlador Drive conserva su confirmación nativa por defecto para consumidores antiguos, mientras la app muestra confirmación Material 3.
- `ComposeAdaptiveTest` verifica las siete secciones, navegación inferior, rail en ventana baja, lista/detalle a 1000 × 600 dp, calendario Material y carga perezosa de un directorio grande.
- `ComposeRetentionTest` bloquea el almacén con una barrera real y gira durante un guardado; verifica que el resultado llega a la pantalla recreada sin duplicar el editor.
- `NativeWorkflowInstrumentedTest` usa toque/semántica Compose para cliente, edición, pedido HALF, liquidación, entrega, gasto y recreación de un borrador. `ComposeAdaptiveInstrumentedTest` verifica un ancho real amplio y restaura los ajustes `wm` al terminar. Se ejecutan únicamente sobre un dispositivo de pruebas: generan registros con etiquetas QA.
- Comando de integración: `source tools/env.sh && ./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --no-daemon --max-workers=1`. Los totales y la evidencia final se registran por el coordinador; no se equipara compilar la instrumentación con ejecutarla.

La autorización de una cuenta real de Drive y los proveedores SAF también requieren pruebas en un dispositivo con servicios Google y OAuth configurado. No se simulan transferencias exitosas ni se incluye información de demostración en el APK.
