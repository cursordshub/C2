
# Milestone
M1 – Metadata Extraction: Parse Java (src/binary) for dependencies, method signatures, fields, access levels, constructors, static initializers, class type.

# Problem Statement
Milestone 1 – Metadata Extraction Large Java codebases contain hundreds/thousands of classes with unknown dependency structures, making manual test-fixture creation slow and error-prone. Developers need a static/binary analysis tool that extracts dependency graphs, method signatures, field access, constructors, and static initializers per class, enabling automated identification of testable units without prior codebase familiarity.

# Requirements analysis
M1 – Metadata Extraction: Requirements
• Parse .java source (unresolved/legacy code) and .class binaries (dependencies without source).
• Resolve symbols/types across modules; build dependency graph (outdegree/indegree) per class.
• Extract: methods called (signature, return type, params, access modifier), fields accessed (type, access modifier), constructors (params, access), static initializers, class kind (abstract/interface/concrete/enum/static methods).
• Output structured metadata (JSON/YAML) keyed by FQN for downstream tooling.
Components/Libraries:
• Source AST: com.github.javaparser.JavaParser (JavaParser) — supports symbol resolution via com.github.javaparser.symbolsolver.JavaSymbolSolver. Alternative: org.eclipse.jdt.core.dom.ASTParser (Eclipse JDT).
• Bytecode/binary: org.objectweb.asm.ClassReader (ASM) for .class/jars; javassist.CtClass (Javassist) as alternative.
• Reflection fallback (loaded classpath): java.lang.reflect.*.
• Graph modeling: org.jgrapht.graph.DefaultDirectedGraph (JGraphT).
Build introspection: Gradle Tooling API (org.gradle.tooling.GradleConnector) or Maven Model (org.apache.maven.model.Model) to resolve classpaths/jars.


# Implementation Plan

   ## High Level

M1 – Metadata Extraction Input: source dirs, compiled classes/jars, classpath. Process: JavaParser (AST+symbol solver) and ASM (bytecode) extract dependencies, signatures, fields, constructors. Output: JSON metadata keyed by FQN, dependency graph (JGraphT). Downstream: feeds M2 (structure) and M3 (context).

   ## Low Level

M1 – Metadata Extraction (Low-Level)
• Build classpath resolver (Gradle Tooling API/Maven Model) to enumerate jars + source roots; feeds JavaParser's ReflectionTypeSolver/JavaSymbolSolver for cross-module resolution.
• Walk source tree with JavaParser.parse(); visit ClassOrInterfaceDeclaration nodes to capture class kind (abstract/interface/enum), constructors, static initializers (InitializerDeclaration.isStatic()).
• For each field/method reference, resolve declaring type via symbol solver; record access modifier, return type, param types (handle generics/erasure edge cases).
• Parallel-path binary scan: ASM ClassReader + ClassVisitor for classes without source (3rd-party/compiled-only deps); reconcile with source-derived metadata by FQN to avoid duplication.
• Handle edge cases explicitly: classes with no explicit constructor (inject default), factory methods (detect static methods returning declaring/related type), builder patterns (heuristic naming detection).
• Build dependency graph nodes/edges (JGraphT DefaultDirectedGraph); compute outdegree/indegree per class for prioritization.
• Serialize per-class metadata to JSON keyed by FQN (schema: dependencies[], methods[], fields[], constructors[], staticInit:bool, classType).
Output: metadata/{FQN}.json + global dependency-graph.json. Downstream: consumed directly by M2 and M3; no upstream dependency (entry point).



# Technical Spec

M1 – Metadata Extraction Spec
• Artifacts: com.github.javaparser:javaparser-symbol-solver-core:3.25.10, org.ow2.asm:asm:9.7, org.ow2.asm:asm-tree:9.7, org.jgrapht:jgrapht-core:1.5.2, com.fasterxml.jackson.core:jackson-databind:2.17.0, org.gradle:gradle-tooling-api:8.7.
• Folder split: src/main/java/.../scanner/{SourceScanner, BinaryScanner, SymbolResolver, GraphBuilder}, model/{ClassMetadata, MethodInfo, FieldInfo}.
• Path resolution: target project path passed as CLI arg; Gradle Tooling API introspects build.gradle/classpath; source roots + jar paths resolved relative to target, not solution repo.
• Output: output/metadata/*.json, dependency-graph.json written to solution's own workspace initially (not target), to avoid polluting target repo during MVP.
Strategy: standalone Gradle project, run via gradle run --args="--target=/path" from IntelliJ; no target coupling yet.

