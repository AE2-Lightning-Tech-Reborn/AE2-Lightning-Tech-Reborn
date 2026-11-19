package com.moakiee.ae2lt.debug;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.blockentity.TianshuPatternStorageBlockEntity;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.menu.TianshuPatternStorageMenu;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_pattern_storage")
@PrefixGameTestTemplate(false)
public final class TianshuPatternStorageGameTests {
    public static ItemStack pattern(ServerLevel level) {
        var seed = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        var input = new GenericStack(AEItemKey.of(Items.REDSTONE), 1);
        var output = new GenericStack(AEItemKey.of(Items.PAPER), 1);
        var member = PatternDetailsHelper.encodeProcessingPattern(List.of(seed, input), List.of(seed, output));
        var payload = new ClosedLoopPatternPayload(List.of(new ClosedLoopMemberPattern(
                SourcePatternSnapshot.fromItemStack(member, level.registryAccess()), 1)),
                List.of(seed), List.of(input), List.of(output), 1, 1, true);
        return ((ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get()).createStack(payload, level.registryAccess());
    }

    private record Fixture(TianshuPatternStorageBlockEntity storage, ServerPlayer player, TianshuPatternStorageMenu menu) {}

    private static Fixture fixture(GameTestHelper helper) {
        var pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get());
        TianshuPatternStorageBlockEntity storage = helper.getBlockEntity(pos);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PatternStorageQA"));
        player.setPos(storage.getBlockPos().getCenter());
        var menu = new TianshuPatternStorageMenu(1, player.getInventory(), storage);
        player.containerMenu = menu;
        return new Fixture(storage, player, menu);
    }

    private static int playerSlot(Fixture fixture, int inventorySlot) {
        return fixture.menu.slots.stream().filter(slot -> slot.container == fixture.player.getInventory()
                && slot.getContainerSlot() == inventorySlot).findFirst().orElseThrow().index;
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void shiftMoveAndPickupConservePatterns(GameTestHelper helper) {
        var f = fixture(helper);
        var pattern = pattern(helper.getLevel());
        f.player.getInventory().setItem(0, pattern.copy());
        helper.assertTrue(f.menu.slots.size() == 72, "36 pattern slots plus player inventory required");
        f.menu.quickMoveStack(f.player, playerSlot(f, 0));
        helper.assertTrue(f.storage.patterns().size() == 1 && f.player.getInventory().getItem(0).isEmpty(),
                "shift insert failed or duplicated pattern");
        f.menu.quickMoveStack(f.player, 0);
        helper.assertTrue(f.storage.patterns().isEmpty() && f.player.getInventory().countItem(pattern.getItem()) == 1,
                "shift extract left reconstructed pattern behind");
        f.storage.getTerminalPatternInventory().insertItem(5, pattern.copy(), false);
        f.menu.clicked(5, 0, ClickType.PICKUP, f.player);
        helper.assertTrue(f.storage.patterns().isEmpty() && ItemStack.isSameItemSameComponents(f.menu.getCarried(), pattern),
                "normal pickup did not extract stored payload");
        f.menu.clicked(7, 0, ClickType.PICKUP, f.player);
        helper.assertTrue(f.storage.patterns().size() == 1 && f.menu.getCarried().isEmpty()
                && f.storage.getTerminalPatternInventory().getStackInSlot(5).isEmpty()
                && !f.storage.getTerminalPatternInventory().getStackInSlot(7).isEmpty(), "normal placement lost slot identity");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void invalidAndFullDestinationsPreserveItems(GameTestHelper helper) {
        var f = fixture(helper);
        var pattern = pattern(helper.getLevel());
        for (var invalid : List.of(new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(ModItems.CLOSED_LOOP_PATTERN.get()))) {
            f.player.getInventory().setItem(0, invalid.copy());
            f.menu.quickMoveStack(f.player, playerSlot(f, 0));
            helper.assertTrue(ItemStack.matches(f.player.getInventory().getItem(0), invalid)
                    && f.storage.patterns().isEmpty(), "invalid item was accepted or consumed");
        }
        for (int i = 0; i < 36; i++) f.storage.getTerminalPatternInventory().insertItem(i, pattern.copy(), false);
        f.player.getInventory().setItem(0, pattern.copy());
        f.menu.quickMoveStack(f.player, playerSlot(f, 0));
        helper.assertTrue(f.storage.patterns().size() == 36 && !f.player.getInventory().getItem(0).isEmpty(),
                "full warehouse consumed pattern");
        for (int i = 0; i < 36; i++) f.player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        f.menu.quickMoveStack(f.player, 0);
        helper.assertTrue(f.storage.patterns().size() == 36, "full backpack deleted pattern");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void editsPersistAndRemovedHostInvalidatesMenu(GameTestHelper helper) {
        var f = fixture(helper);
        f.player.getInventory().setItem(0, pattern(helper.getLevel()));
        f.menu.quickMoveStack(f.player, playerSlot(f, 0));
        var saved = f.storage.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = new TianshuPatternStorageBlockEntity(f.storage.getBlockPos(), f.storage.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.patterns().size() == 1 && ItemStack.isSameItemSameComponents(
                restored.getTerminalPatternInventory().getStackInSlot(0), pattern(helper.getLevel())),
                "GUI changes did not survive save/reload");
        helper.assertTrue(f.menu.stillValid(f.player), "unformed warehouse should be editable");
        helper.getLevel().setBlockAndUpdate(f.storage.getBlockPos(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(!f.menu.stillValid(f.player), "removed warehouse left a valid menu");
        helper.succeed();
    }
}
