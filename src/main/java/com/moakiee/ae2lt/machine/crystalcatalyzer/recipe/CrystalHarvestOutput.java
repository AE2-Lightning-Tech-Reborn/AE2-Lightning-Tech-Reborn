package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayerFactory;

/** A mother rock's mature-cluster harvest, resolved once per datapack load on the server. */
public final class CrystalHarvestOutput implements CrystalCatalyzerOutput {
    private static final GameProfile HARVESTER = new GameProfile(
            UUID.fromString("73dfaf2c-fdc5-43a9-a9f9-89409907fc86"), "[AE2LT Harvest]");
    private final ResourceLocation block;
    private final int count;
    private LootDataManager lootRegistries;
    private ItemStack resolved = ItemStack.EMPTY;

    public CrystalHarvestOutput(ResourceLocation block, int count) {
        if (count <= 0) throw new IllegalArgumentException("harvest output count must be positive");
        this.block = java.util.Objects.requireNonNull(block, "block");
        this.count = count;
    }

    public ResourceLocation block() {
        return block;
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public ItemStack resolve() {
        return resolved.copy();
    }

    public void resolveHarvest(ServerLevel level) {
        var registries = level.getServer().getLootData();
        if (lootRegistries == registries) return;
        lootRegistries = registries;
        resolved = ItemStack.EMPTY;
        var cluster = BuiltInRegistries.BLOCK.getOptional(block);
        if (cluster.isEmpty()) return;

        // Use the normal block-drop path, including block overrides and global loot
        // modifiers. Reading getRandomItemsRaw bypasses those and can return a different item.
        // AE2 clusters deliberately return nothing for entity-less destruction (e.g.
        // explosions). A harvesting entity is needed to model ordinary collection.
        var drops = Block.getDrops(cluster.get().defaultBlockState(), level, BlockPos.ZERO,
                null, FakePlayerFactory.get(level, HARVESTER), new ItemStack(Items.DIAMOND_PICKAXE));
        resolved = singleProduct(drops, count);
        if (resolved.isEmpty()) {
            LogUtils.getLogger().warn("Crystal catalyzer harvest {} has no single product; recipe disabled", block);
        }
    }

    /** Preserve the recipe's base yield, rather than multiplying it by the harvest count. */
    static ItemStack singleProduct(List<ItemStack> drops, int count) {
        ItemStack product = ItemStack.EMPTY;
        for (var drop : drops) {
            if (drop.isEmpty()) continue;
            if (!product.isEmpty() && !ItemStack.isSameItemSameTags(product, drop)) {
                return ItemStack.EMPTY;
            }
            product = drop;
        }
        return product.copyWithCount(count);
    }
}
