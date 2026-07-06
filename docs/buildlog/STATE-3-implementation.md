# STATE 3 — Implementation (M1)

> Gating pipeline STATE 3 output. Implemented the STATE 1 contracts to satisfy the STATE 2 tests,
> then pressure-tested against **real execution** (Ethos 1a): compile → test → run → mutate.

## Files implemented (5 source files, one package `com.toolchain.metadata` — toolchainLite §4)

| # | File | Responsibility |
|---|---|---|
| 1 | `Model.java` | All records + the `[Success, Data, Error]` `Result` boundary contract |
| 2 | `SourceScanner.java` | JavaParser core AST → `ClassMetadata` (kind, static-init, deps, methods, fields, ctors) |
| 3 | `MetadataPipeline.java` | Walk `.java`, merge by FQN, build internal dependency graph (edges + in/out degree) |
| 4 | `JsonWriter.java` | Jackson per-FQN JSON + `dependency-graph.json`; bounded `output.clean` |
| 5 | `Cli.java` | Config resolution + single boundary wrap → exit 0/1 |

Plus: `application.properties` (config), `sample-target/` (bundled sample), `build.gradle` (slimmed: JavaParser **core** + Jackson; JaCoCo + PIT retained).

## Verification (observed system state — the evidence layer, EthosV2 2b)

| Check | Command | Result |
|---|---|---|
| Compile | `gradlew compileJava compileTestJava` | BUILD SUCCESSFUL |
| Tests (real classes) | `gradlew test` | BUILD SUCCESSFUL — 6/6 green |
| Run end-to-end | `gradlew run` | `[SUCCESS] scanned 5 classes, 5 internal edges`; exit 0 |
| JaCoCo XML | `jacocoTestReport` | `build/reports/jacoco/test/jacocoTestReport.xml` present |
| PIT mutation | `gradlew pitest` | 86 mutations, 53 killed (62%), 83% line coverage on mutated classes |

### Observed output (correctness spot-checks)
- `com.sample.PricingService.json` → `"hasStaticInitializer": true` (F6 ✓), enum/interface kinds correct (F7 ✓).
- `com.sample.Order.json` → constructor `[String, Customer, PricingService]`, 4 methods, 4 fields (F3 ✓).
- `dependency-graph.json` → internal edges only: `Order→{Customer,PaymentStatus,PricingService}`,
  `OrderRepository→Order`, `PricingService→Customer`; `String` listed as a dependency but forms **no edge**
  (external type, not scanned — F5/OPEN-1 ✓).

## Falsification: predicted vs actual (Ethos 1a)
- F1 (missing target throws, not silent) — asserted by `MetadataPipelineTest.emptyOrMissingTargetFailsLoudly` ✓
- F4 (bounded clean) — delete restricted to under `output.dir`; verified writer test reruns cleanly ✓
- Boundary contract both branches (Success/Error) — `CliAndWriterTest` ✓
- No mismatch between predicted edge cases and actual behavior.

## Run instructions (IntelliJ end user)
1. Default (bundled sample): run `gradlew run` → writes `output/metadata/*.json` + `output/dependency-graph.json`.
2. Scan another project — pick one:
   - Edit `target.path` in `src/main/resources/application.properties`, or
   - Set the `TARGET` constant in `Cli.java` (copy-paste knob), or
   - `gradlew run --args="--target=C:/path/to/project"`.
3. Exit code 0 = success, 1 = error (message on stderr).

## Residual (non-blocking, tracked in LEDGER.md)
- OPEN-1..4 deferred by design (symbol solving, shallow generics, class-level graph, ASM/binary).
- Mutation survivors (~38%) are acceptable for an MVP; downstream M3 coverage prompts target these.

STATE 3 complete. `gradlew test` and `gradlew run` both green.
