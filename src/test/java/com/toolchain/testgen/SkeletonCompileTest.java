package com.toolchain.testgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.Model.ConstructorInfo;
import com.toolchain.testgen.TestGenModel.Skeleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Falsification G2: the generated skeleton must actually COMPILE against the sample sources
 * with JUnit5 + Mockito on the classpath (present at test runtime). (Scaffolding until STATE 3.)
 */
class SkeletonCompileTest {

    private static ClassMetadata cls(String fqn, ClassType type, ConstructorInfo... ctors) {
        return new ClassMetadata(fqn, type, false, List.of(), Map.of(), List.of(), List.of(), List.of(ctors));
    }

    @Test
    void generatedOrderSkeletonCompiles(@TempDir Path work) throws Exception {
        ClassMetadata order = cls("com.sample.catalog.Order", ClassType.CLASS,
                new ConstructorInfo(List.of("String", "Customer", "PricingService"), "public"));
        Map<String, ClassMetadata> index = Map.of(
                "com.sample.catalog.Order", order,
                "com.sample.catalog.Customer", cls("com.sample.catalog.Customer", ClassType.CLASS),
                "com.sample.catalog.PricingService", cls("com.sample.catalog.PricingService", ClassType.CLASS));

        Skeleton skeleton = new SkeletonBuilder().build(order, index);
        Path genRoot = work.resolve("gen");
        Path testFile = genRoot.resolve("com/sample/catalog/OrderTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, skeleton.source());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler required to run this test");
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();

        List<Path> sources = new ArrayList<>();
        sources.add(testFile);
        Path sampleSrc = Path.of("sample-target", "src", "main", "java");
        try (Stream<Path> walk = Files.walk(sampleSrc)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, null)) {
            Path classes = work.resolve("classes");
            Files.createDirectories(classes);
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            fm.setLocationFromPaths(StandardLocation.CLASS_PATH, classpathEntries());
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(sources);
            boolean ok = compiler.getTask(null, fm, diags, List.of(), null, units).call();

            String errors = diags.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertTrue(ok, "generated skeleton failed to compile:" + errors);
        }
    }

    /** Javac classpath for compiling generated skeletons against sample sources. */
    private static List<Path> classpathEntries() throws Exception {
        return Arrays.stream(compileClasspath().split(java.io.File.pathSeparator))
                .filter(s -> !s.isBlank())
                .map(Paths::get)
                .toList();
    }

    private static String compileClasspath() throws Exception {
        Path cpFile = Path.of("build/test-runtime-classpath.txt");
        if (Files.exists(cpFile)) {
            return Files.readString(cpFile).trim();
        }
        return System.getProperty("java.class.path");
    }
}
