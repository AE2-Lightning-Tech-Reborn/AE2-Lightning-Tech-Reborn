package com.moakiee.ae2lt.compat.gto;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/** Resource-only discovery: never define an optional addon, JEI or game class during Mixin setup. */
public final class GtoUselessJeiAbi {
    public static final String PLUGIN = "com.sorrowmist.useless.compat.jei.JEIPlugin";
    private static final String LEGACY = "appeng/api/integrations/jei/";
    private static final String LOCAL = "com/moakiee/ae2lt/compat/gto/jei/";
    public static final Map<String, String> TYPES = Map.of(
            LEGACY + "IngredientConverter", LOCAL + "GtoIngredientConverter",
            LEGACY + "IngredientConverters", LOCAL + "GtoIngredientConverters",
            "appeng/integration/modules/jei/ItemIngredientConverter", LOCAL + "GtoItemIngredientConverter",
            "appeng/integration/modules/jei/FluidIngredientConverter", LOCAL + "GtoFluidIngredientConverter");
    private static volatile Selection selection = new Selection(false, false);

    private GtoUselessJeiAbi() { }

    public record Selection(boolean restorePlugin, boolean remapAbi) { }

    public static boolean configure(boolean eligible, String aeVersion, String uselessVersion, ClassLoader loader) {
        selection = select(eligible, aeVersion, uselessVersion, name -> {
            try (var input = loader.getResourceAsStream(name + ".class")) {
                return input == null ? null : input.readAllBytes();
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot inspect optional Useless JEI ABI: " + name, failure);
            }
        });
        return selection.remapAbi();
    }

    public static boolean restorePlugin() { return selection.restorePlugin(); }

    static Selection select(boolean eligible, String aeVersion, String uselessVersion,
            Function<String, byte[]> resources) {
        if (!eligible || aeVersion == null || uselessVersion == null
                || !Set.of("15.2610.31", "15.2610.40").contains(aeVersion)
                || !Set.of("20.2.4.7", "20.2.4.7-gto.1").contains(uselessVersion)) {
            return new Selection(false, false);
        }
        var present = new LinkedHashMap<String, byte[]>();
        for (var name : TYPES.keySet()) {
            var bytes = resources.apply(name);
            if (bytes != null) present.put(name, bytes);
        }
        if (present.isEmpty()) return new Selection(true, true);
        if (present.size() != TYPES.size()) {
            throw new IllegalStateException("Partial AE2 legacy JEI API; cannot adapt Useless safely: " + present.keySet());
        }
        for (var entry : present.entrySet()) validateLegacyType(entry.getKey(), entry.getValue());
        return new Selection(true, false);
    }

    private static void validateLegacyType(String name, byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!name.equals(node.name)) throw new IllegalStateException("Legacy JEI class name mismatch: " + name);
        if ((node.access & Opcodes.ACC_PUBLIC) == 0) {
            throw new IllegalStateException("Legacy JEI class is not public: " + name);
        }
        String converter = "L" + LEGACY + "IngredientConverter;";
        if (name.equals(LEGACY + "IngredientConverter")) {
            if ((node.access & Opcodes.ACC_INTERFACE) == 0) throw new IllegalStateException("Legacy JEI converter is not an interface");
            method(node, "getIngredientType", "()Lmezz/jei/api/ingredients/IIngredientType;", false);
            method(node, "getIngredientFromStack", "(Lappeng/api/stacks/GenericStack;)Ljava/lang/Object;", false);
            method(node, "getStackFromIngredient", "(Ljava/lang/Object;)Lappeng/api/stacks/GenericStack;", false);
        } else if (name.equals(LEGACY + "IngredientConverters")) {
            method(node, "register", "(" + converter + ")Z", true);
            method(node, "getConverters", "()Ljava/util/List;", true);
            method(node, "getConverter", "(Lmezz/jei/api/ingredients/IIngredientType;)" + converter, true);
        } else {
            if (!node.interfaces.contains(LEGACY + "IngredientConverter")) {
                throw new IllegalStateException("Legacy JEI concrete converter has an unexpected interface: " + name);
            }
            method(node, "<init>", "()V", false);
            method(node, "getIngredientType", "()Lmezz/jei/api/ingredients/IIngredientType;", false);
            method(node, "getIngredientFromStack", "(Lappeng/api/stacks/GenericStack;)Ljava/lang/Object;", false);
            method(node, "getStackFromIngredient", "(Ljava/lang/Object;)Lappeng/api/stacks/GenericStack;", false);
        }
    }

    static void method(ClassNode node, String name, String descriptor, boolean isStatic) {
        long count = node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)
                && (method.access & Opcodes.ACC_PUBLIC) != 0
                && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic).count();
        if (count != 1) throw new IllegalStateException("Unexpected legacy Useless JEI method: "
                + node.name + "." + name + descriptor + " matches=" + count);
    }
}
