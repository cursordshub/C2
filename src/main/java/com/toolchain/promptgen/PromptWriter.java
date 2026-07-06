package com.toolchain.promptgen;

import com.toolchain.promptgen.PromptGenModel.PromptArtifact;
import com.toolchain.promptgen.PromptGenModel.Report;
import com.toolchain.promptgen.PromptGenModel.SkippedClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes per-class prompt markdown files and the M3 report. */
public final class PromptWriter {

    public List<Path> writePrompts(Path outDir, List<PromptArtifact> prompts) {
        try {
            Files.createDirectories(outDir);
            List<Path> written = new ArrayList<>();
            for (PromptArtifact prompt : prompts) {
                Path file = outDir.resolve(fileName(prompt.sutFqn()));
                Files.writeString(file, prompt.markdown());
                written.add(file);
            }
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write prompts to " + outDir, e);
        }
    }

    public void writeReport(Path reportPath, Report report) {
        try {
            if (reportPath.getParent() != null) {
                Files.createDirectories(reportPath.getParent());
            }
            Files.writeString(reportPath, renderReport(report));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report to " + reportPath, e);
        }
    }

    String renderReport(Report report) {
        StringBuilder b = new StringBuilder("# M3 Prompt Generation Report\n\n");
        if (!report.warnings().isEmpty()) {
            b.append("## Warnings\n\n");
            for (String warning : report.warnings()) {
                b.append("- ").append(warning).append("\n");
            }
            b.append("\n");
        }
        b.append("## Prompts created\n\n");
        if (report.prompts().isEmpty()) {
            b.append("_none_\n\n");
        } else {
            for (PromptArtifact prompt : report.prompts()) {
                b.append("- `").append(fileName(prompt.sutFqn())).append("` — ")
                        .append(prompt.sutFqn()).append(" (")
                        .append(prompt.gaps().size()).append(" method gap(s); test `")
                        .append(prompt.testRelPath()).append("`)\n");
            }
            b.append("\n");
        }
        b.append("## Skipped\n\n");
        if (report.skipped().isEmpty()) {
            b.append("_none_\n\n");
        } else {
            for (SkippedClass skipped : report.skipped()) {
                b.append("- `").append(skipped.fqn()).append("` — ").append(skipped.reason()).append("\n");
            }
            b.append("\n");
        }
        return b.toString();
    }

    private String fileName(String fqn) {
        return fqn.replace('.', '-') + ".md";
    }
}
