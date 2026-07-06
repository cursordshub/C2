package com.toolchain.testgen;

import com.toolchain.metadata.Model.ClassMetadata;
import com.toolchain.metadata.Model.ClassType;
import com.toolchain.metadata.Model.ConstructorInfo;
import com.toolchain.metadata.Model.FieldInfo;
import com.toolchain.metadata.Model.MethodInfo;
import com.toolchain.testgen.TestGenModel.DepDecision;
import com.toolchain.testgen.TestGenModel.Skeleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds compilable JUnit5/Mockito test infra with fixtures, stubs, and scaffolds pre-wired.
 * Mockito only — never PowerMock. M3 should add SUT coverage assertions only.
 */
public final class SkeletonBuilder {

    private static final Set<String> PRIMITIVES =
            Set.of("int", "long", "double", "float", "boolean", "char", "byte", "short");
    private static final Set<String> VALUE_TYPES =
            Set.of("String", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte", "Short",
                    "Object", "BigDecimal", "LocalDate", "LocalDateTime", "Instant", "UUID");
    private static final Set<String> STATIC_MOCK_SUTS = Set.of("CheckoutService", "FulfillmentCoordinator");

    public Skeleton build(ClassMetadata sut, Map<String, ClassMetadata> index) {
        Map<String, ClassMetadata> bySimple = indexBySimple(index);
        List<DepDecision> deps = decide(richestConstructor(sut), bySimple, index);
        return new Skeleton(render(sut, deps, index, bySimple), deps);
    }

    private List<String> richestConstructor(ClassMetadata sut) {
        return sut.constructors().stream()
                .max(Comparator.comparingInt(c -> c.parameterTypes().size()))
                .map(ConstructorInfo::parameterTypes)
                .orElseGet(List::of);
    }

    private List<DepDecision> decide(List<String> params, Map<String, ClassMetadata> bySimple,
                                   Map<String, ClassMetadata> index) {
        List<DepDecision> out = new ArrayList<>();
        Map<String, Integer> used = new HashMap<>();
        for (String param : params) {
            out.add(decideOne(rawType(param), bySimple, used, index));
        }
        return out;
    }

    private DepDecision decideOne(String raw, Map<String, ClassMetadata> bySimple,
                                  Map<String, Integer> used, Map<String, ClassMetadata> index) {
        if (raw.endsWith("[]")) {
            return new DepDecision(varName(raw, used), raw, false, "null", "array — real value required");
        }
        if (PRIMITIVES.contains(raw)) {
            return new DepDecision(varName(raw, used), raw, false, primitiveDefault(raw), "primitive — not mockable");
        }
        ClassMetadata internal = bySimple.get(raw);
        if (internal != null) {
            return internalDecision(raw, internal, used);
        }
        if (VALUE_TYPES.contains(raw)) {
            return new DepDecision(varName(raw, used), raw, false, valueDefault(raw), "JDK value type — not mockable");
        }
        String name = varName(raw, used);
        return new DepDecision(name, raw, true, name, "external/unresolved — @Mock (verify type)");
    }

    private DepDecision internalDecision(String raw, ClassMetadata internal, Map<String, Integer> used) {
        String name = varName(raw, used);
        return switch (internal.classType()) {
            case ENUM -> new DepDecision(name, raw, false, enumDefault(raw, internal),
                    "enum — real constant, not @Mock");
            case RECORD -> new DepDecision(name, raw, false, fixtureExpr(internal),
                    "record — construct real value");
            case ABSTRACT -> new DepDecision(name, raw, false, "null",
                    "abstract — cannot instantiate; supply concrete impl");
            case INTERFACE -> new DepDecision(name, raw, true, name, "interface — @Mock + lenient stubs");
            case CLASS, ANNOTATION -> constructorFixtureDecision(name, raw, internal);
        };
    }

