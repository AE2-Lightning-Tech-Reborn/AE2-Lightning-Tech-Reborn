package com.moakiee.ae2lt.gametest;

import com.moakiee.ae2lt.menu.OverloadAlloyAnvilMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class OverloadAlloyAnvilGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void repairDoesNotWearAnvil(GameTestHelper helper) {
        var position = new BlockPos(0, 2, 0);
        helper.setBlock(position, ModBlocks.OVERLOAD_ALLOY_ANVIL.get());
        helper.assertTrue(helper.getBlockState(position).is(BlockTags.ANVIL),
                "The alloy anvil must be recognized as an anvil by vanilla menus");
        helper.assertTrue(helper.getLevel().getRecipeManager()
                        .byKey(new ResourceLocation("ae2lt", "overload_alloy_anvil")).isPresent(),
                "The alloy anvil must have a Forge 1.20.1 crafting recipe");
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "AlloyAnvilTest"));
        var provider = ModBlocks.OVERLOAD_ALLOY_ANVIL.get()
                .getMenuProvider(helper.getBlockState(position), helper.getLevel(), helper.absolutePos(position));
        helper.assertTrue(provider != null, "The alloy anvil must provide a menu");
        var access = ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(position));
        player.containerMenu = provider.createMenu(1, player.getInventory(), player);
        helper.assertTrue(player.containerMenu instanceof OverloadAlloyAnvilMenu,
                "The alloy anvil must open its Forge-specific anvil menu");

        var output = new ItemStack(Items.IRON_SWORD);
        var left = new ItemStack(Items.IRON_SWORD);
        var right = new ItemStack(Items.IRON_INGOT);
        helper.assertTrue(ForgeHooks.onAnvilRepair(player, output, left, right) == 0,
                "Repairing on the alloy anvil must not wear it down");
        player.containerMenu = new AnvilMenu(2, player.getInventory(), access);
        helper.assertTrue(ForgeHooks.onAnvilRepair(player, output, left, right) > 0,
                "The wear override must not affect ordinary anvil menus");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void impactKeepsAlloyButWearsVanillaAnvils(GameTestHelper helper) {
        var position = helper.absolutePos(new BlockPos(2, 6, 2));
        var alloy = FallingBlockEntity.fall(helper.getLevel(), position,
                ModBlocks.OVERLOAD_ALLOY_ANVIL.get().defaultBlockState());
        alloy.setHurtsEntities(2, 40);
        alloy.causeFallDamage(100, 1, alloy.damageSources().fall());
        var saved = new CompoundTag();
        alloy.saveWithoutId(saved);
        helper.assertTrue(alloy.getBlockState().is(ModBlocks.OVERLOAD_ALLOY_ANVIL.get())
                        && !saved.getBoolean("CancelDrop"),
                "A heavy impact must not wear or destroy the alloy anvil");
        alloy.discard();

        var vanilla = FallingBlockEntity.fall(helper.getLevel(), position,
                Blocks.ANVIL.defaultBlockState());
        vanilla.setHurtsEntities(2, 40);
        vanilla.causeFallDamage(100, 1, vanilla.damageSources().fall());
        helper.assertTrue(vanilla.getBlockState().is(Blocks.CHIPPED_ANVIL),
                "Ordinary anvils must still wear after a heavy impact");
        vanilla.discard();
        helper.succeed();
    }
}
