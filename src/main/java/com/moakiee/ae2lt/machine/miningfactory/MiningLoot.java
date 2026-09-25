package com.moakiee.ae2lt.machine.miningfactory;

import java.util.ArrayList;
import java.util.List;

import appeng.core.definitions.AEParts;
import com.moakiee.ae2lt.compat.mining.ApothicMiningLoot;
import com.moakiee.ae2lt.compat.mining.BloodMagicMiningTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Loot-table evaluation only: no placed blocks, break events or tool mineBlock callbacks. */
public final class MiningLoot {
    private MiningLoot() {}

    public static boolean isPlane(ItemStack stack) {
        return stack.is(AEParts.ANNIHILATION_PLANE.asItem());
    }

    public static boolean isTool(ItemStack stack) {
        // Energy-only tools need an explicit cost adapter; accepting them here would permit free use.
        return isPlane(stack) || stack.has(DataComponents.TOOL) && stack.getMaxDamage() > 0;
    }

    public static BlockState stateOf(ItemStack input) {
        if (!(input.getItem() instanceof BlockItem item) || input.has(DataComponents.BLOCK_ENTITY_DATA)) {
            return null;
        }
        BlockState state = item.getBlock().defaultBlockState();
        var properties = input.get(DataComponents.BLOCK_STATE);
        if (properties != null) {
            state = properties.apply(state);
        }
        // Container contents and other block-entity state cannot be reconstructed from a default state.
        return state.hasBlockEntity() || state.isAir() ? null : state;
    }

    public static ItemStack lootTool(ItemStack installed, BlockState state) {
        if (!isPlane(installed)) {
            return copyTool(installed);
        }
        // Same diamond-tier tool selection as AE2's ItemPickupStrategy.
        var item = state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? Items.DIAMOND_PICKAXE
                : state.is(BlockTags.MINEABLE_WITH_AXE) ? Items.DIAMOND_AXE
                : state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? Items.DIAMOND_SHOVEL
                : state.is(BlockTags.MINEABLE_WITH_HOE) ? Items.DIAMOND_HOE : Items.DIAMOND_PICKAXE;
        ItemStack tool = new ItemStack(item);
        tool.set(DataComponents.ENCHANTMENTS,
                installed.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        return tool;
    }

    public static boolean canHarvest(BlockState state, ItemStack installed) {
        return !state.requiresCorrectToolForDrops() || lootTool(installed, state).isCorrectToolForDrops(state);
    }

    public record Drop(ItemStack stack, long count) {}
    public record Result(int processed, int samples, ItemStack tool, List<Drop> drops) {}

    private static ItemStack copyTool(ItemStack original) {
        ItemStack copy = original.copy();
        // Some optional mods mutate CustomData through getUnsafe(), including on a copied loot tool.
        // Detach it so a prospective roll cannot spend charges or leave recursion flags on the real tool.
        var data = original.get(DataComponents.CUSTOM_DATA);
        if (data != null) copy.set(DataComponents.CUSTOM_DATA, CustomData.of(data.copyTag()));
        return copy;
    }

    public static Result roll(ServerLevel level, BlockPos origin, BlockState state, ItemStack installed,
                              int count, int sampleBudget) {
        ItemStack remainingTool = copyTool(installed);
        boolean plane = isPlane(installed);
        var anointments = plane ? null : BloodMagicMiningTool.read(remainingTool);
        if (anointments != null) count = anointments.limit(count);
        int groups = Math.min(count, sampleBudget);
        int processed = 0;
        int sampled = 0;
        List<Drop> drops = new ArrayList<>();
        for (int group = 0; group < groups && !remainingTool.isEmpty(); group++) {
            int requested = count / groups + (group < count % groups ? 1 : 0);
            ItemStack sampleTool = lootTool(remainingTool, state);
            int actual = 0;
            for (; actual < requested && !remainingTool.isEmpty(); actual++) {
                if (!plane && state.getDestroySpeed(level, origin) != 0) {
                    // Cheap per-block durability evaluation preserves Unbreaking and stops at breakage.
                    int damage = remainingTool.get(DataComponents.TOOL).damagePerBlock();
                    remainingTool.hurtAndBreak(damage, level, (ServerPlayer) null, item -> {});
                }
            }
            if (actual == 0) {
                break;
            }
            for (ItemStack drop : Block.getDrops(state, level, origin, null, null, sampleTool)) {
                if (!drop.isEmpty()) {
                    add(drops, drop, (long) drop.getCount() * actual);
                }
            }
            int weight = actual;
            ApothicMiningLoot.addDrops(level, origin, state, sampleTool,
                    drop -> { if (!drop.isEmpty()) add(drops, drop, (long) drop.getCount() * weight); });
            processed += actual;
            sampled++;
        }
        if (anointments != null) anointments.consume(remainingTool, processed);
        return new Result(processed, sampled, remainingTool, List.copyOf(drops));
    }

    private static void add(List<Drop> drops, ItemStack stack, long count) {
        for (int i = 0; i < drops.size(); i++) {
            Drop previous = drops.get(i);
            if (ItemStack.isSameItemSameComponents(previous.stack(), stack)) {
                drops.set(i, new Drop(previous.stack(), Math.addExact(previous.count(), count)));
                return;
            }
        }
        drops.add(new Drop(stack.copyWithCount(1), count));
    }
}
