# Run guide (work-in-progress)

> **Living doc while the app is being built.** When the toolchain is mature, fully tested, and
> functional, this will be superseded by a root **`README.md`** — a concise manual covering what
> the app does and how to use it end-to-end.

## TL;DR

- **Configure once:** set `target.path` in `src/main/resources/application.properties` to the Java project root. Optionally set `target.source.roots` / `target.test.roots` for non-Maven layouts (`src` + `test`).
- **M1 — metadata:** `./gradlew run` — **in:** configured source roots — **out:** `output/metadata/*.json`, `output/dependency-graph.json` (+ optional `output/dependency-graph.yml`).
- **M2 — test skeletons:** `./gradlew testgen` — **in:** M1 JSON from `output/metadata/` — **out:** new `*Test.java` under `<target>/<test-root>/` (default `src/test/java/`) or `output/tests-local-mirror/<test-root>/` if a test already exists + `output/testgen-report.md`.
- **M3 — LLM prompts:** `./gradlew promptgen` — **in:** M1 JSON, M2 test files, optional JaCoCo/PIT reports — **out:** `output/prompts/*.md` + `output/promptgen-report.md`.
- **Run in order:** M1 → M2 → M3. Windows: use `gradlew.bat` instead of `./gradlew`.

---

## Commands

| What | Command | Success output (typical) |
|---|---|---|
| **M1 — metadata scan** | `./gradlew run` | `[SUCCESS] scanned N classes … -> metadata/*.json + dependency-graph.json` |
| **M2 — test skeletons** | `./gradlew testgen` | `[SUCCESS] X test(s) into target, Y into local mirror … See report.` |
| **M3 — LLM prompts** | `./gradlew promptgen` | `[SUCCESS] X prompt(s) written, Y skipped. See report.` |
| **Unit tests** | `./gradlew test` | `BUILD SUCCESSFUL` |
| **Coverage** | `./gradlew jacocoTestReport` | report under `build/reports/jacoco/test/` |
| **Mutation testing** | `./gradlew pitest` | report under `build/reports/pitest/` |

**Order:** run **M1 first**, then **M2**, then **M3**. M2 reads M1 JSON; M3 reads M1 JSON + M2 skeletons + optional JaCoCo/PIT reports.

Windows: use `gradlew.bat` instead of `./gradlew`.

---

## Configure the target project

The **target** is the Java project you scan (M1) and write generated tests into (M2).

**Shared setting (recommended):** `target.path` in `src/main/resources/application.properties`

```properties
target.path=C:/path/to/your/project
# bundled sample for demos:
# target.path=sample-target
```

**Precedence**

| Milestone | Order (highest first) |
|---|---|
| **M1** (`gradlew run`) | `TARGET` in `metadata/Cli.java` → `target.path` → `--target=` CLI arg |
| **M2** (`gradlew testgen`) | `target.path` → `--target=` CLI arg |

If **nothing** is set, the app **fails fast** — it does **not** silently scan `sample-target`.

**CLI override (lowest precedence for M1):**

```bash
./gradlew run --args="--target=C:/path/to/project"
./gradlew testgen --args="--target=C:/path/to/project"
```

**IntelliJ:** run the `Cli` main class directly, or use Gradle tasks `run` / `testgen`. Set
`target.path` in properties, or paste a path into `TARGET` in `metadata/Cli.java` (M1 only).

---

## Where output goes

| Path | Produced by | Contents |
|---|---|---|
| `output/metadata/*.json` | M1 | Per-class metadata (keyed by FQN) |
| `output/dependency-graph.json` | M1 | Class-level dependency graph |
| `<target>/<test-root>/.../*Test.java` | M2 | New test skeletons (default test root: `src/test/java`) |
| `output/tests-local-mirror/<test-root>/...` | M2 | Skeletons for classes that **already have** a test — **never overwrites target** |
| `output/testgen-report.md` | M2 | What was created where + mock/stub decisions |
| `output/prompts/*.md` | M3 | Per-class chat-ready coverage prompts |
| `output/promptgen-report.md` | M3 | Prompts created, skipped classes, warnings |

`output.clean=true` (default) wipes `output/` **only when M1 actually runs** (target resolved and scan starts).

---

## Common errors (one-liners)

| Symptom | Cause | Fix |
|---|---|---|
| `No target configured…` | `TARGET`, `target.path`, and `--target=` all unset/blank | Set `target.path` in `application.properties` (or M1 `TARGET` / `--target=`) |
| `Metadata dir not found` / `No metadata JSON found` | M2 run before M1 | Run `./gradlew run` first |
| `Target is not a directory` | Bad path | Check `target.path` points at the project root |
| `No .java source files found under` | Empty/wrong target | Point at a project with sources under `target.source.roots` |
| Old `com.sample.*` (or other) files still in `output/` after a failed run | Fail-fast skips `output.clean` | Configure target and re-run M1 — or delete `output/` manually |
| Generated tests don't compile in **target** | Target lacks JUnit5/Mockito | Add test deps to the **target** project's build (not this tool's) |
| M1 and M2 scanned different projects | M1 used `TARGET` override, M2 did not | Use shared `target.path`; blank M1 `TARGET` unless you intend an override |

Exit code **0** = success, **1** = error (message on stderr).

---

## Config reference

All knobs live in `src/main/resources/application.properties`:

| Property | Default | Purpose |
|---|---|---|
| `target.path` | *(blank — must set)* | Target project (M1 + M2 + M3) |
| `target.source.roots` | `src/main/java` | Comma-separated production source roots under target |
| `target.test.roots` | `src/test/java` | Comma-separated test source roots under target |
| `target.test.file.suffixes` | `Test.java,Tests.java,IT.java` | Filename suffixes for collision detection |
| `output.dir` | `output` | M1 JSON output root |
| `output.clean` | `true` | Wipe `output.dir` before M1 write |
| `metadata.dir` | `output/metadata` | M1 JSON consumed by M2 |
| `testgen.mirror.dir` | `output/tests-local-mirror` | Local mirror for collision cases |
| `testgen.report` | `output/testgen-report.md` | M2 generation report |
| `promptgen.output.dir` | `output/prompts` | M3 prompt markdown files |
| `promptgen.report` | `output/promptgen-report.md` | M3 generation report |
| `promptgen.internal.packages` | `com.sample` | Package filter for SUT prompts |
| `promptgen.jacoco.report` | *(auto)* | Override JaCoCo XML path |
| `promptgen.pitest.report` | *(auto)* | Override PIT mutations.xml path |

---

## Quick smoke test (bundled sample)

Complex scenario matrix: **`docs/sample-target-scenarios.md`** (mock vs fixture vs static vs reflection).

```properties
# application.properties
target.path=sample-target
```

```bash
./gradlew run
./gradlew testgen
./gradlew promptgen
# inspect output/testgen-report.md and output/promptgen-report.md
```
