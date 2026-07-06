# Sample target — testing scenario matrix

Bundled under `sample-target/` to exercise M1 metadata extraction and M2 test-infra generation.
**Mocking framework: Mockito only** (including `MockedStatic` for static methods). **Never PowerMock.**

## Primary SUTs (100+ LOC each)

| Class | Lines | Out-degree | Highlights |
|---|---|---|---|
| `CheckoutService` | 100+ | 9 ctor deps | `&&` `\|\|` `>=`, private/helper methods, calls deps 2×+, `@ParameterizedTest` candidate (`calculateShippingFee`) |
| `FulfillmentCoordinator` | 100+ | 7 ctor deps | Same patterns; `estimateFulfillmentCost` parameterized candidate |

## Dependency scenarios

| Class | Role | Test approach |
|---|---|---|
| `TaxCalculator`, `InventoryStore`, `PaymentGateway`, … (interfaces) | **Mockable** | `@Mock` + `MockitoExtension` |
| `OrderContext` | **Constructor fixture** (6 fields, no getters) | `new OrderContext(...)` real instance — not `@Mock` |
| `LegacyAccount` | **Reflection / package access** | Hidden fields (`internalCreditScore`, `legacyToken`) — stub via reflection or same-package |
| `AbstractPricingRule` | **Cannot instantiate** | Created inside `PricingEngine`; use concrete rule or factory in tests |
| `PricingEngine` | **Complex dependency** (100+ LOC, out-degree 3+) | Mock or real; calls `ProductCatalog` / `CurrencyConverter` repeatedly |
| `IdGenerator` | **Static methods** | `try (MockedStatic<IdGenerator> ms = mockStatic(IdGenerator.class)) { ... }` |
| `WarehouseAllocator` | **Not injectable** (`new` inside SUT) | Integration-style test or refactor for seam — `@Mock` cannot replace it |
| `AllocationResult` | **Internal value** | Created by `WarehouseAllocator`; not a ctor dependency |
| `Customer`, `PricingService` | Simple legacy classes | Baseline mock/real examples |

## Non-injectable mock cases (2+)

1. **`WarehouseAllocator`** — constructed inside `CheckoutService.completeCheckout` / `FulfillmentCoordinator.scheduleShipment`.
2. **`IdGenerator.nextOrderId()` / `nextTransactionId()`** — static calls; use `MockedStatic`, not constructor injection.

## Parameterized test candidates

- `CheckoutService.calculateShippingFee(weight, distance, priority)` — branches on `>=`, `&&`, `||`.
- `FulfillmentCoordinator.estimateFulfillmentCost(sku, quantity, priority)` — similar branching.

Suggested:

```java
@ParameterizedTest
@CsvSource({ "1.0, 10, STANDARD, 12.00", "25.0, 500, OVERNIGHT, 45.00" })
void shippingFeeCases(double weight, int distance, ShippingPriority priority, BigDecimal expected) { ... }
```

## M2 output — what gets generated (not just wiring)

M2 front-loads test infra so **M3 only writes SUT coverage assertions**:

| Generated piece | Example |
|---|---|
| `@Mock` fields | `TaxCalculator`, `InventoryStore`, … |
| **Real fixtures** | `OrderContext` via constructor; `LegacyAccount` with reflection stubs |
| **`stubCollaborators()`** | `lenient().when(inventoryStore.reserve(...)).thenReturn(true)` for every mock method |
| **`MockedStatic` helper** | `withStaticIds(...)` for `IdGenerator` (Mockito inline, not PowerMock) |
| **Parameterized scaffold** | `@ParameterizedTest @CsvSource` for `calculateShippingFee` / `estimateFulfillmentCost` |
| **Static-path scaffold** | `completeCheckout_m3Coverage()` wrapped in `withStaticIds` |

Target project test classpath needs: **JUnit5 + junit-jupiter-params + Mockito**.

```properties
target.path=sample-target
```

```bash
./gradlew run
./gradlew testgen
# inspect output/testgen-report.md and generated *Test.java skeleton comments
```
