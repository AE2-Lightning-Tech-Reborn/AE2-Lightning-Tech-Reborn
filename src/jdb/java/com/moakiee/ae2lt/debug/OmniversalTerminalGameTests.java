package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternPayload;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import com.moakiee.ae2lt.integration.useless.UselessModCompat;
import com.moakiee.ae2lt.logic.tianshu.terminal.OmniversalPatternDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.part.TianshuPatternEncodingTerminalPart;
import com.moakiee.ae2lt.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real native items, recipes, inventories, grids and menus; never included in the release jar. */
@GameTestHolder("ae2lt_omniversal")
@PrefixGameTestTemplate(false)
public final class OmniversalTerminalGameTests {
    @GameTest(template = "empty")
    public static void optionalBoundary(GameTestHelper helper) {
        if (!UselessModCompat.isLoaded()) {
            helper.assertTrue(UselessModCompat.recipeGeneration() == -1, "Absent mod catalog");
            helper.assertTrue(UselessModCompat.icon().isEmpty(), "Absent mod icon");
            helper.assertTrue(UselessModCompat.encodeDraft(OmniversalPatternDraft.empty(), helper.getLevel()).isEmpty(),
                    "Absent mod encoder");
            var ordinary = PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)},
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
            var draft = new OmniversalPatternDraft(ordinary);
            helper.assertTrue(UselessModCompat.encodeDraft(draft, helper.getLevel()).isEmpty(),
                    "Absent mod encoder with a nonempty draft");
            helper.assertTrue(UselessModCompat.encodeViewerRecipe(new Object(), helper.getLevel()).isEmpty(),
                    "Absent mod viewer bridge");
            helper.assertTrue(UselessModCompat.preview(draft, helper.getLevel()).outputs().isEmpty(),
                    "Absent mod preview bridge");
            helper.assertTrue(UselessModCompat.targetState(null, ordinary, helper.getLevel()) == null,
                    "Absent mod target bridge");
            UselessModCompat.clearPendingRecipe(new Object());
            helper.assertTrue(TianshuPatternUploadRouting.classify(ordinary, helper.getLevel())
                    == TianshuPatternUploadRouting.Route.PROCESSING_PROVIDER, "Ordinary routing without optional classes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void nativeCodecAndDraftPersistence(GameTestHelper helper) {
        if (!UselessModCompat.isLoaded()) { helper.succeed(); return; }
        Native.codec(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void singleFurnaceWorkflow(GameTestHelper helper) {
        if (!UselessModCompat.isLoaded()) { helper.succeed(); return; }
        Native.single(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void multiblockWorkflow(GameTestHelper helper) {
        if (!UselessModCompat.isLoaded()) { helper.succeed(); return; }
        Native.multiblock(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void menuReopenPersistence(GameTestHelper helper) {
        if (!UselessModCompat.isLoaded()) { helper.succeed(); return; }
        Native.reopen(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void editableSlotsAndRecipeSearch(GameTestHelper helper) {
        if (!UselessModCompat.isLoaded()) { helper.succeed(); return; }
        Native.editable(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void cyclePrimaryOutput(GameTestHelper helper) {
        if (!UselessModCompat.isLoaded()) { helper.succeed(); return; }
        Native.cycle(helper);
    }

    /** Avoid resolving any optional classes while the test scanner loads the outer class. */
    private static final class Native {
        static void cycle(GameTestHelper helper) {
            var fixture = network(helper, new BlockPos(3, 2, 3));
            helper.runAfterDelay(40, () -> {
                var pattern = recipe(helper, "multiple_outputs");
                var original = ProcessingPatternPayload.read(pattern).sparseOutputs();
                var rotated = List.of(original.get(1), original.get(2), original.get(0));
                var wired = open(helper, fixture);
                wired.setTianshuMode(TianshuEncodingMode.PROCESSING);
                wired.getProcessingInputSlots()[0].set(new ItemStack(Items.SAND, 3));
                wired.getProcessingOutputSlots()[0].set(new ItemStack(Items.GLASS, 3));
                wired.selectOmniversalPattern(pattern);
                helper.assertTrue(wired.canCycleOmniversalOutputs(), "Multiple outputs enable cycling");
                wired.cycleOmniversalOutput();
                assertOutputs(helper, wired, rotated);
                helper.assertTrue(wired.getProcessingInputSlots()[0].getItem().is(Items.SAND)
                                && wired.getProcessingOutputSlots()[0].getItem().is(Items.GLASS),
                        "Cycling the Omniversal draft preserves the ordinary processing draft");
                wired.encode();
                var encoded = fixture.terminal().getLogic().getEncodedPatternInv().getStackInSlot(0);
                helper.assertTrue(wired.triggeredUploadAck == 1
                                && UselessModCompat.isOmniversalPattern(encoded)
                                && ProcessingPatternPayload.read(encoded).sparseOutputs().equals(rotated),
                        "Native encoding preserves the chosen primary output and all byproducts");
                wired.removed(fixture.player());
                var reopened = new TianshuPatternEncodingTermMenu(
                        21, fixture.player().getInventory(), fixture.terminal());
                assertOutputs(helper, reopened, rotated);
                reopened.cycleOmniversalOutput();
                reopened.cycleOmniversalOutput();
                assertOutputs(helper, reopened, original);

                var item = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
                var stack = new ItemStack(item);
                var host = new com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost(
                        fixture.player(), null, stack,
                        (player, menu) -> { });
                var wireless = new com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu(
                        22, fixture.player().getInventory(), host);
                wireless.selectOmniversalPattern(pattern);
                wireless.cycleOmniversalOutput();
                assertOutputs(helper, wireless, rotated);
                var validated = UselessModCompat.encodeMatchingDraft(wireless.omniversalDraft, helper.getLevel()).pattern();
                helper.assertTrue(!validated.isEmpty()
                                && ProcessingPatternPayload.read(validated).sparseOutputs().equals(rotated),
                        "Wireless draft keeps native recipe binding after rotating outputs");
                wireless.removed(fixture.player());
                var reloaded = ItemStack.of(stack.save(new CompoundTag()));
                var restoredHost = new com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost(
                        fixture.player(), null, reloaded,
                        (player, menu) -> { });
                var restored = new com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu(
                        23, fixture.player().getInventory(), restoredHost);
                assertOutputs(helper, restored, rotated);
                restored.setTianshuMode(TianshuEncodingMode.PROCESSING);
                helper.assertTrue(!restored.canCycleOmniversalOutputs(), "Other pages cannot cycle this draft");
                restored.cycleOmniversalOutput();
                assertOutputs(helper, restored, rotated);
                restored.setTianshuMode(TianshuEncodingMode.OMNIVERSAL);
                restored.clearOmniversalDraft();
                restored.cycleOmniversalOutput();
                helper.assertTrue(restored.omniversalDraft.isEmpty(), "Empty draft remains empty");
                var largeFluid = new GenericStack(AEFluidKey.of(Fluids.WATER), 3_000_000_001L);
                restored.getOmniversalOutputSlots()[0].set(GenericStack.wrapInItemStack(largeFluid));
                var single = restored.omniversalDraft;
                restored.cycleOmniversalOutput();
                helper.assertTrue(!restored.canCycleOmniversalOutputs() && restored.omniversalDraft.equals(single),
                        "Single output is never deleted by cycling");
                restored.getOmniversalOutputSlots()[4].set(new ItemStack(Items.DIAMOND, 7));
                restored.cycleOmniversalOutput();
                helper.assertTrue(restored.getOmniversalOutputSlots()[0].getItem().is(Items.DIAMOND)
                                && GenericStack.fromItemStack(restored.getOmniversalOutputSlots()[4].getItem()).equals(largeFluid)
                                && restored.getOmniversalOutputSlots()[1].getItem().isEmpty(),
                        "Sparse outputs skip holes and preserve fluid amounts beyond int range");
                helper.succeed();
            });
        }

        private static void assertOutputs(GameTestHelper helper, TianshuPatternEncodingTermMenu menu,
                                          List<GenericStack> expected) {
            helper.assertTrue(ProcessingPatternPayload.read(menu.omniversalDraft.pattern())
                            .sparseOutputs().equals(expected),
                    "Persisted output order and exact amounts match the selected primary: actual=" + ProcessingPatternPayload.read(menu.omniversalDraft.pattern()).sparseOutputs() + " expected=" + expected);
            for (int i = 0; i < menu.getOmniversalOutputSlots().length; i++) {
                var actual = GenericStack.fromItemStack(menu.getOmniversalOutputSlots()[i].getItem());
                helper.assertTrue(java.util.Objects.equals(actual, i < expected.size() ? expected.get(i) : null),
                        "Synchronized output slot " + i + " matches the draft");
            }
        }

        static void editable(GameTestHelper helper) {
            var fixture = network(helper, new BlockPos(3, 2, 3));
            helper.runAfterDelay(40, () -> {
                var menu = open(helper, fixture);
                var tagged = recipe(helper, "tagged_input");
                menu.selectOmniversalPattern(tagged);
                menu.getOmniversalInputSlots()[0].set(new ItemStack(Items.BIRCH_PLANKS, 2));
                menu.encode();
                var inventory = fixture.terminal().getLogic().getEncodedPatternInv();
                var encoded = inventory.getStackInSlot(0);
                helper.assertTrue(UselessModCompat.isOmniversalPattern(encoded)
                        && ProcessingPatternPayload.read(encoded).sparseInputs().get(0).what()
                                .equals(AEItemKey.of(Items.BIRCH_PLANKS)),
                        "An editable alternative in the recipe tag encodes as the selected native recipe: result=" + encoded + " tag=" + encoded.getTag() + " draft=" + menu.omniversalDraft.pattern().getTag() + " status=" + menu.omniversalStatus);
                int ack = menu.triggeredUploadAck;
                menu.getOmniversalInputSlots()[0].set(new ItemStack(Items.DIRT, 2));
                menu.encode();
                helper.assertTrue(menu.triggeredUploadAck == ack && ItemStack.matches(encoded, inventory.getStackInSlot(0)),
                        "An item outside the tag is rejected without changing the owned result");
                menu.getOmniversalInputSlots()[0].set(new ItemStack(Items.BIRCH_PLANKS, 3));
                menu.encode();
                helper.assertTrue(menu.triggeredUploadAck == ack, "Incorrect input quantity is rejected");
                menu.getOmniversalInputSlots()[0].set(new ItemStack(Items.BIRCH_PLANKS, 2));
                menu.getOmniversalOutputSlots()[0].set(new ItemStack(Items.DIAMOND, 99));
                menu.encode();
                helper.assertTrue(menu.triggeredUploadAck == ack, "Incorrect output is rejected");
                menu.removed(fixture.player());
                var reopened = new TianshuPatternEncodingTermMenu(21, fixture.player().getInventory(), fixture.terminal());
                reopened.broadcastChanges();
                helper.assertTrue(GenericStack.fromItemStack(reopened.getOmniversalInputSlots()[0].getItem()).what()
                                .equals(AEItemKey.of(Items.BIRCH_PLANKS))
                        && GenericStack.fromItemStack(reopened.getOmniversalOutputSlots()[0].getItem()).what()
                                .equals(AEItemKey.of(Items.DIAMOND)),
                        "Even an invalid in-progress edit survives closing and reopening");
                reopened.setTianshuMode(TianshuEncodingMode.PROCESSING);
                reopened.getProcessingInputSlots()[0].set(new ItemStack(Items.SAND));
                reopened.setTianshuMode(TianshuEncodingMode.OMNIVERSAL);
                helper.assertTrue(reopened.getOmniversalInputSlots()[0].getItem().is(Items.BIRCH_PLANKS),
                        "Processing-page changes do not overwrite the independent Omniversal draft");
                reopened.clearOmniversalDraft();
                reopened.getOmniversalInputSlots()[0].set(new ItemStack(Items.BIRCH_PLANKS, 2));
                reopened.getOmniversalOutputSlots()[0].set(new ItemStack(Items.AMETHYST_SHARD, 7));
                reopened.getOmniversalMoldSlots()[0].set(new ItemStack(Items.STICK));
                reopened.encode();
                helper.assertTrue(reopened.triggeredUploadAck == 1
                        && UselessModCompat.isOmniversalPattern(inventory.getStackInSlot(0)),
                        "Manual unbound inputs, outputs and molds find and encode a matching native recipe");
                reopened.clearOmniversalDraft();
                reopened.getOmniversalInputSlots()[0].set(new ItemStack(Items.IRON_INGOT, 2));
                reopened.getOmniversalOutputSlots()[0].set(new ItemStack(Items.GOLD_INGOT));
                reopened.encode();
                helper.assertTrue(reopened.triggeredUploadAck == 1
                        && reopened.omniversalStatus.equals("ae2lt.tianshu.omniversal.no_match"),
                        "Matching I/O without its required mold cannot encode");
                reopened.getOmniversalMoldSlots()[0].set(new ItemStack(Items.STICK));
                reopened.encode();
                helper.assertTrue(reopened.triggeredUploadAck == 2
                        && UselessModCompat.preview(reopened.omniversalDraft, helper.getLevel()).molds().size() == 1,
                        "A single mold selects the matching single-mold recipe");
                reopened.getOmniversalMoldSlots()[1].set(new ItemStack(Items.BLAZE_ROD));
                reopened.encode();
                helper.assertTrue(reopened.triggeredUploadAck == 3
                        && UselessModCompat.preview(reopened.omniversalDraft, helper.getLevel()).molds().size() == 2,
                        "Editing the mold slots selects the different matching recipe with identical I/O");
                reopened.getOmniversalMoldSlots()[2].set(new ItemStack(Items.DIRT));
                reopened.encode();
                helper.assertTrue(reopened.triggeredUploadAck == 3, "Extra or wrong molds are filtered out");
                helper.succeed();
            });
        }

        static void reopen(GameTestHelper helper) {
            var fixture = network(helper, new BlockPos(3, 2, 3));
            helper.runAfterDelay(40, () -> {
                var pattern = recipe(helper, "two_molds");
                var expected = new OmniversalPatternDraft(pattern);
                var wired = open(helper, fixture);
                wired.selectOmniversalPattern(pattern);
                assertProcessingInteractions(helper, wired);
                wired.removed(fixture.player());
                var wiredReopened = new TianshuPatternEncodingTermMenu(
                        18, fixture.player().getInventory(), fixture.terminal());
                wiredReopened.broadcastChanges();
                helper.assertTrue(ItemStack.matches(wiredReopened.omniversalDraft.pattern(), expected.pattern())
                        && wiredReopened.tianshuMode == TianshuEncodingMode.OMNIVERSAL,
                        "Closing and reopening the same wired menu retains its unencoded selection: actual=" + wiredReopened.omniversalDraft.pattern().getTag() + " expected=" + expected.pattern().getTag() + " mode=" + wiredReopened.tianshuMode);

                var item = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
                var stack = new ItemStack(item);
                                var wirelessHost = new com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost(
                        fixture.player(), null, stack, (player, menu) -> { });
                var wireless = new com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu(
                        19, fixture.player().getInventory(), wirelessHost);
                wireless.selectOmniversalPattern(pattern);
                assertProcessingInteractions(helper, wireless);
                var previousSnapshot = stack.copy();
                var previousData = previousSnapshot.getTagElement("patternEncodingLogic").copy();
                wireless.getOmniversalInputSlots()[0].set(new ItemStack(Items.DIRT, 2));
                helper.assertTrue(previousData.equals(previousSnapshot.getTagElement("patternEncodingLogic")),
                        "Editing a wireless draft must not mutate an earlier inventory-sync snapshot");
                wireless.getOmniversalOutputSlots()[0].set(new ItemStack(Items.DIAMOND, 99));
                wireless.getOmniversalMoldSlots()[0].set(new ItemStack(Items.BLAZE_ROD));
                wireless.getOmniversalMoldSlots()[1].set(ItemStack.EMPTY);
                var editedWirelessDraft = wireless.omniversalDraft;
                wireless.removed(fixture.player());
                var saved = stack.save(new CompoundTag());
                var reloadedStack = ItemStack.of(saved);
                var reopenedHost = new com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost(
                        fixture.player(), null, reloadedStack,
                        (player, menu) -> { });
                var reopened = new com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu(
                        20, fixture.player().getInventory(), reopenedHost);
                reopened.broadcastChanges();
                helper.assertTrue(reopened.omniversalDraft.equals(editedWirelessDraft)
                        && reopened.tianshuMode == TianshuEncodingMode.OMNIVERSAL,
                        "Closing and reopening the same wireless menu retains invalid input, output and mold edits");
                helper.succeed();
            });
        }

        private static ItemStack recipe(GameTestHelper helper, String name) {
            var choice = com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.entries(helper.getLevel()).stream()
                    .filter(candidate -> candidate.identity().recipeId().toString().equals("ae2lt_omniversal:" + name))
                    .findFirst().orElseThrow();
            var result = UselessModCompat.encodeViewerRecipe(choice, helper.getLevel());
            helper.assertTrue(!result.isEmpty(), "Native fixture must encode: " + name);
            return result;
        }

        private static void assertProcessingInteractions(GameTestHelper helper, TianshuPatternEncodingTermMenu menu) {
            var input = menu.getOmniversalInputSlots()[0];
            var output = menu.getOmniversalOutputSlots()[0];
            var mold = menu.getOmniversalMoldSlots()[0];
            helper.assertTrue(menu.canModifyAmountForSlot(input) && menu.canModifyAmountForSlot(output),
                    "Omniversal input/output use the ordinary processing quantity editor");
            helper.assertTrue(!menu.canModifyAmountForSlot(mold)
                            && !menu.canModifyAmountForSlot(menu.getOmniversalInputSlots()[1])
                            && !menu.canModifyAmountForSlot(null),
                    "Mold types, empty slots and no hovered slot cannot edit quantities");
            helper.assertTrue(menu.isPatternIngredientSlot(input) && menu.isPatternIngredientSlot(mold)
                            && !menu.isPatternIngredientSlot(output),
                    "Craftable markers cover required inputs/molds only");
            menu.setTianshuMode(TianshuEncodingMode.PROCESSING);
            helper.assertTrue(!menu.canModifyAmountForSlot(input), "Hidden Omniversal slots cannot edit quantities");
            var ordinary = menu.getProcessingInputSlots()[0];
            ordinary.set(new ItemStack(Items.SAND, 3));
            helper.assertTrue(menu.canModifyAmountForSlot(ordinary), "Ordinary processing quantity editing remains available");
            menu.setTianshuMode(TianshuEncodingMode.OMNIVERSAL);
            helper.assertTrue(!menu.canModifyAmountForSlot(ordinary), "Hidden ordinary slots cannot edit quantities");
        }

        static void codec(GameTestHelper helper) {
            var pattern = recipe(helper, "two_molds");
            var draft = new OmniversalPatternDraft(pattern, List.of(new ItemStack(Items.STICK), new ItemStack(Items.BLAZE_ROD)));
            var dataType = com.sorrowmist.useless.core.component.UComponents.OMNIVERSAL_PATTERN_DATA.get();
            helper.assertTrue(ComponentAccess.get(pattern, dataType) != null, "Native recipe metadata");
            helper.assertTrue(UselessModCompat.preview(draft, helper.getLevel()).molds().size() == 2,
                    "Both mold requirements survive preview");
            pattern.setCount(42);
            ComponentAccess.remove(draft.pattern(), dataType);
            draft.molds().get(0).setCount(42);
            helper.assertTrue(draft.pattern().getCount() == 1 && ComponentAccess.get(draft.pattern(), dataType) != null,
                    "Draft owns an immutable single-pattern snapshot");
            helper.assertTrue(draft.molds().get(0).getCount() == 1, "Editable mold snapshots are immutable");
            var decoded = OmniversalPatternDraft.read(draft.write());
            helper.assertTrue(decoded.equals(draft), "Native component NBT round trip");
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                draft.writeToPacket(buf);
                helper.assertTrue(new OmniversalPatternDraft(buf).equals(draft), "Native component packet round trip");
            } finally { buf.release(); }
            for (int i = 0; i < 3; i++) {
                var encoded = UselessModCompat.encodeDraft(decoded, helper.getLevel());
                helper.assertTrue(ItemStack.matches(draft.pattern(), encoded), "Repeated native encoding stays bound");
                decoded = new OmniversalPatternDraft(encoded);
            }
            var tampered = draft.pattern();
            var wrong = PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIRT), 1)},
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIAMOND), 99)});
            ProcessingPatternPayload.write(tampered, ProcessingPatternPayload.read(wrong).sparseInputs(), ProcessingPatternPayload.read(wrong).sparseOutputs());
            helper.assertTrue(UselessModCompat.encodeDraft(new OmniversalPatternDraft(tampered), helper.getLevel()).isEmpty(),
                    "Recipe identity must not authorize forged ingredients or outputs");
            var player = FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "OmniversalQA"));
            var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)),
                    Direction.SOUTH, player, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
            part.setOmniversalPatternDraft(draft);
            part.setTianshuEncodingMode(TianshuEncodingMode.OMNIVERSAL);
            var tag = new CompoundTag();
            part.writeToNBT(tag);
            var restored = PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 2)),
                    Direction.SOUTH, player, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
            restored.readFromNBT(tag);
            helper.assertTrue(restored.getOmniversalPatternDraft().equals(draft)
                    && restored.getTianshuEncodingMode() == TianshuEncodingMode.OMNIVERSAL,
                    "Wired terminal restores native draft and mode");
            var wirelessItem = ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
            var wirelessStack = new ItemStack(wirelessItem);
                        var wireless = new com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost(
                    player, null, wirelessStack, (ignoredPlayer, ignoredMenu) -> { });
            wireless.setOmniversalPatternDraft(draft);
            wireless.setTianshuEncodingMode(TianshuEncodingMode.OMNIVERSAL);
            var wirelessRestored = new com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost(
                    player, null, wirelessStack, (ignoredPlayer, ignoredMenu) -> { });
            helper.assertTrue(wirelessRestored.getOmniversalPatternDraft().equals(draft)
                    && wirelessRestored.getTianshuEncodingMode() == TianshuEncodingMode.OMNIVERSAL,
                    "Wireless item restores native draft and mode");
            helper.succeed();
        }

        private static Fixture network(GameTestHelper helper, BlockPos cable) {
            var player = FakePlayerFactory.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "OmniversalQA"));
            PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), null, player,
                    AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
            var terminal = PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), Direction.SOUTH,
                    player, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
            helper.setBlock(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            helper.setBlock(cable.west(), AEBlocks.DRIVE.block());
            DriveBlockEntity drive = (DriveBlockEntity) helper.getBlockEntity(cable.west());
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            return new Fixture(terminal, player);
        }

        private static TianshuPatternEncodingTermMenu open(GameTestHelper helper, Fixture fixture) {
            var grid = fixture.terminal().getActionableNode().getGrid();
            helper.assertTrue(grid.getEnergyService().isNetworkPowered(), "Fixture network power");
            helper.assertTrue(grid.getStorageService().getInventory().insert(AEItemKey.of(AEItems.BLANK_PATTERN),
                    8, Actionable.MODULATE, IActionSource.empty()) == 8, "Fixture blank patterns in real ME storage");
            var menu = new TianshuPatternEncodingTermMenu(17, fixture.player().getInventory(), fixture.terminal());
            fixture.player().containerMenu = menu;
            return menu;
        }

        static void single(GameTestHelper helper) {
            var cable = new BlockPos(3, 2, 3);
            var fixture = network(helper, cable);
            helper.setBlock(cable.east(), com.sorrowmist.useless.init.ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
            helper.runAfterDelay(40, () -> {
                var menu = open(helper, fixture);
                var pattern = recipe(helper, "one_mold");
                var furnace = (com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity)
                        helper.getBlockEntity(cable.east());
                var state = UselessModCompat.targetState(furnace, pattern, helper.getLevel());
                helper.assertTrue(state.supported() && !state.ready(), "Single furnace reports its missing mold");
                helper.assertTrue(!UselessModCompat.targetState(furnace, recipe(helper, "two_molds"), helper.getLevel()).supported(),
                        "Single furnace cannot receive a multi-mold recipe");
                var inventory = fixture.terminal().getLogic().getEncodedPatternInv();
                menu.selectOmniversalPattern(pattern);
                helper.assertTrue(menu.tianshuMode == TianshuEncodingMode.OMNIVERSAL, "Selection enters dedicated mode");
                menu.encode();
                helper.assertTrue(ItemStack.matches(pattern, inventory.getStackInSlot(0)), "Menu creates native result");
                var storage = fixture.terminal().getActionableNode().getGrid().getStorageService().getInventory();
                helper.assertTrue(storage.getAvailableStacks().get(AEItemKey.of(AEItems.BLANK_PATTERN)) == 7,
                        "Encoding consumes exactly one network blank");
                menu.encode();
                helper.assertTrue(storage.getAvailableStacks().get(AEItemKey.of(AEItems.BLANK_PATTERN)) == 7,
                        "Reencoding an existing pattern consumes no new blank");
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 3 && ItemStack.matches(pattern, inventory.getStackInSlot(0))
                        && furnace.getTerminalPatternInventory().getStackInSlot(0).isEmpty(),
                        "Automatic upload never picks a furnace without the matching mold: state=" + menu.uploadState + " result=" + inventory.getStackInSlot(0) + " target=" + furnace.getTerminalPatternInventory().getStackInSlot(0));
                furnace.getItemHandler().setStackInSlot(furnace.getMoldSlot(), new ItemStack(Items.STICK));
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 1 && inventory.getStackInSlot(0).isEmpty(), "Automatic single-furnace upload");
                helper.assertTrue(ItemStack.matches(pattern, furnace.getTerminalPatternInventory().getStackInSlot(0)),
                        "Real single-furnace inventory receives all native data");
                helper.assertTrue(UselessModCompat.targetState(furnace, pattern, helper.getLevel()).ready(), "Installed mold is ready");
                var target = furnace.getTerminalPatternInventory();
                for (int i = 0; i < target.size(); i++) target.setItemDirect(i, pattern.copy());
                menu.encode();
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 3 && ItemStack.matches(pattern, inventory.getStackInSlot(0)),
                        "Full furnace retains the paid encoded pattern");
                helper.assertTrue(storage.getAvailableStacks().get(AEItemKey.of(AEItems.BLANK_PATTERN)) == 6,
                        "Failed upload must not also refund the retained item");
                menu.selectOmniversalPattern(UselessModCompat.icon());
                helper.assertTrue(menu.omniversalDraft.isEmpty(), "Invalid selection clears the previous binding");
                int ack = menu.triggeredUploadAck;
                menu.encode();
                helper.assertTrue(menu.triggeredUploadAck == ack, "Invalid selection cannot encode an old draft");
                helper.assertTrue(ItemStack.matches(pattern, inventory.getStackInSlot(0)), "Invalid selection leaves owned item intact");
                helper.succeed();
            });
        }

        static void multiblock(GameTestHelper helper) {
            var core = new BlockPos(5, 2, 3);
            var cable = core.west(2);
            var fixture = network(helper, cable);
            for (var entry : com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure.entries()) {
                var pos = entry.worldPos(core, Direction.NORTH);
                var block = switch (entry.part()) {
                    case CORE -> com.sorrowmist.useless.init.ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get();
                    case CASING -> com.sorrowmist.useless.init.ModBlocks.OMNIVERSAL_FURNACE_CASING.get();
                    case COIL -> com.sorrowmist.useless.init.ModBlocks.USELESS_COILS.get(1).get();
                    case AIR -> Blocks.AIR;
                };
                helper.setBlock(pos, block);
            }
            helper.setBlock(core.west(), com.sorrowmist.useless.init.ModBlocks.ME_PATTERN_ASSEMBLY.get());
            helper.setBlock(core.east(), com.sorrowmist.useless.init.ModBlocks.OMNIVERSAL_MOLD_HUB.get());
            helper.setBlock(cable.north(), com.sorrowmist.useless.init.ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get());
            helper.runAfterDelay(50, () -> {
                var assembly = (com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity)
                        helper.getBlockEntity(core.west());
                helper.assertTrue(assembly.getController() != null && assembly.getController().isFormed(),
                        "Fixture is an actually validated multiblock");
                var pattern = recipe(helper, "two_molds");
                var state = UselessModCompat.targetState(assembly, pattern, helper.getLevel());
                helper.assertTrue(state.supported() && !state.ready(), "Multiblock reports missing molds");
                var menu = open(helper, fixture);
                menu.selectOmniversalPattern(pattern);
                menu.encode();
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 3
                        && assembly.getTerminalPatternInventory().getStackInSlot(0).isEmpty(),
                        "Automatic upload skips an empty mold hub");
                var hub = (com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity)
                        helper.getBlockEntity(core.east());
                hub.getMolds().setStackInSlot(0, new ItemStack(Items.STICK));
                hub.getMolds().setStackInSlot(1, new ItemStack(Items.BLAZE_ROD));
                helper.assertTrue(UselessModCompat.targetState(assembly, pattern, helper.getLevel()).ready(),
                        "Both installed molds make the native recipe ready");
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 1, "Automatic upload finds the linked ME Pattern Assembly");
                helper.assertTrue(ItemStack.matches(pattern, assembly.getTerminalPatternInventory().getStackInSlot(0)),
                        "ME Pattern Assembly receives native pattern");
                var single = (com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity)
                        helper.getBlockEntity(cable.north());
                var oneMold = recipe(helper, "one_mold");
                single.getItemHandler().setStackInSlot(single.getMoldSlot(), new ItemStack(Items.STICK));
                helper.assertTrue(UselessModCompat.targetState(single, oneMold, helper.getLevel()).ready(),
                        "Competing single furnace has the matching mold too");
                menu.selectOmniversalPattern(oneMold);
                menu.encode();
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 1
                        && ItemStack.matches(oneMold, assembly.getTerminalPatternInventory().getStackInSlot(1))
                        && single.getTerminalPatternInventory().getStackInSlot(0).isEmpty(),
                        "Ready multiblock takes priority even when the single furnace is also ready");
                var multiInventory = assembly.getTerminalPatternInventory();
                for (int i = 0; i < multiInventory.size(); i++) multiInventory.setItemDirect(i, oneMold.copy());
                menu.encode();
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 1
                        && ItemStack.matches(oneMold, single.getTerminalPatternInventory().getStackInSlot(0)),
                        "A full matching multiblock falls back automatically to the matching single furnace");
                multiInventory.setItemDirect(0, ItemStack.EMPTY);
                hub.getMolds().setStackInSlot(0, new ItemStack(Items.DIRT));
                menu.encode();
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 1 && multiInventory.getStackInSlot(0).isEmpty()
                        && ItemStack.matches(oneMold, single.getTerminalPatternInventory().getStackInSlot(1)),
                        "A multiblock with the wrong mold does not outrank a matching single furnace");
                single.getItemHandler().setStackInSlot(single.getMoldSlot(), new ItemStack(Items.DIRT));
                menu.encode();
                menu.uploadEncodedPattern();
                helper.assertTrue(menu.uploadState == 3
                        && ItemStack.matches(oneMold, fixture.terminal().getLogic().getEncodedPatternInv().getStackInSlot(0))
                        && multiInventory.getStackInSlot(0).isEmpty()
                        && single.getTerminalPatternInventory().getStackInSlot(2).isEmpty(),
                        "No matching molds leaves the encoded item in the terminal without selecting any machine");
                assembly.linkController(null, 0);
                helper.assertTrue(!UselessModCompat.targetState(assembly, pattern, helper.getLevel()).supported(),
                        "Unlinked assembly is never auto-routed");
                helper.succeed();
            });
        }

        private record Fixture(TianshuPatternEncodingTerminalPart terminal, ServerPlayer player) { }
    }
}
