package com.toolchain.promptgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptGenCliTest {

    @Test
    void endToEndAgainstSampleTarget(@TempDir Path tempRoot) throws Exception {
        Properties props = new Properties();
        props.setProperty("metadata.dir", "output/metadata");
        props.setProperty("promptgen.output.dir", tempRoot.resolve("prompts").toString());
        props.setProperty("promptgen.report", tempRoot.resolve("report.md").toString());
        props.setProperty("promptgen.internal.packages", "com.sample");
        props.setProperty("testgen.mirror.dir", "output/tests-local-mirror");
        props.setProperty("promptgen.jacoco.report", "");
        props.setProperty("promptgen.pitest.report", "");

        Cli.Result result = Cli.run(Path.of("sample-target"), props);

        assertTrue(result.success(), () -> result.error());
        assertTrue(Files.exists(tempRoot.resolve("report.md")));
        try (var files = Files.list(tempRoot.resolve("prompts"))) {
            assertTrue(files.findAny().isPresent(), "at least one prompt for sample-target scaffolds");
        }
    }
}
