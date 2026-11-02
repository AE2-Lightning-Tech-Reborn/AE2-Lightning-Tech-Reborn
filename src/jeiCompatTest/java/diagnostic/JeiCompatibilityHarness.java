package diagnostic;

import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/** Applies the production mixins to the supplied, unmodified JEI jar, then verifies the result. */
public final class JeiCompatibilityHarness {
    private static byte[] read(String name) throws Exception {
        try (var in = JeiCompatibilityHarness.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) throw new ClassNotFoundException(name);
            return in.readAllBytes();
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
    private static long hooks(ClassNode node, String name) {
        return node.methods.stream().filter(m -> !m.name.contains("ae2lt$")).flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .filter(i -> i instanceof MethodInsnNode c && c.name.contains(name)).count();
    }
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var env = MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("diagnostic.mixins.json");
        var constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
        constructor.setAccessible(true);
        var transformer = (IMixinTransformer) constructor.newInstance();
        env.setActiveTransformer(transformer);
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        var targets = new ArrayList<String>();
        targets.add("mezz.jei.gui.recipes." + (args[2].equals("controller") ? "RecipeTransferButtonController" : "RecipeTransferButton"));
        if (!args[1].equals("control")) {
            targets.add("mezz.jei.gui.recipes.RecipeGuiLogic");
            targets.add(args[1].equals("legacy") ? "mezz.jei.common.transfer.RecipeTransferUtil"
                    : "mezz.jei.common.transfer.RecipeTransferService");
        }
        for (var target : targets) {
            byte[] transformed = transformer.transformClass(env, target, read(target));
            var node = new ClassNode(); new ClassReader(transformed).accept(node, 0);
            Files.write(Path.of(args[0], target.substring(target.lastIndexOf('.') + 1) + ".class"), transformed);
            for (var method : node.methods) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
            check(true, target + " passes bytecode verification");
            if ((target.endsWith("RecipeTransferButtonController") || target.endsWith("RecipeTransferButton")) && !args[1].equals("control")) {
                check(hooks(node, "legacyTransfer") + hooks(node, "contextTransfer") == 1,
                        "exactly one real transfer has scoped metadata; simulation is untouched");
                check(hooks(node, "keepLegacyRecipePageForDirectUpload") + hooks(node, "keepRecipePageForDirectUpload") == 1,
                        "exactly one successful-transfer callback is wrapped");
                check(node.methods.stream().filter(m -> m.name.contains("ae2lt$")).allMatch(m -> (m.access & Opcodes.ACC_STATIC) != 0),
                        "all button bridges are static and independent of local slot 0");
                check(node.methods.stream().noneMatch(m -> m.name.equals("ae2lt$getRecipeLayout")), "no private button field accessors");
            } else if (target.endsWith("RecipeTransferUtil") || target.endsWith("RecipeTransferService")) {
                check(hooks(node, "ae2lt$supply") == 1, "wireless supply preserves exactly one native handler call");
            } else if (target.endsWith("RecipeGuiLogic")) {
                check(hooks(node, "fitMultiblockPreview") + hooks(node, "fitModernMultiblockPreview") == 1, "structure preview layout hook remains active");
            }
        }
        if (!args[1].equals("control")) verifyMetadataScope();
        if (args[1].equals("modern")) verifyPublicContextBridge();
        System.out.println("JEI_COMPATIBILITY_PASS " + args[1]);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void verifyMetadataScope() throws Exception {
        var metadata = com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata.class;
        var field = metadata.getDeclaredField("CURRENT"); field.setAccessible(true);
        var current = (ThreadLocal) field.get(null);
        var snapshot = com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata.Snapshot.class;
        var constructor = snapshot.getDeclaredConstructors()[0];
        Object sentinel = constructor.getParameterCount() == 4
                ? constructor.newInstance(null, "test:outer", List.of("outer"), List.of())
                : constructor.newInstance(null, "test:outer", List.of("outer"));
        current.set(sentinel);
        try {
            boolean result = com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata.withLayout(null, null, () -> {
                check(current.get() == null, "unrelated transfer cannot inherit category metadata");
                return com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata.withLayout(null, null, () -> true);
            });
            check(result && current.get() == sentinel, "nested successful transfers restore the outer metadata");
            var failure = new IllegalStateException("expected handler failure");
            try {
                com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata.withLayout(null, null, () -> { throw failure; });
                throw new AssertionError("handler exception was swallowed");
            } catch (IllegalStateException error) {
                check(error == failure && current.get() == sentinel, "exceptional transfers restore metadata and preserve the original failure");
            }
        } finally { current.remove(); }
    }

    private static void verifyPublicContextBridge() throws Exception {
        var api = Class.forName("mezz.jei.api.recipe.transfer.IRecipeTransferContext");
        Object recipe = new Object();
        int[] completions = {0};
        Object original = java.lang.reflect.Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> switch (method.getName()) {
            case "getRecipe" -> recipe;
            case "getTransferId" -> 37;
            case "isMaxTransfer" -> true;
            case "completeRecipeTransfer" -> { completions[0]++; yield null; }
            default -> null;
        });
        var slots = (mezz.jei.api.gui.ingredient.IRecipeSlotsView) java.lang.reflect.Proxy.newProxyInstance(
                api.getClassLoader(), new Class<?>[] {mezz.jei.api.gui.ingredient.IRecipeSlotsView.class}, (proxy, method, args) -> List.of());
        var wrapped = com.moakiee.ae2lt.client.compat.JeiTransferContextBridge.withSlots(original, slots, false);
        check(api.isInstance(wrapped), "partial refill implements the public context interface");
        check(com.moakiee.ae2lt.client.compat.JeiTransferContextBridge.recipe(wrapped) == recipe
                && com.moakiee.ae2lt.client.compat.JeiTransferContextBridge.slots(wrapped) == slots
                && !com.moakiee.ae2lt.client.compat.JeiTransferContextBridge.maximum(wrapped)
                && (int) api.getMethod("getTransferId").invoke(wrapped) == 37,
                "partial refill preserves recipe/transfer ID and replaces only slots/maximum");
        var resultType = Class.forName("mezz.jei.api.recipe.transfer.RecipeTransferResult");
        api.getMethod("completeRecipeTransfer", resultType).invoke(wrapped, resultType.getField("SUCCESS").get(null));
        check(completions[0] == 1, "partial refill forwards native completion exactly once");
        check(com.moakiee.ae2lt.client.compat.JeiTransferContextBridge.withSlots(original, null, true) == original,
                "unchanged transfers preserve the original context identity");
    }

}
