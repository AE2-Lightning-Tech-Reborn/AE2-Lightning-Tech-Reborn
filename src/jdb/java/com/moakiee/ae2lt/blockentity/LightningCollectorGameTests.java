package com.moakiee.ae2lt.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.api.event.LightningCollectedEvent;
import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real collectors, rods, AE grids and bolt events in disposable worlds; not shipped. */
@GameTestHolder("ae2lt_collector")
@PrefixGameTestTemplate(false)
public final class LightningCollectorGameTests {
    private static final BlockPos POS = new BlockPos(3, 3, 3);
    private static final BlockPos DIAGONAL = POS.offset(1, 1, 1);
    private static final IActionSource ACTION = IActionSource.empty();
    private static final long YIELD = 16;

    private static LightningCollectorBlockEntity collector(GameTestHelper h, BlockPos pos, long initialHv) {
        h.setBlock(pos, ModBlocks.LIGHTNING_COLLECTOR.get());
        h.setBlock(pos.above(), Blocks.LIGHTNING_ROD);
        h.setBlock(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(pos.north(), AEBlocks.DRIVE.block());
        LightningCollectorBlockEntity collector = h.getBlockEntity(pos);
        collector.getInventory().setStackInSlot(0, new ItemStack(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get()));
        var cell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        var storage = StorageCells.getCellInventory(cell, null);
        h.assertTrue(storage != null, "Lightning cell must be registered");
        h.assertTrue(storage.insert(LightningKey.HIGH_VOLTAGE, initialHv, Actionable.MODULATE, ACTION) == initialHv,
                "Could not initialize lightning storage");
        storage.persist();
        DriveBlockEntity drive = h.getBlockEntity(pos.north());
        drive.getInternalInventory().setItemDirect(0, cell);
        return collector;
    }

    private static long stored(LightningCollectorBlockEntity collector, boolean natural) {
        return collector.getMainNode().getGrid().getStorageService().getInventory().extract(
                natural ? LightningKey.EXTREME_HIGH_VOLTAGE : LightningKey.HIGH_VOLTAGE,
                Long.MAX_VALUE, Actionable.SIMULATE, ACTION);
    }

    private static LightningBolt bolt(GameTestHelper h, BlockPos pos, boolean natural) {
        var bolt = EntityType.LIGHTNING_BOLT.create(h.getLevel());
        h.assertTrue(bolt != null, "Lightning entity must be registered");
        bolt.moveTo(Vec3.atBottomCenterOf(h.absolutePos(pos)));
        bolt.setVisualOnly(true);
        bolt.getPersistentData().putBoolean(NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG,
                natural);
        return bolt;
    }

    private static void dispatch(LightningBolt bolt) {
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(bolt));
    }

