# STATE 1 — Architecture & Boundaries (M1: Metadata Extraction)

> Gating pipeline STATE 1 output. Scope: **M1 only**, MVP, source-only.
> Design authority: `docs/lite/toolchainLite.md` (≤5 source files, drop symbol-solver, defer ASM,
> exceptions internally + one boundary wrap, hand-rolled graph, JSON seam, JaCoCo+PIT, configurable target).

---

## 1. Feature analysis

Parse a target Java project's **source** and emit, per class (keyed by FQN):
dependencies, method signatures (name, return type, params, access), field access
(name, type, access), constructors (params, access), static-initializer flag, and class kind
(class / interface / abstract / enum / record / annotation). Plus a project dependency graph
(nodes + directed edges + in/out degree) for downstream prioritisation.

**Deliberately deferred (flagged, not built) — from toolchainLite §5:**
- Symbol solving / cross-module classpath resolution (OPEN-1). Type names are read verbatim from
  the AST; unqualified 3rd-party types stay as simple names. Acceptable for MVP.
- ASM binary/`.class` scanning (source-only core).
- Generics unification (OPEN-2), method-level graph edges (OPEN-3), fat-jar packaging.

---

## 2. Directory structure (Separation of Concerns, ≤5 source files)

Package: `com.toolchain.metadata`

```
src/main/java/com/toolchain/metadata/
  Model.java             (1) all data records (DTOs) + the boundary Result contract
  SourceScanner.java     (2) one .java file -> ClassMetadata (JavaParser core AST)
  MetadataPipeline.java  (3) walk source roots, scan, build graph, orchestrate
  JsonWriter.java        (4) Jackson: per-FQN JSON + dependency-graph.json + bounded output.clean
  Cli.java               (5) entry point: load config / --target / TARGET const, run, Result -> exit 0/1
src/main/resources/
  application.properties     target.path, output.dir, output.clean
sample-target/                 bundled sample Java project so `gradlew run` works out of the box
```

Each file has one reason to change: **shape** (Model), **parse** (SourceScanner),
**orchestrate+graph** (MetadataPipeline), **serialize** (JsonWriter), **boundary/IO/config** (Cli).

---

## 3. Data / IO contracts — deterministic `[Success, Data, Error]`

Internally, modules **throw** (no `Result<T>` threaded through every call — toolchainLite lesson).
The `[Success, Data, Error]` schema is enforced at exactly **one** boundary: `Cli`.

### 3.1 Boundary contract (`Model.Result`)
```
Result {
  boolean success;     // Success flag
  ExtractionResult data;   // present iff success == true, else null
  String error;        // present iff success == false, else null
}
```
Deterministic mapping at the CLI boundary:
- happy path  -> Result(success=true,  data=<result>, error=null)          -> exit 0
- any throwable -> Result(success=false, data=null,     error=<message>)   -> exit 1

### 3.2 Module IO contracts (throw-on-failure)

| Module | Input | Output (Data) | Error mode |
|---|---|---|---|
| `SourceScanner.scan(Path javaFile)` | one `.java` path | `List<ClassMetadata>` (top-level + nested) | throws on parse/IO failure |
| `MetadataPipeline.run(Path targetRoot)` | target project dir | `ExtractionResult` (metadata + graph) | throws if no source root / no `.java` |
| `JsonWriter.write(ExtractionResult, Path outDir, boolean clean)` | result + out dir | writes files, returns `List<Path>` written | throws on IO failure |
| `Cli.main(String[])` | args (`--target=`, `--out=`) | side-effect: files + stdout summary | catches all -> `Result` -> exit code |

### 3.3 Data record schema (`Model`)
```
ClassMetadata { String fqn; ClassType classType; boolean hasStaticInitializer;
                List<String> dependencies; List<MethodInfo> methods;
                List<FieldInfo> fields; List<ConstructorInfo> constructors; }
MethodInfo    { String name; String returnType; List<String> parameterTypes; String access; }
FieldInfo     { String name; String type; String access; }
ConstructorInfo { List<String> parameterTypes; String access; }
ClassType     enum { CLASS, INTERFACE, ABSTRACT, ENUM, RECORD, ANNOTATION }
DependencyGraph { List<String> nodes; List<Edge> edges; Map<String,Integer> outDegree; Map<String,Integer> inDegree; }
Edge          { String from; String to; }
ExtractionResult { Map<String,ClassMetadata> byFqn; DependencyGraph graph; }
Result        { boolean success; ExtractionResult data; String error; }
```

### 3.4 On-disk output contract
```
output/
  metadata/<FQN>.json          one file per class, e.g. com.sample.Order.json
  dependency-graph.json        { nodes, edges, outDegree, inDegree }
```
`output.clean=true` deletes only inside `output.dir` (bounded delete) before writing.

---

## 4. Dependency edge derivation (source-only, no symbol solver)

- **Node** = every scanned FQN.
- **Candidate references** for a class = types appearing in: `extends`/`implements`, field types,
  method return types, method/constructor parameter types (generic type args unwrapped shallowly).
- **Edge A -> B** is emitted **only when B resolves to a scanned FQN** (matched by simple name against
  the scanned set). External/unresolved types are recorded in `dependencies[]` but do **not** create
  edges (keeps the graph internal + deterministic). Documented trade-off (OPEN-1).

---

## 5. Run contract (IntelliJ-friendly)

- Config precedence (highest first): `TARGET` constant in `Cli.java` (copy-paste knob)  >  `application.properties` `target.path`  >  `--target=` CLI arg. If none are set the app **fails fast** (no silent `sample-target` default). A tier is used only when non-blank.
- `output.clean` is applied **before** scanning (in `Cli.run`), so a failed/misconfigured run leaves no misleading stale output.
- Command: `gradlew run` (uses config default `sample-target`) or `gradlew run --args="--target=C:/path/to/project"`.
- Expected stdout: `[SUCCESS] scanned N classes -> output/metadata (+ dependency-graph.json)`, exit 0.
  On failure: `[ERROR] <message>`, exit 1.

---

## 6. Falsification-first (edge cases predicted BEFORE code — verify in STATE 3)

| # | Predicted break | Mitigation |
|---|---|---|
| F1 | Empty target / no `.java` files | pipeline throws explicit error -> exit 1 (not silent success) |
| F2 | FQN contains no package (default package) | key by simple name; JSON filename = FQN as-is |
| F3 | Nested / multiple top-level types in one file | scanner returns a list, not a single metadata |
| F4 | `output.clean` path traversal / deleting wrong dir | bounded delete strictly under resolved `output.dir` |
| F5 | Same simple name in two packages -> ambiguous edge | edge only if unique scanned FQN match; else dependency-only |
| F6 | Static initializer block vs static field init | detect `InitializerDeclaration.isStatic()` specifically |
| F7 | interface/enum/record/abstract misclassified | explicit ClassType resolution order (annotation>interface>enum>record>abstract>class) |
| F8 | Hand-rolled test harness not discovered by `gradlew test` | STATE 2 uses JUnit5 `@Test` (ImplementationGuide rule) |

STATE 1 complete. Proceeding to STATE 2 (tests) without halting, per owner instruction.
