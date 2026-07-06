package com.toolchain.metadata;

import com.toolchain.metadata.Model.ExtractionResult;
import com.toolchain.metadata.Model.Result;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Entry point. Loads config, runs the pipeline, wraps once into [Success, Data, Error] -> exit 0/1. */
public final class Cli {

    /**
     * Copy-paste knob for IntelliJ users. HIGHEST precedence: when non-blank this wins over
     * application.properties (target.path) and any --target= CLI arg. Leave blank to fall back to those.
     */
    static final String TARGET = "";

    /** Optional output add-ons (human-readable artifacts). Remove entries here to disable. */
    static final List<MetadataWriteAddon> OUTPUT_ADDONS = List.of(
            new DependencyGraphYamlWriter()
    );

    public static void main(String[] args) {
        Result result = execute(args);
        if (result.success()) {
            ExtractionResult data = result.data();
            System.out.println("[SUCCESS] scanned " + data.byFqn().size()
                    + " classes, " + data.graph().edges().size() + " internal edges "
                    + "-> metadata/*.json + dependency-graph.json (+ optional add-ons)");
            System.exit(0);
        }
        System.err.println("[ERROR] " + result.error());
        System.exit(1);
    }

    static final String NO_TARGET =
            "No target configured. Set the TARGET constant, target.path in application.properties, "
                    + "or pass --target=<path>. (It will NOT silently scan the bundled sample-target.)";

    /** Resolve config (see precedence in resolveTarget), then run; never throws -> Result. */
    static Result execute(String[] args) {
        Properties props = loadProperties();
        String target = resolveTarget(args, props);
        if (target == null) {
            return Result.fail(NO_TARGET);
        }
        Path outDir = Path.of(arg(args, "--out", props.getProperty("output.dir", "output")));
        boolean clean = Boolean.parseBoolean(props.getProperty("output.clean", "true"));
        return run(Path.of(target), outDir, clean);
    }

    /** Boundary: run pipeline + writer for an already-resolved target; never throws -> Result. */
    static Result run(Path target, Path outDir, boolean clean) {
        return run(target, outDir, clean, loadProperties());
    }

    /** Same as {@link #run(Path, Path, boolean)} with explicit config (for tests). */
    static Result run(Path target, Path outDir, boolean clean, Properties props) {
        try {
            JsonWriter writer = new JsonWriter();
            if (clean) {
                writer.clean(outDir);
            }
            ProjectPaths layout = ProjectPaths.from(props);
            ExtractionResult result = new MetadataPipeline().run(target, layout);
            List<Path> written = writer.write(result, outDir);
            for (MetadataWriteAddon addon : OUTPUT_ADDONS) {
                if (addon.enabled(props)) {
                    written.add(addon.write(result, outDir, props));
                }
            }
            System.out.println("Wrote " + written.size() + " file(s) to " + outDir.toAbsolutePath());
            return Result.ok(result);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * Target precedence (highest first): class {@link #TARGET} constant, then application.properties
     * {@code target.path}, then {@code --target=} CLI arg. Returns {@code null} when none are set
     * (the caller fails fast — no silent sample-target default).
     */
    private static String resolveTarget(String[] args, Properties props) {
        if (TARGET != null && !TARGET.isBlank()) {
            return TARGET;
        }
        String fromProps = props.getProperty("target.path", "");
        if (!fromProps.isBlank()) {
            return fromProps;
        }
        String fromArg = arg(args, "--target", null);
        if (fromArg != null && !fromArg.isBlank()) {
            return fromArg;
        }
        return null;
    }

    private static String arg(String[] args, String key, String fallback) {
        for (String a : args) {
            if (a.startsWith(key + "=")) {
                return a.substring(key.length() + 1);
            }
        }
        return fallback;
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = Cli.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            // Fall back to defaults; config is optional.
        }
        return props;
    }
}
