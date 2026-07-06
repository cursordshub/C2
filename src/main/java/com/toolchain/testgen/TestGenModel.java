package com.toolchain.testgen;

import java.util.List;

/** M2 data records in one file. Input contract is reused from com.toolchain.metadata.Model. */
public final class TestGenModel {

    private TestGenModel() {
    }

    public enum Placement {
        TARGET, LOCAL_MIRROR, SKIPPED
    }

    /** Per-dependency decision derived from M1 metadata (see M2-STATE-1 §4). */
    public record DepDecision(String name, String type, boolean mock, String valueExpr, String reason) {
    }

    /** A built test skeleton: the Java source plus the decisions that shaped it. */
    public record Skeleton(String source, List<DepDecision> deps) {
    }

    /** What happened for one source class. */
    public record TestOutcome(
            String sutFqn,
            String testClass,
            String relPath,
            Placement placement,
            String note,
            List<DepDecision> deps) {
    }

    public record Report(List<TestOutcome> outcomes) {
    }
}
