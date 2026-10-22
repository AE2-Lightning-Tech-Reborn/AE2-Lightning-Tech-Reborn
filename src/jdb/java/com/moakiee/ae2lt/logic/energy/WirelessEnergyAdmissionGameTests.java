package com.moakiee.ae2lt.logic.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class WirelessEnergyAdmissionGameTests {
    record Fixture(AdvancedWirelessOverloadedControllerBlockEntity controller, ServerPlayer player, int frequency,
                   BlockPos pos, WirelessEnergyTestMachine machine) {
        void queue(GameTestHelper h) { WirelessEnergyAdmissions.get(h.getLevel().getServer()).queue(player, frequency, h.getLevel(), pos, Direction.NORTH); }
        WirelessEnergyAdmissions.View view(GameTestHelper h) { return WirelessEnergyAdmissions.get(h.getLevel().getServer()).inspect(h.getLevel(), pos, player.getUUID()); }
        boolean bound(GameTestHelper h) { return WirelessEnergyEndpoints.get(h.getLevel().getServer()).isBound(h.getLevel(), pos, frequency); }
    }
    private static Fixture fixture(GameTestHelper h) {
        var p = new BlockPos(2, 2, 2);
        h.setBlock(p, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
        h.setBlock(p.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(p.west(), AEBlocks.DRIVE.block());
        ((DriveBlockEntity) h.getBlockEntity(p.west())).getInternalInventory().setItemDirect(0,
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_1k_cell"))));
        h.setBlock(p.south(3), WirelessEnergyTestMachine.BLOCK);
        var machine = (WirelessEnergyTestMachine) h.getBlockEntity(p.south(3));
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ProbeOwner"));
        var pos = h.absolutePos(p.south(3));
        player.setPos(pos.getX(), pos.getY(), pos.getZ() - 1);
        var controller = (AdvancedWirelessOverloadedControllerBlockEntity) h.getBlockEntity(p);
        var frequency = WirelessFrequencyManager.get().createFrequency(player, "Admission", 0xffffff, FrequencySecurityLevel.PRIVATE, "");
        controller.setFrequency(frequency.getId());
        h.runAfterDelay(30, () -> controller.getMainNode().getGrid().getStorageService().getInventory()
                .insert(AppFluxBridge.FE_KEY, 100000, Actionable.MODULATE, IActionSource.empty()));
        return new Fixture(controller, player, frequency.getId(), pos, machine);
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void realPlacementEventUsesEnergyPreferenceAndInitializedCapability(GameTestHelper h) {
        placementProbe(h, WirelessEnergyTestMachine.BLOCK);
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void mekanismSmelterPlacementReceivesAutomaticProbe(GameTestHelper h) {
        placementProbe(h, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:energized_smelter")));
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void mekanismInfuserPlacementReceivesAutomaticProbe(GameTestHelper h) {
        placementProbe(h, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:metallurgic_infuser")));
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void mekanismFactoryPlacementReceivesAutomaticProbe(GameTestHelper h) {
        placementProbe(h, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_enriching_factory")));
    }
    private static void placementProbe(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        h.assertTrue(block != net.minecraft.world.level.block.Blocks.AIR, "placement test mod is missing");
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var level = h.getLevel();
            level.removeBlock(f.pos, false);
            level.setBlockAndUpdate(f.pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            var player = new net.neoforged.neoforge.common.util.FakePlayer(level, f.player.getGameProfile()) {
                @Override public boolean isFakePlayer() { return false; }
            };
            // GameTestServer has no profile cache; reproduce the username cache
            // populated by a normal login so Mekanism can assign machine ownership.
            try {
                var remember = net.neoforged.neoforge.common.UsernameCache.class.getDeclaredMethod("setUsername", UUID.class, String.class);
                remember.setAccessible(true);
                remember.invoke(null, player.getUUID(), player.getGameProfile().getName());
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            player.setPos(f.pos.getX(), f.pos.getY(), f.pos.getZ() - 1);
            var card = new ItemStack(com.moakiee.ae2lt.registry.ModItems.OVERLOADED_FREQUENCY_CARD.get());
            com.moakiee.ae2lt.item.OverloadedFrequencyCardItem.setData(card,
                    com.moakiee.ae2lt.item.OverloadedFrequencyCardData.empty()
                            .bindFrequency(f.frequency, level.dimension().location().toString(), f.controller.getBlockPos().asLong(), player.getUUID())
                            .withAutoConnect(false).withAutoConnectEnergy(true));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, card);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(block));
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(f.pos.below()).add(0, 0.5, 0),
                    Direction.UP, f.pos.below(), false);
            net.minecraft.world.InteractionResult result;
            try {
                result = net.neoforged.neoforge.common.CommonHooks.onPlaceItemIntoWorld(
                        new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            } finally {
                level.captureBlockSnapshots = false;
                level.capturedBlockSnapshots.clear();
            }
            h.assertTrue(result.consumesAction(), "real item placement failed");
            h.runAfterDelay(10, () -> {
                var view = WirelessEnergyAdmissions.get(level.getServer()).inspect(level, f.pos, player.getUUID());
                h.assertTrue(f.bound(h) && view != null && view.offered() == 100 && view.accepted() == 100,
                        "real placement hook did not inject its probe independently of ME auto-connect");
                h.succeed();
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void inputInitializedAfterPlacementStillReceivesProbe(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            f.machine.inputReady = false;
            h.getLevel().invalidateCapabilities(f.pos);
            f.queue(h);
            f.machine.duringTick = () -> {
                f.machine.inputReady = true;
                h.getLevel().invalidateCapabilities(f.pos);
                f.machine.duringTick = () -> {};
            };
            h.runAfterDelay(10, () -> {
                h.assertTrue(f.bound(h) && f.view(h).offered() == 100 && f.view(h).accepted() == 100,
                        "placement before FE initialization lost the automatic probe");
                h.succeed();
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void capabilityInitializedBeforeFirstInputCanBeRefreshed(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            f.queue(h);
            f.machine.energy = f.machine.makeStorage();
            h.getLevel().invalidateCapabilities(f.pos);
            h.runAfterDelay(10, () -> {
                h.assertTrue(f.bound(h) && f.view(h).accepted() == 100,
                        "pre-input capability initialization canceled the probe");
                h.succeed();
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void temporarySourceShortfallWaitsWithoutSpendingProbe(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var inventory = f.controller.getMainNode().getGrid().getStorageService().getInventory();
            inventory.extract(AppFluxBridge.FE_KEY, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
            f.queue(h);
            h.runAfterDelay(6, () -> {
                h.assertTrue(f.machine.received == 0, "empty source delivered energy");
                inventory.insert(AppFluxBridge.FE_KEY, 100000, Actionable.MODULATE, IActionSource.empty());
                h.runAfterDelay(10, () -> {
                    h.assertTrue(f.bound(h) && f.view(h).offered() == 100 && f.view(h).accepted() == 100,
                            "temporary source shortage permanently consumed the automatic opportunity");
                    h.succeed();
                });
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void actualZeroReceiptStillConsumesTheSingleProbe(GameTestHelper h) {
        var f = fixture(h);
        f.machine.actualLimit = 0;
        h.runAfterDelay(35, () -> {
            f.queue(h);
            h.runAfterDelay(10, () -> {
                h.assertTrue(f.view(h).state() == WirelessEnergyAdmissions.State.NO_INPUT
                                && f.view(h).offered() == 100 && f.machine.receiveCalls == 1 && !f.bound(h),
                        "a real rejected probe was retried");
                f.machine.actualLimit = 100000;
                f.queue(h);
                h.runAfterDelay(8, () -> {
                    h.assertTrue(f.machine.receiveCalls == 1 && f.machine.received == 0 && !f.bound(h),
                            "receiver recovery allowed a second probe");
                    h.succeed();
                });
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void retainedEnergyAdmitsOnlyAfterRealTicker(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            f.machine.afterReceive = () -> {
                var v = f.view(h);
                if (f.machine.receiveCalls == 1) {
                    h.assertTrue(v.state() == WirelessEnergyAdmissions.State.WAITING && v.offered() <= 100 && !f.bound(h), "first input already enabled continuous output");
                }
            };
            f.queue(h);
            h.assertTrue(f.machine.received == 0, "queue performed immediate IO");
            h.runAfterDelay(10, () -> {
                h.assertTrue(f.view(h).state() == WirelessEnergyAdmissions.State.ACCEPTED && f.bound(h), "retaining ticker did not admit");
                h.assertTrue(f.view(h).accepted() == 100 && !f.machine.energy.canExtract(), "input-only observation failed");
                h.succeed();
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void immediateTrashGetsAtMostOneProbe(GameTestHelper h) { rejected(h, false, 0, true); }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void delayedDestroyerQueuedAfterMachineTickIsRejected(GameTestHelper h) { rejected(h, false, 0, false); }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void delayedDestroyerQueuedInsideMachineTickIsRejected(GameTestHelper h) { rejected(h, true, 7, false); }
    private static void rejected(GameTestHelper h, boolean duringTick, int initial, boolean instant) {
        var f = fixture(h); f.machine.drain = !instant; f.machine.instantTrash = instant; f.machine.retainedFloor = initial;
        h.runAfterDelay(35, () -> {
            f.machine.energy.prime(initial);
            if (duringTick) f.machine.duringTick = () -> { f.queue(h); f.machine.duringTick = () -> {}; };
            else f.queue(h);
            h.runAfterDelay(12, () -> {
                h.assertTrue(!f.bound(h) && f.view(h).state() == WirelessEnergyAdmissions.State.NO_INCREMENT, "destroyer passed delayed observation");
                for (int i = 0; i < 10; i++) f.queue(h);
                WirelessEnergyAdmissions.get(h.getLevel().getServer()).queue(f.player, f.frequency, h.getLevel(), f.pos, Direction.SOUTH);
                h.assertTrue(f.machine.received == 100 && f.machine.receiveCalls == 1, "repeated scan exceeded one 100 FE probe");
                h.runAfterDelay(8, () -> { h.assertTrue(f.machine.received == 100, "destroyer received a retry"); h.succeed(); });
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void partialProbeDoesNotTopUpAndManualOverrideNeedsNoProbe(GameTestHelper h) {
        var f = fixture(h); f.machine.drain = true; f.machine.receiveLimit = 30;
        h.runAfterDelay(35, () -> { f.queue(h); h.runAfterDelay(10, () -> {
            h.assertTrue(f.machine.received == 30 && f.machine.receiveCalls == 1 && !f.bound(h), "partial probe retried");
            int received = f.machine.received;
            String result = WirelessEnergyEndpoints.get(h.getLevel().getServer()).setBound(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH, true);
            h.assertTrue(result.equals("connected") && f.machine.received == received, "manual bind probed");
            h.succeed();
        }); });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void saturationIsUnknownWithoutProbe(GameTestHelper h) {
        var f = fixture(h); f.machine.unknown = true;
        h.runAfterDelay(35, () -> { f.queue(h); h.runAfterDelay(10, () -> {
            h.assertTrue(f.machine.received == 0 && f.view(h).state() == WirelessEnergyAdmissions.State.UNVERIFIED && !f.bound(h), "saturated observation was accepted");
            h.succeed();
        }); });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void capabilityReplacementInterruptsObservation(GameTestHelper h) {
        var f = fixture(h);
        f.machine.afterReceive = () -> { f.machine.energy = f.machine.makeStorage(); h.getLevel().invalidateCapabilities(f.pos); };
        h.runAfterDelay(35, () -> { f.queue(h); h.runAfterDelay(10, () -> {
            h.assertTrue(f.machine.received == 100 && !f.bound(h) && f.view(h).state() == WirelessEnergyAdmissions.State.UNVERIFIED, "changed interface reused old observation");
            f.queue(h); h.assertTrue(f.machine.receiveCalls == 1, "capability notification allowed retry"); h.succeed();
        }); });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void removalLifecycleInterruptsObservationWithoutRetry(GameTestHelper h) {
        var f = fixture(h);
        f.machine.afterReceive = () -> f.machine.setRemoved();
        h.runAfterDelay(35, () -> { f.queue(h); h.runAfterDelay(10, () -> {
            f.machine.clearRemoved(); h.getLevel().invalidateCapabilities(f.pos); f.queue(h);
            h.assertTrue(f.machine.received == 100 && !f.bound(h) && f.view(h).state() == WirelessEnergyAdmissions.State.UNVERIFIED, "unload replayed or approved probe"); h.succeed();
        }); });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void permissionLossAfterInputCannotApprove(GameTestHelper h) {
        var f = fixture(h);
        var frequency = WirelessFrequencyManager.get().getFrequency(f.frequency);
        var saved = new CompoundTag(); frequency.writeToTag(saved, com.moakiee.ae2lt.grid.WirelessFrequency.NBT_MEMBERS_ONLY);
        f.machine.afterReceive = () -> frequency.readFromTag(new CompoundTag(), com.moakiee.ae2lt.grid.WirelessFrequency.NBT_MEMBERS_ONLY);
        h.runAfterDelay(35, () -> { f.queue(h); h.runAfterDelay(10, () -> {
            frequency.readFromTag(saved, com.moakiee.ae2lt.grid.WirelessFrequency.NBT_MEMBERS_ONLY);
            h.assertTrue(f.machine.received == 100 && !f.bound(h) && f.view(h).state() == WirelessEnergyAdmissions.State.UNVERIFIED, "revoked actor passed observation"); f.queue(h);
            h.runAfterDelay(5, () -> { h.assertTrue(f.machine.receiveCalls == 1, "permission restoration retried probe"); h.succeed(); });
        }); });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void sameTypeReplacementStartsNewGenerationOnly(GameTestHelper h) {
        var f = fixture(h); f.machine.drain = true;
        h.runAfterDelay(35, () -> { f.queue(h); h.runAfterDelay(10, () -> {
            var old = f.view(h).generation();
            h.getLevel().removeBlockEntity(f.pos);
            var replacement = new WirelessEnergyTestMachine(f.pos, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
            h.getLevel().getChunkAt(f.pos).addAndRegisterBlockEntity(replacement);
            h.getLevel().invalidateCapabilities(f.pos);
            f.queue(h);
            h.assertTrue(!f.view(h).generation().equals(old), "replacement inherited probe generation");
            h.runAfterDelay(10, () -> { h.assertTrue(f.bound(h) && replacement.received >= 100, "new placement could not admit"); h.succeed(); });
        }); });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 190)
    public static void loadedButNonTickingTargetGetsNoProbe(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var far = new BlockPos(28000, 64, 28000);
            h.getLevel().getChunkAt(far);
            h.getLevel().setBlockAndUpdate(far, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
            var target = (WirelessEnergyTestMachine) h.getLevel().getBlockEntity(far);
            f.player.setPos(far.getX(), far.getY(), far.getZ() - 1);
            var admissions = WirelessEnergyAdmissions.get(h.getLevel().getServer());
            admissions.queue(f.player, f.frequency, h.getLevel(), far, Direction.NORTH);
            h.runAfterDelay(110, () -> {
                h.assertTrue(target.ticks == 0 && target.received == 0, "loaded non-ticking target was probed");
                h.assertTrue(admissions.inspect(h.getLevel(), far, f.player.getUUID()).state() == WirelessEnergyAdmissions.State.UNVERIFIED, "non-ticking attempt not closed");
                h.getLevel().removeBlock(far, false); h.succeed();
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void manualBindingCancelsQueuedProbe(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            f.queue(h);
            WirelessEnergyEndpoints.get(h.getLevel().getServer()).setBound(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH, true);
            h.assertTrue(f.view(h).state() == WirelessEnergyAdmissions.State.MANUAL && f.machine.received == 0, "manual override probed");
            h.runAfterDelay(10, () -> { h.assertTrue(f.view(h).offered() == 0 && f.bound(h), "existing manual target entered admission"); h.succeed(); });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void partialProbeRefundRejectionKeepsOwnedBalance(GameTestHelper h) {
        var f = fixture(h); f.machine.drain = true; f.machine.actualLimit = 30;
        h.runAfterDelay(35, () -> {
            var node = f.controller.getMainNode().getNode();
            var escrow = node.getGrid().getService(WirelessEnergyGridService.class).escrow(node);
            f.machine.afterReceive = () -> node.getGrid().getStorageService().getInventory().insert(AppFluxBridge.FE_KEY, Long.MAX_VALUE,
                    Actionable.MODULATE, IActionSource.empty());
            f.queue(h);
            h.runAfterDelay(12, () -> {
                h.assertTrue(f.view(h).offered() == 100 && f.view(h).accepted() == 30 && !f.bound(h), "incorrect partial probe receipt");
                h.assertTrue(escrow.available() == 70 && f.machine.received == 30, "rejected 70 FE refund lost ownership");
                f.queue(h);
                h.runAfterDelay(5, () -> { h.assertTrue(escrow.available() == 70 && f.machine.receiveCalls == 1, "probe balance replayed"); h.succeed(); });
            });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void savedWaitingSnapshotCannotResumeOrProbeAgain(GameTestHelper h) {
        var f = fixture(h);
        f.machine.afterReceive = () -> {
            if (f.machine.receiveCalls != 1) return;
            var server = h.getLevel().getServer();
            var active = WirelessEnergyAdmissions.get(server);
            var saved = active.save(new CompoundTag(), h.getLevel().registryAccess());
            var restored = WirelessEnergyAdmissions.load(saved, h.getLevel().registryAccess());
            var view = restored.inspect(h.getLevel(), f.pos, f.player.getUUID());
            h.assertTrue(view.state() == WirelessEnergyAdmissions.State.UNVERIFIED && view.offered() == 100, "WAITING snapshot became eligible");
            restored.queue(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH);
            restored.tick(server);
            h.assertTrue(f.machine.receiveCalls == 1, "loaded WAITING snapshot replayed probe");
            h.assertTrue(f.controller.getMainNode().getGrid().getService(WirelessEnergyGridService.class)
                    .escrow(f.controller.getMainNode().getNode()).uncertain() == 100, "probe callback lacked pre-owned uncertain balance");
        };
        h.runAfterDelay(35, () -> { f.queue(h); h.runAfterDelay(10, () -> { h.assertTrue(f.bound(h), "original live admission did not complete"); h.succeed(); }); });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void duplicatePlacementAfterManualUnbindDoesNotProbe(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var endpoints = WirelessEnergyEndpoints.get(h.getLevel().getServer());
            endpoints.setBound(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH, true);
            endpoints.setBound(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH, false);
            f.queue(h);
            h.runAfterDelay(10, () -> { h.assertTrue(f.machine.received == 0 && !f.bound(h), "duplicate event probed a manual generation"); h.succeed(); });
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 160)
    public static void fullManualHistoryRemainsClosedAfterCapacityIsFreedAndReloaded(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            // An isolated SavedData table avoids saturating other concurrently running tests.
            var data = WirelessEnergyAdmissions.load(new CompoundTag(), h.getLevel().registryAccess());
            for (int i = 0; i < 8192; i++) data.manualOverride(
                    new WirelessEnergyEndpoints.Location(h.getLevel().dimension(), new BlockPos(60000 + i, 64, 60000)),
                    f.player.getUUID(), f.frequency, Direction.NORTH);
            data.manualOverride(new WirelessEnergyEndpoints.Location(h.getLevel().dimension(), f.pos),
                    f.player.getUUID(), f.frequency, Direction.NORTH);
            var saved = data.save(new CompoundTag(), h.getLevel().registryAccess());
            h.assertTrue(saved.getBoolean("historyIncomplete"), "full history silently lost a manual exclusion");
            // Simulate removal of a different historical generation, then restart the table.
            saved.getList("attempts", net.minecraft.nbt.Tag.TAG_COMPOUND).remove(0);
            var restored = WirelessEnergyAdmissions.load(saved, h.getLevel().registryAccess());
            restored.queue(f.player, f.frequency, h.getLevel(), f.pos, Direction.NORTH);
            restored.tick(h.getLevel().getServer());
            h.assertTrue(restored.inspect(h.getLevel(), f.pos, f.player.getUUID()) == null
                    && f.machine.received == 0, "freed capacity allowed an unrecorded manual generation to probe");
            h.assertTrue(restored.save(new CompoundTag(), h.getLevel().registryAccess()).getBoolean("historyIncomplete"),
                    "overflow protection was not durable");
            h.succeed();
        });
    }
    @GameTest(template = "wireless_io_empty", timeoutTicks = 1600)
    public static void repeatedDiskChunkUnloadReloadPreservesBindingWithoutProbe(GameTestHelper h) {
        var f = fixture(h);
        h.runAfterDelay(35, () -> {
            var level = h.getLevel();
            var remote = new BlockPos(40000, 64, 40000);
            level.setChunkForced(remote.getX() >> 4, remote.getZ() >> 4, true);
            level.setBlockAndUpdate(remote, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
            f.player.setPos(remote.getX(), remote.getY(), remote.getZ() - 1);
            var endpoints = WirelessEnergyEndpoints.get(level.getServer());
            h.assertTrue(endpoints.setBound(f.player, f.frequency, level, remote, Direction.NORTH, true).equals("connected"), "disk fixture bind failed");
            var id = endpoints.inspect(f.frequency, f.player.getUUID()).stream().filter(e -> e.location().pos().equals(remote)).findFirst().orElseThrow().id();
            diskCycle(h, f, remote, id, 3);
        });
    }
    private static void diskCycle(GameTestHelper h, Fixture f, BlockPos pos, UUID id, int cycles) {
        var level = h.getLevel();
        var node = f.controller.getMainNode().getNode();
        node.getGrid().getService(WirelessEnergyGridService.class).escrow(node).credit(200000);
        var original = (WirelessEnergyTestMachine) level.getBlockEntity(pos);
        original.energy.prime(0); original.setChanged();
        int before = original.received;
        h.runAfterDelay(12, () -> {
            h.assertTrue(original.received > before, "disk fixture not supplied");
            level.getChunkSource().save(true);
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
            h.runAfterDelay(400, () -> {
                h.assertTrue(level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null, "chunk manager did not unload fixture");
                level.getChunkAt(pos);
                level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                var restored = (WirelessEnergyTestMachine) level.getBlockEntity(pos);
                h.assertTrue(restored != original && restored.received == original.received, "disk reload did not restore final machine state");
                var endpoints = WirelessEnergyEndpoints.get(level.getServer());
                h.assertTrue(endpoints.inspect(f.frequency, f.player.getUUID()).stream().anyMatch(e -> e.id().equals(id)), "disk reload changed binding generation");
                h.assertTrue(WirelessEnergyAdmissions.get(level.getServer()).inspect(level, pos, f.player.getUUID()).offered() == 0, "reload probed an existing manual endpoint");
                if (cycles > 1) diskCycle(h, f, pos, id, cycles - 1);
                else { level.removeBlock(pos, false); level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false); h.succeed(); }
            });
        });
    }
}
