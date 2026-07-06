# M2 STATE 3 — Implementation (Test Infra Generation)

> Implemented the STATE 1 contracts to satisfy STATE 2 tests, then pressure-tested against real
> execution (Ethos 1a): compile → test → real-compile a generated skeleton → run the generator → inspect output.

## Files implemented (5 source files, package `com.toolchain.testgen`)

| # | File | Responsibility |
|---|---|---|
| 1 | `TestGenModel.java` | DTOs: `DepDecision`, `Skeleton`, `TestOutcome`, `Placement`, `Report` |
| 2 | `MetadataLoader.java` | Read M1 `output/metadata/*.json` → `ClassMetadata` (Jackson; reuses M1 record) |
| 3 | `SkeletonBuilder.java` | Mock policy (§4) + renders JUnit5/Mockito source; picks richest constructor |
| 4 | `TestGenPipeline.java` | Placement (target vs local mirror), non-overwrite, file writes, report |
| 5 | `Cli.java` | Config + `[Success, Data, Error]` boundary → exit 0/1; `gradlew testgen` |

Config added to `application.properties`: `metadata.dir`, `testgen.mirror.dir`, `testgen.report`.

## Defect found & fixed by execution (Ethos 1a — the falsification loop paid off)
- **M1 was scanning the target's `src/test` tree.** After adding a pre-existing `CustomerTest` to the
  sample target, M1 emitted metadata for `com.sample.CustomerTest`, and M2 then generated a
  `CustomerTestTest` (tests-for-tests). Observed via `gradlew run` reporting **6** classes instead of 5.
  **Fix:** `MetadataPipeline.isProductionSource()` skips `/src/test/` and `/test/java/` paths.
  Re-run confirmed 5 classes and no spurious `*TestTest`.

## Verification (observed system state)

| Check | Command | Result |
|---|---|---|
| Tests (10, real classes) | `gradlew test` | BUILD SUCCESSFUL |
| Generated skeleton compiles | `SkeletonCompileTest` (javax.tools + Mockito/JUnit on cp) | passes (G2) |
| M1→M2 chain | `gradlew run testgen` | `2 into target, 1 into local mirror (not overwritten), 2 skipped` |
| Mutation (widened to `com.toolchain.*`) | `gradlew pitest` | 197 mutations, 57% killed, 73% test strength |

### Observed generator output (correctness)
- `sample-target/.../OrderTest.java` → `@ExtendWith(MockitoExtension.class)`, `@Mock Customer`,
  `@Mock PricingService`, `new Order("", customer, pricingService)` — String real, collaborators mocked.
- `output/tests-local-mirror/.../CustomerTest.java` → collision path; **no** Mockito/`@ExtendWith`
  (both deps are `String` → real) — G6 ✓. The **original** target `CustomerTest` was left untouched (G1 ✓).
- `output/testgen-report.md` → three placement sections (incl. "DO NOT overwrite target") + per-class
  **mockability decisions** documenting what can/can't be mocked and why (owner requirements 4 & 6 ✓).
- Skipped: `PaymentStatus` (enum), `OrderRepository` (interface) with reasons.

## Falsification: predicted vs actual
- G1 no-overwrite → verified (mirror + untouched original). G2 compiles → verified (real javac).
- G3 no mocking of String/enum/primitive → verified (report + builder test). G4 default ctor → PricingService.
- G6 no `@ExtendWith` when zero mocks → verified (mirror CustomerTest).
- New: production-only scan (unpredicted break) found and fixed.

## Run instructions (IntelliJ end user)
1. Run M1 first: `gradlew run` (produces `output/metadata/*.json`).
2. Run M2: `gradlew testgen`.
   - New-test classes → written into `<target>/src/test/java/<pkg>/<Class>Test.java`.
   - Classes that already have a test → written into `output/tests-local-mirror/...` (never overwrites target).
   - Read `output/testgen-report.md` for the created-locally list + mockability rationale, then hand-edit.
3. Change target via `application.properties` `target.path`, `TARGET` constant in `testgen/Cli.java`, or `gradlew testgen --args="--target=C:/path"`.

## Residual (LEDGER)
- OPEN-1 (unresolved external types → assumed mockable) now also affects cross-package imports for
  external deps; internal cross-package deps are imported correctly.
- Records: generated with default construction only (canonical-constructor components not modelled by M1).
- Generated tests require the target to have JUnit5 + Mockito on its own classpath (per M2 spec).

STATE 3 complete. `gradlew test` green; `gradlew testgen` produces correct target/mirror split + report.
