package com.toolchain.metadata;

import java.util.List;
import java.util.Map;

/**
 * All M1 data records in one file (toolchainLite: collapse per-class model files into nested records).
 * These records are the shared contract consumed by downstream milestones via the JSON artifact.
 */
public final class Model {

    private Model() {
    }

    public enum ClassType {
        CLASS, INTERFACE, ABSTRACT, ENUM, RECORD, ANNOTATION
    }

    public record MethodInfo(String name, String returnType, List<String> parameterTypes, String access) {
    }

    public record FieldInfo(String name, String type, String access) {
    }

    public record ConstructorInfo(List<String> parameterTypes, String access) {
    }

    public record ClassMetadata(
            String fqn,
            ClassType classType,
            boolean hasStaticInitializer,
            List<String> dependencies,
            Map<String, Integer> dependencyUsage,
            List<MethodInfo> methods,
            List<FieldInfo> fields,
            List<ConstructorInfo> constructors) {
    }

    public record Edge(String from, String to) {
    }

    public record DependencyGraph(
            List<String> nodes,
            List<Edge> edges,
            Map<String, Integer> outDegree,
            Map<String, Integer> inDegree) {
    }

    public record ExtractionResult(Map<String, ClassMetadata> byFqn, DependencyGraph graph) {
    }

    /** The single [Success, Data, Error] boundary contract, produced only by Cli. */
    public record Result(boolean success, ExtractionResult data, String error) {
        public static Result ok(ExtractionResult data) {
            return new Result(true, data, null);
        }

        public static Result fail(String error) {
            return new Result(false, null, error);
        }
    }
}
