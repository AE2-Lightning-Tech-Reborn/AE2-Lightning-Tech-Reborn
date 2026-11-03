package com.moakiee.ae2lt.compat.gto;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.util.ClassSignature;

/** Rebind exactly the three audited Useless classes; retain their original behavior and AEKey type. */
public final class GtoUselessJeiTransform {
    private static final String PREFIX = "com/sorrowmist/useless/compat/jei/";
    private static final String CONVERTER = "appeng/api/integrations/jei/IngredientConverter";
    public static final Set<String> TARGETS = Set.of(PREFIX + "GenericStackJeiIngredientProviders",
            PREFIX + "AEKeyJeiIngredients", PREFIX + "AEKeyJeiIngredients$2");
    private static final Remapper REMAPPER = new Remapper() {
        @Override public String map(String name) { return GtoUselessJeiAbi.TYPES.getOrDefault(name, name); }
    };

    private GtoUselessJeiTransform() { }

    public static void apply(ClassNode node) {
        if (!TARGETS.contains(node.name)) throw new IllegalStateException("Unaudited Useless JEI target: " + node.name);
        validateOriginal(node);
        var mapped = new ClassNode();
        node.accept(new ClassRemapper(mapped, REMAPPER));
        audit(mapped);
        try {
            // Copy the complete ASM node, including frames, generic signatures and annotations.
            for (var field : ClassNode.class.getFields()) {
                if (field.getDeclaringClass() == ClassNode.class && !Modifier.isStatic(field.getModifiers())) {
                    field.set(node, field.get(mapped));
                }
            }
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot publish the complete Useless JEI class transform", failure);
        }
    }

    private static void validateOriginal(ClassNode node) {
        if (node.name.endsWith("GenericStackJeiIngredientProviders")) {
            field(node, "ITEMS", "Lappeng/integration/modules/jei/ItemIngredientConverter;");
            field(node, "FLUIDS", "Lappeng/integration/modules/jei/FluidIngredientConverter;");
            if (node.methods.stream().filter(method -> method.name.equals("convert") && method.desc.equals(
                    "(Lappeng/api/stacks/GenericStack;L" + CONVERTER + ";)L" + PREFIX
                            + "GenericStackJeiIngredientProvider$Ingredient;")).count() != 1) {
                throw new IllegalStateException("Useless GenericStack JEI conversion contract changed");
            }
        } else if (node.name.endsWith("AEKeyJeiIngredients")) {
            field(node, "CONVERTER", "L" + CONVERTER + ";");
            GtoUselessJeiAbi.method(node, "register",
                    "(Lmezz/jei/api/registration/IModIngredientRegistration;Ljava/nio/file/Path;)V", true);
        } else {
            if (!node.interfaces.equals(java.util.List.of(CONVERTER))) {
                throw new IllegalStateException("Useless AEKey converter interfaces changed: " + node.interfaces);
            }
            GtoUselessJeiAbi.method(node, "getIngredientType", "()Lmezz/jei/api/ingredients/IIngredientType;", false);
            GtoUselessJeiAbi.method(node, "getIngredientFromStack", "(Lappeng/api/stacks/GenericStack;)Ljava/lang/Object;", false);
            GtoUselessJeiAbi.method(node, "getStackFromIngredient", "(Ljava/lang/Object;)Lappeng/api/stacks/GenericStack;", false);
        }
    }

    private static void field(ClassNode node, String name, String descriptor) {
        if (node.fields.stream().filter(field -> field.name.equals(name) && field.desc.equals(descriptor)).count() != 1) {
            throw new IllegalStateException("Useless JEI field contract changed: " + node.name + "." + name);
        }
    }

    public static void audit(ClassNode node) {
        var writer = new ClassWriter(0);
        node.accept(writer);
        String completeClass = new String(writer.toByteArray(), StandardCharsets.ISO_8859_1);
        if (completeClass.contains("appeng/api/integrations/jei/")
                || completeClass.contains("appeng/integration/modules/jei/")) {
            throw new IllegalStateException("Unmapped legacy JEI reference remains in " + node.name);
        }
    }

    /** Mixin builds ClassInfo before preApply. Update its active descriptors, sets and signature as well. */
    public static void synchronizeClassInfo(Object info, ClassNode node) {
        try {
            var type = info.getClass();
            var interfaces = type.getDeclaredField("interfaces");
            interfaces.setAccessible(true);
            @SuppressWarnings("unchecked") var cached = (Set<String>) interfaces.get(info);
            var mappedInterfaces = new LinkedHashSet<String>();
            for (String name : cached) mappedInterfaces.add(REMAPPER.mapType(name));
            if (!mappedInterfaces.equals(new LinkedHashSet<>(node.interfaces))) {
                throw new IllegalStateException("Useless JEI Mixin interface cache is ambiguous: " + node.name);
            }
            cached.clear();
            cached.addAll(mappedInterfaces);
            remapMembers(info, "fields", false, node);
            remapMembers(info, "methods", true, node);
            var signature = type.getDeclaredField("signature");
            signature.setAccessible(true);
            signature.set(info, ClassSignature.of(node));
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException("Cannot synchronize Mixin's Useless JEI class metadata", failure);
        }
    }

    /** TargetClassContext retains its own preApply signature reference and writes it back after merging. */
    public static void synchronizeTargetSignature(Object context, ClassNode node) {
        if (!TARGETS.contains(node.name)) throw new IllegalStateException("Unaudited Useless JEI target: " + node.name);
        try {
            var field = context.getClass().getDeclaredField("signature");
            field.setAccessible(true);
            var previous = (ClassSignature) field.get(context);
            var mapped = ClassSignature.of(node);
            if (!REMAPPER.mapSignature(previous.toString(), false).equals(mapped.toString())) {
                throw new IllegalStateException("Useless JEI target signature cache is ambiguous: " + node.name);
            }
            field.set(context, mapped);
            if (field.get(context) != mapped) throw new IllegalStateException("Target signature remap was ignored");
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException("Cannot synchronize Mixin's Useless JEI target signature", failure);
        }
    }

    private static void remapMembers(Object info, String fieldName, boolean methods, ClassNode node)
            throws ReflectiveOperationException {
        var field = info.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var members = (Set<Object>) field.get(info);
        var values = new ArrayList<>(members);
        // Mixin Member.hashCode may depend on the active descriptor; rebuild the owning set.
        members.clear();
        for (Object member : values) {
            var getter = member.getClass().getMethod("getDesc");
            var nameGetter = member.getClass().getMethod("getName");
            var remap = member.getClass().getMethod("remapTo", String.class);
            getter.setAccessible(true);
            nameGetter.setAccessible(true);
            remap.setAccessible(true);
            var descriptor = (String) getter.invoke(member);
            var mapped = methods ? REMAPPER.mapMethodDesc(descriptor) : REMAPPER.mapDesc(descriptor);
            var name = (String) nameGetter.invoke(member);
            boolean matches = methods
                    ? node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(mapped))
                    : node.fields.stream().anyMatch(value -> value.name.equals(name) && value.desc.equals(mapped));
            if (!matches) throw new IllegalStateException("Useless JEI Mixin member cache is ambiguous: "
                    + node.name + "." + name + mapped);
            remap.invoke(member, mapped);
            if (!mapped.equals(getter.invoke(member))) throw new IllegalStateException("Mixin descriptor remap was ignored");
            members.add(member);
        }
    }
}
