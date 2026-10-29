package com.moakiee.ae2lt.compat.gto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.*;

/** Preserve Core gto.6's two text hooks after AE2-GTO .40 moved its title into a widget. */
public final class GtoCoreClientMixinExtension implements IExtension {
    private static final String TARGET = "appeng/client/gui/me/crafting/CraftConfirmScreen";
    private static final String MIXIN = "com.moakiee.thunderbolt.mixin.ae2.client.CraftConfirmScreenMixin";
    private static final String TEXT = "L" + TARGET
            + ";setTextContent(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V";
    private static final String TITLE = "Lappeng/client/gui/me/crafting/CraftingScreenTitle;"
            + "m_93666_(Lnet/minecraft/network/chat/Component;)V";

    public static void register() {
        var transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        ((Extensions) transformer.getExtensions()).add(new GtoCoreClientMixinExtension());
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    @Override public void preApply(ITargetClassContext context) {
        if (!TARGET.equals(context.getClassNode().name)) return;
        try {
            var loader = getClass().getClassLoader();
            var contextApi = Class.forName("com.bawnorton.mixinsquared.reflection.TargetClassContextExtension", true, loader);
            Consumer<Object> transform = extension -> {
                try {
                    var mixins = (Iterable<?>) contextApi.getMethod("getMixins").invoke(extension);
                    for (var entry : mixins) {
                        var mixin = (IMixinInfo) entry;
                        if (!MIXIN.equals(mixin.getClassName())) continue;
                        var node = mixin.getClassNode(0);
                        adapt(node, context.getClassNode());
                        var mixinApi = Class.forName("com.bawnorton.mixinsquared.reflection.MixinInfoExtension", true, loader);
                        var stateApi = Class.forName("com.bawnorton.mixinsquared.reflection.StateExtension", true, loader);
                        Consumer<Object> updateState = state -> {
                            try { stateApi.getMethod("setClassNode", ClassNode.class).invoke(state, node); }
                            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                        };
                        Consumer<Object> updateMixin = info -> {
                            try {
                                var state = mixinApi.getMethod("getState").invoke(info);
                                stateApi.getMethod("tryAs", Object.class, Consumer.class).invoke(null, state, updateState);
                            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                        };
                        mixinApi.getMethod("tryAs", IMixinInfo.class, Consumer.class).invoke(null, mixin, updateMixin);
                    }
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot retarget Core's Dev25 confirmation hooks", failure);
                }
            };
            contextApi.getMethod("tryAs", ITargetClassContext.class, Consumer.class).invoke(null, context, transform);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("GTO MixinSquared reflection API changed", failure);
        }
    }

    static void adapt(ClassNode mixin, ClassNode target) {
        var update = target.methods.stream().filter(method -> method.name.equals("updateBeforeRender")
                && method.desc.equals("()V")).findFirst().orElseThrow();
        int texts = 0, titles = 0;
        for (var instruction : update.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                var signature = "L" + call.owner + ";" + call.name + call.desc;
                if (TEXT.equals(signature)) texts++;
                if (TITLE.equals(signature)) titles++;
            }
        }
        if (texts != 1 || titles != 1) throw new IllegalStateException(
                "AE2-GTO .40 confirmation text contract changed: " + texts + "/" + titles);
        int changed = 0;
        for (var method : mixin.methods) {
            boolean title = method.name.equals("thunderbolt$appendAlgorithmName");
            if (!title && !method.name.equals("thunderbolt$previewStatus")) continue;
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")) continue;
                var at = (AnnotationNode) value(annotation, "at");
                if (!List.of("updateBeforeRender").equals(value(annotation, "method"))
                        || !TEXT.equals(value(at, "target"))
                        || !Integer.valueOf(title ? 0 : 1).equals(value(at, "ordinal"))
                        || !Integer.valueOf(1).equals(value(annotation, "index"))) {
                    throw new IllegalStateException("Core gto.6 confirmation hook changed: " + method.name);
                }
                // No callback, require count or feature is removed. Only its actual call site changes.
                setValue(at, "target", title ? TITLE : TEXT);
                setValue(at, "ordinal", 0);
                setValue(annotation, "index", title ? 0 : 1);
                changed++;
            }
        }
        if (changed != 2) throw new IllegalStateException("Expected two Core confirmation hooks, found " + changed);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static void setValue(AnnotationNode annotation, String key, Object value) {
        if (annotation.values == null) annotation.values = new ArrayList<>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) { annotation.values.set(i + 1, value); return; }
        }
        annotation.values.add(key);
        annotation.values.add(value);
    }

    @Override public void postApply(ITargetClassContext context) { }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) { }
}
