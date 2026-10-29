package com.moakiee.ae2lt.client.compat;

import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.LinkedHashSet;
import java.util.List;
import mezz.jei.api.gui.IRecipeLayoutDrawable;

/**
 * Carries the JEI category through its universal transfer call. JEI passes only the recipe display
 * object to transfer handlers, which is not necessarily a Minecraft {@code Recipe}.
 *
 * <p>This helper must remain outside every package declared as a Mixin package. Mixin package
 * classes are reserved for transformation and cannot be loaded directly at runtime.
 */
public final class JeiRecipeTransferMetadata {
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();
    private static mezz.jei.api.runtime.IJeiRuntime runtime;

    public static void setRuntime(mezz.jei.api.runtime.IJeiRuntime value) { runtime = value; }

    public static com.moakiee.ae2lt.client.tianshu.TianshuUploadAliasEditScreen<?> aliasEditor(
            TianshuPatternEncodingTermMenu menu) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var parent = mc.screen instanceof mezz.jei.gui.recipes.RecipesGui recipesGui
                ? ((com.moakiee.ae2lt.mixin.recipeviewer.jei.JeiRecipesGuiAccessor) recipesGui).ae2lt$getParentScreen()
                : mc.screen;
        return parent instanceof com.moakiee.ae2lt.client.tianshu.TianshuUploadAliasEditScreen<?> editor
                && editor.getMenu() == menu && mc.player != null && mc.player.containerMenu == menu
                ? editor : null;
    }

    /** The machines shown in JEI's left catalyst column, using stable item registry IDs. */
    public static List<String> machineIds(mezz.jei.api.recipe.RecipeType<?> type) {
        if (runtime == null || type == null) return List.of();
        return runtime.getRecipeManager().createRecipeCatalystLookup(type).getItemStack()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .distinct().toList();
    }

    private JeiRecipeTransferMetadata() {
    }

    public static void begin(
            TianshuPatternEncodingTermMenu menu, IRecipeLayoutDrawable<?> recipeLayout) {
        clear();
        if (menu == null || recipeLayout == null || recipeLayout.getRecipeCategory() == null) return;
        var category = recipeLayout.getRecipeCategory();
        var type = category.getRecipeType();
        String sourceKey = type == null || type.getUid() == null ? "" : type.getUid().toString();
        var aliases = new LinkedHashSet<String>();
        if (!sourceKey.isBlank()) aliases.add(sourceKey);
        if (category.getTitle() != null && !category.getTitle().getString().isBlank()) {
            aliases.add(category.getTitle().getString());
        }
        CURRENT.set(new Snapshot(menu, sourceKey, List.copyOf(aliases), machineIds(type)));
    }

    /** Scope metadata to the real transfer, restoring it even if a handler throws or nests a transfer. */
    public static boolean withLayout(net.minecraft.world.inventory.AbstractContainerMenu menu,
            IRecipeLayoutDrawable<?> layout, java.util.function.BooleanSupplier transfer) {
        var previous = CURRENT.get();
        try {
            clear();
            if (menu instanceof TianshuPatternEncodingTermMenu tianshuMenu) begin(tianshuMenu, layout);
            return transfer.getAsBoolean();
        } finally {
            if (previous == null) clear();
            else CURRENT.set(previous);
        }
    }

    public static Snapshot snapshotFor(TianshuPatternEncodingTermMenu menu) {
        var snapshot = CURRENT.get();
        return snapshot != null && snapshot.menu() == menu ? snapshot : Snapshot.EMPTY;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Snapshot(
            TianshuPatternEncodingTermMenu menu, String sourceKey, List<String> defaultAliases, List<String> machineIds) {
        private static final Snapshot EMPTY = new Snapshot(null, "", List.of(), List.of());
    }
}
