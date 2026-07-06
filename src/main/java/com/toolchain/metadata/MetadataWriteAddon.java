package com.toolchain.metadata;

import com.toolchain.metadata.Model.ExtractionResult;

import java.nio.file.Path;
import java.util.Properties;

/**
 * Optional output hook for M1 artifacts. Add or remove implementations from {@link Cli#OUTPUT_ADDONS}
 * without changing the core JSON pipeline.
 */
public interface MetadataWriteAddon {

    boolean enabled(Properties config);

    Path write(ExtractionResult result, Path outDir, Properties config);
}
