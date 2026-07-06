# Toolchain Lite — Lessons & Lightweight Proposal

> Distilled from the M1 MVP build log (`docs/buildlog/STATE-1..3`) and milestone summaries.
> Goal: same purpose and functions as the full build, with **fewer files and fewer lines**.
> Hard rule (owner recommendation): **≤ 5 source files per milestone**.
> The lite version will be implemented and tested in a separate project.

---

## 1. What M1 actually cost

| Metric | M1 MVP (full) | Lite target |
|---|---|---|
| Main source files | **19** (10 in `model/` alone) | **≤ 5** |
| Packages | 8 (`model, config, scanner, merge, graph, output, pipeline, cli`) | 1 per milestone |
| Runtime deps | javaparser-**symbol-solver**, asm, jackson | javaparser-**core**, jackson (asm optional) |
| Test files | 10 (9 + support) | 2–3 high-value |

Functionally M1 is complete and correct (23 tests green). The weight is in **structure**, not behaviour.

---

## 2. Lessons learned (M1)

- **Over-fragmented model.** 10 one-record files added navigation cost with zero behaviour. → Collapse all DTOs into **one** file (nested records).
- **Symbol solver = high cost, low MVP payoff.** Without the target's classpath, `resolve()` mostly throws and we degrade to `unresolved:*` anyway (ledger OPEN-1/V1). → Drop `symbol-solver-core`; read type names straight from the AST. Optionally re-resolve later.
- **`Result<T>` everywhere = ceremony.** Every module returned `Result`, doubling branch code. → Throw a single checked/unchecked failure internally; wrap **once** at the CLI boundary (still deterministic exit 0/1).
- **One-file-per-tiny-class.** merge/graph/writer/pipeline were ~20 lines each in separate packages. → Fold orchestration + merge + graph + write into **one** pipeline file.
- **ASM added a whole extra file + dep** for a rarely-used binary path in the demo. → Make binary scanning **optional/deferred**; source-only core.
- **Structural rules (15-line/3-param) forced good decomposition** but multiplied tiny methods. → Keep the *spirit* (small functions), relax the hard caps for the lite build.
- **What worked — keep it:** records as DTOs, hand-rolled graph (no JGraphT), `output.clean` bounded delete, `application.properties` + `--target`, gating (STATE 1→2→3) as a *process* (not more files), Falsification-First ledger.

---

## 3. Lite design principles

1. One package per milestone: `com.toolchain.<milestone>` (`metadata`, `testgen`, `promptgen`).
2. ≤ 5 source files/milestone; one holds all data records.
3. Prefer exceptions + a single boundary catch over a `Result` wrapper threaded through every call.
4. Fewest deps that still deliver the function; heavy resolution/graph libs stay out.
5. Communicate between milestones via the **JSON artifact** only (already the design seam).
6. Keep the JaCoCo + PIT + runnable-entry-point + configurable-target guardrails.

---

## 4. Proposed lite structure (≤ 5 files per milestone)

### M1 — Metadata Extraction  (`com.toolchain.metadata`)
1. `Model.java` — all records (`ClassMetadata, MethodInfo, FieldInfo, ConstructorInfo, ClassType, DependencyGraph`) in one file.
2. `SourceScanner.java` — JavaParser AST → metadata (+ inline binary/ASM *only if* enabled).
3. `MetadataPipeline.java` — walk, merge (source wins), build graph, orchestrate.
4. `JsonWriter.java` — Jackson per-FQN JSON + graph + `output.clean`.
5. `Cli.java` — load `application.properties` / `--target`, run, exit 0/1.

### M2 — Test Infra Generation  (`com.toolchain.testgen`)
1. `Model.java` — generation DTOs (or reuse M1's `Model` as shared contract).
2. `MetadataLoader.java` — read M1 JSON via Jackson.
3. `SkeletonBuilder.java` — JavaPoet TypeSpec: `@Mock` fields, lifecycle, import partitioning.
4. `TestWriter.java` — emit `.java` skeletons to `output/tests/`.
5. `Cli.java` — entry point.

### M3 — LLM Prompt Automation  (`com.toolchain.promptgen`)
1. `Model.java` — prompt/coverage DTOs.
2. `CoverageMapper.java` — parse JaCoCo/PIT XML → uncovered targets per FQN.
3. `PromptBuilder.java` — combine metadata + skeleton + gaps into templated prompt.
4. `PromptWriter.java` — write `output/prompts/*.md`.
5. `Cli.java` — entry point.

---

## 5. Keep / Cut / Defer

- **Keep:** records DTOs, hand-rolled graph, JSON seam, `output.clean`, config knob, gating process, JaCoCo+PIT.
- **Cut:** `symbol-solver-core`, `Result<T>` plumbing, per-class model files, separate merge/graph/pipeline packages.
- **Defer (flag, don't build):** classpath resolution (OPEN-1), generics unification (OPEN-2), method-level graph edges (OPEN-3), fat-jar packaging.

---

## 6. Trade-offs (Falsification First)

- **Dropping symbol solver** → richer FQNs for 3rd-party types are lost; acceptable since MVP already degraded them. Re-add behind a flag if resolution becomes required.
- **Exceptions over `Result`** → fewer lines, but discipline needed so failures surface as clean CLI exit codes (one boundary catch). Test that path explicitly.
- **Fatter files** → each file does more; mitigate with clear sections and 2–3 focused tests per milestone rather than per-class tests.

---

## 7. Acceptance criteria for the lite build

- Each milestone: ≤ 5 source files, runnable entry point, configurable target, JaCoCo XML + PIT wired.
- M1 produces the **same** `output/metadata/{FQN}.json` + `dependency-graph.json` for the sample target.
- Green `gradlew test` (2–3 tests/milestone hitting real classes) and a working `gradlew run`.
- Milestones chained only through JSON artifacts.
