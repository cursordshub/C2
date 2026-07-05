# Milestone
M2 – Test Infra Generation: Auto-generate JUnit5/Mockito test classes, imports, mocks/stubs, fixtures, lifecycle blocks from metadata.

# Problem Statement
Milestone 2 – Test Infra Generation Manually scaffolding JUnit5/Mockito test classes (imports, mocks, fixtures, lifecycle methods) for untested classes is repetitive and time-consuming at scale. A generator is needed that consumes extracted metadata to auto-produce compilable test skeletons with correct dependency declarations, reducing setup effort before actual test logic is written.

# Requirements analysis
M2 – Test Infra Generation: Requirements
• Consume M1 metadata to auto-generate compilable JUnit5 test class skeletons.
• Auto-generate Mockito mocks/stubs, argument matchers, imports (internal vs 3rd-party by package/FQN).
• Generate lifecycle methods (@BeforeEach, @AfterEach, @BeforeAll, @AfterAll), fixture setup, resource closing for static/stateful deps.
Components/Libraries:
• Code generation: com.squareup.javapoet.JavaFile/TypeSpec/MethodSpec (JavaPoet).
• Test framework: org.junit.jupiter.api.* (JUnit5), org.mockito.Mockito, org.mockito.ArgumentMatchers.
Optional templating: freemarker.template.Template or org.apache.velocity for boilerplate control.

# Implementation Plan

## High Level
M2 – Test Infra Generation Input: M1 JSON/FQN metadata. Process: JavaPoet generates JUnit5/Mockito skeletons, imports, fixtures, lifecycle methods. Output: compilable test class stubs. Downstream: feeds M3 as scaffolding context; upstream depends entirely on M1 metadata accuracy.

## Low Level
M2 – Test Infra Generation (Low-Level)
• Load M1 JSON per target FQN; resolve constructor injection strategy (constructor args → mock candidates via dependency list).
• Classify each dependency as mock (interface/collaborator) vs stub (data-holder) vs real (pure/value object) using M1 classType + outdegree heuristics.
• Generate class skeleton via JavaPoet TypeSpec.classBuilder; annotate @ExtendWith(MockitoExtension.class).
• Auto-generate @Mock fields for dependencies, @InjectMocks or manual constructor wiring based on constructor metadata.
• Generate @BeforeEach/@AfterEach stubs; populate resource-closing calls for static/stateful deps flagged in M1.
• Resolve imports: partition FQNs by internal package prefix vs external groupId to auto-import correctly, avoid collisions.
Output: compilable .java test skeleton per class + generation log (unresolved gaps).


# Technical Spec
M2 – Test Infra Generation Spec
• Artifacts: com.squareup:javapoet:1.13.0; generated code targets org.junit.jupiter:junit-jupiter:5.10.2, org.mockito:mockito-core:5.12.0, org.mockito:mockito-junit-jupiter:5.12.0 (added to target's build.gradle, not tool's).
• Folder split: generator/{SkeletonBuilder, MockStrategyResolver, ImportPartitioner, LifecycleGenerator}.
• Path resolution: two modes — (a) write to solution's output/tests/ for review, (b) write directly into target's src/test/java/<FQN-derived-path> once validated.
Strategy: test mode (a) first on sample classes; promote to mode (b) after manual review; avoid IntelliJ copy-paste to prevent classpath drift.

