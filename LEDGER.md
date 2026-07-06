# Falsification Ledger — M1 (lite)

Running record of vulnerabilities predicted, mitigated, and left open. For the next agent/session.

## Mitigated (verified by real execution in STATE 3)
- F1 missing/empty target throws → exit 1 (test: `emptyOrMissingTargetFailsLoudly`).
- F3 multiple/nested top-level types → scanner returns a list (test: Order arity).
- F4 `output.clean` bounded delete strictly under `output.dir` (writer reruns cleanly).
- F5 ambiguous simple name → no edge (`String` dep forms no graph edge).
- F6 static `{ }` block detected via `InitializerDeclaration.isStatic()` (PricingService JSON).
- F7 class kind resolution enum/interface/abstract/record/annotation (observed JSON).
- F8 JUnit5 `@Test` used → discovered by `gradlew test` (6/6 green).
- Boundary `[Success, Data, Error]` both branches (test: `cliBoundary...`).

## M2 — Mitigated (fixture generation, verified by execution)
- M2 now emits **full infra**, not wiring-only: real fixtures, reflection stubs, lenient mock stubs, MockedStatic helper, parameterized scaffolds.
- Interface method stubs were skipped when M1 recorded them as `package-private` — fixed (stub all non-private methods).
- `OrderContext` constructor fixture uses realistic values (`"US", "USD", 2, 100, false, "WEB"`), not empty strings.
- `LegacyAccount` gets `stubLegacyAccountHiddenState()` (package-private field + reflection on `internalCreditScore`).
- 13/13 tests green; `gradlew run testgen` succeeds against `sample-target`.
- G1 never overwrites an existing target test → colliding class emitted to local mirror; original untouched
  (test: `TestGenPipelineTest`; live run: `CustomerTest` → `output/tests-local-mirror`).
- G2 generated skeleton actually compiles with JUnit5+Mockito on classpath (test: `SkeletonCompileTest`, real `javac`).
- G3 primitives / `String` / enum / record are NOT mocked → real values (mock policy; report documents it).
- G4 class with no explicit constructor → default construction, zero mocks (`PricingService`).
- G6 `@ExtendWith(MockitoExtension)` + mockito imports only when ≥1 mock (mirror `CustomerTest` has none).
- FIXED (found by execution): M1 was scanning the target `src/test` tree → emitted metadata for test
  classes → M2 generated `*TestTest`. Fix: `MetadataPipeline.isProductionSource()` excludes test sources.

## M2 — Open
- OPEN-5: unresolved external ctor types are ASSUMED mockable and left unimported → may not compile in
  target for 3rd-party non-value types until the user adds the import (ties to OPEN-1, no symbol solver).
- OPEN-6: records generated with default construction only (canonical-constructor components not modelled).
- OPEN-7: collision detection is filename-heuristic (`<Class>Test/Tests/IT.java`); unconventional test
  names could evade it. Acceptable for MVP; report makes placement explicit for human review.

## Open (deferred by design — toolchainLite §5)
- OPEN-1: No symbol solving. Unqualified 3rd-party type names stay simple; internal edges only.
- OPEN-2: Generics unified only shallowly (outer type of `List<Foo>` -> `List`, arg `Foo` captured).
- OPEN-3: Graph edges are class-level, not method-level.
- OPEN-4: ASM/binary (`.class`/jar) scanning deferred; source-only core.

## M3 — Mitigated (verified by execution)
- Parses JaCoCo XML (with DTD) and PIT `mutations.xml`; parse failures degrade to warnings, not hard fail.
- Falls back to M2 `_m3Coverage` scaffold scan when target has no coverage reports (`sample-target` path).
- Package filter via `promptgen.internal.packages` (defaults to `dependency-graph.internal.packages`).
- Report path resolution: explicit config → target `build/reports/` → toolchain `build/reports/`.
- 26/26 tests green; `gradlew promptgen` writes prompts for `CheckoutService` + `FulfillmentCoordinator` scaffolds.

## M3 — Open
- OPEN-8: Target-project JaCoCo/PIT requires the target to run its own `./gradlew test jacocoTestReport pitest` — toolchain reports only cover `com.toolchain.*` unless paths overridden.
- OPEN-9: Prompts include full test skeleton; very large tests may need truncation in a future token-budget pass.

## Notes
- `[Success, Data, Error]` contract enforced only at the `Cli` boundary; modules throw internally.
