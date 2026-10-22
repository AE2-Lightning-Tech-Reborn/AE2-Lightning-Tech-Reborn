package com.moakiee.ae2lt.integration.useless;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Public native API boundary. Forge builds do not require a Useless jar on their classpath. */
final class UselessModPatternBridge {
    private static final String BASE = "com.sorrowmist.useless.";
    private static final String CATALOG = "content.recipe.AlloyFurnaceRecipeCatalog";
    private static final String ENCODING = "content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding";
    private static final String DETAILS = "content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails";
    private UselessModPatternBridge() {}

    static Object call(Object receiver, String method, Object... args) {
        Class<?> type = receiver instanceof Class<?> cls ? cls : receiver.getClass();
        for (Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != args.length) continue;
            var parameters = candidate.getParameterTypes();boolean fits = true;
            for (int i = 0; i < args.length; i++) if (args[i] != null && !parameters[i].isInstance(args[i])) { fits = false;break; }
            if (!fits || receiver instanceof Class<?> && !Modifier.isStatic(candidate.getModifiers())) continue;
            try { return candidate.invoke(receiver instanceof Class<?> ? null : receiver, args); }
            catch (InvocationTargetException error) {
                var cause = error.getCause();if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error fatal) throw fatal;
                throw new IllegalStateException("Native omniversal API failed", cause);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalStateException("Incompatible Useless API: " + type.getName() + "." + method);
    }
    static Class<?> type(String name) {
        try { return Class.forName(BASE + name, false, UselessModPatternBridge.class.getClassLoader()); }
        catch (ClassNotFoundException error) { throw new IllegalStateException("Missing Useless API: " + name, error); }
    }
    static long recipeGeneration() { return ((Number) call(type(CATALOG), "generation")).longValue(); }
    static boolean isViewerRecipe(Object selection) { return selection != null && type(CATALOG + "$Entry").isInstance(selection); }
    static ItemStack encodeViewerRecipe(Object entry, Level level) {
        if (!isViewerRecipe(entry)) return ItemStack.EMPTY;
        var source = (ItemStack) call(type(ENCODING), "createProcessingPattern", call(entry, "recipe"));
        return source.isEmpty() ? ItemStack.EMPTY : (ItemStack) call(type(ENCODING), "encode", source, entry, level);
    }
    private static Object nativeDetails(ItemStack pattern, Level level) {
        var details = PatternDetailsHelper.decodePattern(pattern, level);
        return details != null && type(DETAILS).isInstance(details) ? details : null;
    }
    private static ItemStack source(OmniversalPatternDraft draft) {
        var inputs = draft.inputs();var outputs = draft.outputs();
        if (inputs.stream().filter(Objects::nonNull).noneMatch(s -> s.amount() > 0)
                || outputs.stream().filter(Objects::nonNull).noneMatch(s -> s.amount() > 0)) return ItemStack.EMPTY;
        return PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new));
    }
    @SuppressWarnings("unchecked")
    private static List<Object> candidates(Level level, AEProcessingPattern details) {
        return (List<Object>) call(type(CATALOG), "findPatternCandidates", level, details);
    }
    private static boolean matches(Object entry, Level level, AEProcessingPattern details) {
        return Boolean.TRUE.equals(call(type(CATALOG), "matchesRecipe", level, call(entry, "sourceId"), call(entry, "recipe"), details));
    }
    static ItemStack encodeDraft(ItemStack pattern, Level level) {
        if (!UselessModCompat.isOmniversalPattern(pattern)) return ItemStack.EMPTY;
        var selected = nativeDetails(pattern, level);
        if (selected == null) return ItemStack.EMPTY;
        var source = source(new OmniversalPatternDraft(pattern));if (source.isEmpty()) return ItemStack.EMPTY;
        var details = new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(source)));
        var selectedRecipe = call(selected, "recipe");
        for (var entry : candidates(level, details)) {
            if (Objects.equals(selectedRecipe, call(entry, "recipe")) && matches(entry, level, details)) {
                var result = (ItemStack) call(type(ENCODING), "encode", source, details, entry, level);
                return nativeDetails(result, level) != null ? result : ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }
    static UselessModCompat.Preview preview(ItemStack pattern, Level level) {
        var draft = new OmniversalPatternDraft(pattern);var details = nativeDetails(pattern, level);
        var recipe = details == null ? null : call(details, "recipe");
        return new UselessModCompat.Preview(draft.inputs().stream().filter(Objects::nonNull).toList(),
                draft.outputs().stream().filter(Objects::nonNull).toList(), recipe == null ? List.of() : molds(recipe), "");
    }
    @SuppressWarnings("unchecked")
    private static List<ItemStack> molds(Object recipe) {
        return ((List<Object>) call(recipe, "molds")).stream()
                .map(mold -> (ItemStack) call(type("content.recipe.AdapterUtils"), "itemRepresentative", mold))
                .filter(Objects::nonNull).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }
    @SuppressWarnings("unchecked")
    private static boolean matchesMolds(Object recipe, List<ItemStack> supplied) {
        var required = (List<Object>) call(recipe, "molds");
        return required.size() == supplied.size() && Boolean.TRUE.equals(call(
                type("content.blockentities.multiblock.OmniversalMoldHubBlockEntity"), "matchesMolds", required, supplied));
    }
    static UselessModCompat.EncodingResult encodeMatchingDraft(ItemStack pattern, @Nullable List<ItemStack> editedMolds, Level level) {
        var source = source(new OmniversalPatternDraft(pattern));
        if (source.isEmpty()) return noMatch();
        var supplied = (editedMolds == null ? preview(pattern, level).molds() : editedMolds).stream().filter(stack -> !stack.isEmpty()).toList();
        var selected = encodeDraft(pattern, level);
        if (!selected.isEmpty() && matchesMolds(call(nativeDetails(selected, level), "recipe"), supplied))
            return new UselessModCompat.EncodingResult(selected, "");
        var details = new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(source)));
        for (var entry : candidates(level, details)) if (matchesMolds(call(entry, "recipe"), supplied) && matches(entry, level, details)) {
            var result = (ItemStack) call(type(ENCODING), "encode", source, details, entry, level);
            if (nativeDetails(result, level) != null) return new UselessModCompat.EncodingResult(result, "");
        }
        return noMatch();
    }
    private static UselessModCompat.EncodingResult noMatch() { return new UselessModCompat.EncodingResult(ItemStack.EMPTY, "ae2lt.tianshu.omniversal.no_match"); }
    static void clearPendingRecipe(Object logic) {
        if (type("content.machines.advanced_alloy_furnace.ae.PendingOmniversalPatternHolder").isInstance(logic)) {
            call(logic, "uselessMod$setPendingOmniversalRecipe", (Object) null);
            call(logic, "uselessMod$setPendingOmniversalSourceId", (Object) null);
        }
    }
    @Nullable static UselessModCompat.TargetState targetState(PatternContainer target, ItemStack pattern, Level level) {
        boolean single = type("content.blockentities.AdvancedAlloyFurnaceBlockEntity").isInstance(target);
        Object machine;
        if (single) machine = target;
        else if (type("content.blockentities.multiblock.MePatternAssemblyBlockEntity").isInstance(target)) {
            machine = call(target, "getController");
            if (machine == null || !Boolean.TRUE.equals(call(machine, "isFormed")))
                return new UselessModCompat.TargetState(false, false, true, "ae2lt.tianshu.omniversal.upload.unformed");
        } else return null;
        var details = nativeDetails(pattern, level);
        if (details == null) return new UselessModCompat.TargetState(false, false, !single, "ae2lt.tianshu.omniversal.invalid");
        var recipe = call(details, "recipe");
        if (single && ((List<?>) call(recipe, "molds")).size() > 1)
            return new UselessModCompat.TargetState(false, false, false, "ae2lt.tianshu.omniversal.upload.requires_multiblock");
        var availability = call(machine, "getTaskAvailability", recipe);
        if (Boolean.TRUE.equals(call(availability, "available"))) return new UselessModCompat.TargetState(true, true, !single, "");
        var reason = (String) call(availability, "statusKey");
        boolean missing = reason.endsWith("waiting_missing_mold") || reason.endsWith("waiting_mold_hub");
        return new UselessModCompat.TargetState(missing, false, !single, missing ? "ae2lt.tianshu.omniversal.upload.missing_mold" : reason);
    }
}
