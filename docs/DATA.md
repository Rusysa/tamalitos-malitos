# DATA component — decisions and execution evidence

Shared API: `CONTRACT.md`; no UI/Drive/config changes. DB `tamalitos.db`, SQLite schema version 1. MXN centavos (Long); no seed data. Names/notes trimmed, required names/descriptions, dates strictly ISO real calendar dates, checked totals, parameterized SQL.

## Business decisions

- Customer names, item description/unit price are historical order snapshots, not retroactively renamed.
- Pending counter includes PENDING and PREPARING. DELIVERED can still owe money. Cancelling an order with any payments is refused (refund workflow absent). Cancellation is final and repeated CANCELLED is a no-op.
- Products with null stock have no stock tracking. Tracked stock reserves on creation, aggregated across duplicate lines; unpaid cancellation releases each historical reservation once. Tracking cannot switch null/non-null while noncancelled orders reference the product. Manual stock count/price/active edits remain possible.
- Reports: inclusive range; sales/receivables/status counters use delivery date, collected uses each payment's actual timestamp converted using current device timezone, expenses use their date. Cancelled orders excluded. Cash flow = collected minus expenses (not profit).

## TDD execution log

Tests were written before their implementation, slice by slice. **Initial slices do not constitute verified strict RED/GREEN**: parent tooling was not ready; first execution returned `bash: line 1: tools/env.sh: No such file or directory` (exit 1). Continued test-first implementation under user's explicit tooling-blocked allowance, not a claim of test execution.

Once tooling became available, actual Gradle commands used `source tools/env.sh && ./gradlew testDebugUnitTest --tests ... --no-daemon --max-workers=1`:

- `docs-data-first-run.log`: compile failed in 4m 6s. Missing parallel DriveController/UI functions and DATA API still in progress; no tests executed.
- `docs-data-payment-red.log`: compile failed in 42s, primarily missing DriveController and transient UI compile errors. This is a blocked run, **not a behavioral RED**.
- `docs-data-expense-red.log`: compile failed in 33s; unresolved DriveController and `UiLedger.kt:52 'val' cannot be reassigned` / Int vs Long. Expense placeholder had been added after test-first test so it would fail behaviorally once compilation is unblocked. No tests executed in this run.

All saved logs referenced above are now under `docs/data-evidence/`. No toolchain installs, project Gradle/config edits, commits, or Word changes by DATA owner. Test dependencies were resolved using the existing project configuration.

### Executed verification and genuine behavioral RED/GREEN