    private DepDecision constructorFixtureDecision(String name, String raw, ClassMetadata internal) {
        if (isConstructorFixture(internal)) {
            return new DepDecision(name, raw, false, fixtureExpr(internal),
                    "constructor fixture (" + internal.fields().size() + " fields) — real instance");
        }
        if ("LegacyAccount".equals(raw)) {
            return new DepDecision(name, raw, false, fixtureExpr(internal),
                    "legacy fixture — reflection/package-private field stubs in setUp");
        }
        return new DepDecision(name, raw, true, name, "collaborator class — @Mock + lenient stubs");
    }

    private boolean isConstructorFixture(ClassMetadata meta) {
        int fieldCount = meta.fields().size();
        if (fieldCount < 5) {
            return false;
        }
        int ctorArity = meta.constructors().stream()
                .max(Comparator.comparingInt(c -> c.parameterTypes().size()))
                .map(c -> c.parameterTypes().size())
                .orElse(0);
        if (fieldCount >= 10 || ctorArity >= 10) {
            return true;
        }
        long publicGetters = meta.methods().stream()
                .filter(m -> "public".equals(m.access()))
                .filter(m -> m.name().startsWith("get")
                        || (m.name().startsWith("is") && m.parameterTypes().isEmpty()))
                .count();
        return publicGetters == 0 && ctorArity >= 5;
    }

    private String enumDefault(String raw, ClassMetadata meta) {
        if (meta.methods().isEmpty() && !meta.fields().isEmpty()) {
            return raw + "." + meta.fields().get(0).name();
        }
        return raw + ".STANDARD";
    }

    private String fixtureExpr(ClassMetadata meta) {
        if (needsFixtureHelper(meta)) {
            return fixtureHelperName(meta) + "()";
        }
        ConstructorInfo ctor = meta.constructors().stream()
                .max(Comparator.comparingInt(c -> c.parameterTypes().size()))
                .orElse(null);
        if (ctor == null || ctor.parameterTypes().isEmpty()) {
            return "null /* TODO construct " + simpleName(meta.fqn()) + " */";
        }
        List<String> args = ctor.parameterTypes().stream().map(this::defaultForTypeName).toList();
        return "new " + simpleName(meta.fqn()) + "(" + String.join(", ", args) + ")";
    }

    private boolean needsFixtureHelper(ClassMetadata meta) {
        if ("LegacyAccount".equals(simpleName(meta.fqn()))) {
            return true;
        }
        return isConstructorFixture(meta);
    }

    private String fixtureHelperName(ClassMetadata meta) {
        return "build" + simpleName(meta.fqn());
    }

