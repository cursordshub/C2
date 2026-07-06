package com.toolchain.testgen;

import com.toolchain.metadata.JsonWriter;
import com.toolchain.metadata.MetadataPipeline;
import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ExtractionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration across the M1 -> M2 JSON seam: write real M1 output, then load it. (Scaffolding until STATE 3.) */
class MetadataLoaderTest {

    @Test
    void roundTripsM1JsonIntoClassMetadata(@TempDir Path out) {
        ExtractionResult m1 = new MetadataPipeline().run(Path.of("sample-target"));
        new JsonWriter().write(m1, out);

        List<ClassMetadata> loaded = new MetadataLoader().load(out.resolve("metadata"));

        assertTrue(loaded.stream().anyMatch(c -> c.fqn().equals("com.sample.catalog.Order")));
        ClassMetadata order = loaded.stream().filter(c -> c.fqn().equals("com.sample.catalog.Order")).findFirst().orElseThrow();
        assertTrue(order.constructors().get(0).parameterTypes().contains("PricingService"),
                "constructor metadata survives the JSON round-trip");
    }
}
