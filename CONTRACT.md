# Tamalitos Malitos — implementation contract

User's Word project requests Android 8.1+, SQLite offline, clients, orders, inventory, reports and Drive backups. Latest user clarifies priority: customer directory, orders, expenses; initial payment full/half/unpaid, later installments and debt. No Google Cloud configured; implement real Drive integration with honest setup and failure states. Spanish UI, MXN default (document assumption), no sample personal/business data seeded, no secrets, no server needed. One device, backup/restore (NOT concurrent multidevice synchronization).

## Build (parent owns all Gradle/config/manifest/resources except UI-created resources)
Package com.tamalitos.malitos; Kotlin 2.1.20, AGP8.9.2, Gradle8.11.1, JDK17, compileSdk35,targetSdk34,minSdk27. Latest user request replaces the original widget-only interface: Jetpack Compose with Material Design 3 and adaptive layouts for phones and landscape tablets. Compose compiler plugin matches Kotlin 2.1.20; Compose BOM2025.04.01 pins UI1.8.0/Material3 1.3.2 compatible with SDK35. Dependencies also include androidx.activity:activity-compose:1.9.3, lifecycle runtime/viewmodel Compose2.8.7, androidx.work:work-runtime-ktx:2.9.1, com.google.android.gms:play-services-auth:21.3.0. Tests JUnit4, Robolectric4.14.1, androidx.test:core1.6.1 and Compose UI semantics tests. Robolectric tests @Config(sdk=[28]) saves downloads. MainActivity must remain the launcher component so existing installs retain their data. Manifest application .TamalitosApplication for backup scheduling provided by Drive owner. No permissions besides INTERNET,ACCESS_NETWORK_STATE. Disabled platform cloud backups; local JSON export/import Storage Access Framework. Do not change SQLite schema or the versioned backup format for an interface redesign.

## Ownership (parallel workers, do NOT edit other worker files)
DATA owns app/src/main/java/com/tamalitos/malitos/{Models.kt,BusinessRules.kt,BusinessStore.kt}, app/src/test/java/com/tamalitos/malitos/{BusinessRulesTest.kt,BusinessStoreTest.kt,BackupTest.kt}; docs/DATA.md. Can add own prefixed tests.
UI owns app/src/main/java/com/tamalitos/malitos/{MainActivity.kt,Ui*.kt} plus new presentation/theme/repository files, app/src/test/java/com/tamalitos/malitos/Ui*Test.kt, UI-specific regression tests and instrumented workflows, docs/MANUAL.md. Single ComponentActivity with Compose/Material3 screens Inicio/Clientes/Pedidos/Gastos/Informes/Productos/Respaldo. Keep green/cream/terracotta identity with light/dark themes. Compact phone navigation must keep all seven destinations reachable without squeezing seven items into a bottom bar. Medium widths use a navigation rail; expanded available width (840dp+) supports concurrent customer/order list and detail panes. Adapt to current window dimensions, including multiwindow and short landscape heights; do not lock orientation or infer tablet status from hardware. Use lazy lists, accessible 48dp targets, keyboard-safe scrollable forms, retained drafts/navigation/selection and generation-fenced actions after restore. Tests must inspect actual Compose semantics, not invisible legacy widgets.
DRIVE owns app/src/main/java/com/tamalitos/malitos/{Drive*.kt,TamalitosApplication.kt}, app/src/test/java/com/tamalitos/malitos/Drive*Test.kt, docs/GOOGLE_DRIVE.md. Implement API below. Parent owns README, integration tests, fixes after coordination, tooling/config.

## Fixed shared API (DATA implements exactly this; extend only if necessary, document)
All in package com.tamalitos.malitos. Monetary values Long integer centavos, never Float/Double. Time LocalDate for input ISO yyyy-MM-dd and timestamp System.currentTimeMillis for payments. Use java.time available min27.

