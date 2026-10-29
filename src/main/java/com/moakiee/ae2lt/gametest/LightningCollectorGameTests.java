package com.moakiee.ae2lt.gametest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.api.event.LightningCollectedEvent;
import com.moakiee.ae2lt.api.lightning.LightningTier;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class LightningCollectorGameTests {
    private static final BlockPos COLLECTOR_POS = new BlockPos(3, 3, 3);
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();

    private LightningCollectorGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void capturesDiagonalAndVerticalBoundaryButRejectsOutsideCube(GameTestHelper helper) {
        var collector = placeCollector(helper, COLLECTOR_POS, 0);
        helper.runAtTickTime(20, () -> {
            strike(helper, COLLECTOR_POS.above().offset(1, 1, 1));
            helper.assertTrue(stored(collector) == collector.getPreview(LightningKey.Tier.HIGH_VOLTAGE).min(),
                    "Diagonal strike must reach the collector");
        });
        helper.runAtTickTime(22, () -> {
            strike(helper, COLLECTOR_POS.above(2));
            long expected = 2L * collector.getPreview(LightningKey.Tier.HIGH_VOLTAGE).min();
            helper.assertTrue(stored(collector) == expected,
                    "Strike one block above the rod must reach the collector");
        });
        helper.runAtTickTime(24, () -> {
            strike(helper, COLLECTOR_POS.above(3));
            long expected = 2L * collector.getPreview(LightningKey.Tier.HIGH_VOLTAGE).min();
            helper.assertTrue(stored(collector) == expected,
                    "Strike two blocks above the rod must remain outside the collection cube");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void overlappingCollectorsCaptureOnlyOnce(GameTestHelper helper) {
        var first = placeCollector(helper, COLLECTOR_POS, 0);
        var second = placeCollector(helper, COLLECTOR_POS.east(2), 0);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(first.getMainNode().getGrid() != second.getMainNode().getGrid(),
                    "Collectors must have separate networks");
            strike(helper, COLLECTOR_POS.above().east());
            helper.assertTrue(stored(first) + stored(second) == first.getPreview(LightningKey.Tier.HIGH_VOLTAGE).min(),
                    "One strike must charge only one collector");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void nearestCollectorReceivesTheStrike(GameTestHelper helper) {
        var distant = placeCollector(helper, COLLECTOR_POS, 0);
        var nearest = placeCollector(helper, COLLECTOR_POS.offset(1, 1, 1), 0);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(distant.getMainNode().getGrid() != nearest.getMainNode().getGrid(),
                    "Collectors must have separate networks");
            strike(helper, COLLECTOR_POS.offset(1, 2, 1));
            helper.assertTrue(stored(distant) == 0, "A farther collector must not take the strike");
            helper.assertTrue(stored(nearest) == nearest.getPreview(LightningKey.Tier.HIGH_VOLTAGE).min(),
                    "The collector directly beneath the strike must receive it");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void fullCollectorDoesNotBlockAnother(GameTestHelper helper) {
        var full = placeCollector(helper, COLLECTOR_POS, Long.MAX_VALUE);
        var available = placeCollector(helper, COLLECTOR_POS.east(2), 0);
        helper.runAtTickTime(20, () -> {
            strike(helper, COLLECTOR_POS.above().east());
            helper.assertTrue(stored(full) == Long.MAX_VALUE, "Full collector must retain its contents");
            helper.assertTrue(stored(available) == available.getPreview(LightningKey.Tier.HIGH_VOLTAGE).min(),
                    "Failed capture must fall through to another collector");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void partialInsertionConsumesTheStrike(GameTestHelper helper) {
        var nearlyFull = placeCollector(helper, COLLECTOR_POS, Long.MAX_VALUE - 1);
        var available = placeCollector(helper, COLLECTOR_POS.east(2), 0);
        helper.runAtTickTime(20, () -> {
            strike(helper, COLLECTOR_POS.above().east());
            helper.assertTrue(stored(nearlyFull) == Long.MAX_VALUE, "Partial capture must insert one unit");
            helper.assertTrue(stored(available) == 0, "Partly captured strike must not charge another collector");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void cancelledCollectorFallsThroughToModifiedCapture(GameTestHelper helper) {
        var cancelled = placeCollector(helper, COLLECTOR_POS, 0);
        var available = placeCollector(helper, COLLECTOR_POS.offset(1, 1, 1), 0);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(cancelled.getMainNode().getGrid() != available.getMainNode().getGrid(),
                    "Collectors must have separate networks");
            helper.assertTrue(available.getMainNode().isActive(), "Fallback collector must be active");
            var attempts = new AtomicInteger();
            Consumer<LightningCollectedEvent> listener = event -> {
                if (event.getLevel() != helper.getLevel()
                        || (!event.getCollectorPos().equals(cancelled.getBlockPos())
                        && !event.getCollectorPos().equals(available.getBlockPos()))) return;
                helper.assertTrue(event.getTier() == LightningTier.HIGH_VOLTAGE && !event.isNaturalWeather(),
                        "Artificial lightning must report its HV tier to listeners");
                attempts.incrementAndGet();
                if (event.getCollectorPos().equals(cancelled.getBlockPos())) event.setCanceled(true);
                else event.setAmount(5);
            };
            MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                strike(helper, COLLECTOR_POS.above());
                helper.assertTrue(attempts.get() == 2, "Cancellation must allow one other capture attempt");
                helper.assertTrue(stored(cancelled) == 0, "Cancelled collector must store nothing");
                long received = stored(available);
                helper.assertTrue(received == 5,
                        "The next collector must store the modified amount, got " + received);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void reentrantAndReloadedBoltCannotCaptureTwice(GameTestHelper helper) {
        var collector = placeCollector(helper, COLLECTOR_POS, 0);
        helper.runAtTickTime(20, () -> {
            var lightning = bolt(helper, COLLECTOR_POS.above());
            var calls = new AtomicInteger();
            Consumer<LightningCollectedEvent> listener = event -> {
                if (event.getLevel() == helper.getLevel()
                        && event.getCollectorPos().equals(collector.getBlockPos())
                        && calls.incrementAndGet() == 1) {
                    NaturalLightningTransformationHandler.handleLightningTick(lightning);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                NaturalLightningTransformationHandler.handleLightningTick(lightning);
                NaturalLightningTransformationHandler.handleLightningTick(lightning);
                var saved = new CompoundTag();
                lightning.saveWithoutId(saved);
                var restored = bolt(helper, COLLECTOR_POS.above());
                restored.load(saved);
                NaturalLightningTransformationHandler.handleLightningTick(restored);
                helper.assertTrue(calls.get() == 1, "A bolt must post only one capture event after reentry and reload");
                helper.assertTrue(stored(collector) == collector.getPreview(LightningKey.Tier.HIGH_VOLTAGE).min(),
                        "The same bolt must charge the collector only once");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
            }
        });
        helper.runAtTickTime(22, () -> {
            strike(helper, COLLECTOR_POS.above());
            helper.assertTrue(stored(collector) == 2L * collector.getPreview(LightningKey.Tier.HIGH_VOLTAGE).min(),
                    "An independent bolt at the same position must still charge the collector");
            helper.succeed();
        });
    }

    private static LightningCollectorBlockEntity placeCollector(GameTestHelper helper, BlockPos pos, long initialAmount) {
        helper.setBlock(pos, ModBlocks.LIGHTNING_COLLECTOR.get());
        helper.setBlock(pos.above(), Blocks.LIGHTNING_ROD);
        helper.setBlock(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos.north(), AEBlocks.DRIVE.block());
        LightningCollectorBlockEntity collector = (LightningCollectorBlockEntity) helper.getBlockEntity(pos);
        collector.getInventory().setStackInSlot(0, new ItemStack(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get()));
        var cell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        var storage = StorageCells.getCellInventory(cell, null);
        helper.assertTrue(storage != null, "Lightning cell must be registered");
        helper.assertTrue(storage.insert(LightningKey.HIGH_VOLTAGE, initialAmount, Actionable.MODULATE,
                ACTION_SOURCE) == initialAmount, "Could not initialize lightning cell");
        storage.persist();
        DriveBlockEntity drive = (DriveBlockEntity) helper.getBlockEntity(pos.north());
        drive.getInternalInventory().setItemDirect(0, cell);
        return collector;
    }

    private static long stored(LightningCollectorBlockEntity collector) {
        return collector.getMainNode().getGrid().getStorageService().getInventory().extract(
                LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE);
    }

    private static void strike(GameTestHelper helper, BlockPos relativePos) {
        NaturalLightningTransformationHandler.handleLightningTick(bolt(helper, relativePos));
    }

    private static LightningBolt bolt(GameTestHelper helper, BlockPos relativePos) {
        var bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        helper.assertTrue(bolt != null, "Lightning entity must be registered");
        bolt.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(relativePos)));
        return bolt;
    }
}
