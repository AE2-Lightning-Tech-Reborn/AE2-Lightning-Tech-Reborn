package com.moakiee.ae2lt.logic.energy;

import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises real server ticks, real placement events and the installed Construction Sticks implementation. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class FrequencyCardBulkPlacementGameTests {
    private static final String BATCH = "frequency_bulk_placement";
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static int nextArea;

    private record Fixture(ServerLevel level, BlockPos base, BlockPos seed, ServerPlayer player,
                           AdvancedWirelessOverloadedControllerBlockEntity controller, int frequency, ItemStack card,
                           List<BlockPos> placed) {
        WirelessEnergyEndpoints endpoints() { return WirelessEnergyEndpoints.get(level.getServer()); }
        BlockPos target(int i) { return seed.offset(i % 16, i / 256, i / 16 % 16); }
        void preferences(boolean me, boolean fe) {
            OverloadedFrequencyCardItem.setData(card,
                    OverloadedFrequencyCardItem.getData(card).withAutoConnect(me).withAutoConnectEnergy(fe));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playerLookup(ServerLevel level) {
        try {
            var field = PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            return (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }

    private static void ready(GameTestHelper h, Consumer<Fixture> action) {
        var level = h.getLevel();
        var base = new BlockPos(200000 + nextArea++ * 128, 80, 200000);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            level.setChunkForced((base.getX() >> 4) + x, (base.getZ() >> 4) + z, true);
            level.getChunk((base.getX() >> 4) + x, (base.getZ() >> 4) + z);
        }
        var placed = new ArrayList<BlockPos>();
        for (var pos : List.of(base, base.east(), base.west())) placed.add(pos);
        level.setBlockAndUpdate(base, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(base.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(base.west(), AEBlocks.DRIVE.block().defaultBlockState());
        ((DriveBlockEntity) level.getBlockEntity(base.west())).getInternalInventory().setItemDirect(0,
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("appflux:fe_1k_cell"))));
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "BulkPlacementQA")) {
            @Override public boolean isFakePlayer() { return false; }
        };
        playerLookup(level).put(player.getUUID(), player);
        var seed = base.south(6);
        player.setPos(seed.getX() + .5, seed.getY() - 1, seed.getZ() - 2);
        var controller = (AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(base);
        int frequency = WirelessFrequencyManager.get().createFrequency(player, "Bulk placement QA", 0xffffff,
                FrequencySecurityLevel.PRIVATE, "").getId();
        controller.setFrequency(frequency);
        var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
        OverloadedFrequencyCardItem.bindFrequency(card, frequency, level.dimension(), base, player.getUUID());
        player.setItemInHand(InteractionHand.OFF_HAND, card);
        var f = new Fixture(level, base, seed, player, controller, frequency, card, placed);
        FIXTURES.add(f);
        h.runAfterDelay(35, () -> {
            var node = controller.getMainNode().getNode();
            node.getGrid().getService(WirelessEnergyGridService.class).escrow(node).credit(30_000_000);
            action.accept(f);
        });
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (var f : FIXTURES) {
            playerLookup(level).remove(f.player.getUUID());
            for (var pos : f.placed) level.removeBlock(pos, false);
            for (int x = -1; x < 2; x++) for (int z = 0; z < 2; z++) {
                level.setChunkForced((f.base.getX() >> 4) + x, (f.base.getZ() >> 4) + z, false);
            }
        }
        FIXTURES.clear();
    }

    private static void multiPlace(Fixture f, Block block, int count) {
        multiPlace(f, block, count, f::target);
    }
    private static void multiPlace(Fixture f, Block block, int count, java.util.function.IntFunction<BlockPos> positions) {
        var snapshots = new ArrayList<BlockSnapshot>();
        for (int i = 0; i < count; i++) {
            var pos = positions.apply(i); f.placed.add(pos);
            snapshots.add(BlockSnapshot.create(f.level.dimension(), f.level, pos));
            f.level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
        var event = NeoForge.EVENT_BUS.post(new BlockEvent.EntityMultiPlaceEvent(snapshots, Blocks.AIR.defaultBlockState(), f.player));
        if (event.isCanceled()) throw new IllegalStateException("Placement fixture unexpectedly cancelled");
    }

    private static void assertAllFe(GameTestHelper h, Fixture f, int count) {
        for (int i = 0; i < count; i++) {
            var view = WirelessEnergyAdmissions.get(f.level.getServer()).inspect(f.level, f.target(i), f.player.getUUID());
            h.assertTrue(f.endpoints().isBound(f.level, f.target(i), f.frequency), "missed placed target " + i + ": " + view);
            h.assertTrue(view != null && view.state() == WirelessEnergyAdmissions.State.ACCEPTED
                    && view.offered() == 100 && view.accepted() == 100, "placement was probed incorrectly: " + view);
        }
    }

    @GameTest(template = "wireless_io_empty", batch = BATCH, timeoutTicks = 280)
    public static void multiPlaceAdmitsEveryFeMachineBeyondVanillaReach(GameTestHelper h) {
        ready(h, f -> {
            f.preferences(false, true);
            multiPlace(f, WirelessEnergyTestMachine.BLOCK, 96);
            h.assertTrue(!f.player.canInteractWithBlock(f.target(95), 1), "fixture must exceed vanilla reach");
            h.runAfterDelay(50, () -> { assertAllFe(h, f, 96); h.succeed(); });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = BATCH, timeoutTicks = 280)
    public static void realConstructionStickUsesSuccessfulPlacementCallback(GameTestHelper h) {
        ready(h, f -> {
            f.preferences(false, true);
            try {
                var method = Class.forName("mrbysco.constructionstick.basics.StickUtil").getMethod("placeBlock",
                        Level.class, Player.class, BlockState.class, BlockPos.class, BlockItem.class);
                long started = System.nanoTime();
                for (int i = 0; i < 64; i++) {
                    var pos = f.target(i); f.placed.add(pos);
                    h.assertTrue((Boolean) method.invoke(null, f.level, f.player,
                            WirelessEnergyTestMachine.BLOCK.defaultBlockState(), pos, null), "wand rejected placement " + i);
                }
                System.out.println("FREQUENCY_WAND_PROBE placed=64 eventMs=" + (System.nanoTime() - started) / 1e6);
            } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
            h.runAfterDelay(50, () -> { assertAllFe(h, f, 64); h.succeed(); });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = BATCH, timeoutTicks = 280)
    public static void meMultiPlaceSharesOneLogicalClusterAndHonorsIndependentFePreference(GameTestHelper h) {
        ready(h, f -> {
            f.preferences(true, false);
            multiPlace(f, AEBlocks.DRIVE.block(), 32, f.seed::east);
            h.runAfterDelay(70, () -> {
                var links = WirelessLinkRegistry.get(f.level.getServer()).save(new CompoundTag(), f.level.registryAccess())
                        .getList("links", Tag.TAG_COMPOUND);
                int matching = 0;
                for (int i = 0; i < links.size(); i++) if (links.getCompound(i).getString("FrequencyId").equals(Integer.toString(f.frequency))) matching++;
                h.assertTrue(matching == 1, "physical cluster has duplicate logical links: " + matching);
                for (int i = 0; i < 32; i++) {
                    var drive = (DriveBlockEntity) f.level.getBlockEntity(f.seed.east(i));
                    h.assertTrue(drive.getMainNode().isActive(), "ME target lacks a channel " + i);
                }
                h.assertTrue(f.endpoints().inspect(f.frequency, f.player.getUUID()).isEmpty(), "disabled FE preference created endpoints");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = BATCH, timeoutTicks = 280)
    public static void replacedPlacementAndDisabledPreferencesDoNotAdmitNewGeneration(GameTestHelper h) {
        ready(h, f -> {
            f.preferences(false, true);
            multiPlace(f, WirelessEnergyTestMachine.BLOCK, 1);
            var original = f.level.getBlockEntity(f.seed);
            f.level.removeBlock(f.seed, false);
            f.level.setBlockAndUpdate(f.seed, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
            h.assertTrue(f.level.getBlockEntity(f.seed) != original, "replacement fixture did not replace the BE");
            f.preferences(false, false);
            var pos = f.target(1); f.placed.add(pos);
            var snapshot = BlockSnapshot.create(f.level.dimension(), f.level, pos);
            f.level.setBlockAndUpdate(pos, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.AIR.defaultBlockState(), f.player));
            h.runAfterDelay(30, () -> {
                h.assertTrue(f.endpoints().inspect(f.frequency, f.player.getUUID()).isEmpty(), "new generation or disabled preference was admitted");
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = BATCH, timeoutTicks = 280)
    public static void bulkWorkSpansTicksRejectsRepeatRequestsAndNeverCreatesMeLinks(GameTestHelper h) {
        ready(h, f -> {
            multiPlace(f, WirelessEnergyTestMachine.BLOCK, 1024);
            var beforeGrid = f.controller.getMainNode().getGrid();
            int startedTick = f.level.getServer().getTickCount();
            var result = new AtomicReference<WirelessEnergyEndpoints.BulkResult>();
            var queued = f.endpoints().toggleConnected(f.player, f.frequency, f.level, f.seed, Direction.NORTH, result::set);
            h.assertTrue(queued.error().equals("bulk_queued") && result.get() == null, "bulk edits ran inside interaction");
            var repeated = f.endpoints().toggleConnected(f.player, f.frequency, f.level, f.seed, Direction.NORTH, ignored -> {});
            h.assertTrue(repeated.error().equals("bulk_busy"), "repeat input queued a second toggle");
            pollBulk(h, f, result, 0, 0, 0, () -> {
                h.assertTrue(result.get().changed() == 1024 && result.get().skipped() == 0, "bulk lost targets: " + result.get());
                int elapsed = f.level.getServer().getTickCount() - startedTick;
                h.assertTrue(elapsed >= 3 && elapsed <= 40, "1024-target operation exceeded its latency gate: " + elapsed);
                h.assertTrue(f.controller.getMainNode().getGrid() == beforeGrid, "FE bulk rebuilt ME topology");
                for (int i = 0; i < 1024; i++) {
                    h.assertTrue(WirelessEnergyAdmissions.get(f.level.getServer()).inspect(f.level, f.target(i), f.player.getUUID()).offered() == 0,
                            "manual bulk performed automatic probe " + i);
                }
                h.succeed();
            });
        });
    }

    @GameTest(template = "wireless_io_empty", batch = BATCH, timeoutTicks = 280)
    public static void bulkDoesNotBindReplacementCreatedByCapabilityCallback(GameTestHelper h) {
        ready(h, f -> {
            multiPlace(f, WirelessEnergyTestMachine.BLOCK, 8);
            var replacedPos = f.target(1);
            var original = (WirelessEnergyTestMachine) f.level.getBlockEntity(replacedPos);
            original.duringCapability = () -> {
                original.duringCapability = () -> {};
                f.level.removeBlock(replacedPos, false);
                f.level.setBlockAndUpdate(replacedPos, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
            };
            var result = new AtomicReference<WirelessEnergyEndpoints.BulkResult>();
            var queued = f.endpoints().toggleConnected(f.player, f.frequency, f.level, f.seed, Direction.NORTH, result::set);
            h.assertTrue(queued.error().equals("bulk_queued"), "bulk was not queued");
            pollBulk(h, f, result, 0, 0, 0, () -> {
                h.assertTrue(result.get().changed() == 7 && result.get().skippedReasons().equals(Map.of("target_changed", 1)),
                        "replacement generation was admitted: " + result.get());
                h.assertTrue(!f.endpoints().isBound(f.level, replacedPos, f.frequency), "replacement inherited old binding");
                h.succeed();
            });
        });
    }

    private static void pollBulk(GameTestHelper h, Fixture f, AtomicReference<WirelessEnergyEndpoints.BulkResult> result,
                                 int ticks, int maximumEdits, long maximumNanos, Runnable completed) {
        h.runAfterDelay(1, () -> {
            int before = f.endpoints().inspect(f.frequency, f.player.getUUID()).size();
            long started = System.nanoTime();
            f.endpoints().tickBulkConnections(f.level.getServer());
            long nanos = System.nanoTime() - started;
            int edits = f.endpoints().inspect(f.frequency, f.player.getUUID()).size() - before;
            h.assertTrue(edits <= WirelessEnergyBulkConnections.EDITS_PER_TICK, "per-tick edit budget exceeded: " + edits);
            if (result.get() == null) pollBulk(h, f, result, ticks + 1, Math.max(maximumEdits, edits), Math.max(maximumNanos, nanos), completed);
            else {
                System.out.println("FREQUENCY_BULK_PROBE changed=" + result.get().changed() + " ticks=" + (ticks + 1)
                        + " maxEdits=" + Math.max(maximumEdits, edits) + " maxSliceMs=" + Math.max(maximumNanos, nanos) / 1e6);
                completed.run();
            }
        });
    }
}
