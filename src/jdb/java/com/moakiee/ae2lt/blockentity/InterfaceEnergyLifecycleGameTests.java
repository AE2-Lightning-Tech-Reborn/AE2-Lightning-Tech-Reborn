package com.moakiee.ae2lt.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.service.EnergyService;
import appeng.util.SettingsFrom;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

/**
 * Native lifecycle coverage for the interface's raw-AE transfer reservation.
 * The real one-slot import path, barrel, AE energy service, drops and placement are used.
 * Reflection only selects the existing import entry point and observes its custodian.
 */
@GameTestHolder("ae2lt_interface_ae")
@PrefixGameTestTemplate(false)
public final class InterfaceEnergyLifecycleGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final BlockPos BARREL = POS.south();
    private static final BlockPos COPY = POS.east(2);
    private static final String TRANSFER_ENERGY = "ae2ltTransferEnergy";
    private static final AEItemKey STONE = AEItemKey.of(Items.STONE);
    private static final double EPSILON = 1.0e-6;

    private record Reservation(OverloadedInterfaceBlockEntity owner,
                               EnergyService energy, double initialRawAe,
                               double externallyAddedRawAe, double paidRawAe,
                               double retainedRawAe) {}

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void refusedPrepaidAeSurvivesNativeHostSaveAndReload(GameTestHelper helper) {
        withRejectedRefund(helper, reservation -> {
            var saved = reservation.owner().saveWithoutMetadata();
            close(helper, saved.getDouble(TRANSFER_ENERGY), reservation.retainedRawAe(),
                    "Native host save omitted retained raw AE");
            // Replace the actual world host, rather than loading a detached custodian copy.
            helper.setBlock(POS, Blocks.AIR);
            helper.setBlock(POS, ModBlocks.OVERLOADED_INTERFACE.get());
            var restored = owner(helper);
            restored.load(saved);
            close(helper, access(restored).storedEnergy(), reservation.retainedRawAe(),
                    "Native host reload lost retained raw AE");
            helper.assertTrue(restored.benchmarkBufferedImportAmount() == 64,
                    "Host reload lost the paid physical import");
            close(helper, restored.saveWithoutMetadata().getDouble(TRANSFER_ENERGY),
                    reservation.retainedRawAe(), "A second host save changed retained raw AE");
            var preview = new CompoundTag();
            restored.exportSettings(SettingsFrom.DISMANTLE_ITEM, preview, player(helper));
            restored.load(saved);
            restored.addAdditionalDrops(helper.getLevel(), restored.getBlockPos(), new ArrayList<>());
            close(helper, access(restored).storedEnergy(), reservation.retainedRawAe(),
                    "Host reload retained a stale dismantle-preview handoff");
            System.out.println("INTERFACE_AE_SAVE_RELOAD_PASS retainedRawAe="
                    + reservation.retainedRawAe() + " items=64");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void memoryCardCannotCopyOrConsumeRetainedPrepaidAe(GameTestHelper helper) {
        withRejectedRefund(helper, reservation -> {
            helper.setBlock(COPY, ModBlocks.OVERLOADED_INTERFACE.get());
            var destination = (OverloadedInterfaceBlockEntity) helper.getBlockEntity(COPY);
            var player = player(helper);
            for (int i = 0; i < 3; i++) {
                var memory = new CompoundTag();
                reservation.owner().exportSettings(SettingsFrom.MEMORY_CARD, memory, player);
                helper.assertTrue(!containsKey(memory, TRANSFER_ENERGY),
                        "MEMORY_CARD exported physical raw AE");
                destination.importSettings(SettingsFrom.MEMORY_CARD, memory, player);
                close(helper, access(destination).storedEnergy(), 0,
                        "MEMORY_CARD minted a destination raw-AE balance");
                close(helper, access(reservation.owner()).storedEnergy(),
                        reservation.retainedRawAe(), "MEMORY_CARD consumed the source raw AE");
            }
            helper.assertTrue(destination.benchmarkBufferedImportAmount() == 0
                            && reservation.owner().benchmarkBufferedImportAmount() == 64,
                    "MEMORY_CARD copied or consumed paid physical imports");
            assertFundingConserved(helper, reservation);
            var replacement = new CompoundTag();
            reservation.owner().exportSettings(SettingsFrom.DISMANTLE_ITEM, replacement, player);
            var reused = replacement.copy();
            reservation.owner().exportSettings(SettingsFrom.MEMORY_CARD, reused, player);
            helper.assertTrue(!containsKey(reused, TRANSFER_ENERGY),
                    "MEMORY_CARD retained physical raw AE from a reused output tag");
            reservation.owner().importSettings(SettingsFrom.DISMANTLE_ITEM, replacement, player);
            reservation.owner().addAdditionalDrops(helper.getLevel(),
                    reservation.owner().getBlockPos(), new ArrayList<>());
            close(helper, access(reservation.owner()).storedEnergy(), reservation.retainedRawAe(),
                    "Dismantle import retained a stale preview handoff from the replaced snapshot");
            System.out.println("INTERFACE_AE_MEMORY_CARD_PASS exports=3 imports=3 retainedRawAe="
                    + reservation.retainedRawAe());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void refusedPrepaidAeTravelsThroughGetDropsAndNativePlacement(GameTestHelper helper) {
        withRejectedRefund(helper, reservation -> {
            var original = reservation.owner();
            var player = player(helper);
            // Export previews can be repeated by loot/native wrench code. They must not spend custody.
            for (int i = 0; i < 2; i++) {
                original.exportSettings(SettingsFrom.DISMANTLE_ITEM, new CompoundTag(), player);
                close(helper, access(original).storedEnergy(), reservation.retainedRawAe(),
                        "A dismantle-settings preview consumed the source raw AE");
            }
            var drops = new ArrayList<>(Block.getDrops(original.getBlockState(), helper.getLevel(),
                    original.getBlockPos(), original, player, ItemStack.EMPTY));
            close(helper, access(original).storedEnergy(), reservation.retainedRawAe(),
                    "getDrops preview consumed the source raw AE");
            original.addAdditionalDrops(helper.getLevel(), original.getBlockPos(), drops);
            original.clearContent();
            helper.setBlock(POS, Blocks.AIR);
            var restored = placeDrop(helper, player, drops);
            assertPortableOwnership(helper, reservation, restored, drops, "get-drops");
            System.out.println("INTERFACE_AE_GET_DROPS_PASS retainedRawAe="
                    + reservation.retainedRawAe() + " items=64");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void refusedPrepaidAeTravelsThroughNativeWrenchAndPlacement(GameTestHelper helper) {
        withRejectedRefund(helper, reservation -> {
            var player = player(helper);
            var wrench = AEItems.CERTUS_QUARTZ_WRENCH.stack();
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            var hit = new BlockHitResult(Vec3.atCenterOf(reservation.owner().getBlockPos()),
                    Direction.UP, reservation.owner().getBlockPos(), false);
            var result = reservation.owner().disassembleWithWrench(
                    player, helper.getLevel(), hit, wrench);
            helper.assertTrue(result.consumesAction()
                            && helper.getLevel().getBlockState(helper.absolutePos(POS)).isAir(),
                    "Native wrench did not dismantle the interface");
            var drops = new ArrayList<ItemStack>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                var stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) drops.add(stack.copy());
            }
            player.getInventory().clearContent();
            var restored = placeDrop(helper, player, drops);
            assertPortableOwnership(helper, reservation, restored, drops, "native-wrench");
            System.out.println("INTERFACE_AE_WRENCH_PASS retainedRawAe="
                    + reservation.retainedRawAe() + " items=64");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void refusedPrepaidAeTravelsThroughLevelDestroyAndNativePlacement(GameTestHelper helper) {
        withRejectedRefund(helper, reservation -> {
            var original = reservation.owner();
            helper.assertTrue(helper.getLevel().destroyBlock(original.getBlockPos(), true),
                    "Native Level.destroyBlock did not remove the interface");
            helper.assertTrue(original.isRemoved(), "Level.destroyBlock left the old host live");
            var drops = collectNativeDrops(helper);
            var restored = placeDrop(helper, player(helper), drops);
            assertPortableOwnership(helper, reservation, restored, drops, "level-destroy");
            System.out.println("INTERFACE_AE_LEVEL_DESTROY_PASS retainedRawAe="
                    + reservation.retainedRawAe() + " items=64");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void refusedPrepaidAeTravelsThroughPlayerDestroyAndNativePlacement(GameTestHelper helper) {
        withRejectedRefund(helper, reservation -> {
            var original = reservation.owner();
            var player = player(helper);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
            helper.assertTrue(original.getBlockState().canHarvestBlock(
                            helper.getLevel(), original.getBlockPos(), player),
                    "Native survival player cannot harvest the interface with a netherite pickaxe");
            helper.assertTrue(player.gameMode.destroyBlock(original.getBlockPos()),
                    "Native ServerPlayerGameMode.destroyBlock did not remove the interface");
            helper.assertTrue(original.isRemoved(), "Player mining left the old host live");
            var drops = collectNativeDrops(helper);
            var restored = placeDrop(helper, player, drops);
            assertPortableOwnership(helper, reservation, restored, drops, "player-destroy");
            System.out.println("INTERFACE_AE_PLAYER_DESTROY_PASS retainedRawAe="
                    + reservation.retainedRawAe() + " items=64");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void discardedLivePreviewsDoNotSpendLaterPlayerDestroyReceipt(GameTestHelper helper) {
        withRejectedRefund(helper, reservation -> {
            var original = reservation.owner();
            var player = player(helper);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
            for (int i = 0; i < 3; i++) {
                var discardedSettings = new CompoundTag();
                original.exportSettings(SettingsFrom.DISMANTLE_ITEM, discardedSettings, player);
                close(helper, discardedSettings.getDouble(TRANSFER_ENERGY), reservation.retainedRawAe(),
                        "A live settings preview omitted the retained raw-AE snapshot");
                var discardedDrops = Block.getDrops(original.getBlockState(), helper.getLevel(),
                        original.getBlockPos(), original, player, player.getMainHandItem());
                assertDropPreview(helper, discardedDrops, reservation.retainedRawAe());
                close(helper, access(original).storedEnergy(), reservation.retainedRawAe(),
                        "Discarded live previews spent the source reservation");
            }
            helper.assertTrue(original.getBlockState().canHarvestBlock(
                            helper.getLevel(), original.getBlockPos(), player),
                    "Native survival player cannot harvest the previewed interface");
            helper.assertTrue(player.gameMode.destroyBlock(original.getBlockPos()),
                    "Native player mining did not remove the previewed interface");
            helper.assertTrue(original.isRemoved(), "Previewed player mining left the old host live");
            var drops = collectNativeDrops(helper);
            var restored = placeDrop(helper, player, drops);
            assertPortableOwnership(helper, reservation, restored, drops, "preview-player-destroy");
            System.out.println("INTERFACE_AE_PREVIEW_PLAYER_DESTROY_PASS retainedRawAe="
                    + reservation.retainedRawAe() + " previews=3 items=64");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void discardedPostRemovalPreviewsDoNotSpendFinalPlayerLootReceipt(GameTestHelper helper) {
        withRejectedRefund(helper, reservation -> {
            var original = reservation.owner();
            var originalState = original.getBlockState();
            var player = player(helper);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            var tool = new ItemStack(Items.NETHERITE_PICKAXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            helper.assertTrue(originalState.canHarvestBlock(
                            helper.getLevel(), original.getBlockPos(), player),
                    "Native survival player cannot harvest the interface before removal");
            // Survival mining removes the host before playerDestroy computes its loot.
            helper.assertTrue(helper.getLevel().removeBlock(original.getBlockPos(), false),
                    "Native removal did not remove the interface before player loot");
            helper.assertTrue(original.isRemoved(), "Native removal left the old host live");
            close(helper, access(original).storedEnergy(), 0,
                    "Removed host retained a spendable reservation before loot export");
            double gridAfterRemoval = rawStored(reservation.energy());
            for (int i = 0; i < 3; i++) {
                var discardedDrops = Block.getDrops(originalState, helper.getLevel(),
                        original.getBlockPos(), original, player, tool);
                assertDropPreview(helper, discardedDrops, reservation.retainedRawAe());
                close(helper, access(original).storedEnergy(), 0,
                        "A post-removal preview recreated source-owned raw AE");
                close(helper, rawStored(reservation.energy()), gridAfterRemoval,
                        "A post-removal preview refunded the same receipt into the old grid");
            }
            originalState.getBlock().playerDestroy(helper.getLevel(), player,
                    original.getBlockPos(), originalState, original, tool);
            var drops = collectNativeDrops(helper);
            var restored = placeDrop(helper, player, drops);
            assertPortableOwnership(helper, reservation, restored, drops, "post-removal-preview");
            System.out.println("INTERFACE_AE_POST_REMOVAL_PREVIEW_PASS retainedRawAe="
                    + reservation.retainedRawAe() + " previews=3 items=64");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void callbackWrenchCannotCopyOrCancelAnActiveAeReservation(GameTestHelper helper) {
        withRejectedRefund(helper, original -> {
            var player = player(helper);
            var wrench = AEItems.CERTUS_QUARTZ_WRENCH.stack();
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            double reserved = access(original).storedEnergy();
            var preview = new CompoundTag();
            original.exportSettings(SettingsFrom.DISMANTLE_ITEM, preview, player);
            helper.assertTrue(!containsKey(preview, TRANSFER_ENERGY),
                    "Active callback exported its still-uncharged raw-AE reservation");
            for (var drop : Block.getDrops(original.getBlockState(), helper.getLevel(),
                    original.getBlockPos(), original, player, wrench)) {
                helper.assertTrue(drop.getTag() == null || !containsKey(drop.getTag(), TRANSFER_ENERGY),
                        "Active callback getDrops copied its still-uncharged reservation");
            }
            var hit = new BlockHitResult(Vec3.atCenterOf(original.getBlockPos()),
                    Direction.UP, original.getBlockPos(), false);
            helper.assertTrue(original.disassembleWithWrench(
                            player, helper.getLevel(), hit, wrench) == InteractionResult.FAIL,
                    "Synchronous callback wrench was not refused while its AE receipt was pending");
            helper.assertTrue(helper.getLevel().getBlockEntity(original.getBlockPos()) == original
                            && !original.isRemoved(),
                    "Refused callback wrench removed or replaced the active host");
            close(helper, access(original).storedEnergy(), reserved,
                    "Refused callback wrench consumed or refunded the pending reservation");
            helper.assertTrue(player.getInventory().countItem(ModBlocks.OVERLOADED_INTERFACE.get().asItem()) == 0,
                    "Refused callback wrench produced an interface item");
        }, reservation -> {
            helper.assertTrue(reservation.paidRawAe() > 0,
                    "The transfer interrupted by callback wrench was not charged");
            assertFundingConserved(helper, reservation);
            System.out.println("INTERFACE_AE_CALLBACK_WRENCH_PASS retainedRawAe="
                    + reservation.retainedRawAe() + " paidRawAe=" + reservation.paidRawAe() + " items=64");
            helper.succeed();
        });
    }

    private static void withRejectedRefund(GameTestHelper helper, Consumer<Reservation> action) {
        withRejectedRefund(helper, original -> {}, action);
    }

    private static void withRejectedRefund(GameTestHelper helper,
                                           Consumer<OverloadedInterfaceBlockEntity> duringExtraction,
                                           Consumer<Reservation> action) {
        helper.setBlock(POS.below(), Blocks.STONE);
        helper.setBlock(POS.west(), AEBlocks.DENSE_ENERGY_CELL.block());
        var cell = (EnergyCellBlockEntity) helper.getBlockEntity(POS.west());
        cell.injectAEPower(1_000_000, Actionable.MODULATE);
        helper.setBlock(POS, ModBlocks.OVERLOADED_INTERFACE.get());
        var original = owner(helper);
        original.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
        original.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.OFF);
        helper.setBlock(BARREL, Blocks.BARREL);
        var barrel = (BarrelBlockEntity) helper.getBlockEntity(BARREL);
        barrel.setItem(0, new ItemStack(Items.STONE, 64));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(original.getMainNode().isActive()
                            && original.getMainNode().getGrid().getEnergyService().isNetworkPowered(),
                    "Real interface/energy-cell grid did not become active");
            var grid = original.getMainNode().getGrid();
            var energy = (EnergyService) grid.getEnergyService();
            fillGrid(energy);
            var custodian = access(original);
            close(helper, custodian.storedEnergy(), 0, "Fixture already owned prepaid raw AE");
            double initial = rawStored(energy);
            double multiplier = PowerMultiplier.CONFIG.multiplier;
            helper.assertTrue(Double.isFinite(multiplier) && multiplier > 0,
                    "Invalid configured AE multiplier");
            double[] added = {0};
            int[] callbacks = {0};
            var handler = new InvWrapper(barrel) {
                @Override
                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    var taken = super.extractItem(slot, amount, simulate);
                    if (!simulate && !taken.isEmpty()) {
                        // The actual transfer callback fills storage after AE prepayment and
                        // before endBatch, forcing the native injectPower refund to be rejected.
                        callbacks[0]++;
                        helper.assertTrue(custodian.storedEnergy() > PowerCostUtil.cost(STONE, 64) * multiplier,
                                "Production import did not create an unused AE reservation");
                        added[0] += fillGrid(energy);
                        duringExtraction.accept(original);
                    }
                    return taken;
                }
            };
            custodian.beginBatch(grid);
            long moved;
            try {
                moved = importSlot(original, handler, custodian);
            } finally {
                helper.assertTrue(custodian.endBatch(),
                        "Full-grid refund did not leave a changed retained-AE balance");
            }
            double spent = PowerCostUtil.cost(STONE, moved) * multiplier;
            double retained = custodian.storedEnergy();
            helper.assertTrue(moved == 64 && callbacks[0] == 1 && barrel.isEmpty()
                            && original.benchmarkBufferedImportAmount() == 64,
                    "Native interface import failed to move exactly 64 physical stone");
            helper.assertTrue(retained > 0 && added[0] > spent,
                    "Fixture did not exercise a rejected prepaid-AE refund");
            close(helper, energy.injectPower(retained, Actionable.SIMULATE), retained,
                    "The native grid still accepts the supposedly rejected refund");
            var reservation = new Reservation(original, energy, initial, added[0], spent, retained);
            assertFundingConserved(helper, reservation);
            original.saveChanges();
            System.out.println("INTERFACE_AE_RETENTION_SETUP_PASS retainedRawAe=" + retained
                    + " paidRawAe=" + spent + " externalRawAe=" + added[0] + " items=64");
            action.accept(reservation);
        });
    }

    private static void assertFundingConserved(GameTestHelper helper, Reservation reservation) {
        close(helper, reservation.initialRawAe() + reservation.externallyAddedRawAe(),
                rawStored(reservation.energy()) + reservation.paidRawAe()
                        + access(reservation.owner()).storedEnergy(),
                "Native reservation/refund path did not conserve raw AE");
    }

    private static void assertPortableOwnership(GameTestHelper helper, Reservation reservation,
                                                OverloadedInterfaceBlockEntity restored,
                                                List<ItemStack> drops, String route) {
        long looseStone = drops.stream().filter(stack -> stack.is(Items.STONE))
                .mapToLong(ItemStack::getCount).sum();
        helper.assertTrue(looseStone + restored.benchmarkBufferedImportAmount() == 64,
                route + " lost or duplicated the paid physical imports");
        double actual = access(restored).storedEnergy();
        System.out.println("INTERFACE_AE_LIFECYCLE_OBSERVED route=" + route
                + " expectedRetainedRawAe=" + reservation.retainedRawAe()
                + " restoredRawAe=" + actual
                + " removedHostRawAe=" + access(reservation.owner()).storedEnergy()
                + " looseStone=" + looseStone
                + " restoredBufferedItems=" + restored.benchmarkBufferedImportAmount());
        // Native AE2 node-buffer shares can change during topology removal. Compare the
        // independently owned reservation itself; never mask it with those grid-buffer changes.
        close(helper, actual, reservation.retainedRawAe(),
                route + " lost retained prepaid raw AE during native dismantle/placement");
        close(helper, access(reservation.owner()).storedEnergy(), 0,
                route + " left a second spendable reservation in the removed host");
        double gridBeforeCleanup = rawStored(reservation.energy());
        access(reservation.owner()).endBatch();
        helper.assertTrue(access(reservation.owner()).maxAffordable(null, STONE, 64) == 0,
                route + " removed host could authorize another offline transfer");
        close(helper, access(reservation.owner()).storedEnergy(), 0,
                route + " restored the already handed-off reservation during later batch cleanup");
        close(helper, rawStored(reservation.energy()), gridBeforeCleanup,
                route + " refunded an already handed-off reservation into the old grid");
        double reservedRawAe = reservation.paidRawAe() + reservation.retainedRawAe();
        close(helper, actual + reservation.paidRawAe(), reservedRawAe,
                route + " did not conserve the prepaid reservation");
        close(helper, restored.saveWithoutMetadata().getDouble(TRANSFER_ENERGY), actual,
                route + " restored balance was not durable in native host NBT");
    }

    private static List<ItemStack> collectNativeDrops(GameTestHelper helper) {
        var drops = new ArrayList<ItemStack>();
        for (var entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(POS)).inflate(2))) {
            drops.add(entity.getItem().copy());
            entity.discard();
        }
        return drops;
    }

    private static void assertDropPreview(GameTestHelper helper, List<ItemStack> drops, double expectedRawAe) {
        long blocks = drops.stream().filter(stack -> stack.is(ModBlocks.OVERLOADED_INTERFACE.get().asItem()))
                .mapToLong(ItemStack::getCount).sum();
        helper.assertTrue(blocks == 1, "Loot preview did not return exactly one interface item");
        double rawAe = drops.stream().filter(stack -> stack.is(ModBlocks.OVERLOADED_INTERFACE.get().asItem()))
                .mapToDouble(stack -> rawAeInTag(stack.getTag())).sum();
        close(helper, rawAe, expectedRawAe, "Loot preview omitted or duplicated the raw-AE receipt");
    }

    private static double rawAeInTag(Tag tag) {
        double amount = 0;
        if (tag instanceof CompoundTag compound) {
            amount += compound.getDouble(TRANSFER_ENERGY);
            for (var key : compound.getAllKeys()) amount += rawAeInTag(compound.get(key));
        } else if (tag instanceof ListTag list) {
            for (var child : list) amount += rawAeInTag(child);
        }
        return amount;
    }

    private static OverloadedInterfaceBlockEntity placeDrop(GameTestHelper helper,
                                                             ServerPlayer player,
                                                             List<ItemStack> drops) {
        long blocks = drops.stream().filter(stack -> stack.is(ModBlocks.OVERLOADED_INTERFACE.get().asItem()))
                .mapToLong(ItemStack::getCount).sum();
        helper.assertTrue(blocks == 1, "Native dismantle did not produce exactly one interface item");
        var blockItem = drops.stream().filter(stack -> stack.is(ModBlocks.OVERLOADED_INTERFACE.get().asItem()))
                .findFirst().orElseThrow().copy();
        var abs = helper.absolutePos(POS);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, blockItem);
        var hit = new BlockHitResult(Vec3.atCenterOf(abs.below()).add(0, 0.5, 0),
                Direction.UP, abs.below(), false);
        var context = new BlockPlaceContext(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, blockItem, hit);
        helper.assertTrue(((BlockItem) blockItem.getItem()).place(context).consumesAction(),
                "Native BlockItem placement failed");
        helper.assertTrue(helper.getLevel().getBlockEntity(abs) instanceof OverloadedInterfaceBlockEntity,
                "Native BlockItem placement did not recreate the interface at the original position");
        return owner(helper);
    }

    private static double fillGrid(EnergyService energy) {
        double offer = energy.getMaxStoredPower() + 1024;
        double before = rawStored(energy);
        double remainder = energy.injectPower(offer, Actionable.MODULATE);
        double accepted = offer - remainder;
        if (Math.abs(rawStored(energy) - before - accepted) > EPSILON) {
            throw new IllegalStateException("Native grid fill returned an inconsistent raw-AE receipt");
        }
        return accepted;
    }

    private static double rawStored(EnergyService energy) {
        // getStoredPower is a periodically refreshed telemetry cache; simulation reads native providers.
        return energy.extractProviderPower(Double.MAX_VALUE, Actionable.SIMULATE);
    }

    private static long importSlot(OverloadedInterfaceBlockEntity owner, IItemHandler handler,
                                   PowerCostUtil.EnergyAccess custodian) {
        try {
            var method = OverloadedInterfaceBlockEntity.class.getDeclaredMethod("importExtractSlotToBuffer",
                    IItemHandler.class, int.class, AEItemKey.class, long.class,
                    PowerCostUtil.EnergyAccess.class, BarrelBlockEntity.class);
            method.setAccessible(true);
            return (long) method.invoke(owner, handler, 0, STONE, 64L, custodian, null);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Native one-slot import failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot select native one-slot import", failure);
        }
    }

    private static PowerCostUtil.EnergyAccess access(OverloadedInterfaceBlockEntity owner) {
        try {
            var field = OverloadedInterfaceBlockEntity.class.getDeclaredField("energyAccess");
            field.setAccessible(true);
            return (PowerCostUtil.EnergyAccess) field.get(owner);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot observe interface raw-AE custodian", failure);
        }
    }

    private static OverloadedInterfaceBlockEntity owner(GameTestHelper helper) {
        return (OverloadedInterfaceBlockEntity) helper.getBlockEntity(POS);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "InterfaceAeQA"));
        player.getInventory().clearContent();
        player.getAbilities().instabuild = false;
        return player;
    }

    private static boolean containsKey(Tag tag, String key) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains(key)) return true;
            for (var child : compound.getAllKeys()) {
                if (containsKey(compound.get(child), key)) return true;
            }
        } else if (tag instanceof ListTag list) {
            for (var child : list) if (containsKey(child, key)) return true;
        }
        return false;
    }

    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Double.isFinite(actual) && Double.isFinite(expected)
                        && Math.abs(actual - expected) <= EPSILON,
                message + ": actual=" + actual + " expected=" + expected);
    }
}