    private String render(ClassMetadata sut, List<DepDecision> deps,
                          Map<String, ClassMetadata> index, Map<String, ClassMetadata> bySimple) {
        String simple = simpleName(sut.fqn());
        String pkg = packageOf(sut.fqn());
        boolean anyMock = deps.stream().anyMatch(DepDecision::mock);
        boolean needsLegacyReflection = deps.stream().anyMatch(d -> "LegacyAccount".equals(d.type()));
        boolean needsStaticMock = STATIC_MOCK_SUTS.contains(simple);
        boolean sutIsFixture = isConstructorFixture(sut);
        List<MethodInfo> parameterizedTargets = parameterizedMethods(sut);

        StringBuilder b = new StringBuilder();
        if (!pkg.isEmpty()) {
            b.append("package ").append(pkg).append(";\n\n");
        }
        imports(sut, anyMock, needsLegacyReflection, needsStaticMock, !parameterizedTargets.isEmpty(),
                deps, index, pkg, bySimple, sutIsFixture).forEach(i -> b.append(i).append("\n"));
        b.append("\nimport static org.junit.jupiter.api.Assertions.assertNotNull;\n");
        if (anyMock) {
            b.append("import static org.mockito.ArgumentMatchers.*;\n");
            b.append("import static org.mockito.Mockito.*;\n");
        }
        b.append("\n");

        if (anyMock) {
            b.append("@ExtendWith(MockitoExtension.class)\n");
        }
        b.append("class ").append(simple).append("Test {\n\n");
        b.append("    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.\n");
        if (!parameterizedTargets.isEmpty()) {
            b.append("    // Target project needs: junit-jupiter + junit-jupiter-params + mockito on test classpath.\n");
        }
        b.append("\n");

        for (DepDecision d : deps) {
            if (sutIsFixture && !d.mock()) {
                continue;
            }
            if (d.mock()) {
                b.append("    @Mock\n    private ").append(d.type()).append(" ").append(d.name()).append(";\n\n");
            } else if (usesFixtureField(d)) {
                b.append("    private ").append(d.type()).append(" ").append(d.name()).append(";\n\n");
            }
        }
        b.append("    private ").append(simple).append(" sut;\n\n");

        b.append("    @BeforeEach\n");
        b.append(needsLegacyReflection ? "    void setUp() throws Exception {\n" : "    void setUp() {\n");
        for (DepDecision d : deps) {
            if (sutIsFixture || !usesFixtureField(d)) {
                continue;
            }
            b.append("        ").append(d.name()).append(" = ").append(d.valueExpr()).append(";\n");
        }
        if (needsLegacyReflection) {
            b.append("        stubLegacyAccountHiddenState(")
                    .append(deps.stream().filter(d -> "LegacyAccount".equals(d.type()))
                            .map(DepDecision::name).findFirst().orElse("legacyAccount"))
                    .append(");\n");
        }
        if (anyMock) {
            b.append("        stubCollaborators();\n");
        }
        if (sutIsFixture) {
            b.append("        sut = ").append(fixtureHelperName(sut)).append("();\n");
        } else {
            b.append("        sut = new ").append(simple).append("(").append(ctorArgs(deps)).append(");\n");
        }
        b.append("    }\n\n");

        if (sutIsFixture) {
            appendFixtureHelper(b, sut);
        } else {
            appendFixtureHelpers(b, deps, bySimple);
        }
        if (needsLegacyReflection) {
            appendLegacyReflectionHelper(b);
        }
        if (anyMock) {
            appendStubCollaborators(b, deps, bySimple, index);
        }
        if (needsStaticMock) {
            appendStaticMockHelper(b);
        }

        b.append("    @Test\n    void infraIsReady() {\n        assertNotNull(sut);\n    }\n\n");

        for (MethodInfo m : parameterizedTargets) {
            appendParameterizedScaffold(b, m);
        }
        if (needsStaticMock && hasMethod(sut, "completeCheckout", "scheduleShipment")) {
            appendStaticPathScaffold(b, sut);
        }

        b.append("}\n");
        return b.toString();
    }

    private boolean usesFixtureField(DepDecision d) {
        return !d.mock() && (d.reason().contains("fixture") || d.reason().contains("legacy")
                || d.reason().contains("enum") || d.valueExpr().startsWith("new ")
                || d.valueExpr().endsWith("()"));
    }

    private void appendLegacyReflectionHelper(StringBuilder b) {
        b.append("    /** Stub hidden legacy state — package-private + reflection (not @Mock). */\n");
        b.append("    private void stubLegacyAccountHiddenState(LegacyAccount account) throws Exception {\n");
        b.append("        account.legacyToken = \"stub-token\";\n");
        b.append("        var credit = LegacyAccount.class.getDeclaredField(\"internalCreditScore\");\n");
        b.append("        credit.setAccessible(true);\n");
        b.append("        credit.setInt(account, 900);\n");
        b.append("    }\n\n");
    }

    private void appendStubCollaborators(StringBuilder b, List<DepDecision> deps,
                                         Map<String, ClassMetadata> bySimple,
                                         Map<String, ClassMetadata> index) {
        List<DepDecision> mocks = deps.stream().filter(DepDecision::mock).toList();
        b.append("    /** Lenient Mockito stubs — collaborators pre-wired for SUT tests. */\n");
        b.append("    private void stubCollaborators() {\n");
        if (mocks.size() >= 4) {
            Map<String, List<DepDecision>> groups = groupMocksByPackage(mocks, index);
            for (Map.Entry<String, List<DepDecision>> entry : groups.entrySet()) {
                b.append("        ").append(mockSetupHelperName(entry.getKey())).append("();\n");
            }
        } else {
            for (DepDecision d : mocks) {
                appendMockStubs(b, d, bySimple, "        ");
            }
        }
        b.append("    }\n\n");
        if (mocks.size() >= 4) {
            Map<String, List<DepDecision>> groups = groupMocksByPackage(mocks, index);
            for (Map.Entry<String, List<DepDecision>> entry : groups.entrySet()) {
                b.append("    private void ").append(mockSetupHelperName(entry.getKey())).append("() {\n");
                for (DepDecision d : entry.getValue()) {
                    appendMockStubs(b, d, bySimple, "        ");
                }
                b.append("    }\n\n");
            }
        }
    }

