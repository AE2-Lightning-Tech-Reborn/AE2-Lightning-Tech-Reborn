package com.moakiee.ae2lt.debug;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;
import com.moakiee.ae2lt.integration.jei.multiblock.InteractiveMultiblockWidget;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.RecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Verifies the transformed JEI layout, cache, widget and side-button geometry. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class JeiMultiblockLayoutClientProbe {
    private static int ticks;
    private static boolean done;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done
                || !Boolean.getBoolean("ae2lt.multiblockLayoutProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null) return;
        if (++ticks < 100) return;
        try {
            var gui = (RecipesGui) Internal.getJeiRuntime().getRecipesGui();
            gui.showTypes(List.of(MultiblockStructureCategory.TYPE));
            var logic = (RecipeGuiLogic) field(gui, "logic");
            var category = (MultiblockStructureCategory) logic.getSelectedRecipeCategory();
            var small = verify(logic, category, 210);
            require(logic.getVisibleRecipeLayoutsWithButtons(210, 10, null).get(0).recipeLayout()
                    == small.recipeLayout(), "unchanged dimensions must retain the cache");
            var tall = verify(logic, category, 410);
            require(tall.recipeLayout() != small.recipeLayout(), "height increase must replace the cache");
            require(small.recipeLayout().getRect().getHeight() == 184,
                    "existing layout bounds must remain frozen after category growth");
            var shrunk = verify(logic, category, 250);
            require(shrunk.recipeLayout() != tall.recipeLayout(), "height decrease must replace the cache");
            verify(logic, category, 100);
            // Let JEI rebuild from the actual screen's available area before rendering.
            gui.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            if (ticks == 100) return;
            Screenshot.grab(mc.gameDirectory, "multiblock-layout.png", mc.getMainRenderTarget(), ignored -> {});
            Files.writeString(Path.of("multiblock-layout-result.txt"),
                    "PASS: real JEI mixin; growth/shrink/minimum; cache reuse/invalidation; widget, transfer and bookmark geometry.\n");
            done = true;
            mc.setScreen(null);
            mc.stop();
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("multiblock-layout-result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
            done = true;
            mc.setScreen(null);
            mc.stop();
        }
    }

    private static RecipeLayoutWithButtons<?> verify(RecipeGuiLogic logic,
            MultiblockStructureCategory category, int availableHeight) throws Exception {
        int expected = Math.max(MultiblockStructureCategory.HEIGHT, availableHeight - 28);
        var layouts = logic.getVisibleRecipeLayoutsWithButtons(availableHeight, 10, null);
        require(!layouts.isEmpty(), "structure recipes must be registered");
        require(category.getHeight() == expected, "category height: " + category.getHeight());
        for (var buttons : layouts) {
            var layout = buttons.recipeLayout();
            require(layout.getRect().getHeight() == expected, "recipe rectangle height");
            var widgets = (List<?>) field(layout, "allWidgets");
            var widget = (InteractiveMultiblockWidget) widgets.stream()
                    .filter(InteractiveMultiblockWidget.class::isInstance).findFirst().orElseThrow();
            require(widget.getArea().height() == expected, "widget drawing/hit-test height");
            require(layout.getRecipeTransferButtonArea().getY() == expected - 14,
                    "transfer button must follow the lower corner");
            require(layout.getRecipeBookmarkButtonArea().getY() < layout.getRecipeTransferButtonArea().getY(),
                    "bookmark button must remain above transfer");
        }
        return layouts.get(0);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
