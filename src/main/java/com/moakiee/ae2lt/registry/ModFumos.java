package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.item.FumoBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registers AE2LT fumo decoration blocks.
 * 所有 Fumo 方块均由 AE2LT 自己注册，不再向 ME Placement Tool 让渡 Moakiee/Cystrysu。
 */
public final class ModFumos {

    public static DeferredBlock<FumoBlock> MOAKIEE_FUMO;
    public static DeferredBlock<FumoBlock> CYSTRYSU_FUMO;
    public static DeferredBlock<FumoBlock> PIGMEE_FUMO;
    public static DeferredBlock<FumoBlock> CREATIVE_PIGMEE_FUMO;
    public static DeferredBlock<FumoBlock> HYPERDIMENSIONAL_PIGMEE_FUMO;
    public static DeferredItem<FumoBlockItem> MOAKIEE_FUMO_ITEM;
    public static DeferredItem<FumoBlockItem> CYSTRYSU_FUMO_ITEM;
    public static DeferredItem<FumoBlockItem> PIGMEE_FUMO_ITEM;
    public static DeferredItem<FumoBlockItem> CREATIVE_PIGMEE_FUMO_ITEM;
    public static DeferredItem<FumoBlockItem> HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM;

    private ModFumos() {
    }

    public static void register() {
        MOAKIEE_FUMO = ModBlocks.registerExternalBlock("moakiee_fumo", FumoBlock::new);
        MOAKIEE_FUMO_ITEM = ModItems.ITEMS.register("moakiee_fumo",
                id -> new FumoBlockItem(MOAKIEE_FUMO.get(), ModItems.registeredProperties(new Item.Properties(), id)));
        CYSTRYSU_FUMO = ModBlocks.registerExternalBlock("cystrysu_fumo", FumoBlock::new);
        CYSTRYSU_FUMO_ITEM = ModItems.ITEMS.register("cystrysu_fumo",
                id -> new FumoBlockItem(CYSTRYSU_FUMO.get(), ModItems.registeredProperties(new Item.Properties(), id)));

        PIGMEE_FUMO = ModBlocks.registerExternalBlock("pigmee_fumo", FumoBlock::new);
        PIGMEE_FUMO_ITEM = ModItems.ITEMS.register("pigmee_fumo",
                id -> new FumoBlockItem(PIGMEE_FUMO.get(), ModItems.registeredProperties(new Item.Properties(), id),
                        "tooltip.ae2lt.pigmee_fumo"));

        CREATIVE_PIGMEE_FUMO = ModBlocks.registerExternalBlock("creative_pigmee_fumo", FumoBlock::new);
        CREATIVE_PIGMEE_FUMO_ITEM = ModItems.ITEMS.register("creative_pigmee_fumo",
                id -> new FumoBlockItem(
                        CREATIVE_PIGMEE_FUMO.get(),
                        ModItems.registeredProperties(new Item.Properties().rarity(Rarity.EPIC), id),
                        "tooltip.ae2lt.creative_pigmee_fumo"));

        HYPERDIMENSIONAL_PIGMEE_FUMO =
                ModBlocks.registerExternalBlock("hyperdimensional_pigmee_fumo", FumoBlock::new);
        HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM =
                ModItems.ITEMS.register("hyperdimensional_pigmee_fumo",
                        id -> new FumoBlockItem(
                                HYPERDIMENSIONAL_PIGMEE_FUMO.get(),
                                ModItems.registeredProperties(new Item.Properties().rarity(Rarity.EPIC), id),
                                "tooltip.ae2lt.hyperdimensional_pigmee_fumo"));
    }
}
