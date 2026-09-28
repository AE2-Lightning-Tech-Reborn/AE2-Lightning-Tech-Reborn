package com.moakiee.ae2lt.menu;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.moakiee.ae2lt.AE2LightningTech;

/** Empty-slot sprites registered explicitly in the 26.1 GUI atlas. */
public final class Ae2ltSlotBackgrounds {
    private static final Map<Slot, Identifier> ICONS = Collections.synchronizedMap(new WeakHashMap<>());

    public static final Identifier ELECTRO_CHIME_CRYSTAL = sprite("electro_chime_crystal");
    public static final Identifier FILTER_COMPONENT = sprite("filter_component");
    public static final Identifier LIGHTNING_COLLAPSE_MATRIX = sprite("lightning_collapse_matrix");
    public static final Identifier MINING_TOOL = sprite("mining_tool");
    public static final Identifier MINING_BLOCK = sprite("mining_block");

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "block/slot/" + name);
    }

    /** 给普通槽位绑定已注册到 GUI 图集的空槽背景。 */
    public static <T extends Slot> T withBackground(T slot, Identifier sprite) {
        ICONS.put(slot, sprite);
        return slot;
    }

    public static Identifier iconFor(Slot slot) {
        return ICONS.get(slot);
    }

    private Ae2ltSlotBackgrounds() {
    }
}