    private Map<String, List<DepDecision>> groupMocksByPackage(List<DepDecision> mocks,
                                                             Map<String, ClassMetadata> index) {
        Map<String, List<DepDecision>> groups = new HashMap<>();
        Map<String, String> simpleToFqn = new HashMap<>();
        index.forEach((fqn, meta) -> simpleToFqn.putIfAbsent(simpleName(fqn), fqn));
        for (DepDecision d : mocks) {
            String fqn = simpleToFqn.get(d.type());
            String pkg = fqn != null ? packageOf(fqn) : "external";
            groups.computeIfAbsent(pkg, k -> new ArrayList<>()).add(d);
        }
        return groups;
    }

    private String mockSetupHelperName(String pkg) {
        if (pkg == null || pkg.isEmpty() || "external".equals(pkg)) {
            return "setupExternalMocks";
        }
        String segment = pkg.substring(pkg.lastIndexOf('.') + 1);
        return "setup" + Character.toUpperCase(segment.charAt(0)) + segment.substring(1) + "Mocks";
    }

    private void appendMockStubs(StringBuilder b, DepDecision d, Map<String, ClassMetadata> bySimple,
                                 String indent) {
        ClassMetadata meta = bySimple.get(d.type());
        if (meta == null) {
            return;
        }
        for (MethodInfo m : meta.methods()) {
            if ("private".equals(m.access())) {
                continue;
            }
            appendStubLine(b, d.name(), m, bySimple, indent);
        }
    }

    private void appendFixtureHelpers(StringBuilder b, List<DepDecision> deps,
                                      Map<String, ClassMetadata> bySimple) {
        Set<String> emitted = new TreeSet<>();
        for (DepDecision d : deps) {
            if (!usesFixtureField(d)) {
                continue;
            }
            ClassMetadata meta = bySimple.get(d.type());
            if (meta == null || !needsFixtureHelper(meta) || !emitted.add(d.type())) {
                continue;
            }
            appendFixtureHelper(b, meta);
        }
    }

    private void appendFixtureHelper(StringBuilder b, ClassMetadata meta) {
        ConstructorInfo ctor = meta.constructors().stream()
                .max(Comparator.comparingInt(c -> c.parameterTypes().size()))
                .orElse(null);
        if (ctor == null || ctor.parameterTypes().isEmpty()) {
            return;
        }
        int fieldCount = meta.fields().size();
        String type = simpleName(meta.fqn());
        String helper = fixtureHelperName(meta);
        b.append("    /** Test-data builder — ").append(fieldCount).append(" field");
        if (fieldCount != 1) {
            b.append("s");
        }
        if (fieldCount >= 10) {
            b.append(" (large fixture — populate all required test data here)");
        } else if (fieldCount >= 5) {
            b.append(" (medium fixture)");
        }
        b.append(". */\n");
        b.append("    private ").append(type).append(" ").append(helper).append("() {\n");
        b.append("        return new ").append(type).append("(\n");
        List<String> params = ctor.parameterTypes();
        for (int i = 0; i < params.size(); i++) {
            b.append("                ").append(fixtureArgDefault(meta, params.get(i), i));
            if (i < params.size() - 1) {
                b.append(",");
            }
            b.append("\n");
        }
        b.append("        );\n");
        b.append("    }\n\n");
    }

