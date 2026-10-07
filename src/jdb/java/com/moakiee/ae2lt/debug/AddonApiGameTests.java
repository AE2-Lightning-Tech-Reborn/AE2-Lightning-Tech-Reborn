package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.api.device.*;
import com.moakiee.ae2lt.api.event.*;
import com.moakiee.ae2lt.api.lightning.LightningTier;
import com.moakiee.ae2lt.api.lightning.collector.*;
import com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Disposable addon implementations. Never included in the released mod artifact. */
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
@GameTestHolder("ae2lt_addon_api")
@PrefixGameTestTemplate(false)
public final class AddonApiGameTests {
    private static final BlockPos POS = new BlockPos(3, 3, 3);

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean("ae2lt.addonApiTests")) return;
        event.enqueueWork(() -> {
            CollectorCrystalApi.register(ResourceLocation.parse("minecraft:paper"), new CollectorCrystalBehavior() {
                public OutputRange preview(ItemStack s, LightningTier tier, OutputRange base) { return new OutputRange(4, 4); }
                public ItemStack onCaptured(Capture context, ItemStack original) {
                    var collector = (LightningCollectorBlockEntity) context.level().getBlockEntity(context.collectorPos());
                    if (collector.captureLightning(false)) throw new AssertionError("callback reentered capture");
                    var result = original.copy();
                    result.set(DataComponents.CUSTOM_NAME, Component.literal("captured:" + context.insertedAmount()));
                    return result;
                }
            });
            DeviceWorkbenchApi.register(ResourceLocation.parse("minecraft:stick"), new Tool());
        });
    }

    private static final class Tool implements WorkbenchDevice {
        public ItemStack core(ItemStack d) { return ItemStack.EMPTY; }
        public boolean canPlaceCore(ItemStack d, ItemStack s) { return false; }
        public void setCore(ItemStack d, ItemStack s) {}
        public List<ItemStack> modules(ItemStack d) { return List.of(); }
        public String moduleId(ItemStack s) { return "test:none"; }
        public int maxInstallAmount(ItemStack s) { return 0; }
        public boolean canInstallOne(ItemStack d, ItemStack s) { return false; }
        public boolean installOne(ItemStack d, ItemStack s) { return false; }
        public ItemStack uninstallOne(ItemStack d, String id) { return ItemStack.EMPTY; }
        public ItemStack uninstallAll(ItemStack d, String id) { return ItemStack.EMPTY; }
        public boolean serverTick(ItemStack d, Context context) {
            if (d.has(DataComponents.CUSTOM_NAME)) return false;
            d.set(DataComponents.CUSTOM_NAME, Component.literal("charged")); return true;
        }
    }

    private static LightningCollectorBlockEntity collector(GameTestHelper h, long initial) {
        h.setBlock(POS, ModBlocks.LIGHTNING_COLLECTOR.get());
        h.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS.north(), AEBlocks.DRIVE.block());
        LightningCollectorBlockEntity collector = h.getBlockEntity(POS);
        collector.getInventory().setStackInSlot(0, new ItemStack(Items.PAPER));
        var cell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        var storage = StorageCells.getCellInventory(cell, null);
        h.assertTrue(storage != null, "cell storage registered");
        if (initial > 0) storage.insert(LightningKey.HIGH_VOLTAGE, initial, Actionable.MODULATE, IActionSource.empty());
        storage.persist();
        DriveBlockEntity drive = h.getBlockEntity(POS.north());
        drive.getInternalInventory().setItemDirect(0, cell);
        return collector;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void partialCaptureCommitsBeforeCallbackAndReportsActualAmount(GameTestHelper h) {
        var collector = collector(h, Long.MAX_VALUE - 1);
        h.runAtTickTime(25, () -> {
            var events = new AtomicInteger();
            Consumer<LightningCaptureCompletedEvent> listener = e -> {
                if (!e.getCollectorPos().equals(collector.getBlockPos())) return;
                h.assertTrue(e.getRequestedAmount() == 4 && e.getInsertedAmount() == 1, "actual partial insertion");
                h.assertTrue(collector.getInstalledCrystal().getHoverName().getString().equals("captured:1"), "callback committed before event");
                events.incrementAndGet();
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                h.assertTrue(collector.captureLightning(false), "capture succeeds");
                h.assertTrue(!collector.captureLightning(false), "same tick cannot capture twice");
                h.assertTrue(events.get() == 1, "exactly one completion");
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cancellationAndFullStorageNeverCultivate(GameTestHelper h) {
        var collector = collector(h, Long.MAX_VALUE);
        h.runAtTickTime(25, () -> {
            Consumer<LightningCaptureCompletedEvent> unexpected = e -> {
                if (e.getCollectorPos().equals(collector.getBlockPos())) throw new AssertionError("zero storage completed");
            };
            Consumer<LightningCollectedEvent> cancel = e -> {
                if (e.getCollectorPos().equals(collector.getBlockPos())) e.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(unexpected); NeoForge.EVENT_BUS.addListener(cancel);
            try { h.assertTrue(!collector.captureLightning(false), "cancelled capture"); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            try {
                h.assertTrue(!collector.captureLightning(false), "full storage capture");
                h.assertTrue(!collector.getInstalledCrystal().has(DataComponents.CUSTOM_NAME), "no cultivation");
            } finally { NeoForge.EVENT_BUS.unregister(unexpected); }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void replacedCrystalIsNotOverwrittenByCaptureCallback(GameTestHelper h) {
        var collector = collector(h, 0);
        h.runAtTickTime(25, () -> {
            var replacement = new ItemStack(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get());
            Consumer<LightningCollectedEvent> swap = e -> {
                if (e.getCollectorPos().equals(collector.getBlockPos())) collector.getInventory().setStackInSlot(0, replacement);
            };
            NeoForge.EVENT_BUS.addListener(swap);
            try {
                h.assertTrue(collector.captureLightning(false), "capture succeeds");
                h.assertTrue(collector.getInstalledCrystal().is(replacement.getItem()), "replacement retained");
            } finally { NeoForge.EVENT_BUS.unregister(swap); }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void workbenchTicksRegisteredOrdinaryItem(GameTestHelper h) {
        h.setBlock(POS, ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get());
        h.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        OverloadDeviceWorkbenchBlockEntity bench = h.getBlockEntity(POS);
        h.assertTrue(bench.getDeviceInventory().isItemValid(0, new ItemStack(Items.STICK)), "ordinary addon item admitted");
        bench.getDeviceInventory().setItemDirect(0, new ItemStack(Items.STICK));
        h.runAtTickTime(30, () -> {
            h.assertTrue(bench.getInstalledDevice().getHoverName().getString().equals("charged"), "native ticker reached addon");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void batchReceiptCannotBypassServerThreadCheck(GameTestHelper h) {
        h.setBlock(POS, ModBlocks.MATTER_WARPING_MATRIX_PORT.get());
        MatrixPortBlockEntity port = h.getBlockEntity(POS);
        var request = new TianshuSynthesizer.SynthesisRequest(AEItemKey.of(Items.STICK), List.of(), 1,
                TianshuSynthesizer.CAPABILITY_ID, TianshuSynthesizer.API_VERSION, UUID.randomUUID());
        port.submit(request);
        var result = java.util.concurrent.CompletableFuture.supplyAsync(() -> port.submit(request)).join();
        h.assertTrue(result.rejectionReason() == TianshuSynthesizer.RejectionReason.NOT_SERVER_THREAD, "cached receipt does not bypass thread check");
        h.succeed();
    }
}
