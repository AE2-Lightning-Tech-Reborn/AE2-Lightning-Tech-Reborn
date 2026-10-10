package com.moakiee.ae2lt.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * Empty-slot sprites from the block atlas, rendered through {@link Slot#setBackground}.
 * Use one background mechanism per slot to avoid layering over AE2 icons.
 */
public final class Ae2ltSlotBackgrounds {

    public static final ResourceLocation ELECTRO_CHIME_CRYSTAL = sprite("electro_chime_crystal");
    public static final ResourceLocation FILTER_COMPONENT = sprite("filter_component");
    public static final ResourceLocation LIGHTNING_COLLAPSE_MATRIX = sprite("lightning_collapse_matrix");

    public static final ResourceLocation MINING_TOOL = sprite("mining_tool");
    public static final ResourceLocation MINING_BLOCK = sprite("mining_block");

    private static ResourceLocation sprite(String name) {
        return new ResourceLocation(AE2LightningTech.MODID, "block/slot/" + name);
    }

    /** 给一个普通槽位绑定空槽背景图,sprite 必须已被 stitch 进 {@link InventoryMenu#BLOCK_ATLAS}。 */
    public static <T extends Slot> T withBackground(T slot, ResourceLocation sprite) {
        slot.setBackground(InventoryMenu.BLOCK_ATLAS, sprite);
        return slot;
    }

    private Ae2ltSlotBackgrounds() {
    }
}

