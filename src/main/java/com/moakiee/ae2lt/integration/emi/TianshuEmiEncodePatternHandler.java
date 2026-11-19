package com.moakiee.ae2lt.integration.emi;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import com.moakiee.ae2lt.client.tianshu.TianshuUploadAliasEditScreen;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.widget.Widget;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/** Uses the recipe fill button as a source picker while editing an upload alias. */
public final class TianshuEmiEncodePatternHandler<T extends TianshuPatternEncodingTermMenu>
        extends EmiEncodePatternHandler<T> {
    public TianshuEmiEncodePatternHandler(Class<T> containerClass) {
        super(containerClass);
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        if (context.getScreen() instanceof TianshuUploadAliasEditScreen<?>) {
            return context.getType() == EmiCraftContext.Type.FILL_BUTTON;
        }
        return super.canCraft(recipe, context);
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        if (context.getScreen() instanceof TianshuUploadAliasEditScreen<?> editor) {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.containerMenu != editor.getMenu()
                    || context.getType() != EmiCraftContext.Type.FILL_BUTTON) return false;
            var holder = recipe.getBackingRecipe();
            if (holder == null && recipe.getId() != null) {
                holder = mc.player.level().getRecipeManager().byKey(recipe.getId()).orElse(null);
            }
            var category = recipe.getCategory();
            editor.setRecipeSource(holder, category == null ? "" : category.getId().toString());
            if (mc.screen != editor) mc.setScreen(editor);
            return true;
        }
        return super.craft(recipe, context);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<T> context) {
        if (context.getScreen() instanceof TianshuUploadAliasEditScreen<?>) {
            return List.of(ClientTooltipComponent.create(Component.translatable(
                    "ae2lt.tianshu.aliases.recipe_source").getVisualOrderText()));
        }
        return super.getTooltip(recipe, context);
    }

    @Override
    public void render(EmiRecipe recipe, EmiCraftContext<T> context, List<Widget> widgets, GuiGraphics draw) {
        if (!(context.getScreen() instanceof TianshuUploadAliasEditScreen<?>)) {
            super.render(recipe, context, widgets, draw);
        }
    }
}
