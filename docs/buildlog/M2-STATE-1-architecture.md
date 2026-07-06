# M2 STATE 1 — Architecture & Boundaries (Test Infra Generation)

> Gating STATE 1 for **M2 only**, MVP. Consumes M1's JSON artifact (the milestone seam).
> Design authority: `docs/lite/toolchainLite.md` §4 (M2) — ≤5 source files, one package
> `com.toolchain.testgen`, reuse M1 `Model` as the input contract, string-based skeletons (no
> heavy codegen dep for MVP), exceptions internally + one `[Success, Data, Error]` boundary wrap at CLI.

---

## 1. Feature analysis (completion criteria from owner)

1. Generate a JUnit5/Mockito **test skeleton per class**, mirroring the source package.
2. **No-existing-test** class → write directly into the **target** `src/test/java/<pkg>/<Class>Test.java`.
3. **Existing-test** class → **do not overwrite**; write to a **local mirror** subfolder that mirrors the
   target's `src/test/java` structure, for manual editing.
4. Produce a **report/list** of what was created locally (collisions) vs in the target, plus **skips**.
5. Use metadata to build **fixtures/mocks/stubs**: declare the SUT's dependencies in the test, then
   decide per dependency whether it is a **mock / real value / cannot-mock**, and wire construction.
6. **Document** what can and cannot be mocked (in the report).

---

## 2. Directory structure (≤5 source files, package `com.toolchain.testgen`)

```
src/main/java/com/toolchain/testgen/
  TestGenModel.java     (1) M2 DTOs: DepDecision, Skeleton, TestOutcome, Placement, Report
  MetadataLoader.java   (2) read M1 output/metadata/*.json (Jackson) -> List<Model.ClassMetadata>
  SkeletonBuilder.java  (3) mock policy + build the test-source String from ClassMetadata
  TestGenPipeline.java  (4) orchestrate: placement decision, write files, build report (folds writer)
  Cli.java              (5) config + [Success, Data, Error] boundary -> exit 0/1
```
Input contract reused: `com.toolchain.metadata.Model.ClassMetadata` (no duplicate DTOs — JSON seam).
Run task: `gradlew testgen` (separate from M1's `gradlew run`).

---

## 3. Data / IO contracts — deterministic, throw-internally + wrap-once

| Module | Input | Output (Data) | Error mode |
|---|---|---|---|
| `MetadataLoader.load(Path metadataDir)` | dir of per-FQN JSON | `List<ClassMetadata>` | throws if dir missing/empty |
| `SkeletonBuilder.build(ClassMetadata, Map<String,ClassMetadata>)` | one SUT + index | `Skeleton{source, deps}` | pure; no IO |
| `TestGenPipeline.run(Path target, Path mirror, List<ClassMetadata>)` | target + mirror + metadata | `Report{outcomes[]}` (+ files on disk) | throws on IO failure |
| `Cli.execute(String[])` | args/props | side-effects + `Result` | catches all -> exit 0/1 |

DTOs:
```
enum Placement { TARGET, LOCAL_MIRROR, SKIPPED }
record DepDecision(String name, String type, boolean mock, String valueExpr, String reason)
record Skeleton(String source, List<DepDecision> deps)
record TestOutcome(String sutFqn, String testClass, String relPath, Placement placement, String note, List<DepDecision> deps)
record Report(List<TestOutcome> outcomes)
```

---

## 4. Mock policy — "what can / cannot be mocked" (the core M2 decision, documented)

Injection point = the SUT constructor with the **most parameters** (richest fixture). For each param
type (generics stripped to the outer raw type), decide using **M1 metadata** first, then type heuristics:

| Param type resolves to… | Decision | Value wired | Reason |
|---|---|---|---|
| primitive (`int,long,double,float,boolean,char,byte,short`) | REAL | typed default (`0`,`0L`,`0.0`,`false`,…) | primitive — not mockable |
| internal `INTERFACE` / `CLASS` / `ABSTRACT` (in M1 metadata) | **MOCK** | `@Mock` field ref | mockable collaborator (Mockito) |
| internal `ENUM` | REAL | `null` + TODO | enum is final — cannot mock; supply a real constant |
| internal `RECORD` | REAL | `null` + TODO | record is final — cannot mock; construct a real value |
| known final JDK value type (`String`) | REAL | `""` | final value type — not mockable |
| JDK wrapper (`Integer,Long,Double,Boolean,…`) | REAL | default | value type — not mockable |
| external / unresolved type | MOCK (flagged) | `@Mock` field ref | unresolved (OPEN-1): assumed mockable — **verify** |

**SUT-level generation gate** (which classes get a test at all):
- `CLASS`, `RECORD` → generate.
- `INTERFACE` → SKIP (no implementation to instantiate).
- `ABSTRACT` → SKIP (cannot instantiate directly).
- `ENUM`, `ANNOTATION` → SKIP (no constructor collaborators to scaffold).

Rationale is emitted per class in the report so the human sees exactly why each decision was made.

---

## 5. Placement / non-overwrite logic (owner requirement 2–4)

- Target test path = `<target>/src/test/java/<pkg-as-dirs>/<Class>Test.java`.
- **Existing-test detection** (heuristic, MVP): a file named `<Class>Test.java` OR `<Class>Tests.java`
  OR `<Class>IT.java` exists anywhere under `<target>/src/test`. If found → **collision**.
- Collision → write to `<mirror>/src/test/java/<pkg>/<Class>Test.java`; outcome `LOCAL_MIRROR`.
- No collision → write to target path; outcome `TARGET`.
- The report lists all `LOCAL_MIRROR` entries under a prominent "created locally — DO NOT overwrite target"
  section, plus `TARGET` and `SKIPPED` sections.

---

## 6. Falsification-first (predicted breaks — verify in STATE 3)

| # | Predicted break | Mitigation |
|---|---|---|
| G1 | Overwrites an existing target test | collision detection → local mirror; report flags it (sample `CustomerTest` proves it) |
| G2 | Generated skeleton doesn't compile | STATE 2 real-compiles a skeleton with `javax.tools` + Mockito/JUnit on classpath |
| G3 | Mocking a final/value type (String/enum/record) → Mockito fails | policy forces REAL for primitives/String/enum/record |
| G4 | Class with no explicit constructor | default `new Sut()`, zero mocks |
| G5 | Cross-package dependency needs an import | add import from internal dep FQN; external unresolved flagged (OPEN-1) |
| G6 | `@ExtendWith(MockitoExtension)` with zero mocks → unused-import/strictness noise | annotation + mockito imports emitted only when ≥1 mock |
| G7 | Mirror/target path traversal | paths derived strictly from FQN under a fixed root |

STATE 1 complete. Proceeding to STATE 2 (tests) without halting.
