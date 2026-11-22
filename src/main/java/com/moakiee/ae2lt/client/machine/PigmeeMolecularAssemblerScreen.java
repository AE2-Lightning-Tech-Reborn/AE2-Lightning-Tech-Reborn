package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import java.util.List;

import appeng.client.gui.style.ScreenStyle;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.client.ldlib.MachineGauges;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.PigmeeMolecularAssemblerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class PigmeeMolecularAssemblerScreen
        extends LdlibAeScreen<PigmeeMolecularAssemblerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID,
            "textures/gui/pigmee_molecular_assembler.png");
    private static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(TEXTURE, 256, 256);
    private static final int CRAFTING_GRID_X = 29;
    private static final int CRAFTING_GRID_Y = 43;
    private static final int CRAFTING_GRID_U = 179;
    private static final int CRAFTING_GRID_V = 2;
    private static final int CRAFTING_GRID_SIZE = 52;
    private static final int PATTERN_SLOT_X = 126;
    private static final int PATTERN_SLOT_Y = 29;
    private static final int PATTERN_SLOT_U = 179;
    private static final int PATTERN_SLOT_V = 57;
    private static final int PATTERN_SLOT_SIZE = 16;
    private static final SpriteTexture GRID = ATLAS.slice(
            CRAFTING_GRID_U, CRAFTING_GRID_V, CRAFTING_GRID_SIZE, CRAFTING_GRID_SIZE);
    private static final SpriteTexture PATTERN = ATLAS.slice(
            PATTERN_SLOT_U, PATTERN_SLOT_V, PATTERN_SLOT_SIZE, PATTERN_SLOT_SIZE);
    private final BackgroundLayer decorations;

    public PigmeeMolecularAssemblerScreen(
            PigmeeMolecularAssemblerMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        decorations = new BackgroundLayer(imageWidth, imageHeight)
                .add(GRID, CRAFTING_GRID_X, CRAFTING_GRID_Y, CRAFTING_GRID_SIZE, CRAFTING_GRID_SIZE)
                .add(PATTERN, PATTERN_SLOT_X, PATTERN_SLOT_Y, PATTERN_SLOT_SIZE, PATTERN_SLOT_SIZE);
        widgets.add("progressBar", MachineGauges.verticalFilledProgress(style.getImage("progressBar"), menu,
                () -> List.of(Component.literal(menu.getCurrentProgress() + "%"))));
    }

    @Override
    protected void init() {
        decorations.onRemoved();
        super.init();
    }

    @Override
    public void removed() {
        try {
            decorations.onRemoved();
        } finally {
            super.removed();
        }
    }

    @Override
    public void drawBG(
            GuiGraphics graphics,
            int offsetX,
            int offsetY,
            int mouseX,
            int mouseY,
            float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        decorations.render(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
    }
}