1. Independent existing Kotlin compiler/JUnit4 execution: `docs-data-pure-green.log`, `OK (4 tests)`.
2. While DriveController still blocked the project compiler, a temporary Gradle init script resolved the existing `debugUnitTestRuntimeClasspath` (92 actual artifacts). `DataRunTests.py` compiled only owned sources using Kotlin 2.1.20, extracted the real AAR classes, and ran JUnit4/Robolectric28. No substitute models, databases, or fabricated results. `docs-data-isolated-suite.log`: `OK (17 tests)`.
3. **Model item validation RED:** test `invalidOrderItemCannotProducePlausibleMoney` first failed with `AssertionError: Expected invalid item rejection` (3 tests, 1 failure), `docs-data-model-red.log`. Added constructor quantity/price/description validation; `docs-data-model-green.log`: `OK (18 tests)` including SQLite/backup regressions.
4. **UTF-8 preservation RED:** `malformedUnicodeIsRejectedBeforeItCanBeLostInUtf8Backup` first failed with `AssertionError: Expected validation rejection` (10 tests, 1 failure), `docs-data-unicode-red.log`. Added well-formed surrogate-pair validation to all persisted text; accepts valid emoji, rejects unpaired surrogates that otherwise silently change during UTF-8 export. `docs-data-unicode-green.log`: `OK (19 tests)`.
5. Added verification-only real SQLite trigger injection for payment insertion failure and two-store-instance stale-balance protection. No implementation change was needed; `docs-data-final-isolated.log`: `OK (21 tests)`.
6. Once parallel app compilation completed, normal Gradle executed 21 DATA tests, but 16 failed **before test setup** in custom `TamalitosApplication.onCreate -> DriveRuntime.schedule -> WorkManager.getInstance`. `gradle-final.log` captures this application-startup dependency, not a DATA logic failure. Explicitly configured plain `android.app.Application` for DATA unit tests (manifest NONE alone is insufficient under Gradle's generated manifest properties). `gradle-data-green.log`: `BUILD SUCCESSFUL in 1m 36s`, XML counts 21 tests / 0 failures.
7. **Checked subtraction RED through normal Gradle:** new `DataModelsTest.subtractionOverflowIsRejectedInsteadOfWrapping` failed at line 35, 1 test / 1 failure; `gradle-subtract-red.log`. Added `Math.subtractExact` wrapper and applied it to balance/cash-flow, avoiding `-Long.MIN_VALUE` overflow. **Final normal Gradle GREEN:** `gradle-final-green.log`, `BUILD SUCCESSFUL in 1m 13s`; XML confirms **22 tests, 0 failures, 0 errors, 0 skipped**. Copies of each JUnit XML are saved here so future full-suite runs cannot overwrite DATA evidence.

Initial blocked slices were test-first but did not have an executed pre-implementation behavioral RED. Therefore this is **not a claim of fully verified strict TDD for every initial behavior**. Later slices above have real failing-before-change / passing-after-change evidence. No outputs were fabricated.

## Repeat the normal DATA test suite

```bash
source tools/env.sh
./gradlew testDebugUnitTest \
  --tests com.tamalitos.malitos.BusinessRulesTest \
  --tests com.tamalitos.malitos.DataModelsTest \
  --tests com.tamalitos.malitos.BusinessStoreTest \
  --tests com.tamalitos.malitos.BackupTest \
  --no-daemon --max-workers=1
```

Coverage: strict money parsing and overflow; all initial payment modes; derived totals, balances, labels; customer persistence/edit/search/delete restrictions; optional stock and inactive catalog; order snapshots and duplicate-line reservations; validation rollback; installments, overpayment refusal, delivery with debt; final/idempotent cancellation and overflow rollback; expenses/date ranges; report scopes/counters/cash flow; JSON round trips preserving IDs, relationships, historical stock reservations, status, payment timestamps and notes; invalid backup refusal; and actual SQLite-trigger-induced failures after destructive restore deletion and after stock reservation.

Only DATA tests were selected. All main and test Kotlin sources compiled in the final Gradle runs, but UI/Drive/application-startup integration tests were not run by this worker. Parent should verify application startup/WorkManager separately; the DATA unit tests deliberately do not initialize Drive scheduling. Remaining build warnings are deprecations in `MainActivity.kt` and `UiActivityTest.kt`, not DATA files.

### Optional isolated runner (only if parallel sources are temporarily incomplete)

```bash
source tools/env.sh
./gradlew --init-script app/src/test/java/com/tamalitos/malitos/DataClasspath.init.gradle \
  dataRuntimeClasspath --no-daemon --max-workers=1
python3 app/src/test/java/com/tamalitos/malitos/DataRunTests.py
# Or one owned class:
python3 app/src/test/java/com/tamalitos/malitos/DataRunTests.py BusinessStoreTest
```

These helpers do not edit the project build configuration. They resolve its real runtime artifacts and compile/run real owned source files; generated files live in `build/data-isolated/`. They are not an APK/integration test substitute.

## JSON backup v1

- Root exact fields: `format = "com.tamalitos.malitos.backup"`, integer `version = 1`, `currency = "MXN"`, nonnegative millisecond `exportedAt`, arrays `customers`, `products`, `orders`, `expenses`.
- Customer fields: `id,name,phone,address,notes`. Product: `id,name,priceCents,stock,active` (`stock` explicit JSON null or nonnegative Int; `active` actual boolean).
- Order: `id,customerId,customerName,deliveryDate,deliveryAddress,notes,status,items,payments`. Status exact enum name. Item: `productId,description,quantity,unitPriceCents,stockReserved`; productId explicit null for freeform, stockReserved historical boolean. Payment: `id,orderId,amountCents,timestamp,note`. Expense: `id,description,category,amountCents,date,notes`.
- Limits: 10 MiB UTF-8 and character cap before parsing, depth 16, 1,000,000 value tokens, 10,000 rows per root array, 1,000 items per order, 100,000 aggregate items plus payments. Names/item descriptions/expense descriptions/categories 200 UTF-16 code units; phone 100; address/notes 2,000. IDs positive and below Long.MAX_VALUE, money exact integer cents, quantities/stock Int-bounded. Money text parser caps numeric input at 24 characters.
- Reject missing/extra fields, unsupported format/version/currency, duplicate object keys/IDs, strings pretending to be numbers or booleans, fractional numbers, malformed/trailing/over-nested JSON, invalid calendar dates, broken references, overpaid/cancelled-paid orders, inconsistent active-order stock reservations, empty order items, checked-arithmetic overflow, NUL, malformed Unicode. Strict syntax guard is necessary because Android JSONTokener otherwise accepts non-JSON syntax.
- Import parses and validates the **entire snapshot before opening/changing SQLite**. Replacement, sequence reset, insertion, and foreign-key verification execute in one transaction. SQLite failures roll back deleted rows and sequences. Import sets snapshot stock directly rather than reserving it again. Valid explicit empty backups can replace all data; caller must obtain destructive-restore confirmation (UI/Drive responsibility).
- Export is one transactionally consistent multi-table snapshot; it applies the same validation/limits as import. JSON contains personal/business information and is not encrypted by this component.

## Intentional scope and limitations

- No refunds, credit/overpayments, order edits/deletions, customer-with-orders deletion, multi-device merge/sync, encryption, or seeded business data.
- SQLite schema is version 1; unsupported upgrades fail without deleting data. A future version requires an explicit migration.
- Aggregate report arithmetic raises meaningful validation on Long overflow rather than wrapping. Payment-date reporting uses the current device timezone; timezone changes may shift payments near midnight.
- Snapshot uses IDs plus historical customer/item names and historical tracked-stock flags, not a live catalog recalculation. Imported existing inactive products may remain referenced by historical orders.
- DATA implementation/tests are green. Parent remains responsible for the complete UI/Drive test suite, APK/device tests, and live Drive authorization/cloud verification.
