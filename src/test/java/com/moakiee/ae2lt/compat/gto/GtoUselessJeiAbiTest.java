package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class GtoUselessJeiAbiTest {
    private static final String CONVERTER = "appeng/api/integrations/jei/IngredientConverter";

    @ParameterizedTest
    @CsvSource({"15.2610.31,20.2.4.7", "15.2610.31,20.2.4.7-gto.1",
            "15.2610.40,20.2.4.7", "15.2610.40,20.2.4.7-gto.1"})
    void absentLegacyApiRestoresPluginAndAdaptsBothAuditedAeReleases(String ae, String useless) {
        var seen = new java.util.HashSet<String>();
        var result = GtoUselessJeiAbi.select(true, ae, useless, name -> {
            seen.add(name);
            return null;
        });
        assertEquals(new GtoUselessJeiAbi.Selection(true, true), result);
        assertEquals(GtoUselessJeiAbi.TYPES.keySet(), seen);
    }

    @Test
    void unsupportedOrIneligibleEnvironmentsNeverReadOptionalResources() {
        Function<String, byte[]> forbidden = name -> { throw new AssertionError("resolved " + name); };
        assertEquals(new GtoUselessJeiAbi.Selection(false, false),
                GtoUselessJeiAbi.select(false, "15.2610.31", "20.2.4.7-gto.1", forbidden));
        for (String ae : new String[] {null, "", "15.4.11", "15.2610.41"}) {
            assertEquals(new GtoUselessJeiAbi.Selection(false, false),
                    GtoUselessJeiAbi.select(true, ae, "20.2.4.7-gto.1", forbidden));
        }
        for (String useless : new String[] {null, "", "20.2.4.6", "20.2.4.8"}) {
            assertEquals(new GtoUselessJeiAbi.Selection(false, false),
                    GtoUselessJeiAbi.select(true, "15.2610.40", useless, forbidden));
        }
    }

    @Test
    void completeCompatibleLegacyApiKeepsOriginalTypesAndOnlyRestoresDiscovery() {
        var resources = completeApi();
        assertEquals(new GtoUselessJeiAbi.Selection(true, false),
                GtoUselessJeiAbi.select(true, "15.2610.40", "20.2.4.7-gto.1", resources::get));
    }

    @Test
    void everyPartialApiCombinationFailsExplicitly() {
        var resources = completeApi();
        var names = resources.keySet().toArray(String[]::new);
        for (int mask = 1; mask < 15; mask++) {
            var selected = new LinkedHashMap<String, byte[]>();
            for (int index = 0; index < names.length; index++) {
                if ((mask & (1 << index)) != 0) selected.put(names[index], resources.get(names[index]));
            }
            var failure = assertThrows(IllegalStateException.class, () ->
                    GtoUselessJeiAbi.select(true, "15.2610.31", "20.2.4.7", selected::get));
            assertTrue(failure.getMessage().contains("Partial AE2 legacy JEI API"));
        }
    }

    @Test
    void changedPublicProtocolFailsInsteadOfAssumingCompatibilityFromClassNames() {
        for (boolean publicMethods : new boolean[] {false, true}) {
            var resources = completeApi();
            resources.put(CONVERTER, protocolClass(CONVERTER, publicMethods, publicMethods));
            assertThrows(IllegalStateException.class, () ->
                    GtoUselessJeiAbi.select(true, "15.2610.40", "20.2.4.7-gto.1", resources::get));
        }
    }

    static Map<String, byte[]> completeApi() {
        var result = new LinkedHashMap<String, byte[]>();
        for (String name : GtoUselessJeiAbi.TYPES.keySet()) result.put(name, protocolClass(name, true, false));
        return result;
    }

    private static byte[] protocolClass(String name, boolean publicMethods, boolean wrongReturn) {
        var writer = new ClassWriter(0);
        boolean protocol = name.equals(CONVERTER);
        boolean registry = name.endsWith("/IngredientConverters");
        int flags = Opcodes.ACC_PUBLIC | (protocol ? Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT : 0);
        writer.visit(Opcodes.V17, flags, name, null, "java/lang/Object",
                !protocol && !registry ? new String[] {CONVERTER} : null);
        int access = publicMethods ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE;
        if (registry) {
            method(writer, access | Opcodes.ACC_STATIC, "register", "(L" + CONVERTER + ";)Z");
            method(writer, access | Opcodes.ACC_STATIC, "getConverters", "()Ljava/util/List;");
            method(writer, access | Opcodes.ACC_STATIC, "getConverter",
                    "(Lmezz/jei/api/ingredients/IIngredientType;)L" + CONVERTER + ";");
        } else {
            if (!protocol) method(writer, access, "<init>", "()V");
            method(writer, access, "getIngredientType", "()Lmezz/jei/api/ingredients/IIngredientType;");
            method(writer, access, "getIngredientFromStack", "(Lappeng/api/stacks/GenericStack;)"
                    + (wrongReturn ? "Ljava/lang/String;" : "Ljava/lang/Object;"));
            method(writer, access, "getStackFromIngredient", "(Ljava/lang/Object;)Lappeng/api/stacks/GenericStack;");
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void method(ClassWriter writer, int access, String name, String descriptor) {
        // Resource-only ABI fixtures are inspected by ASM; they are never defined in a JVM.
        writer.visitMethod(access, name, descriptor, null, null).visitEnd();
    }
}
