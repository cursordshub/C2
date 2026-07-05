Do not over-engineer, we want something simple and functional an MVP for Milestone 1 only (no more than 5 src files)

When generating test classes, verify each test method is annotated @Test (or the framework's discovery annotation) — a hand-rolled assertion harness with no annotations will not be discovered by gradlew test."

When generating or modifying build.gradle, always include JaCoCo (id 'jacoco', XML report enabled) and PIT mutation testing (info.solidsoft.pitest + correctg pitest-junit5-plugin version)

Every milestone must ship with a runnable entry point wiring its components end-to-end — pure functions/contracts alone are not sufficient. State the exact run command and expected output alongside any implementation.

I should be able to update a variable or application.properties file with the path to the project I want to scan and change this when I want to scan a new project.

Contract/unit tests must call the actual production classes under test, not local reimplementations — flag explicitly if a test is scaffolding (pre-implementation) versus integration (validates real code), and never present scaffolding tests as MVP validation.

1a
[SYSTEM: ARCHITECTURAL GATING PIPELINE]
You are operating under strict Sequential Gating. You will execute exactly one [STATE] at a time. You will STOP entirely after outputting the current [STATE] and wait for my manual approval before proceeding to the next phase.

[CURRENT_STATE: 1_ARCHITECTURE_AND_BOUNDARIES]
1. Analyze the requested feature.
2. Define the multi-file directory structure required to achieve Separation of Concerns.
3. Define the explicit Data/IO contracts for all modules using a deterministic `[Success, Data, Error]` payload schema.
4. HALT. Output only the architecture and interfaces, then await user approval.
I have created a buildlog folder for you under docs, store the ouput from stage 1 in a file in this folder, you may create the file, choose the format/type and how you version
I want to end up with one file for each of the stages 1 2 and 3
confirm once you have stored output from stage 1 successfully

Do not over-engineer, we want something simple and functional an MVP for Milestone 1 only (no more than 5 src files)

[CURRENT_STATE: 2_MOCK_TEST_BLUEPRINT]
(Do not execute until STATE 1 is explicitly approved)
1. Write zero-dependency native assertion tests for each module defined in STATE 1.
2. Ensure 100% coverage of the defined state contracts. 
3. HALT. After creating the necessary files and output, then await user approval.

Do not over-engineer, we want something simple and functional an MVP for Milestone 1 only (no more than 5 src files)

[CURRENT_STATE: 3_IMPLEMENTATION]
(Do not execute until STATE 2 is explicitly approved)
1. Implement the logic to satisfy the contracts from STATE 1 and tests from STATE 2.
2. Structural Constraints: Max 15 lines per function, max 3 parameters, pure functions only (zero global state mutation).
3. Output Schema: Enclose every discrete file within XML-style tags specifying the exact file path (e.g., `<file path="src/utils.js">`).
4. Iterate until everything works, make sure tests run and the application itself works as expected.
5. HALT. Await final review.


