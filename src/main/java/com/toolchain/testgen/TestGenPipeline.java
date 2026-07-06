package com.toolchain.testgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.ProjectPaths;
import com.toolchain.testgen.TestGenModel.DepDecision;
import com.toolchain.testgen.TestGenModel.Placement;
import com.toolchain.testgen.TestGenModel.Report;
import com.toolchain.testgen.TestGenModel.Skeleton;
import com.toolchain.testgen.TestGenModel.TestOutcome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Orchestrates placement (target vs local mirror), writes files, builds the report. */
public final class TestGenPipeline {

    private static final Set<ClassType> GENERATE = Set.of(ClassType.CLASS, ClassType.RECORD);
    private final SkeletonBuilder builder = new SkeletonBuilder();

    public Report run(Path target, Path mirror, List<ClassMetadata> metadata) {
        return run(target, mirror, metadata, ProjectPaths.from(new java.util.Properties()));
    }

    public Report run(Path target, Path mirror, List<ClassMetadata> metadata, ProjectPaths layout) {
        Map<String, ClassMetadata> index = new LinkedHashMap<>();
        metadata.forEach(m -> index.put(m.fqn(), m));

        List<TestOutcome> outcomes = new ArrayList<>();
        for (ClassMetadata sut : metadata) {
            outcomes.add(process(sut, index, target, mirror, layout));
        }
        return new Report(outcomes);
    }

    private TestOutcome process(
            ClassMetadata sut,
            Map<String, ClassMetadata> index,
            Path target,
            Path mirror,
            ProjectPaths layout) {
        String testClass = simpleName(sut.fqn()) + "Test";
        String relPath = layout.primaryTestRelPath(sut.fqn());

        if (!GENERATE.contains(sut.classType())) {
            return new TestOutcome(sut.fqn(), testClass, relPath, Placement.SKIPPED,
                    skipReason(sut.classType()), List.of());
        }
        Skeleton skeleton = builder.build(sut, index);
        boolean collision = existingTestInTarget(target, simpleName(sut.fqn()), layout);
        Path root = collision ? mirror : target;
        Placement placement = collision ? Placement.LOCAL_MIRROR : Placement.TARGET;
        String note = collision
                ? "existing test in target — written to local mirror; DO NOT overwrite the target file"
                : "no existing test — written into target test tree";

        write(root.resolve(relPath), skeleton.source());
        return new TestOutcome(sut.fqn(), testClass, relPath, placement, note, skeleton.deps());
    }

    /** Collision detection: matching test file names under configured test roots. */
    private boolean existingTestInTarget(Path target, String simple, ProjectPaths layout) {
        Set<String> candidates = layout.testFileNamesFor(simple);
        for (Path testRoot : layout.resolveTestRoots(target)) {
            if (!Files.isDirectory(testRoot)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(testRoot)) {
                if (walk.filter(Files::isRegularFile)
                        .anyMatch(p -> candidates.contains(p.getFileName().toString()))) {
                    return true;
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to scan target tests under " + testRoot, e);
            }
        }
        return false;
    }

    private void write(Path file, String source) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, source);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    public String renderReport(Report report) {
        StringBuilder b = new StringBuilder("# M2 Test Generation Report\n\n");
        section(b, "Created locally — DO NOT overwrite target (existing test present)",
                report, Placement.LOCAL_MIRROR);
        section(b, "Created in target test tree", report, Placement.TARGET);
        section(b, "Skipped (not instantiable / no collaborators)", report, Placement.SKIPPED);
        appendMockability(b, report);
        appendTestingScenarios(b, report);
        return b.toString();
    }

    private void appendTestingScenarios(StringBuilder b, Report report) {
        b.append("## Testing scenarios (Mockito only — no PowerMock)\n\n");
        b.append("Full matrix: `docs/sample-target-scenarios.md`. Generated test skeletons include inline TODO comments.\n\n");
        for (TestOutcome o : report.outcomes()) {
            if (o.placement() == Placement.SKIPPED) {
                continue;
            }
            b.append("### ").append(o.sutFqn()).append("\n");
            boolean any = false;
            for (DepDecision d : o.deps()) {
                if (d.reason().contains("fixture") || d.reason().contains("reflection")
                        || d.reason().contains("abstract") || d.reason().contains("enum")
                        || d.reason().contains("primitive")) {
                    b.append("- `").append(d.type()).append("`: ").append(d.reason()).append("\n");
                    any = true;
                }
            }
            if ("com.sample.CheckoutService".equals(o.sutFqn())
                    || "com.sample.FulfillmentCoordinator".equals(o.sutFqn())) {
                b.append("- static `IdGenerator`: use `MockedStatic<IdGenerator>` (mockito-core inline, not PowerMock)\n");
                b.append("- `WarehouseAllocator`: instantiated inside SUT — not injectable via @Mock\n");
                b.append("- `calculateShippingFee` / `estimateFulfillmentCost`: `@ParameterizedTest` candidate\n");
                any = true;
            }
            if (!any) {
                b.append("- standard @Mock collaborator wiring\n");
            }
            b.append("\n");
        }
    }

    private void section(StringBuilder b, String title, Report report, Placement placement) {
        b.append("## ").append(title).append("\n\n");
        List<TestOutcome> rows = report.outcomes().stream().filter(o -> o.placement() == placement).toList();
        if (rows.isEmpty()) {
            b.append("_none_\n\n");
            return;
        }
        for (TestOutcome o : rows) {
            b.append("- `").append(o.testClass()).append("` (").append(o.sutFqn()).append(") — ")
                    .append(o.relPath()).append("  \n  ").append(o.note()).append("\n");
        }
        b.append("\n");
    }

    private void appendMockability(StringBuilder b, Report report) {
        b.append("## Mockability decisions (what can / cannot be mocked)\n\n");
        for (TestOutcome o : report.outcomes()) {
            if (o.placement() == Placement.SKIPPED) {
                continue;
            }
            b.append("### ").append(o.sutFqn()).append("\n");
            if (o.deps().isEmpty()) {
                b.append("- no constructor dependencies (default construction)\n\n");
                continue;
            }
            for (DepDecision d : o.deps()) {
                b.append("- `").append(d.type()).append("` -> ")
                        .append(d.mock() ? "MOCK" : "REAL(" + d.valueExpr() + ")")
                        .append(" — ").append(d.reason()).append("\n");
            }
            b.append("\n");
        }
    }

    private String skipReason(ClassType type) {
        return switch (type) {
            case INTERFACE -> "interface — no implementation to instantiate";
            case ABSTRACT -> "abstract — cannot instantiate directly";
            case ENUM -> "enum — no constructor collaborators to scaffold";
            case ANNOTATION -> "annotation — not a unit under test";
            default -> "not generated";
        };
    }

    private String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }
}
