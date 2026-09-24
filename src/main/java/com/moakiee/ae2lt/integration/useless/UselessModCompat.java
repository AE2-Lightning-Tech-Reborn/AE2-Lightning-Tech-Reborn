package com.moakiee.ae2lt.integration.useless;

import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternContainer;
import com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Optional boundary: common terminal classes never resolve Useless Mod classes. */
public final class UselessModCompat {
    private static final Identifier PATTERN_ID =
            Identifier.fromNamespaceAndPath("useless_mod", "omniversal_pattern");

    private UselessModCompat() {
    }

    public static boolean isLoaded() {
        // No compatible Useless Mod artifact exists for 26.1.2. Keep the page unavailable.
        return false;
    }

    public static boolean isOmniversalPattern(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && PATTERN_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static ItemStack icon() {
        return isLoaded() ? new ItemStack(BuiltInRegistries.ITEM.getValue(PATTERN_ID)) : ItemStack.EMPTY;
    }

    public static long recipeGeneration() {
        return -1L;
    }

    /** Called by the optional JEI mixin with the viewer's exact catalog entry. */
    public static boolean isViewerRecipe(Object entry) {
        return false;
    }

    public static ItemStack encodeViewerRecipe(Object entry, Level level) {
        return ItemStack.EMPTY;
    }

    /** Re-resolves the recipe and validates all inputs/outputs on the authoritative server. */
    public static ItemStack encodeDraft(OmniversalPatternDraft draft, Level level) {
        if (draft == null || draft.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.EMPTY;
    }

    public static EncodingResult encodeMatchingDraft(OmniversalPatternDraft draft, Level level) {
        return new EncodingResult(ItemStack.EMPTY, "ae2lt.tianshu.omniversal.no_match");
    }

    public record EncodingResult(ItemStack pattern, String reason) {
    }

    public static Preview preview(OmniversalPatternDraft draft, Level level) {
        if (draft == null || draft.isEmpty()) return Preview.EMPTY;
        return Preview.EMPTY;
    }

    public static void clearPendingRecipe(Object logic) {
        // The unavailable optional mod cannot retain a pending recipe.
    }

    @Nullable
    public static TargetState targetState(PatternContainer target, ItemStack pattern, Level level) {
        return null;
    }

    public record Preview(List<GenericStack> inputs, List<GenericStack> outputs,
                          List<ItemStack> molds, String recipeId) {
        public static final Preview EMPTY = new Preview(List.of(), List.of(), List.of(), "");

        public Preview {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            molds = molds.stream().map(ItemStack::copy).toList();
        }
    }

    /** Auto-upload requires matching molds and prefers a ready multiblock over a ready single furnace. */
    public record TargetState(boolean supported, boolean ready, boolean multiblock, String reason) {
    }
}