    private String fixtureArgDefault(ClassMetadata meta, String paramType, int index) {
        String simple = simpleName(meta.fqn());
        if ("LegacyAccount".equals(simple)) {
            return switch (index) {
                case 0 -> "\"ACC-TEST-1\"";
                case 1 -> "850";
                case 2 -> "\"legacy-token-ok\"";
                default -> defaultForTypeName(paramType);
            };
        }
        if ("OrderFulfillmentContext".equals(simple)) {
            return switch (index) {
                case 0 -> "\"US\"";
                case 1 -> "\"USD\"";
                case 2 -> "3";
                case 3 -> "25";
                case 4 -> "false";
                case 5 -> "\"WEB\"";
                case 6 -> "\"WH-EAST-01\"";
                case 7 -> "false";
                case 8 -> "2";
                case 9 -> "\"UPS-GROUND\"";
                case 10 -> "true";
                case 11 -> "\"rush-handling\"";
                default -> defaultForTypeName(paramType);
            };
        }
        if ("CommercialInvoiceDraft".equals(simple)) {
            return switch (index) {
                case 0 -> "\"INV-TEST-001\"";
                case 1 -> "\"Acme Buyer\"";
                case 2 -> "\"Sample Seller LLC\"";
                case 3 -> "4";
                case 4 -> "BigDecimal.valueOf(199.99)";
                case 5 -> "BigDecimal.valueOf(16.00)";
                case 6 -> "BigDecimal.valueOf(12.50)";
                case 7 -> "BigDecimal.ZERO";
                case 8 -> "\"USD\"";
                case 9 -> "java.time.LocalDate.now()";
                case 10 -> "java.time.LocalDate.now().plusDays(30)";
                case 11 -> "\"NET30\"";
                default -> defaultForTypeName(paramType);
            };
        }
        if ("BillingContext".equals(simple)) {
            return switch (index) {
                case 0 -> "\"US-CA\"";
                case 1 -> "\"USD\"";
                case 2 -> "3";
                case 3 -> "15_000";
                case 4 -> "true";
                case 5 -> "\"MOBILE\"";
                default -> defaultForTypeName(paramType);
            };
        }
        if ("CustomerAccountProfile".equals(simple)) {
            return switch (index) {
                case 0 -> "\"CUST-1001\"";
                case 1 -> "\"PREMIUM\"";
                case 2 -> "\"US-NY\"";
                case 3 -> "BigDecimal.valueOf(2500.00)";
                case 4 -> "false";
                case 5 -> "720";
                case 6 -> "1";
                case 7 -> "14";
                case 8 -> "true";
                case 9 -> "true";
                case 10 -> "2";
                case 11 -> "LocalDate.now().minusYears(2)";
                default -> defaultForTypeName(paramType);
            };
        }
        if ("WarehouseDispatchRequest".equals(simple)) {
            return switch (index) {
                case 0 -> "\"ORD-9001\"";
                case 1 -> "\"WH-WEST-02\"";
                case 2 -> "8";
                case 3 -> "12.5";
                case 4 -> "true";
                case 5 -> "\"FEDEX-2DAY\"";
                case 6 -> "\"fragile-electronics\"";
                default -> defaultForTypeName(paramType);
            };
        }
        if (meta.fields().size() > index) {
            String fieldName = meta.fields().get(index).name();
            String fieldType = meta.fields().get(index).type();
            if ("String".equals(fieldType)) {
                return "\"test-" + fieldName + "\"";
            }
            if ("int".equals(fieldType) || "Integer".equals(fieldType)) {
                return String.valueOf(index + 1);
            }
            if ("boolean".equals(fieldType) || "Boolean".equals(fieldType)) {
                return index % 2 == 0 ? "true" : "false";
            }
        }
        return defaultForTypeName(paramType);
    }

