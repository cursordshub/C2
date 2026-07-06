package com.toolchain.metadata;

import com.toolchain.metadata.Model.ExtractionResult;
import com.toolchain.metadata.Model.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: real {@link JsonWriter} on disk + the {@link Cli} boundary contract
 * [Success, Data, Error]. (Scaffolding until STATE 3.)
 */
class CliAndWriterTest {

    @Test
    void writerEmitsPerFqnJsonAndGraph(@TempDir Path out) {
        ExtractionResult result = new MetadataPipeline().run(Path.of("sample-target"));
        List<Path> written = new JsonWriter().write(result, out);

        assertTrue(written.stream().anyMatch(p -> p.getFileName().toString().equals("dependency-graph.json")));
        assertTrue(Files.exists(out.resolve("metadata").resolve("com.sample.catalog.Order.json")),
                "per-FQN JSON emitted");
    }

    @Test
    void cliRunWithYamlAddonEnabledWritesHumanGraph(@TempDir Path out) {
        Properties props = new Properties();
        props.setProperty(DependencyGraphYamlWriter.ENABLED_KEY, "true");
        props.setProperty(DependencyGraphYamlWriter.PACKAGES_KEY, "com.sample");

        Result ok = Cli.run(Path.of("sample-target"), out, true, props);
        assertTrue(ok.success());
        assertTrue(Files.exists(out.resolve("dependency-graph.yml")));
    }

    @Test
    void cliBoundaryReturnsSuccessForValidTargetAndFailureForBadTarget() {
        // Tests the [Success, Data, Error] boundary directly via run(), independent of target
        // precedence resolution (the TARGET constant would otherwise shadow any injected target).
        Result ok = Cli.run(Path.of("sample-target"), Path.of(tempOut("ok")), true);
        assertTrue(ok.success(), "valid target -> Success");
        assertTrue(ok.error() == null);

        Result bad = Cli.run(Path.of("does-not-exist-xyz"), Path.of(tempOut("bad")), true);
        assertFalse(bad.success(), "bad target -> Error (exit 1 path)");
        assertTrue(bad.error() != null && !bad.error().isBlank());
    }

    private static String tempOut(String tag) {
        try {
            return Files.createTempDirectory("m1-" + tag).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