data class Customer(val id:Long=0,val name:String,val phone:String="",val address:String="",val notes:String="")
data class Product(val id:Long=0,val name:String,val priceCents:Long,val stock:Int?=null,val active:Boolean=true)
data class OrderItem(val productId:Long?,val description:String,val quantity:Int,val unitPriceCents:Long) { val totalCents:Long }
enum class InitialPayment { FULL, HALF, UNPAID, CUSTOM }
enum class OrderStatus { PENDING, PREPARING, DELIVERED, CANCELLED }
data class Payment(val id:Long=0,val orderId:Long,val amountCents:Long,val timestamp:Long,val note:String="")
data class Order(val id:Long,val customerId:Long,val customerName:String,val deliveryDate:String,val deliveryAddress:String,val notes:String,val status:OrderStatus,val items:List<OrderItem>,val payments:List<Payment>) { val totalCents:Long; val paidCents:Long; val balanceCents:Long; val paymentLabel:String }
data class Expense(val id:Long=0,val description:String,val category:String,val amountCents:Long,val date:String,val notes:String="")
data class Report(val salesCents:Long,val collectedCents:Long,val expensesCents:Long,val receivablesCents:Long,val pendingOrders:Int,val deliveredOrders:Int) { val cashFlowCents:Long }
object Money { fun parse(text:String):Long; fun format(cents:Long):String }
object BusinessRules { fun initialPayment(totalCents:Long,mode:InitialPayment,customCents:Long=0):Long }
class BusinessStore(context:android.content.Context):java.io.Closeable {
 fun customers(query:String=""):List<Customer>
 fun saveCustomer(customer:Customer):Long
 fun deleteCustomer(id:Long) // forbid customer with orders; meaningful validation
 fun products(includeInactive:Boolean=false):List<Product>
 fun saveProduct(product:Product):Long
 fun orders(query:String="",status:OrderStatus?=null):List<Order>
 fun order(id:Long):Order?
 fun createOrder(customerId:Long,deliveryDate:String,deliveryAddress:String,notes:String,items:List<OrderItem>,paymentMode:InitialPayment,customPaymentCents:Long=0):Long
 fun addPayment(orderId:Long,amountCents:Long,note:String=""):Long
 fun setOrderStatus(orderId:Long,status:OrderStatus)
 fun expenses(from:String?=null,to:String?=null):List<Expense>
 fun saveExpense(expense:Expense):Long
 fun deleteExpense(id:Long)
 fun report(from:String?=null,to:String?=null):Report
 fun exportBackup():String // versioned UTF8 JSON snapshot, transactionally consistent
 fun importBackup(json:String) // validate all input BEFORE changes; atomic replace; invalid leaves DB intact; preserve ids/relations/status/payments, limits prevent malicious content
 override fun close()
}
Orders reserve/decrement tracked stock on creation, release on cancellation once; forbid cancelled->other to avoid ambiguity, cancel with payments must be refused (refund not implemented). Freeform items allowed; quantity>=1,unitPrice>0. Strict cents parse (max2 decimals comma/dot, whitespace trim, negatives rejected), checked arithmetic bounds. FULL/HALF/UNPAID initial payments included in reports; half rounds up one centavo for odd totals. Additional payments >0 and <=balance; delivered need not paid. Reports clarify date selection: sales/order outstanding/pending by delivery date; collected by actual payment date; expenses by date. Cancelled orders excluded from sales/outstanding/pending. Report counters tests and validation.

## Drive shared API (DRIVE implements exactly this)
class DriveController(private val activity:android.app.Activity,private val onStatus:(String)->Unit,private val onRestored:()->Unit) {
 fun connectAndBackup() // prompt authorization, start backup; set connected only after verified upload
 fun restoreLatest() // authorize if necessary; confirm destructive restore BEFORE applying, make local safety copy
 fun disconnect() // stop scheduled backups, clear local connected/account metadata (no secret persistence)
 fun handleActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?):Boolean
 companion object {
  fun status(context:android.content.Context):String
  fun isConnected(context:android.content.Context):Boolean
  fun setAutomatic(context:android.content.Context,enabled:Boolean)
  fun isAutomatic(context:android.content.Context):Boolean
 }
}
AuthorizationClient Identity with Scope https://www.googleapis.com/auth/drive.appdata; Android OAuth package+SHA1 configured outside app (NO fake clientId). Native startIntentSenderForResult within controller and Activity forwards result. HTTPS Drive v3 list/upload appDataFolder JSON versioned timestamped backup and verify remote exact file metadata md5Checksum (compare real bytes checksum) or media readback before success. Background WorkManager 12h network required, Tasks.await(authorize), if resolution needed return actionable reconnect failure; never initiate UI in background. Tokens only in memory, never logs/preferences. Keep last e.g.10 backups only after newest verified. Use connect status/prefs to track times/error; no fabricated success. Local export/import works independently.

## Verification
Follow strict TDD for behavior: write one failing test, execute it (once tooling ready), implement, execute green, next slice. Store failing and passing logs in docs or build test output if desired; no fabricated test claims. Gradle bootstrapped by parent. command: source tools/env.sh && ./gradlew testDebugUnitTest --no-daemon --max-workers=1; coordinate concurrent Gradle use, avoid running duplicate heavy builds. If dependencies unavailable yet write tests, wait or use actual compiler once ready; record limitations honestly. No commits, no modifications to Word. Do not install tools or edit build files independently.