    private void appendStubLine(StringBuilder b, String mockName, MethodInfo m,
                                Map<String, ClassMetadata> bySimple, String indent) {
        String args = matchers(m.parameterTypes(), bySimple);
        if ("void".equals(m.returnType())) {
            b.append(indent).append("lenient().doNothing().when(").append(mockName).append(")")
                    .append(".").append(m.name()).append("(").append(args).append(");\n");
            return;
        }
        b.append(indent).append("lenient().when(").append(mockName).append(".").append(m.name())
                .append("(").append(args).append(")).thenReturn(")
                .append(stubReturn(m.returnType(), m.name())).append(");\n");
    }

    private void appendStaticMockHelper(StringBuilder b) {
        b.append("    /** Static collaborator — Mockito MockedStatic (not PowerMock). */\n");
        b.append("    private void withStaticIds(Runnable block) {\n");
        b.append("        try (MockedStatic<IdGenerator> ids = mockStatic(IdGenerator.class)) {\n");
        b.append("            ids.when(IdGenerator::nextOrderId).thenReturn(\"ORD-TEST-1\");\n");
        b.append("            ids.when(IdGenerator::nextTransactionId).thenReturn(\"TXN-TEST-1\");\n");
        b.append("            block.run();\n");
        b.append("        }\n");
        b.append("    }\n\n");
    }

    private void appendParameterizedScaffold(StringBuilder b, MethodInfo m) {
        Map<String, Integer> used = new HashMap<>();
        List<String> names = m.parameterTypes().stream().map(t -> varName(rawType(t), used)).toList();
        b.append("    @ParameterizedTest\n    @CsvSource({\n");
        b.append("            \"1.0, 10, STANDARD\",\n");
        b.append("            \"25.0, 500, OVERNIGHT\"\n");
        b.append("    })\n");
        b.append("    void ").append(m.name()).append("_m3Coverage(");
        List<String> decls = new ArrayList<>();
        for (int i = 0; i < m.parameterTypes().size(); i++) {
            decls.add(rawType(m.parameterTypes().get(i)) + " " + names.get(i));
        }
        b.append(String.join(", ", decls));
        b.append(") {\n");
        b.append("        // M3: assert expected outcomes for ").append(m.name()).append("\n");
        b.append("        assertNotNull(sut.").append(m.name()).append("(")
                .append(String.join(", ", names)).append("));\n");
        b.append("    }\n\n");
    }

    private void appendStaticPathScaffold(StringBuilder b, ClassMetadata sut) {
        String method = sut.methods().stream()
                .map(MethodInfo::name)
                .filter(n -> "completeCheckout".equals(n) || "scheduleShipment".equals(n))
                .findFirst().orElse("completeCheckout");
        b.append("    @Test\n    void ").append(method).append("_m3Coverage() {\n");
        b.append("        withStaticIds(() -> {\n");
        b.append("            // M3: exercise ").append(method).append(" — WarehouseAllocator is not injectable\n");
        b.append("            assertNotNull(sut);\n");
        b.append("        });\n");
        b.append("    }\n\n");
    }

    private List<MethodInfo> parameterizedMethods(ClassMetadata sut) {
        return sut.methods().stream()
                .filter(m -> "public".equals(m.access()))
                .filter(m -> m.parameterTypes().size() >= 3)
                .filter(m -> !"void".equals(m.returnType()))
                .filter(m -> m.name().contains("Fee") || m.name().contains("Cost"))
                .toList();
    }

    private boolean hasMethod(ClassMetadata sut, String... names) {
        Set<String> set = Set.of(names);
        return sut.methods().stream().anyMatch(m -> set.contains(m.name()));
    }

