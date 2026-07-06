package com.toolchain.promptgen;

import java.util.List;

/** M3 data records — prompt/coverage DTOs in one file. */
public final class PromptGenModel {

    private PromptGenModel() {
    }

    public enum GapSource {
        JACOCO, PIT, SCAFFOLD
    }

    /** Uncovered or under-tested method on a SUT class. */
    public record MethodGap(
            String methodName,
            int missedBranches,
            int missedLines,
            List<Integer> uncoveredLines,
            List<String> survivingMutations,
            List<GapSource> sources) {
    }

    /** Aggregated coverage picture for one FQN. */
    public record ClassCoverage(String fqn, List<MethodGap> gaps) {
    }

    /** A chat-ready prompt for one class. */
    public record PromptArtifact(
            String sutFqn,
            String testClass,
            String testRelPath,
            String markdown,
            List<MethodGap> gaps) {
    }

    public record SkippedClass(String fqn, String reason) {
    }

    public record Report(
            List<PromptArtifact> prompts,
            List<SkippedClass> skipped,
            List<String> warnings) {
    }
}
