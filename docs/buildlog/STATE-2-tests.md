# STATE 2 — Mock/Test Blueprint (M1)

> Gating pipeline STATE 2 output. Tests are written against the **real** production classes
> defined by the STATE 1 contracts. Per `ImplementationGuide.md`: every test method is JUnit5
> `@Test` (a hand-rolled harness with no annotations is not discovered by `gradlew test`).

## Status of these tests
**Scaffolding (pre-implementation).** They reference real classes (`SourceScanner`,
`MetadataPipeline`, `JsonWriter`, `Cli`) whose bodies are `UnsupportedOperationException` stubs
in STATE 2. They **compile** (`compileTestJava` BUILD SUCCESSFUL) but will **fail at runtime**
until STATE 3 implements the logic. They are NOT presented as MVP validation — that happens in STATE 3.

## Test files (3, high-value, hitting real classes — toolchainLite §3)

| File | Real class under test | Contract asserted |
|---|---|---|
| `SourceScannerTest` | `SourceScanner.scan(Path)` | FQN, ClassType (CLASS/INTERFACE/ENUM), constructor arity, dependencies, method presence, static-init flag (F3, F6, F7) |
| `MetadataPipelineTest` | `MetadataPipeline.run(Path)` | `byFqn` population, internal graph edges (Order→Customer, Order→PricingService), outdegree, missing-target throws (F1, F5) |
| `CliAndWriterTest` | `JsonWriter.write(...)`, `Cli.execute(...)` | per-FQN JSON + `dependency-graph.json` on disk; `[Success,Data,Error]` boundary: valid target → Success, bad target → Error (F4 via bounded write, boundary mapping) |

## Coverage of the STATE 1 contracts
- `SourceScanner` IO contract → SourceScannerTest.
- `MetadataPipeline` IO contract + graph derivation (§4) → MetadataPipelineTest.
- `JsonWriter` on-disk output contract (§3.4) → CliAndWriterTest.
- `Cli` `[Success, Data, Error]` boundary (§3.1) → CliAndWriterTest (both branches).

## Verified this stage
- `gradlew compileJava compileTestJava` → BUILD SUCCESSFUL. Contracts are consistent and compilable.

STATE 2 complete. Proceeding to STATE 3 (implementation) without halting, per owner instruction.
