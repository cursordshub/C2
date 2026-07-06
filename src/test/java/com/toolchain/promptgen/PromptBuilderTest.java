package com.toolchain.promptgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.Model.MethodInfo;
import com.toolchain.promptgen.PromptGenModel.ClassCoverage;
import com.toolchain.promptgen.PromptGenModel.GapSource;
import com.toolchain.promptgen.PromptGenModel.MethodGap;
import com.toolchain.promptgen.PromptGenModel.PromptArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void buildsPromptWithGapsAndSkeleton(@TempDir Path target) throws Exception {
        String fqn = "com.sample.billing.CheckoutService";
        Path testFile = target.resolve("src/test/java/com/sample/billing/CheckoutServiceTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
                class CheckoutServiceTest {
                    void completeCheckout_m3Coverage() {
                        // M3: exercise completeCheckout
                    }
                }
                """);

        ClassMetadata meta = new ClassMetadata(
                fqn, ClassType.CLASS, false, List.of("TaxCalculator"), Map.of(),
                List.of(new MethodInfo("completeCheckout", "CheckoutResult",
                        List.of("String", "int", "double", "int"), "public")),
                List.of(), List.of());

        ClassCoverage coverage = new ClassCoverage(fqn, List.of(
                new MethodGap("completeCheckout", 2, 4, List.of(80), List.of("negated conditional"),
                        List.of(GapSource.SCAFFOLD, GapSource.PIT))));

        List<PromptArtifact> prompts = new PromptBuilder().build(
                Map.of(fqn, meta),
                Map.of(fqn, coverage),
                target,
                null);

        assertEquals(1, prompts.size());
        String md = prompts.get(0).markdown();
        assertTrue(md.contains("completeCheckout"));
        assertTrue(md.contains("Mockito only"));
        assertTrue(md.contains("completeCheckout_m3Coverage"));
        assertTrue(md.contains("negated conditional"));
        assertTrue(md.contains("TaxCalculator"));
    }
}
