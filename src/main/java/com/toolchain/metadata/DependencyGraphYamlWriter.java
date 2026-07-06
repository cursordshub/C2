package com.toolchain.metadata;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.DependencyGraph;
import com.toolchain.metadata.Model.ExtractionResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Human-readable dependency graph add-on ({@code dependency-graph.yml}).
 * Not consumed by M2 or any other code — remove by dropping from {@link Cli#OUTPUT_ADDONS}.
 */
public final class DependencyGraphYamlWriter implements MetadataWriteAddon {

    static final String ENABLED_KEY = "dependency-graph.yaml.enabled";
    static final String PATH_KEY = "dependency-graph.yaml.path";
    static final String PACKAGES_KEY = "dependency-graph.internal.packages";

    @Override
    public boolean enabled(Properties config) {
        return Boolean.parseBoolean(config.getProperty(ENABLED_KEY, "false"));
    }

    @Override
    public Path write(ExtractionResult result, Path outDir, Properties config) {
        try {
            Path file = outDir.resolve(config.getProperty(PATH_KEY, "dependency-graph.yml"));
            Files.createDirectories(outDir);
            List<String> internalPrefixes = parsePrefixes(config.getProperty(PACKAGES_KEY, ""));
            Files.writeString(file, render(result, internalPrefixes));
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write dependency graph YAML to " + outDir, e);
        }
    }

    String render(ExtractionResult result, List<String> internalPrefixes) {
        Map<String, String> simpleToFqn = simpleNameIndex(result.byFqn());
        DependencyGraph graph = result.graph();
        StringBuilder b = new StringBuilder();
        b.append("# Human-readable internal dependency graph (not consumed by the toolchain).\n");
        b.append("# Dependencies filtered by package prefix; ordered by usage within each class (most -> least).\n");
        b.append("internal_packages:\n");
        if (internalPrefixes.isEmpty()) {
            b.append("  []\n");
        } else {
            for (String prefix : internalPrefixes) {
                b.append("  - ").append(prefix).append('\n');
            }
        }
        b.append('\n');

        result.byFqn().keySet().stream().sorted().forEach(fqn -> {
            ClassMetadata meta = result.byFqn().get(fqn);
            List<DepEntry> deps = internalDependencies(meta, simpleToFqn, internalPrefixes, graph);
            b.append(fqn).append(":\n");
            b.append("  out_degree: ").append(deps.size()).append('\n');
            b.append("  dependencies:\n");
            if (deps.isEmpty()) {
                b.append("    []\n");
            } else {
                for (DepEntry dep : deps) {
                    b.append("    - fqn: ").append(dep.fqn()).append('\n');
                    b.append("      used_by_dependent: ").append(dep.usedByDependent()).append('\n');
                    b.append("      out_degree: ").append(graph.outDegree().getOrDefault(dep.fqn(), 0)).append('\n');
                    b.append("      in_degree: ").append(graph.inDegree().getOrDefault(dep.fqn(), 0)).append('\n');
                }
            }
            b.append('\n');
        });
        return b.toString();
    }

    private List<DepEntry> internalDependencies(
            ClassMetadata meta,
            Map<String, String> simpleToFqn,
            List<String> internalPrefixes,
            DependencyGraph graph) {
        List<DepEntry> out = new ArrayList<>();
        for (Map.Entry<String, Integer> usage : meta.dependencyUsage().entrySet()) {
            String targetFqn = simpleToFqn.get(usage.getKey());
            if (targetFqn == null || targetFqn.equals(meta.fqn())) {
                continue;
            }
            if (!matchesInternalPackage(targetFqn, internalPrefixes)) {
                continue;
            }
            if (!graph.nodes().contains(targetFqn)) {
                continue;
            }
            out.add(new DepEntry(targetFqn, usage.getValue()));
        }
        out.sort(Comparator.comparingInt(DepEntry::usedByDependent).reversed()
                .thenComparing(DepEntry::fqn));
        return out;
    }

    private boolean matchesInternalPackage(String fqn, List<String> prefixes) {
        if (prefixes.isEmpty()) {
            return true;
        }
        for (String prefix : prefixes) {
            if (fqn.equals(prefix) || fqn.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    static List<String> parsePrefixes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** Same unambiguous simple-name index as {@link MetadataPipeline}. */
    private Map<String, String> simpleNameIndex(Map<String, ClassMetadata> byFqn) {
        Map<String, String> unique = new HashMap<>();
        Map<String, Boolean> ambiguous = new HashMap<>();
        for (String fqn : byFqn.keySet()) {
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            if (unique.containsKey(simple)) {
                ambiguous.put(simple, true);
            } else {
                unique.put(simple, fqn);
            }
        }
        ambiguous.keySet().forEach(unique::remove);
        return unique;
    }

    private record DepEntry(String fqn, int usedByDependent) {
    }
}
