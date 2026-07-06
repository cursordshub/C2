package com.toolchain.metadata;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.Model.ConstructorInfo;
import com.toolchain.metadata.Model.FieldInfo;
import com.toolchain.metadata.Model.MethodInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Parses one .java file into ClassMetadata (top-level + nested types). Source-only, no symbol solver. */
public final class SourceScanner {

    static {
        StaticJavaParser.getConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    public List<ClassMetadata> scan(Path javaFile) {
        CompilationUnit cu = parse(javaFile);
        List<ClassMetadata> out = new ArrayList<>();
        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            out.add(toMetadata(type));
        }
        return out;
    }

    private CompilationUnit parse(Path javaFile) {
        try {
            return StaticJavaParser.parse(javaFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse " + javaFile + ": " + e.getMessage(), e);
        }
    }

    private ClassMetadata toMetadata(TypeDeclaration<?> type) {
        String fqn = type.getFullyQualifiedName().orElse(type.getNameAsString());
        Map<String, Integer> usage = dependencyUsage(type);
        return new ClassMetadata(fqn, classType(type), hasStaticInitializer(type),
                dependencyNames(usage), usage, methods(type), fields(type), constructors(type));
    }

    private ClassType classType(TypeDeclaration<?> type) {
        if (type instanceof EnumDeclaration) {
            return ClassType.ENUM;
        }
        if (type instanceof RecordDeclaration) {
            return ClassType.RECORD;
        }
        if (type instanceof ClassOrInterfaceDeclaration c) {
            if (c.isInterface()) {
                return ClassType.INTERFACE;
            }
            return c.isAbstract() ? ClassType.ABSTRACT : ClassType.CLASS;
        }
        return ClassType.ANNOTATION;
    }

    private boolean hasStaticInitializer(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .anyMatch(m -> m instanceof InitializerDeclaration i && i.isStatic());
    }

    private List<MethodInfo> methods(TypeDeclaration<?> type) {
        List<MethodInfo> out = new ArrayList<>();
        type.getMethods().forEach(m -> out.add(new MethodInfo(
                m.getNameAsString(), m.getType().asString(), paramTypes(m.getParameters()),
                access(m.getAccessSpecifier()))));
        return out;
    }

    private List<FieldInfo> fields(TypeDeclaration<?> type) {
        List<FieldInfo> out = new ArrayList<>();
        type.getFields().forEach(f -> f.getVariables().forEach(v ->
                out.add(new FieldInfo(v.getNameAsString(), v.getType().asString(),
                        access(f.getAccessSpecifier())))));
        return out;
    }

    private List<ConstructorInfo> constructors(TypeDeclaration<?> type) {
        List<ConstructorInfo> out = new ArrayList<>();
        type.getMembers().stream()
                .filter(m -> m instanceof ConstructorDeclaration)
                .map(m -> (ConstructorDeclaration) m)
                .forEach(c -> out.add(new ConstructorInfo(paramTypes(c.getParameters()),
                        access(c.getAccessSpecifier()))));
        return out;
    }

    private List<String> paramTypes(com.github.javaparser.ast.NodeList<com.github.javaparser.ast.body.Parameter> params) {
        List<String> out = new ArrayList<>();
        params.forEach(p -> out.add(p.getType().asString()));
        return out;
    }

    /** Reference counts by simple type name (fields, methods, params, extends/implements, generics). */
    private Map<String, Integer> dependencyUsage(TypeDeclaration<?> type) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        type.findAll(ClassOrInterfaceType.class).forEach(t -> {
            String name = t.getNameAsString();
            if (!name.equals(type.getNameAsString())) {
                counts.merge(name, 1, Integer::sum);
            }
        });
        return counts;
    }

    private List<String> dependencyNames(Map<String, Integer> usage) {
        return new ArrayList<>(new TreeSet<>(usage.keySet()));
    }

    private String access(AccessSpecifier spec) {
        return spec == AccessSpecifier.NONE ? "package-private" : spec.asString();
    }
}