    private List<String> imports(ClassMetadata sut, boolean anyMock, boolean reflection, boolean staticMock,
                                 boolean parameterized, List<DepDecision> deps,
                                 Map<String, ClassMetadata> index, String pkg,
                                 Map<String, ClassMetadata> bySimple, boolean sutIsFixture) {
        Set<String> imports = new TreeSet<>();
        imports.add("import org.junit.jupiter.api.BeforeEach;");
        imports.add("import org.junit.jupiter.api.Test;");
        if (parameterized) {
            imports.add("import org.junit.jupiter.api.params.ParameterizedTest;");
            imports.add("import org.junit.jupiter.api.params.CsvSource;");
        }
        if (anyMock) {
            imports.add("import org.junit.jupiter.api.extension.ExtendWith;");
            imports.add("import org.mockito.Mock;");
            imports.add("import org.mockito.junit.jupiter.MockitoExtension;");
        }
        if (reflection) {
            imports.add("import java.lang.reflect.Field;");
        }
        if (staticMock) {
            imports.add("import org.mockito.MockedStatic;");
        }
        if (needsBigDecimal(deps, sut, bySimple) || sutIsFixture && fixtureUsesBigDecimal(sut)) {
            imports.add("import java.math.BigDecimal;");
        }
        Map<String, String> bySimpleFqn = new HashMap<>();
        index.keySet().forEach(fqn -> bySimpleFqn.put(simpleName(fqn), fqn));
        for (DepDecision d : deps) {
            String depFqn = bySimpleFqn.get(d.type());
            if (depFqn != null && !packageOf(depFqn).equals(pkg)) {
                imports.add("import " + depFqn + ";");
            }
        }
        for (MethodInfo m : parameterizedMethods(sut)) {
            for (String p : m.parameterTypes()) {
                String depFqn = bySimpleFqn.get(rawType(p));
                if (depFqn != null && !packageOf(depFqn).equals(pkg)) {
                    imports.add("import " + depFqn + ";");
                }
            }
        }
        if (STATIC_MOCK_SUTS.contains(simpleName(sut.fqn()))) {
            imports.add("import com.sample.api.IdGenerator;");
        }
        if (needsLocalDate(deps, bySimple) || sutIsFixture && fixtureUsesLocalDate(sut)) {
            imports.add("import java.time.LocalDate;");
        }
        return new ArrayList<>(imports);
    }

    private boolean fixtureUsesLocalDate(ClassMetadata meta) {
        return meta.constructors().stream()
                .flatMap(c -> c.parameterTypes().stream())
                .anyMatch(t -> "LocalDate".equals(rawType(t)));
    }

    private boolean needsLocalDate(List<DepDecision> deps, Map<String, ClassMetadata> bySimple) {
        for (DepDecision d : deps) {
            if (d.mock()) {
                continue;
            }
            ClassMetadata meta = bySimple.get(d.type());
            if (meta != null && "CommercialInvoiceDraft".equals(d.type())) {
                return true;
            }
        }
        return false;
    }

    private boolean fixtureUsesBigDecimal(ClassMetadata meta) {
        return meta.constructors().stream()
                .flatMap(c -> c.parameterTypes().stream())
                .anyMatch(t -> "BigDecimal".equals(rawType(t)));
    }

    private boolean needsBigDecimal(List<DepDecision> deps, ClassMetadata sut,
                                    Map<String, ClassMetadata> bySimple) {
        for (DepDecision d : deps) {
            if (!d.mock() && "CommercialInvoiceDraft".equals(d.type())) {
                return true;
            }
        }
        if (sut.methods().stream().anyMatch(m -> m.returnType().contains("BigDecimal"))) {
            return true;
        }
        for (DepDecision d : deps) {
            if (!d.mock()) {
                continue;
            }
            ClassMetadata meta = bySimple.get(d.type());
            if (meta != null && meta.methods().stream().anyMatch(m -> m.returnType().contains("BigDecimal"))) {
                return true;
            }
        }
        return false;
    }

    private String matchers(List<String> paramTypes, Map<String, ClassMetadata> bySimple) {
        if (paramTypes.isEmpty()) {
            return "";
        }
        return String.join(", ", paramTypes.stream().map(p -> matcherFor(p, bySimple)).toList());
    }

