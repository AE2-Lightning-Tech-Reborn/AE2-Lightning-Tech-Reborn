package com.moakiee.ae2lt.integration.emi;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIWidget;
import com.lowdragmc.lowdraglib2.integration.xei.emi.handler.EMIRecipeWidgetHandler;

import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockPreviewUI;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * EMI recipe page hosting the same LDLib2 multiblock preview as JEI.
 *
 * <p>The native LDLib widget and recipeWidget protocol route rendering and slot
 * interaction to a fresh per-layout UI. Lookup metadata stays explicit so EMI's index
 * does not depend on which panel tab happens to be built.</p>
 */
final class EmiMultiblockStructureRecipe extends ModularUIEMIRecipe {
    private final MultiblockStructureRecipe structure;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    EmiMultiblockStructureRecipe(MultiblockStructureRecipe structure) {
        super(self -> MultiblockPreviewUI.createForEmi(((EmiMultiblockStructureRecipe) self).structure));
        this.structure = structure;
        this.inputs = structure.materials().stream()
                .map(material -> (EmiIngredient) EmiStack.of(material.block(), material.count()))
                .toList();
        this.outputs = structure.focusStacks().stream()
                .map(EmiStack::of)
                .toList();
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return AE2LTEmiPlugin.MULTIBLOCK_STRUCTURE;
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(
                structure.id().getNamespace(),
                "/" + structure.id().getPath());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return List.of();
    }

    @Override
    public int getDisplayWidth() {
        return MultiblockPreviewUI.DEFAULT_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return MultiblockPreviewUI.DEFAULT_HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        var ui = MultiblockPreviewUI.createForEmi(structure);
        ui.setTickWhileRending(true);
        ui.setAllowDebugMode(false);
        ui.setDrawTooltips(false);
        ui.init(widgets.getWidth(), widgets.getHeight());
        MultiblockPreviewUI.setSlotRenderer(ui, (context, slot) -> {
            // HEAD creates an ordinary native slot on each draw, with zero render delta.
            var nativeSlot = new dev.emi.emi.api.widget.SlotWidget(EmiStack.of(slot.getValue()),
                    Math.round(slot.getPositionX()), Math.round(slot.getPositionY()));
            context.refreshLocalMouse();
            nativeSlot.render(context.graphics, (int) context.localMouseX, (int) context.localMouseY, 0.0F);
        });
        var widget = new ModularUIEMIWidget(ui);
        var handler = new EMIRecipeWidgetHandler(widget::getLocalToWorld);
        var event = UIEvent.create("recipeWidget");
        event.target = ui.ui.rootElement;
        event.customData = handler;
        UIEventDispatcher.dispatchAllChildren(event);
        for (var slot : handler.slots) {
            // Informational preview slots had no explicit recipe context in HEAD.
            widgets.add(slot);
        }
        widgets.add(widget);
        widgets.addDrawable(0, 0, 0, 0,
                (graphics, x, y, ticks) -> EmiMultiblockInputEvents.recordRender(ui, graphics));
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public boolean hideCraftable() {
        return true;
    }

    @Override
    public @Nullable RecipeHolder<?> getBackingRecipe() {
        return null;
    }
}
