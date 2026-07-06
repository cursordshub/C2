package com.toolchain.testgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolchain.metadata.Model.ClassMetadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Reads M1 output/metadata/*.json into ClassMetadata records (the M1 -> M2 JSON seam). */
public final class MetadataLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ClassMetadata> load(Path metadataDir) {
        if (metadataDir == null || !Files.isDirectory(metadataDir)) {
            throw new IllegalArgumentException("Metadata dir not found: " + metadataDir
                    + " (run M1 `gradlew run` first)");
        }
        List<ClassMetadata> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(metadataDir)) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                out.add(mapper.readValue(json.toFile(), ClassMetadata.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read metadata from " + metadataDir, e);
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("No metadata JSON found in " + metadataDir);
        }
        return out;
    }
}