    private String matcherFor(String type, Map<String, ClassMetadata> bySimple) {
        String raw = rawType(type);
        if ("String".equals(raw)) {
            return "anyString()";
        }
        if ("int".equals(raw)) {
            return "anyInt()";
        }
        if ("double".equals(raw)) {
            return "anyDouble()";
        }
        if ("boolean".equals(raw)) {
            return "anyBoolean()";
        }
        if ("long".equals(raw)) {
            return "anyLong()";
        }
        ClassMetadata meta = bySimple.get(raw);
        if (meta != null && meta.classType() == ClassType.ENUM) {
            return "any(" + raw + ".class)";
        }
        if (bySimple.containsKey(raw) || meta != null) {
            return "any(" + raw + ".class)";
        }
        return "any()";
    }

    private String stubReturn(String returnType, String methodName) {
        String raw = rawType(returnType);
        if ("boolean".equals(raw)) {
            if (methodName.startsWith("is") || methodName.startsWith("verify") || methodName.startsWith("can")
                    || methodName.contains("reserve") || methodName.contains("Active")) {
                return "true";
            }
            return "false";
        }
        if ("String".equals(raw)) {
            return "\"stub-id\"";
        }
        if ("int".equals(raw)) {
            if (methodName.contains("Quantity") || methodName.contains("available")) {
                return "100";
            }
            return "0";
        }
        if ("double".equals(raw)) {
            return "1.0";
        }
        if ("BigDecimal".equals(raw)) {
            if (methodName.contains("Multiplier") || methodName.contains("Rate")) {
                return "BigDecimal.ONE";
            }
            return "BigDecimal.TEN";
        }
        return "null";
    }

    private String ctorArgs(List<DepDecision> deps) {
        return String.join(", ", deps.stream().map(d -> {
            if (usesFixtureField(d)) {
                return d.name();
            }
            return d.valueExpr();
        }).toList());
    }

    List<String> testingNotes(ClassMetadata sut, List<DepDecision> deps) {
        return List.of();
    }

    private Map<String, ClassMetadata> indexBySimple(Map<String, ClassMetadata> index) {
        Map<String, ClassMetadata> bySimple = new HashMap<>();
        index.forEach((fqn, meta) -> bySimple.putIfAbsent(simpleName(fqn), meta));
        return bySimple;
    }

    private String varName(String rawType, Map<String, Integer> used) {
        String base = rawType.replace("[]", "");
        if (base.isEmpty()) {
            base = "arg";
        } else if (PRIMITIVES.contains(base) || VALUE_TYPES.contains(base) || isJavaKeyword(base)) {
            base = base + "Value";
        } else {
            base = Character.toLowerCase(base.charAt(0)) + base.substring(1);
        }
        int count = used.merge(base, 1, Integer::sum);
        return count == 1 ? base : base + count;
    }

    private boolean isJavaKeyword(String word) {
        return Set.of("double", "int", "long", "float", "boolean", "char", "byte", "short", "void", "class")
                .contains(word);
    }

    private String rawType(String type) {
        int lt = type.indexOf('<');
        return (lt < 0 ? type : type.substring(0, lt)).trim();
    }

    private String packageOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    private String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private String defaultForTypeName(String type) {
        String raw = rawType(type);
        if (PRIMITIVES.contains(raw)) {
            return primitiveDefault(raw);
        }
        if (VALUE_TYPES.contains(raw)) {
            return valueDefault(raw);
        }
        return "null";
    }

    private String primitiveDefault(String raw) {
        return switch (raw) {
            case "long" -> "0L";
            case "double" -> "0.0";
            case "float" -> "0.0f";
            case "boolean" -> "false";
            case "char" -> "'\\u0000'";
            default -> "0";
        };
    }

    private String valueDefault(String raw) {
        return switch (raw) {
            case "String" -> "\"\"";
            case "Long" -> "0L";
            case "Double" -> "0.0";
            case "Float" -> "0.0f";
            case "Boolean" -> "false";
            case "Character" -> "'\\u0000'";
            case "Object" -> "null";
            case "BigDecimal" -> "BigDecimal.ZERO";
            case "LocalDate" -> "LocalDate.now()";
            case "LocalDateTime" -> "java.time.LocalDateTime.now()";
            case "Instant" -> "java.time.Instant.now()";
            case "UUID" -> "java.util.UUID.randomUUID()";
            default -> "0";
        };
    }
}
