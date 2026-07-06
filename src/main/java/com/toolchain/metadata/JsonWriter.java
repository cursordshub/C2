package com.toolchain.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ExtractionResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Serializes ExtractionResult to per-FQN JSON + dependency-graph.json. */
public final class JsonWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Bounded delete of everything under outDir (nothing else). Safe if outDir does not exist. */
    public void clean(Path outDir) {
        try {
            cleanBounded(outDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clean " + outDir, e);
        }
    }

    public List<Path> write(ExtractionResult result, Path outDir) {
        try {
            Path metadataDir = outDir.resolve("metadata");
            Files.createDirectories(metadataDir);

            List<Path> written = new ArrayList<>();
            for (ClassMetadata m : result.byFqn().values()) {
                Path file = metadataDir.resolve(m.fqn() + ".json");
                mapper.writeValue(file.toFile(), m);
                written.add(file);
            }
            Path graphFile = outDir.resolve("dependency-graph.json");
            mapper.writeValue(graphFile.toFile(), result.graph());
            written.add(graphFile);
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write metadata to " + outDir, e);
        }
    }

    /** Bounded delete: removes contents strictly under outDir, nothing else (F4). */
    private void cleanBounded(Path outDir) throws IOException {
        if (!Files.exists(outDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(outDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(outDir))
                    .forEach(this::deleteQuietly);
        }
    }

    private void deleteQuietly(Path p) {
        try {
            Files.delete(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + p, e);
        }
    }
}
