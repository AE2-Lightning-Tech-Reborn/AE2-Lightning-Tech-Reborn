package com.moakiee.ae2lt.compat.gto;

import java.util.function.Consumer;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.*;

/** Retain EAEP's upgrades, wireless links and migration; replace only its obsolete job access. */
public final class GtoEaepMixinExtension implements IExtension {
    private static final String PROVIDER_MIXIN =
            "com.extendedae_plus.mixin.ae2.compat.PatternProviderLogicCompatMixin";
    private static final String EAEP = "com.extendedae_plus.mixin.ae2.";
    private static final Set<String> TARGETS = Set.of(
            "appeng/helpers/patternprovider/PatternProviderLogic",
            "appeng/menu/me/items/PatternEncodingTermMenu",
            "appeng/menu/me/crafting/CraftingCPUMenu");

    public static void register() {
        var transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        ((Extensions) transformer.getExtensions()).add(new GtoEaepMixinExtension());
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    @Override public void preApply(ITargetClassContext context) {
        if (!TARGETS.contains(context.getClassNode().name)) return;
        try {
            var loader = getClass().getClassLoader();
            var contextApi = Class.forName("com.bawnorton.mixinsquared.reflection.TargetClassContextExtension", true, loader);
            Consumer<Object> transform = extension -> {
                try {
                    var mixins = (Iterable<?>) contextApi.getMethod("getMixins").invoke(extension);
                    for (var entry : mixins) {
                        var mixin = (IMixinInfo) entry;
                        var name = mixin.getClassName();
                        if (!name.equals(PROVIDER_MIXIN)
                                && !name.equals(EAEP + "helpers.patternprovider.PatternProviderLogicAdvancedMixin")
                                && !name.equals(EAEP + "menu.PatternEncodingTermMenuMixin")
                                && !name.equals(EAEP + "accessor.PatternEncodingTermMenuAccessor")
                                && !name.equals(EAEP + "menu.CraftingCPUMenuManualStatusMixin")) continue;
                        var node = mixin.getClassNode(0);
                        if (name.equals(PROVIDER_MIXIN)) adapt(node);
                        else if (name.endsWith(".PatternProviderLogicAdvancedMixin")) adaptAdvancedBlocking(node);
                        else if (name.endsWith(".PatternEncodingTermMenuAccessor")) adaptEncodingAccessor(node);
                        else if (name.endsWith(".PatternEncodingTermMenuMixin")) adaptEncoding(node, context.getClassNode());
                        else adaptCpuMenu(node, context.getClassNode());
                        publish(loader, mixin, node);
                    }
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot adapt EAEP provider mixin", failure);
                }
            };
            contextApi.getMethod("tryAs", ITargetClassContext.class, Consumer.class).invoke(null, context, transform);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("GTO MixinSquared reflection API changed", failure);
        }
    }

    private static void publish(ClassLoader loader, IMixinInfo mixin, ClassNode node)
            throws ReflectiveOperationException {
        var mixinApi = Class.forName("com.bawnorton.mixinsquared.reflection.MixinInfoExtension", true, loader);
        var stateApi = Class.forName("com.bawnorton.mixinsquared.reflection.StateExtension", true, loader);
        Consumer<Object> stateConsumer = state -> {
            try { stateApi.getMethod("setClassNode", ClassNode.class).invoke(state, node); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        };
        Consumer<Object> mixinConsumer = extension -> {
            try {
                var state = mixinApi.getMethod("getState").invoke(extension);
                stateApi.getMethod("tryAs", Object.class, Consumer.class).invoke(null, state, stateConsumer);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        };
        mixinApi.getMethod("tryAs", IMixinInfo.class, Consumer.class).invoke(null, mixin, mixinConsumer);
    }

    public static void adapt(ClassNode node) {
        int replaced = 0;
        for (var method : node.methods) {
            if (!method.name.equals("eap$compatTryVirtualCompletion")
                    || !method.desc.equals("(Lappeng/api/crafting/IPatternDetails;)V")) continue;
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "com/moakiee/ae2lt/compat/gto/GtoEaepProviderSupport", "tryVirtualCompletion",
                    "(Lappeng/helpers/patternprovider/PatternProviderLogic;Lappeng/api/crafting/IPatternDetails;)V", false));
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.maxStack = 2;
            method.maxLocals = 2;
            replaced++;
        }
        boolean removedHelper = node.methods.removeIf(method -> method.name.equals("eap$compatShouldFinishWholeJob"));
        // The legacy TAIL callback also runs when GTO's stub rejects a push. Its replacement
        // is attached to the shared real-success callback by GtoEaepProviderTransform.
        boolean movedCallback = node.methods.removeIf(method -> method.name.equals("eap$compatOnPushPattern"));
        if (replaced != 1 || !removedHelper || !movedCallback)
            throw new IllegalStateException("Unexpected EAEP provider job API: "
                    + replaced + "/" + removedHelper + "/" + movedCallback);
    }

    private static void adaptAdvancedBlocking(ClassNode node) {
        // Keep its settings, persistence and interface. The sole dispatcher hook is moved
        // to GTO's real dispatch method by GtoEaepProviderTransform.
        long hooks = node.methods.stream().filter(method -> method.name.equals("eap$redirectBlockingContains")).count();
        if (hooks != 1) throw new IllegalStateException("Expected one EAEP blocking hook, found " + hooks);
        node.methods.removeIf(method -> method.name.equals("eap$redirectBlockingContains"));
    }

    private static void adaptEncoding(ClassNode node, ClassNode target) {
        long fields = node.fields.stream().filter(field -> field.name.equals("blankPatternSlot")).count();
        if (fields != 1) throw new IllegalStateException("EAEP blank slot contract changed");
        node.fields.removeIf(field -> field.name.equals("blankPatternSlot"));
        var method = node.methods.stream().filter(candidate -> candidate.name.equals("eap$tryAutoFill"))
                .findFirst().orElseThrow(() -> new IllegalStateException("EAEP refill hook missing"));
        reset(method);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/moakiee/ae2lt/compat/gto/GtoEaepPatternEncodingSupport", "releaseLegacyBlanks",
                "(Lappeng/menu/me/items/PatternEncodingTermMenu;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 1;
        method.maxLocals = 4;
        retargetBroadcast(node, target, "eap$onBroadcastChanges");
    }

    private static void adaptEncodingAccessor(ClassNode node) {
        var method = node.methods.stream().filter(candidate -> candidate.name.equals("eap$getBlankPatternSlot"))
                .findFirst().orElseThrow(() -> new IllegalStateException("EAEP blank slot accessor missing"));
        // GTO genuinely has no blank slot. EAEP's upload refund explicitly accepts null,
        // and its other three accessors still expose the real encoding slots/inventories.
        method.visibleAnnotations = null;
        method.invisibleAnnotations = null;
        method.access = Opcodes.ACC_PUBLIC;
        reset(method);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
    }

    private static void adaptCpuMenu(ClassNode node, ClassNode target) {
        retargetBroadcast(node, target, "eap$syncManualWaitingStatus");
    }

    private static void retargetBroadcast(ClassNode node, ClassNode target, String handler) {
        var candidates = target.methods.stream().filter(method -> method.desc.equals("()V")
                && (method.name.equals("m_38946_") || method.name.equals("broadcastChanges"))).toList();
        if (candidates.size() != 1) throw new IllegalStateException("GTO menu broadcast contract changed: " + target.name);
        int changed = 0;
        for (var method : node.methods) {
            if (!method.name.equals(handler) || method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                setValue(annotation, "method", java.util.List.of(candidates.get(0).name + "()V"));
                setValue(annotation, "remap", false);
                changed++;
            }
        }
        if (changed != 1) throw new IllegalStateException("EAEP menu handler changed: " + handler);
    }

    private static void setValue(AnnotationNode annotation, String key, Object value) {
        if (annotation.values == null) annotation.values = new java.util.ArrayList<>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) { annotation.values.set(i + 1, value); return; }
        }
        annotation.values.add(key);
        annotation.values.add(value);
    }

    private static void reset(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
    }

    @Override public void postApply(ITargetClassContext context) { }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) { }
}
