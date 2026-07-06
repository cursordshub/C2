package com.toolchain.promptgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.ProjectPaths;
import com.toolchain.promptgen.PromptGenModel.ClassCoverage;
import com.toolchain.promptgen.PromptGenModel.GapSource;
import com.toolchain.promptgen.PromptGenModel.MethodGap;
import com.toolchain.promptgen.PromptGenModel.PromptArtifact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

/** Combines M1 metadata, M2 test skeleton, and coverage gaps into chat-ready prompts. */
public final class PromptBuilder {

    public List<PromptArtifact> build(
            Map<String, ClassMetadata> metadataIndex,
            Map<String, ClassCoverage> coverage,
            Path target,
            Path mirror) {
        return build(metadataIndex, coverage, target, mirror, ProjectPaths.from(new Properties()));
    }

    public List<PromptArtifact> build(
            Map<String, ClassMetadata> metadataIndex,
            Map<String, ClassCoverage> coverage,
            Path target,
            Path mirror,
            ProjectPaths layout) {
        List<PromptArtifact> out = new ArrayList<>();
        for (ClassCoverage classCoverage : coverage.values()) {
            ClassMetadata meta = metadataIndex.get(classCoverage.fqn());
            if (meta == null) {
                continue;
            }
            Optional<TestFile> testFile = locateTest(meta.fqn(), target, mirror, layout);
            if (testFile.isEmpty()) {
                continue;
            }
            out.add(buildOne(meta, classCoverage, testFile.get()));
        }
        return out;
    }

    private PromptArtifact buildOne(ClassMetadata meta, ClassCoverage coverage, TestFile testFile) {
        String markdown = render(meta, coverage, testFile);
        return new PromptArtifact(
                meta.fqn(),
                testFile.simpleName(),
                testFile.relPath(),
                markdown,
                coverage.gaps());
    }

    private String render(ClassMetadata meta, ClassCoverage coverage, TestFile testFile) {
        StringBuilder b = new StringBuilder();
        b.append("# Coverage prompt: ").append(meta.fqn()).append("\n\n");
        b.append("## Task\n\n");
        b.append("Complete the **M3 coverage tests** for `").append(meta.fqn()).append("`. ");
        b.append("The M2 skeleton already wires mocks, fixtures, and helpers — **do not remove or rewrite infra**. ");
        b.append("Replace `_m3Coverage` scaffold bodies and add assertions that kill surviving mutants.\n\n");

        b.append("## Constraints\n\n");
        b.append("- **Mockito only** — never PowerMock.\n");
        b.append("- Keep existing `@Mock` fields, `build*()` helpers, `stubCollaborators()`, reflection/static helpers.\n");
        b.append("- Add real assertions (`assertEquals`, `assertThrows`, `verify`, etc.) — not `assertNotNull(sut)` alone.\n");
        b.append("- Target branches listed below; prefer `@ParameterizedTest` when a scaffold already exists.\n");
        b.append("- Output: return the **full updated test class** ready to paste over `")
                .append(testFile.relPath()).append("`.\n\n");

        b.append("## Class under test\n\n");
        b.append("- **FQN:** `").append(meta.fqn()).append("`\n");
        b.append("- **Kind:** `").append(meta.classType()).append("`\n");
        b.append("- **Internal dependencies:** ");
        if (meta.dependencies().isEmpty()) {
            b.append("_none recorded_\n");
        } else {
            b.append(String.join(", ", meta.dependencies().stream().map(d -> "`" + d + "`").toList()));
            b.append("\n");
        }
        b.append("\n### Public methods (from M1 metadata)\n\n");
        appendMethods(b, meta);

        b.append("\n## Coverage gaps (JaCoCo / PIT / M2 scaffolds)\n\n");
        if (coverage.gaps().isEmpty()) {
            b.append("_none_\n");
        } else {
            for (MethodGap gap : coverage.gaps()) {
                b.append("### `").append(gap.methodName()).append("`\n");
                b.append("- Missed branches: ").append(gap.missedBranches()).append("\n");
                b.append("- Missed lines: ").append(gap.missedLines()).append("\n");
                if (!gap.uncoveredLines().isEmpty()) {
                    b.append("- Lines: ").append(gap.uncoveredLines()).append("\n");
                }
                if (!gap.survivingMutations().isEmpty()) {
                    b.append("- Surviving mutations: ")
                            .append(String.join("; ", gap.survivingMutations())).append("\n");
                }
                b.append("- Sources: ").append(gap.sources()).append("\n");
                b.append("- Scaffold method: `").append(gap.methodName()).append("_m3Coverage()`\n\n");
            }
        }

        b.append("## Existing M2 test skeleton\n\n");
        b.append("```java\n").append(testFile.source()).append("\n```\n");
        return b.toString();
    }

    private void appendMethods(StringBuilder b, ClassMetadata meta) {
        if (meta.methods().isEmpty()) {
            b.append("_none recorded_\n");
            return;
        }
        for (var method : meta.methods()) {
            b.append("- `").append(method.name()).append("(");
            b.append(String.join(", ", method.parameterTypes())).append(")` -> `")
                    .append(method.returnType()).append("`\n");
        }
    }

    private Optional<TestFile> locateTest(String sutFqn, Path target, Path mirror, ProjectPaths layout) {
        Optional<TestFile> fromTarget = findTest(sutFqn, target, layout);
        if (fromTarget.isPresent()) {
            return fromTarget;
        }
        if (mirror != null) {
            return findTest(sutFqn, mirror, layout);
        }
        return Optional.empty();
    }

    private Optional<TestFile> findTest(String sutFqn, Path root, ProjectPaths layout) {
        for (String candidate : layout.testFileRelCandidates(sutFqn)) {
            Path file = root.resolve(candidate);
            if (Files.isRegularFile(file)) {
                return Optional.of(readTest(file, candidate));
            }
        }
        String simple = simpleName(sutFqn);
        for (Path testDir : layout.resolveTestRoots(root)) {
            if (!Files.isDirectory(testDir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(testDir)) {
                Optional<Path> match = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equals(simple + "Test.java"))
                        .findFirst();
                if (match.isPresent()) {
                    Path file = match.get();
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    return Optional.of(readTest(file, rel));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to locate test for " + sutFqn, e);
            }
        }
        return Optional.empty();
    }

    private TestFile readTest(Path file, String relPath) {
        try {
            return new TestFile(file.getFileName().toString(), relPath, Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read test " + file, e);
        }
    }

    private String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private record TestFile(String simpleName, String relPath, String source) {
    }
}
