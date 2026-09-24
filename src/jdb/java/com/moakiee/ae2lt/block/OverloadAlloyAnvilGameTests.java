package com.moakiee.ae2lt.block;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.menu.OverloadAlloyAnvilMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Real scheduled block ticks, falling entities, damage, landing and reload. */


public final class OverloadAlloyAnvilGameTests {

    public static void recipeMiningAndLootAreRegistered(GameTestHelper helper) {
        var alloy = new ItemStack(ModItems.OVERLOAD_ALLOY.get());
        var input = CraftingInput.of(3, 3, List.of(
                alloy.copy(), alloy.copy(), alloy.copy(),
                ItemStack.EMPTY, new ItemStack(Blocks.ANVIL),
                ItemStack.EMPTY,
                ItemStack.EMPTY, alloy.copy(), ItemStack.EMPTY));
        var level = helper.getLevel();
        var recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElseThrow(() -> new AssertionError("missing alloy anvil recipe"));
        var result = recipe.value().assemble(input);
        check(result.is(ModBlocks.OVERLOAD_ALLOY_ANVIL.get().asItem()) && result.getCount() == 1,
                "recipe does not produce one alloy anvil");
        var state = ModBlocks.OVERLOAD_ALLOY_ANVIL.get().defaultBlockState();
        check(state.is(BlockTags.ANVIL), "native anvil tag missing");
        var ironPick = new ItemStack(Items.IRON_PICKAXE);
        check(ironPick.isCorrectToolForDrops(state), "iron pickaxe cannot harvest alloy anvil");
        check(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),
                "wooden pickaxe bypasses iron-tool requirement");
        var drops = Block.getDrops(state, level,
                helper.absolutePos(new BlockPos(2, 2, 2)), null, null, ironPick);
        check(drops.size() == 1 && drops.getFirst().is(result.getItem()) && drops.getFirst().getCount() == 1,
                "mining loot lost or duplicated the alloy anvil");
        helper.succeed();
    }

    public static void nativeMenuHonorsCallbacksAndXpWithoutAlloyWear(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("825dd685-4aa2-475d-bb11-34729e0170f7"), "AlloyAnvilQA"));
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        player.experienceLevel = 100;
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        int[] repairs = {0};
        Consumer<AnvilCraftEvent.Pre> repair = event -> {
            if (event.getEntity() == player) { repairs[0]++;
                long seed = 0;
                while (net.minecraft.util.RandomSource.create(seed).nextFloat() >= 0.12F) seed++;
                player.getRandom().setSeed(seed); }
        };
        NeoForge.EVENT_BUS.addListener(repair);
        try {
            for (boolean alloy : new boolean[]{true, false}) {
                var state = (alloy ? ModBlocks.OVERLOAD_ALLOY_ANVIL.get() : Blocks.ANVIL).defaultBlockState();
                level.setBlockAndUpdate(pos, state);
                var menu = (AnvilMenu) state.getMenuProvider(level, pos)
                        .createMenu(0, player.getInventory(), player);
                check((menu instanceof OverloadAlloyAnvilMenu) == alloy, "wrong block menu provider");
                menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
                menu.setItemName("Reborn alloy anvil");
                int cost = menu.getCost(), before = player.experienceLevel;
                check(cost > 0 && menu.getSlot(2).hasItem(), "native rename preview missing");
                menu.getSlot(2).onTake(player, menu.getSlot(2).remove(1));
                check(player.experienceLevel == before - cost && !menu.getSlot(0).hasItem(), "wrong XP/input consumption");
                check(level.getBlockState(pos).is(alloy ? ModBlocks.OVERLOAD_ALLOY_ANVIL.get() : Blocks.CHIPPED_ANVIL),
                        "alloy wear protection changed ordinary anvil behavior");
                menu.removed(player);
            }
            check(repairs[0] == 2, "native repair callbacks not preserved");
        } finally {
            NeoForge.EVENT_BUS.unregister(repair);
        }
        helper.succeed();
    }

    public static void unsupportedAnvilsFallAndKeepAllFourFacings(GameTestHelper helper) {
        int x = 1;
        for (var facing : Direction.Plane.HORIZONTAL) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 6, 2), ModBlocks.OVERLOAD_ALLOY_ANVIL.get().defaultBlockState()
                    .setValue(AnvilBlock.FACING, facing));
            x += 2;
        }
        helper.runAfterDelay(5, () -> {
            check(falling(helper).size() == 4, "unsupported anvils did not become falling entities");
        });
        helper.runAfterDelay(35, () -> {
            int column = 1;
            for (var facing : Direction.Plane.HORIZONTAL) {
                helper.assertBlockPresent(ModBlocks.OVERLOAD_ALLOY_ANVIL.get(), new BlockPos(column, 2, 2));
                helper.assertBlockProperty(new BlockPos(column, 2, 2), AnvilBlock.FACING, facing);
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(column, 6, 2));
                column += 2;
            }
            check(falling(helper).isEmpty(), "landed entities remained alive");
            helper.succeed();
        });
    }

    public static void supportPreventsFallingUntilRemoved(GameTestHelper helper) {
        var pos = new BlockPos(2, 5, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.OVERLOAD_ALLOY_ANVIL.get());
        helper.runAfterDelay(10, () -> {
            helper.assertBlockPresent(ModBlocks.OVERLOAD_ALLOY_ANVIL.get(), pos);
            check(falling(helper).isEmpty(), "supported anvil fell");
            helper.setBlock(pos.below(), Blocks.AIR);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(ModBlocks.OVERLOAD_ALLOY_ANVIL.get(), new BlockPos(2, 2, 2));
            helper.succeed();
        });
    }

    public static void heavyImpactHurtsEntitiesWithoutDestroyingAlloyAndSurvivesReload(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 7, 2), ModBlocks.OVERLOAD_ALLOY_ANVIL.get().defaultBlockState()
                .setValue(AnvilBlock.FACING, Direction.WEST));
        helper.runAfterDelay(5, () -> {
            var entity = falling(helper).getFirst();
            var victim = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
            victim.setPos(entity.position());
            var before = victim.getHealth();
            // Guarantees native wear probability > 1 instead of relying on random falls.
            entity.causeFallDamage(100, 1, entity.damageSources().fall());
            check(victim.getHealth() == before - 40, "native anvil impact damage changed");
            var saved = new CompoundTag();
            entity.saveWithoutId(com.moakiee.ae2lt.api.compat.ValueIO.output(saved, helper.getLevel().registryAccess()));
            check(!saved.getBooleanOr("CancelDrop", false), "alloy was destroyed by native anvil wear");
            check(saved.getBooleanOr("HurtEntities", false), "impact damage disabled");
            victim.discard();
            entity.discard();
            var restored = new FallingBlockEntity(EntityType.FALLING_BLOCK, helper.getLevel());
            restored.load(com.moakiee.ae2lt.api.compat.ValueIO.input(saved, helper.getLevel().registryAccess()));
            helper.getLevel().addFreshEntity(restored);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(ModBlocks.OVERLOAD_ALLOY_ANVIL.get(), new BlockPos(2, 2, 2));
            helper.assertBlockProperty(new BlockPos(2, 2, 2), AnvilBlock.FACING, Direction.WEST);
            helper.succeed();
        });
    }

    public static void torchLandingDropsExactlyOneAlloyAnvil(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.TORCH);
        helper.setBlock(new BlockPos(2, 6, 2), ModBlocks.OVERLOAD_ALLOY_ANVIL.get());
        helper.runAfterDelay(40, () -> {
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds(helper));
            int count = drops.stream().filter(e -> e.getItem().is(ModBlocks.OVERLOAD_ALLOY_ANVIL.get().asItem()))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            check(count == 1 && falling(helper).isEmpty(), "torch landing lost or duplicated the anvil");
            helper.succeed();
        });
    }

    public static void ordinaryAnvilsStillWearOnImpact(GameTestHelper helper) {
        for (var block : new Block[]{Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL}) {
            var pos = helper.absolutePos(new BlockPos(2, 6, 2));
            var entity = FallingBlockEntity.fall(helper.getLevel(), pos,
                    block.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST));
            entity.setHurtsEntities(2, 40);
            entity.causeFallDamage(100, 1, entity.damageSources().fall());
            var saved = new CompoundTag();
            entity.saveWithoutId(com.moakiee.ae2lt.api.compat.ValueIO.output(saved, helper.getLevel().registryAccess()));
            if (block == Blocks.DAMAGED_ANVIL) {
                check(saved.getBooleanOr("CancelDrop", false), "ordinary damaged anvil no longer breaks");
            } else {
                check(entity.getBlockState().is(block == Blocks.ANVIL ? Blocks.CHIPPED_ANVIL : Blocks.DAMAGED_ANVIL),
                        "ordinary anvil no longer wears");
                check(entity.getBlockState().getValue(AnvilBlock.FACING) == Direction.EAST, "ordinary facing changed");
            }
            entity.discard();
        }
        helper.succeed();
    }

    private static List<FallingBlockEntity> falling(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, bounds(helper));
    }

    private static AABB bounds(GameTestHelper helper) {
        return new AABB(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)),
                Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(10, 12, 10))));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
