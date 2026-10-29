package com.moakiee.ae2lt.debug;

import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Real terminal encoding and upload against a powered ME network and full provider. */
@net.neoforged.neoforge.gametest.GameTestHolder("ae2lt_upload_retention")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TianshuPatternUploadGameTests {
    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 150)
    public static void wiredFullTargetRetainsEncodedPattern(GameTestHelper helper) {
        fullTarget(helper, false);
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 150)
    public static void wirelessFullTargetRetainsEncodedPattern(GameTestHelper helper) {
        fullTarget(helper, true);
    }

    private static void fullTarget(GameTestHelper helper, boolean wireless) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UploadRetention"));
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        PartHelper.setPart(level, cable, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, cable, Direction.SOUTH, player,
                ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        level.setBlockAndUpdate(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cable.west(), AEBlocks.DRIVE.block().defaultBlockState());
        ((DriveBlockEntity) level.getBlockEntity(cable.west())).getInternalInventory()
                .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        level.setBlockAndUpdate(cable.east(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        level.setBlockAndUpdate(cable.east(2), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
        level.setBlockAndUpdate(cable.north(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        player.setPos(cable.getX() + .5, cable.getY() + 1, cable.getZ() + .5);

        helper.runAfterDelay(40, () -> {
            var grid = terminal.getActionableNode().getGrid();
            check(helper, grid.getEnergyService().isNetworkPowered(), "network is powered");
            var storage = grid.getStorageService().getInventory();
            var source = IActionSource.ofPlayer(player);
            var blank = AEItemKey.of(AEItems.BLANK_PATTERN);
            check(helper, storage.insert(blank, 8, Actionable.MODULATE, source) == 8, "store eight blank patterns");
            var provider = (PatternProviderBlockEntity) level.getBlockEntity(cable.east());
            var target = provider.getLogic().getPatternInv();
            check(helper, TianshuPatternUploadRouting.isCraftingUploadGroup(provider.getLogic().getTerminalGroup()),
                    "provider adjacent to assembler is a crafting upload target");

            var item = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
            var stack = new ItemStack(item);
            stack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), cable.north()));
            stack.set(AEComponents.STORED_ENERGY, 1000000.0);
            player.getInventory().setItem(0, stack);
            var locator = MenuLocators.forInventorySlot(0);
            var wirelessHost = wireless ? new TianshuWirelessPatternEncodingTermMenuHost(
                    item, player, locator, (p, m) -> {}) : null;
            TianshuPatternEncodingTermMenu menu = wireless
                    ? new TianshuWirelessPatternEncodingTermMenu(17, player.getInventory(), wirelessHost)
                    : new TianshuPatternEncodingTermMenu(17, player.getInventory(), terminal);
            player.containerMenu = menu;
            menu.getSlots(SlotSemantics.CRAFTING_GRID).getFirst().set(new ItemStack(Items.OAK_LOG));
            menu.broadcastChanges();
            menu.encode();
            var encodedInventory = (wireless ? wirelessHost.getLogic() : terminal.getLogic()).getEncodedPatternInv();
            var encoded = encodedInventory.getStackInSlot(0).copy();
            check(helper, PatternDetailsHelper.decodePattern(encoded, level) != null, "native crafting encoding succeeds");
            check(helper, storage.extract(blank, 99, Actionable.SIMULATE, source) == 7, "encoding charges one blank");
            for (int i = 0; i < target.size(); i++) target.setItemDirect(i, encoded.copy());

            for (int attempt = 0; attempt < 3; attempt++) {
                menu.uploadEncodedPattern();
                check(helper, menu.uploadState == 3, "full target reports upload failure");
                check(helper, ItemStack.matches(encoded, encodedInventory.getStackInSlot(0)),
                        "failed upload retains the exact paid encoded pattern in its slot");
                check(helper, storage.extract(blank, 99, Actionable.SIMULATE, source) == 7,
                        "failed upload neither refunds nor consumes another blank");
            }

            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            var reopenedHost = wireless ? new TianshuWirelessPatternEncodingTermMenuHost(
                    item, player, locator, (p, m) -> {}) : null;
            TianshuPatternEncodingTermMenu reopened = wireless
                    ? new TianshuWirelessPatternEncodingTermMenu(18, player.getInventory(), reopenedHost)
                    : new TianshuPatternEncodingTermMenu(18, player.getInventory(), terminal);
            player.containerMenu = reopened;
            var retained = (wireless ? reopenedHost.getLogic() : terminal.getLogic()).getEncodedPatternInv();
            check(helper, ItemStack.matches(encoded, retained.getStackInSlot(0)), "close and reopen retain the encoded item");
            target.setItemDirect(0, ItemStack.EMPTY);
            helper.runAfterDelay(5, () -> {
                reopened.broadcastChanges();
                check(helper, target.getStackInSlot(0).isEmpty() && ItemStack.matches(encoded, retained.getStackInSlot(0)),
                        "freeing target space waits for a user action");
                reopened.uploadEncodedPattern();
                check(helper, reopened.uploadState == 1 && retained.getStackInSlot(0).isEmpty(), "manual retry succeeds");
                check(helper, ItemStack.matches(encoded, target.getStackInSlot(0)), "target receives the retained pattern intact");
                check(helper, storage.extract(blank, 99, Actionable.SIMULATE, source) == 7, "retry consumes no new blank");
                reopened.uploadEncodedPattern();
                check(helper, storage.extract(blank, 99, Actionable.SIMULATE, source) == 7, "empty retry cannot refund a blank");
                try {
                    var encode = TianshuPatternEncodingTermMenu.class
                            .getDeclaredMethod("encodeServerWithOptions", Boolean.class);
                    encode.setAccessible(true);
                    encode.invoke(reopened, true);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
                check(helper, retained.getStackInSlot(0).isEmpty()
                                && storage.extract(blank, 99, Actionable.SIMULATE, source) == 7,
                        "duplicate interception still refunds only a newly rejected encoding");
                reopened.removed(player);
                player.containerMenu = player.inventoryMenu;
                System.out.println("TIANSHU_UPLOAD_RETENTION_PASS " + (wireless ? "wireless" : "wired"));
                helper.succeed();
            });
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 150)
    public static void multiplayerUploadUndo(GameTestHelper helper) {
        var level = helper.getLevel();
        var alice = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UploadAlice"));
        var bob = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UploadBob"));
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        PartHelper.setPart(level, cable, null, alice, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, cable, Direction.SOUTH, alice,
                ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        level.setBlockAndUpdate(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cable.east(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        alice.setPos(cable.getX() + .5, cable.getY() + 1, cable.getZ() + .5);
        bob.setPos(alice.position());
        helper.runAfterDelay(40, () -> {
            var a = new TianshuPatternEncodingTermMenu(31, alice.getInventory(), terminal);
            var b = new TianshuPatternEncodingTermMenu(32, bob.getInventory(), terminal);
            alice.containerMenu = a;
            bob.containerMenu = b;
            var provider = (PatternProviderBlockEntity) level.getBlockEntity(cable.east());
            var target = provider.getLogic().getPatternInv();
            var source = terminal.getLogic().getEncodedPatternInv();
            var first = processing(Items.IRON_INGOT, Items.GOLD_INGOT);
            var second = processing(Items.COPPER_INGOT, Items.DIAMOND);
            source.setItemDirect(0, first.copy());
            a.sendUploadTargets(alice);
            var group = a.getUploadTargets().getFirst().group();
            int staleRevision = a.getUploadTargetRequestRevision();
            source.setItemDirect(0, second.copy());
            a.uploadTianshuPatternToTarget(alice, group, staleRevision);
            check(helper, a.uploadState == 3 && target.isEmpty() && ItemStack.matches(second, source.getStackInSlot(0)),
                    "stale target request cannot upload another player's replacement pattern: state=" + a.uploadState + ", target=" + target.getStackInSlot(0) + ", source=" + source.getStackInSlot(0) + ", matches=" + ItemStack.matches(second, source.getStackInSlot(0)));
            a.uploadTianshuPatternToTarget(alice, group, staleRevision);
            check(helper, a.uploadState == 3 && target.isEmpty() && ItemStack.matches(second, source.getStackInSlot(0)),
                    "replaying rejected request cannot acquire the refreshed target binding");
            source.setItemDirect(0, first.copy());
            a.setMode(appeng.parts.encoding.EncodingMode.PROCESSING);
            a.getSlots(SlotSemantics.PROCESSING_INPUTS).getFirst().set(new ItemStack(Items.IRON_INGOT));
            a.getSlots(SlotSemantics.PROCESSING_OUTPUTS).getFirst().set(new ItemStack(Items.GOLD_INGOT));
            a.encode();
            check(helper, a.triggeredUploadAck > 0, "Alice encoded a real processing pattern");
            source.setItemDirect(0, second.copy());
            a.sendUploadTargets(alice);
            a.uploadTianshuPatternToTarget(alice, group, a.getUploadTargetRequestRevision(), a.triggeredUploadAck);
            check(helper, a.uploadState == 3 && target.isEmpty() && ItemStack.matches(second, source.getStackInSlot(0)),
                    "automatic upload rejects another player's pattern replaced before target discovery");
            source.setItemDirect(0, first.copy());
            a.sendUploadTargets(alice);
            a.uploadTianshuPatternToTarget(alice, group, a.getUploadTargetRequestRevision());
            check(helper, a.uploadState == 1 && source.isEmpty() && !target.isEmpty(), "Alice upload succeeds");
            b.undoLastUpload();
            check(helper, source.isEmpty() && !target.isEmpty(), "Bob cannot undo Alice's receipt");
            a.undoLastUpload();
            check(helper, ItemStack.matches(first, source.getStackInSlot(0)) && target.isEmpty(), "Alice reclaims original paid pattern");
            a.undoLastUpload();
            check(helper, source.getStackInSlot(0).getCount() == 1 && target.isEmpty(), "repeated undo cannot duplicate");

            a.sendUploadTargets(alice);
            a.uploadTianshuPatternToTarget(alice, group, a.getUploadTargetRequestRevision());
            source.setItemDirect(0, second.copy());
            a.undoLastUpload();
            check(helper, ItemStack.matches(second, source.getStackInSlot(0)) && !target.isEmpty(), "occupied shared terminal rejects undo");
            source.setItemDirect(0, ItemStack.EMPTY);
            var live = target.getStackInSlot(0);
            target.setItemDirect(0, live.copy());
            a.undoLastUpload();
            check(helper, source.isEmpty() && !target.isEmpty() && !a.canUndoUpload,
                    "equal pattern replaced by another player invalidates the receipt");
            target.setItemDirect(0, ItemStack.EMPTY);
            source.setItemDirect(0, first.copy());
            a.sendUploadTargets(alice);
            a.uploadTianshuPatternToTarget(alice, group, a.getUploadTargetRequestRevision());
            source.setItemDirect(0, second.copy());
            b.sendUploadTargets(bob);
            b.uploadTianshuPatternToTarget(bob, group, b.getUploadTargetRequestRevision());
            check(helper, target.getStackInSlot(0).getCount() == 1 && target.getStackInSlot(1).getCount() == 1,
                    "two players upload into different slots");
            a.undoLastUpload();
            check(helper, target.getStackInSlot(0).isEmpty() && ItemStack.matches(second, target.getStackInSlot(1)),
                    "Alice undo leaves Bob's upload untouched");
            source.setItemDirect(0, ItemStack.EMPTY);
            b.undoLastUpload();
            check(helper, target.isEmpty() && ItemStack.matches(second, source.getStackInSlot(0)), "Bob reclaims his own upload");
            source.setItemDirect(0, first.copy());
            a.sendUploadTargets(alice);
            a.uploadTianshuPatternToTarget(alice, group, a.getUploadTargetRequestRevision());
            source.setItemDirect(0, second.copy());
            a.sendUploadTargets(alice);
            a.uploadTianshuPatternToTarget(alice, group, a.getUploadTargetRequestRevision());
            a.undoLastUpload();
            check(helper, ItemStack.matches(second, source.getStackInSlot(0)) && ItemStack.matches(first, target.getStackInSlot(0)),
                    "only most recent successful upload is reclaimed");
            source.setItemDirect(0, ItemStack.EMPTY);
            a.undoLastUpload();
            check(helper, source.isEmpty() && ItemStack.matches(first, target.getStackInSlot(0)), "undo does not expose older history");
            source.setItemDirect(0, second.copy());
            a.sendUploadTargets(alice);
            a.uploadTianshuPatternToTarget(alice, group, a.getUploadTargetRequestRevision());
            level.setBlockAndUpdate(cable.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            a.undoLastUpload();
            check(helper, source.isEmpty(), "removed target cannot materialize another pattern");
            a.removed(alice);
            b.removed(bob);
            alice.containerMenu = alice.inventoryMenu;
            bob.containerMenu = bob.inventoryMenu;
            System.out.println("TIANSHU_MULTIPLAYER_UNDO_PASS");
            helper.succeed();
        });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test", timeoutTicks = 200, batch = "closed_loop_upload_undo")
    public static void closedLoopUploadUndo(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var direction = Direction.NORTH;
        var build = TianshuJdbHarness.class.getDeclaredMethod("buildComplete", net.minecraft.server.level.ServerLevel.class, BlockPos.class, Direction.class);
        build.setAccessible(true);
        build.invoke(null, level, pos, direction);
        var controller = (com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity) level.getBlockEntity(pos);
        controller.initializeIdentityFromItem(ItemStack.EMPTY);
        controller.scanNow();
        check(helper, controller.isFormed(), "closed-loop test controller forms");
        var portPos = com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner.worldPos(pos,
                com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate.LOWER_PORT, direction);
        var port = (com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity) level.getBlockEntity(portPos);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LoopUndo"));
        var cable = pos.offset(12, 0, 0);
        PartHelper.setPart(level, cable, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, cable, Direction.SOUTH, player, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        level.setBlockAndUpdate(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        player.setPos(cable.getX() + .5, cable.getY() + 1, cable.getZ() + .5);
        helper.runAfterDelay(2, () -> appeng.api.networking.GridHelper.createConnection(port.getMainNode().getNode(), terminal.getActionableNode()));
        helper.runAfterDelay(50, () -> {
            var seed = AEItemKey.of(Items.DIAMOND);
            var raw = AEItemKey.of(Items.REDSTONE);
            var output = AEItemKey.of(Items.PAPER);
            var member = PatternDetailsHelper.encodeProcessingPattern(
                    java.util.List.of(new appeng.api.stacks.GenericStack(seed, 1), new appeng.api.stacks.GenericStack(raw, 1)),
                    java.util.List.of(new appeng.api.stacks.GenericStack(seed, 1), new appeng.api.stacks.GenericStack(output, 1)));
            var payload = new com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload(
                    java.util.List.of(new com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern(
                            com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot.fromItemStack(member, level.registryAccess()), 1)),
                    java.util.List.of(new appeng.api.stacks.GenericStack(seed, 1)),
                    java.util.List.of(new appeng.api.stacks.GenericStack(raw, 1)),
                    java.util.List.of(new appeng.api.stacks.GenericStack(output, 1)), 1, 1, true);
            var pattern = ((com.moakiee.ae2lt.item.ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get()).createStack(payload, level.registryAccess());
            var menu = new TianshuPatternEncodingTermMenu(41, player.getInventory(), terminal);
            player.containerMenu = menu;
            var source = terminal.getLogic().getEncodedPatternInv();
            var repository = port.getClosedLoopPatternRepository();
            source.setItemDirect(0, pattern.copy());
            menu.broadcastChanges();
            menu.uploadEncodedPattern();
            check(helper, source.isEmpty() && repository.size() == 1 && menu.canUndoUpload, "closed-loop upload creates receipt");
            menu.undoLastUpload();
            check(helper, repository.size() == 0 && ItemStack.matches(pattern, source.getStackInSlot(0)), "closed-loop undo restores paid stack");
            menu.uploadEncodedPattern();
            var original = repository.get(0);
            repository.replace(original, payload);
            port.closedLoopPatternsChanged();
            menu.undoLastUpload();
            check(helper, repository.size() == 1 && repository.get(0) == payload && source.isEmpty() && !menu.canUndoUpload,
                    "another player's logical replacement cannot be undone by an old receipt");
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            System.out.println("TIANSHU_CLOSED_LOOP_UNDO_PASS");
            helper.succeed();
        });
    }

    private static ItemStack processing(net.minecraft.world.item.Item input, net.minecraft.world.item.Item output) {
        return PatternDetailsHelper.encodeProcessingPattern(
                java.util.List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(input), 1)),
                java.util.List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(output), 1)));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
