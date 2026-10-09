package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu;
import net.minecraftforge.common.util.FakePlayerFactory;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Disposable addon implementations. Never included in the released mod artifact. */
@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
@GameTestHolder("ae2lt_addon_api")
@PrefixGameTestTemplate(false)
public final class AddonApiGameTests {
    private static final BlockPos POS = new BlockPos(3, 3, 3);

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean("ae2lt.addonApiTests")) return;
        event.enqueueWork(() -> {
            CollectorCrystalApi.register(new ResourceLocation("minecraft:paper"), new CollectorCrystalBehavior() {
                public OutputRange preview(ItemStack candidate, LightningTier tier, OutputRange base) { return new OutputRange(4, 4); }
                public ItemStack onCaptured(Capture context, ItemStack original) {
                    var collector = (LightningCollectorBlockEntity) context.level().getBlockEntity(context.collectorPos());
                    if (collector.captureLightning(false)) throw new AssertionError("callback reentered capture");
                    var result = original.copy();
                    result.setHoverName( Component.literal("captured:" + context.insertedAmount()));
                    return result;
                }
            });
            DeviceWorkbenchApi.register(new ResourceLocation("minecraft:stick"), new Tool());
        });
    }

    private static final class Tool implements WorkbenchDevice {
        public ItemStack core(ItemStack deviceStack) { return ItemStack.EMPTY; }
        public boolean canPlaceCore(ItemStack deviceStack, ItemStack candidate) { return false; }
        public void setCore(ItemStack deviceStack, ItemStack candidate) {}
        public List<ItemStack> modules(ItemStack deviceStack) { return List.of(); }
        public String moduleId(ItemStack candidate) { return "test:none"; }
        public int maxInstallAmount(ItemStack candidate) { return 0; }
        public boolean canInstallOne(ItemStack deviceStack, ItemStack candidate) { return false; }
        public boolean installOne(ItemStack deviceStack, ItemStack candidate) { return false; }
        public ItemStack uninstallOne(ItemStack deviceStack, String moduleId) { return ItemStack.EMPTY; }
        public ItemStack uninstallAll(ItemStack deviceStack, String moduleId) { return ItemStack.EMPTY; }
        public boolean serverTick(ItemStack deviceStack, Context context) {
            if (deviceStack.hasCustomHoverName()) return false;
            deviceStack.setHoverName( Component.literal("charged")); return true;
        }
    }

    private static LightningCollectorBlockEntity collector(GameTestHelper helper, long initial) {
        helper.setBlock(POS, ModBlocks.LIGHTNING_COLLECTOR.get());
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS.north(), AEBlocks.DRIVE.block());
        LightningCollectorBlockEntity collector = (LightningCollectorBlockEntity) helper.getBlockEntity(POS);
        collector.getInventory().setStackInSlot(0, new ItemStack(Items.PAPER));
        var cell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        var storage = StorageCells.getCellInventory(cell, null);
        helper.assertTrue(storage != null, "cell storage registered");
        if (initial > 0) storage.insert(LightningKey.HIGH_VOLTAGE, initial, Actionable.MODULATE, IActionSource.empty());
        storage.persist();
        DriveBlockEntity drive = (DriveBlockEntity) helper.getBlockEntity(POS.north());
        drive.getInternalInventory().setItemDirect(0, cell);
        return collector;
    }

    @GameTest(templateNamespace = "ae2lt_addon_api", template = "empty", timeoutTicks = 100)
    public static void partialCaptureCommitsBeforeCallbackAndReportsActualAmount(GameTestHelper helper) {
        var collector = collector(helper, Long.MAX_VALUE - 1);
        helper.runAtTickTime(25, () -> {
            var events = new AtomicInteger();
            Consumer<LightningCaptureCompletedEvent> listener = event -> {
                if (!event.getCollectorPos().equals(collector.getBlockPos())) return;
                helper.assertTrue(event.getRequestedAmount() == 4 && event.getInsertedAmount() == 1, "actual partial insertion");
                helper.assertTrue(collector.getInstalledCrystal().getHoverName().getString().equals("captured:1"), "callback committed before event");
                events.incrementAndGet();
            };
            MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                helper.assertTrue(collector.captureLightning(false), "capture succeeds");
                helper.assertTrue(!collector.captureLightning(false), "same tick cannot capture twice");
                helper.assertTrue(events.get() == 1, "exactly one completion");
            } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_addon_api", template = "empty", timeoutTicks = 100)
    public static void cancellationAndFullStorageNeverCultivate(GameTestHelper helper) {
        var collector = collector(helper, Long.MAX_VALUE);
        helper.runAtTickTime(25, () -> {
            Consumer<LightningCaptureCompletedEvent> unexpected = event -> {
                if (event.getCollectorPos().equals(collector.getBlockPos())) throw new AssertionError("zero storage completed");
            };
            Consumer<LightningCollectedEvent> cancel = event -> {
                if (event.getCollectorPos().equals(collector.getBlockPos())) event.setCanceled(true);
            };
            MinecraftForge.EVENT_BUS.addListener(unexpected); MinecraftForge.EVENT_BUS.addListener(cancel);
            try { helper.assertTrue(!collector.captureLightning(false), "cancelled capture"); }
            finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
            try {
                helper.assertTrue(!collector.captureLightning(false), "full storage capture");
                helper.assertTrue(!collector.getInstalledCrystal().hasCustomHoverName(), "no cultivation");
            } finally { MinecraftForge.EVENT_BUS.unregister(unexpected); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_addon_api", template = "empty", timeoutTicks = 100)
    public static void replacedCrystalIsNotOverwrittenByCaptureCallback(GameTestHelper helper) {
        var collector = collector(helper, 0);
        helper.runAtTickTime(25, () -> {
            var replacement = new ItemStack(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get());
            Consumer<LightningCollectedEvent> swap = event -> {
                if (event.getCollectorPos().equals(collector.getBlockPos())) collector.getInventory().setStackInSlot(0, replacement);
            };
            MinecraftForge.EVENT_BUS.addListener(swap);
            try {
                helper.assertTrue(collector.captureLightning(false), "capture succeeds");
                helper.assertTrue(collector.getInstalledCrystal().is(replacement.getItem()), "replacement retained");
            } finally { MinecraftForge.EVENT_BUS.unregister(swap); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_addon_api", template = "empty", timeoutTicks = 100)
    public static void workbenchTicksRegisteredOrdinaryItem(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get());
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        OverloadDeviceWorkbenchBlockEntity bench = (OverloadDeviceWorkbenchBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(bench.getDeviceInventory().isItemValid(0, new ItemStack(Items.STICK)), "ordinary addon item admitted");
        bench.getDeviceInventory().setItemDirect(0, new ItemStack(Items.STICK));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(bench.getInstalledDevice().getHoverName().getString().equals("charged"), "native ticker reached addon");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt_addon_api", template = "empty")
    public static void batchReceiptCannotBypassServerThreadCheck(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.MATTER_WARPING_MATRIX_PORT.get());
        MatrixPortBlockEntity port = (MatrixPortBlockEntity) helper.getBlockEntity(POS);
        var request = new TianshuSynthesizer.SynthesisRequest(AEItemKey.of(Items.STICK), List.of(), 1,
                TianshuSynthesizer.CAPABILITY_ID, TianshuSynthesizer.API_VERSION, UUID.randomUUID());
        port.submit(request);
        var result = java.util.concurrent.CompletableFuture.supplyAsync(() -> port.submit(request)).join();
        helper.assertTrue(result.rejectionReason() == TianshuSynthesizer.RejectionReason.NOT_SERVER_THREAD, "cached receipt does not bypass thread check");
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_addon_api", template = "empty", timeoutTicks = 100)
    public static void workbenchShiftClickAcceptsRegisteredOrdinaryItem(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get());
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var bench = (OverloadDeviceWorkbenchBlockEntity) helper.getBlockEntity(POS);
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "AddonWorkbench"));
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.setItem(9, new ItemStack(Items.STICK, 2));
        var menu = new OverloadDeviceWorkbenchMenu(23, inventory, bench);
        try {
            int sourceSlot = -1;
            for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
                var slot = menu.getSlot(slotIndex);
                if (slot.container == inventory && slot.getItem().is(Items.STICK)) {
                    sourceSlot = slotIndex;
                    break;
                }
            }
            helper.assertTrue(sourceSlot >= 0, "player stack has a menu slot");
            helper.assertTrue(!menu.quickMoveStack(player, sourceSlot).isEmpty(), "addon item shift-click accepted");
            helper.assertTrue(bench.getInstalledDevice().is(Items.STICK)
                    && bench.getInstalledDevice().getCount() == 1, "exactly one addon device installed");
            helper.assertTrue(inventory.getItem(9).is(Items.STICK)
                    && inventory.getItem(9).getCount() == 1, "remaining item stays in player inventory");
            helper.assertTrue(menu.quickMoveStack(player, sourceSlot).isEmpty(), "full device slot rejects another item");
            helper.assertTrue(inventory.getItem(9).getCount() == 1, "rejected move does not consume an item");
        } finally {
            menu.removed(player);
        }
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(bench.getInstalledDevice().getHoverName().getString().equals("charged"),
                    "shift-click-installed addon receives server ticks");
            helper.succeed();
        });
    }
}
