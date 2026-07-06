package com.toolchain.metadata;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.DependencyGraph;
import com.toolchain.metadata.Model.Edge;
import com.toolchain.metadata.Model.ExtractionResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Walks configured production roots, scans .java, merges by FQN, builds the internal dependency graph. */
public final class MetadataPipeline {

    private final SourceScanner scanner = new SourceScanner();

    public ExtractionResult run(Path targetRoot) {
        return run(targetRoot, ProjectPaths.from(new java.util.Properties()));
    }

    public ExtractionResult run(Path targetRoot, ProjectPaths layout) {
        if (targetRoot == null || !Files.isDirectory(targetRoot)) {
            throw new IllegalArgumentException("Target is not a directory: " + targetRoot);
        }
        Map<String, ClassMetadata> byFqn = scanAll(targetRoot, layout);
        if (byFqn.isEmpty()) {
            throw new IllegalStateException("No .java source files found under configured source roots of: "
                    + targetRoot + " (roots: " + layout.sourceRoots() + ")");
        }
        return new ExtractionResult(byFqn, buildGraph(byFqn));
    }

    private Map<String, ClassMetadata> scanAll(Path targetRoot, ProjectPaths layout) {
        Map<String, ClassMetadata> byFqn = new TreeMap<>();
        for (Path sourceRoot : layout.resolveSourceRoots(targetRoot)) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            for (Path javaFile : javaFiles(sourceRoot)) {
                scanner.scan(javaFile).forEach(m -> byFqn.put(m.fqn(), m));
            }
        }
        return byFqn;
    }

    private List<Path> javaFiles(Path sourceRoot) {
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk " + sourceRoot, e);
        }
    }

    private DependencyGraph buildGraph(Map<String, ClassMetadata> byFqn) {
        Map<String, String> simpleToFqn = simpleNameIndex(byFqn);
        List<String> nodes = new ArrayList<>(byFqn.keySet());
        List<Edge> edges = new ArrayList<>();
        Map<String, Integer> out = zeroed(nodes);
        Map<String, Integer> in = zeroed(nodes);

        for (ClassMetadata m : byFqn.values()) {
            for (String dep : m.dependencies()) {
                String targetFqn = simpleToFqn.get(dep);
                if (targetFqn != null && !targetFqn.equals(m.fqn())) {
                    edges.add(new Edge(m.fqn(), targetFqn));
                    out.merge(m.fqn(), 1, Integer::sum);
                    in.merge(targetFqn, 1, Integer::sum);
                }
            }
        }
        return new DependencyGraph(nodes, edges, out, in);
    }

    /** Simple name -> FQN, but only for names that map unambiguously (F5: ambiguous names skipped). */
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

    private Map<String, Integer> zeroed(List<String> nodes) {
        Map<String, Integer> m = new LinkedHashMap<>();
        nodes.forEach(n -> m.put(n, 0));
        return m;
    }
}
