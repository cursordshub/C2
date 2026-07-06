# M2 STATE 2 — Mock/Test Blueprint (Test Infra Generation)

> Tests target the **real** M2 classes defined by STATE 1. JUnit5 `@Test` (discoverable).
> **Scaffolding (pre-implementation):** compiles (`compileTestJava` BUILD SUCCESSFUL) but fails at
> runtime until STATE 3 fills the stubs. NOT presented as MVP validation.

## Test files (4, high-value, hitting real classes)

| File | Real class(es) | Contract asserted |
|---|---|---|
| `MetadataLoaderTest` | `MetadataLoader` (+ real M1 `MetadataPipeline`/`JsonWriter`) | M1→M2 JSON seam round-trips `ClassMetadata` incl. constructor params |
| `SkeletonBuilderTest` | `SkeletonBuilder` + mock policy | mocks internal collaborators, REAL for String, primitives & enums NOT mocked, interface mocked, correct source constructs (G3) |
| `TestGenPipelineTest` | `TestGenPipeline` | placement: new→TARGET, existing→LOCAL_MIRROR (no overwrite), enum/interface→SKIPPED; report lists collisions + warns (G1) |
| `SkeletonCompileTest` | `SkeletonBuilder` + `javax.tools` compiler | generated skeleton **actually compiles** with sample sources + JUnit5/Mockito on classpath (G2) |

## Mapping to STATE 1 contracts
- §3 IO contracts: MetadataLoader (loader test), SkeletonBuilder (builder + compile tests), TestGenPipeline (pipeline test).
- §4 mock policy: SkeletonBuilderTest (primitive/String/enum/interface branches).
- §5 placement/non-overwrite: TestGenPipelineTest (both branches + skips + report).
- G2 (compilability): SkeletonCompileTest performs a real `getTask().call()`.

## Verified this stage
- `gradlew compileJava compileTestJava` → BUILD SUCCESSFUL. Contracts consistent + compilable.

STATE 2 complete. Proceeding to STATE 3 (implementation) without halting.