    private static void allCells(GameTestHelper h, boolean natural) {
        var collector = collector(h, POS, 0);
        int index = 0;
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    var strike = POS.above().offset(x, y, z);
                    long expected = ++index * YIELD;
                    h.runAtTickTime(20 + index, () -> {
                        dispatch(bolt(h, strike, natural));
                        h.assertTrue(stored(collector, natural) == expected, "Missing cube cell " + strike);
                        h.assertTrue(stored(collector, !natural) == 0, "Strike produced the wrong tier");
                    });
                }
            }
        }
        h.runAtTickTime(50, h::succeed);
    }

    @GameTest(template = "empty")
    public static void artificialLightningAcceptsAll27Cells(GameTestHelper h) {
        allCells(h, false);
    }

    @GameTest(template = "empty")
    public static void naturalLightningAcceptsAll27Cells(GameTestHelper h) {
        allCells(h, true);
    }

    @GameTest(template = "empty")
    public static void bothTiersRejectOutsideEveryFace(GameTestHelper h) {
        var collector = collector(h, POS, 0);
        h.runAtTickTime(20, () -> {
            for (boolean natural : new boolean[] {false, true}) {
                for (var direction : Direction.values()) {
                    dispatch(bolt(h, POS.above().relative(direction, 2), natural));
                }
            }
            h.assertTrue(stored(collector, false) == 0 && stored(collector, true) == 0,
                    "A bolt beyond the 3x3x3 cube was captured");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void missingTopRodPreventsCapture(GameTestHelper h) {
        var collector = collector(h, POS, 0);
        h.setBlock(POS.above(), Blocks.AIR);
        // A rod beside the collector must not qualify as its top rod.
        h.setBlock(POS.above().east(), Blocks.LIGHTNING_ROD);
        h.runAtTickTime(20, () -> {
            dispatch(bolt(h, POS.above(), false));
            dispatch(bolt(h, POS.above(), true));
            h.assertTrue(stored(collector, false) == 0 && stored(collector, true) == 0,
                    "Collector without a top rod captured lightning");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void overlappingCollectorsCaptureOncePerBoltForBothTiers(GameTestHelper h) {
        var first = collector(h, POS, 0);
        var second = collector(h, POS.east(2), 0);
        h.runAtTickTime(20, () -> {
            h.assertTrue(first.getMainNode().getGrid() != second.getMainNode().getGrid(),
                    "Overlap fixture must use independent storage networks");
            dispatch(bolt(h, POS.above().east(), false));
            h.assertTrue(stored(first, false) + stored(second, false) == YIELD,
                    "Artificial bolt was collected more than once");
        });
        h.runAtTickTime(22, () -> {
            dispatch(bolt(h, POS.above().east(), true));
            h.assertTrue(stored(first, true) + stored(second, true) == YIELD,
                    "Natural bolt was collected more than once");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void nearestCollectorWins(GameTestHelper h) {
        var first = collector(h, POS, 0);
        var second = collector(h, DIAGONAL, 0);
        h.runAtTickTime(20, () -> {
            h.assertTrue(first.getMainNode().getGrid() != second.getMainNode().getGrid(),
                    "Distance fixture must use independent storage networks");
            dispatch(bolt(h, POS.above(), false));
            h.assertTrue(stored(first, false) == YIELD && stored(second, false) == 0,
                    "Nearest collector must receive the whole strike");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void fullNearestCollectorFallsThrough(GameTestHelper h) {
        var first = collector(h, POS, Long.MAX_VALUE);
        var second = collector(h, DIAGONAL, 0);
        h.runAtTickTime(20, () -> {
            dispatch(bolt(h, POS.above(), false));
            h.assertTrue(stored(first, false) == Long.MAX_VALUE && stored(second, false) == YIELD,
                    "A full collector must not block another eligible collector");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void partialInsertionStillConsumesTheBolt(GameTestHelper h) {
        var first = collector(h, POS, Long.MAX_VALUE - 1);
        var second = collector(h, DIAGONAL, 0);
        var bolt = bolt(h, POS.above(), false);
        h.runAtTickTime(20, () -> {
            dispatch(bolt);
            h.assertTrue(stored(first, false) == Long.MAX_VALUE && stored(second, false) == 0,
                    "Partial insertion must not also feed another collector");
        });
        h.runAtTickTime(22, () -> {
            dispatch(bolt);
            h.assertTrue(stored(second, false) == 0, "Later ticks recaptured a partly stored bolt");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void reentryAndReloadCannotRecaptureTheSameBolt(GameTestHelper h) {
        var first = collector(h, POS, 0);
        var second = collector(h, POS.east(2), 0);
        var bolt = bolt(h, POS.above().east(), false);
        var saved = new CompoundTag();
        h.runAtTickTime(20, () -> {
            var calls = new AtomicInteger();
            Consumer<LightningCollectedEvent> listener = event -> {
                if (!event.getCollectorPos().equals(first.getBlockPos())
                        && !event.getCollectorPos().equals(second.getBlockPos())) return;
                // Bound recursion so a broken guard fails an assertion instead of overflowing.
                if (calls.incrementAndGet() == 1) dispatch(bolt);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                dispatch(bolt);
                dispatch(bolt);
                h.assertTrue(calls.get() == 1, "Re-entry or a repeated tick dispatched capture twice");
                bolt.saveWithoutId(saved);
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
        });
        h.runAtTickTime(23, () -> {
            dispatch(bolt);
            var restored = bolt(h, POS.above().east(), false);
            restored.load(saved);
            dispatch(restored);
            h.assertTrue(stored(first, false) + stored(second, false) == YIELD,
                    "Same bolt was recaptured after another tick or NBT reload");
            // Deduplication belongs to the bolt, not the position: a new strike still works.
            dispatch(bolt(h, POS.above().east(), false));
            h.assertTrue(stored(first, false) + stored(second, false) == 2 * YIELD,
                    "An independent bolt at the same position was incorrectly suppressed");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void liveEntityTicksCannotFeedOverlappingCollectorsTwice(GameTestHelper h) {
        var first = collector(h, POS, 0);
        var second = collector(h, POS.east(2), 0);
        h.runAtTickTime(20, () -> h.getLevel().addFreshEntity(bolt(h, POS.above().east(), false)));
        h.runAtTickTime(100, () -> {
            h.assertTrue(stored(first, false) + stored(second, false) == YIELD,
                    "Real lightning tick/flash lifecycle must yield only one capture");
            h.succeed();
        });
    }
}
