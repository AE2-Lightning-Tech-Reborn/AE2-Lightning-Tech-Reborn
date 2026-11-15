package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.integration.recipeviewer.multiblock.InteractiveMultiblockPreview;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Standalone adapter for the same preview used by EMI and JEI. */
public final class LargeFactoryPreviewScreen extends Screen implements InteractiveMultiblockPreview.SlotDelegate {
    private final Screen parent;
    private final MultiblockStructureRecipe recipe;
    private final InteractiveMultiblockPreview preview;
    private int left, top;

    public LargeFactoryPreviewScreen(Screen parent, MultiblockStructureRecipe recipe, BlockPos controller, Direction facing) {
        super(recipe.title());
        this.parent = parent;
        this.recipe = recipe;
        preview = new InteractiveMultiblockPreview(recipe, InteractiveMultiblockPreview.DEFAULT_WIDTH,
                InteractiveMultiblockPreview.DEFAULT_HEIGHT, this, () -> {
                    LargeFactoryProjection.toggle(recipe, controller, facing);
                    net.minecraft.client.Minecraft.getInstance().player.closeContainer();
                });
    }

    @Override protected void init() {
        left = (width - InteractiveMultiblockPreview.DEFAULT_WIDTH) / 2;
        top = (height - InteractiveMultiblockPreview.DEFAULT_HEIGHT - 24) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(left + 77, top + InteractiveMultiblockPreview.DEFAULT_HEIGHT + 4, 146, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        preview.drawWidget(graphics, mouseX - left, mouseY - top);
        graphics.pose().popPose();
        var tooltip = preview.getTooltip(mouseX - left, mouseY - top);
        if (!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return preview.handleMouseClick(x - left, y - top, button, false) || super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return preview.handleMouseDragged(x - left, y - top, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return preview.handleMouseScrolled(x - left, y - top, dx, dy) || super.mouseScrolled(x, y, dx, dy);
    }
    @Override public void tick() { preview.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public int materialSlotCount() { return recipe.materials().size(); }
    @Override public int alternativeSlotCount() { return InteractiveMultiblockPreview.MAX_ALTERNATIVE_SLOTS; }
    @Override public void drawMaterialSlot(GuiGraphics graphics, int index, int x, int y) {
        var material = recipe.materials().get(index);
        drawSlot(graphics, material.block(), x, y);
    }
    @Override public void drawSelectedBlockSlot(GuiGraphics graphics, Block block, int x, int y) { drawSlot(graphics, block, x, y); }
    @Override public void drawAlternativeSlot(GuiGraphics graphics, int index, Block block, int x, int y) { drawSlot(graphics, block, x, y); }
    private void drawSlot(GuiGraphics graphics, Block block, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF555555);
        graphics.renderItem(new ItemStack(block), x, y);
    }
}
