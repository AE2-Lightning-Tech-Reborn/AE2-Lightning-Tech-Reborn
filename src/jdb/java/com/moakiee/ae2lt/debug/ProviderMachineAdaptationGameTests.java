package com.moakiee.ae2lt.debug;

import java.lang.reflect.Method;
import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.util.SettingsFrom;
import com.hrznstudio.titanium.block.tile.ActiveTile;
import com.hrznstudio.titanium.component.fluid.FluidTankComponent;
import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import com.hrznstudio.titanium.component.sideness.IFacingComponent.FaceMode;
import com.hrznstudio.titanium.util.FacingUtil;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.logic.config.MemoryCardConfigSupport;
import com.moakiee.ae2lt.logic.provider.AllowedOutputFilter;
import com.moakiee.ae2lt.logic.provider.MachineAdapter;
import com.moakiee.ae2lt.logic.provider.MachineAdapterRegistry;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.registry.ModBlocks;

/** Actual IF 3.6.39/Titanium capabilities, through the server provider and adapter paths. */
@GameTestHolder("ae2lt_machine_adaptation")
@PrefixGameTestTemplate(false)
public final class ProviderMachineAdaptationGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final AEFluidKey WATER = AEFluidKey.of(Fluids.WATER);
    private static final AEItemKey STONE = AEItemKey.of(Items.STONE);

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void adjacentUnfilteredFluids(GameTestHelper h) { providerReturn(h, false, false, true); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void adjacentFilteredFluids(GameTestHelper h) { providerReturn(h, false, true, true); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void wirelessUnfilteredFluids(GameTestHelper h) { providerReturn(h, true, false, true); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void wirelessFilteredFluids(GameTestHelper h) { providerReturn(h, true, true, true); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void adjacentUnfilteredItems(GameTestHelper h) { providerReturn(h, false, false, false); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void adjacentFilteredItems(GameTestHelper h) { providerReturn(h, false, true, false); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void wirelessUnfilteredItems(GameTestHelper h) { providerReturn(h, true, false, false); }
    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 400)
    public static void wirelessFilteredItems(GameTestHelper h) { providerReturn(h, true, true, false); }

    private static void providerReturn(GameTestHelper h, boolean wireless, boolean filtered, boolean fluids) {
        var provider = provider(h);
        provider.setFilteredImport(filtered);
        provider.setReturnMode(ReturnMode.AUTO);
        provider.getExposedPatternInventory().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(fluids ? WATER : STONE, fluids ? 1000 : 1))));
        var machinePos = wireless ? POS.offset(4, 0, 0) : POS.south();
        var machine = machine(h, machinePos, fluids ? "dissolution_chamber" : "ore_laser_base");
        if (wireless) {
            provider.setProviderMode(ProviderMode.WIRELESS);
            h.assertTrue(provider.addOrUpdateConnection(h.getLevel().dimension(),
                    h.absolutePos(machinePos), Direction.NORTH), "wireless binding");
        }
        Runnable fill = () -> {
            if (fluids) {
                tank(machine, "input_fluid").setFluidStack(new FluidStack(Fluids.WATER, 1000));
                tank(machine, "output_fluid").setFluidStack(new FluidStack(Fluids.WATER, 2000));
            } else {
                inventory(machine, "lens").setStackInSlot(0, new ItemStack(Items.STONE, 11));
                inventory(machine, "output").setStackInSlot(0, new ItemStack(Items.STONE, 7));
                inventory(machine, "output").setStackInSlot(1, new ItemStack(Items.STONE, 16));
            }
        };
        h.runAfterDelay(40, () -> {
            h.assertTrue(provider.isMachineAdaptationEnabled(), "new provider defaults on");
            h.assertTrue(provider.getMainNode().isActive(), "provider network active");
            fill.run();
            if (fluids) {
                var raw = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                        h.absolutePos(machinePos), Direction.NORTH);
                h.assertTrue(raw.drain(new FluidStack(Fluids.WATER, 3000), FluidAction.SIMULATE).getAmount() == 1000,
                        "raw handler drains the input first: reproduction must be real");
            } else {
                var raw = (com.hrznstudio.titanium.component.inventory.MultiInventoryComponent.MultiInvCapabilityHandler<?>)
                        h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(machinePos), Direction.NORTH);
                boolean extractableLens = false;
                for (int slot = 0; slot < raw.getSlots(); slot++) {
                    if (raw.getFromSlot(slot).getName().equals("lens") && raw.extractItem(slot, 64, true).getCount() == 11) {
                        extractableLens = true;
                    }
                }
                h.assertTrue(extractableLens, "actual sided capability must expose extractable lens contents");
            }
        });
        long product = fluids ? 2000 : 23;
        long input = fluids ? 1000 : 11;
        AEKey key = fluids ? WATER : STONE;
        h.runAfterDelay(130, () -> {
            h.assertTrue(inputAmount(machine, fluids) == input, "same resource input must remain");
            h.assertTrue(stored(provider, key) == product, "all product slots/tanks return");
            provider.setMachineAdaptationEnabled(false);
            h.assertTrue(provider.getReturnMode() == ReturnMode.AUTO, "toggle must not disable AUTO");
        });
        h.runAfterDelay(210, () -> {
            h.assertTrue(inputAmount(machine, fluids) == 0 && stored(provider, key) == product + input,
                    "OFF restores generic extraction, including the previously protected input");
            provider.setMachineAdaptationEnabled(true);
            fill.run();
        });
        h.runAfterDelay(310, () -> {
            h.assertTrue(inputAmount(machine, fluids) == input, "ON restores protection without stale cache");
            h.assertTrue(stored(provider, key) == product * 2 + input, "ON still returns products");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void defaultsPersistenceMemoryCardStreamAndServerMenu(GameTestHelper h) {
        var a = provider(h);
        h.setBlock(POS.north(2), ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
        var b = (OverloadedPatternProviderBlockEntity) h.getBlockEntity(POS.north(2));
        h.assertTrue(a.isMachineAdaptationEnabled() && b.isMachineAdaptationEnabled(), "default on");
        a.setMachineAdaptationEnabled(false);
        var tag = new CompoundTag();
        a.saveAdditional(tag, h.getLevel().registryAccess());
        b.loadTag(tag, h.getLevel().registryAccess());
        h.assertTrue(!b.isMachineAdaptationEnabled(), "false survives save/reload");
        tag.remove("MachineAdaptationEnabled");
        b.loadTag(tag, h.getLevel().registryAccess());
        h.assertTrue(b.isMachineAdaptationEnabled(), "missing field resets to true even on reused instance");
        var card = DataComponentMap.builder();
        a.exportSettings(SettingsFrom.MEMORY_CARD, card, null);
        b.importSettings(SettingsFrom.MEMORY_CARD, card.build(), null);
        h.assertTrue(!b.isMachineAdaptationEnabled(), "copy false");
        a.setMachineAdaptationEnabled(true);
        card = DataComponentMap.builder();
        a.exportSettings(SettingsFrom.MEMORY_CARD, card, null);
        b.importSettings(SettingsFrom.MEMORY_CARD, card.build(), null);
        h.assertTrue(b.isMachineAdaptationEnabled(), "copy true");
        b.setMachineAdaptationEnabled(false);
        var oldCard = DataComponentMap.builder();
        var oldTag = new CompoundTag();
        oldTag.putBoolean("FilteredImport", true);
        MemoryCardConfigSupport.writeCustomTag(oldCard, oldTag);
        b.importSettings(SettingsFrom.MEMORY_CARD, oldCard.build(), null);
        h.assertTrue(!b.isMachineAdaptationEnabled(), "old partial card preserves destination, per existing convention");
        try {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                streamMethod("writeToStream").invoke(a, buffer);
                streamMethod("readFromStream").invoke(b, buffer);
                h.assertTrue(b.isMachineAdaptationEnabled(), "block update stream syncs true");
                buffer.clear();
                a.setMachineAdaptationEnabled(false);
                streamMethod("writeToStream").invoke(a, buffer);
                streamMethod("readFromStream").invoke(b, buffer);
                h.assertTrue(!b.isMachineAdaptationEnabled(), "block update stream syncs false");
            } finally { buffer.release(); }
            var menu = new OverloadedPatternProviderMenu(1, new Inventory(h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE)), a);
            menu.broadcastChanges();
            h.assertTrue(!menu.isMachineAdaptationEnabled(), "server menu publishes disabled state");
            var toggle = OverloadedPatternProviderMenu.class.getDeclaredMethod("toggleMachineAdaptation");
            toggle.setAccessible(true);
            toggle.invoke(menu);
            menu.broadcastChanges();
            h.assertTrue(a.isMachineAdaptationEnabled() && menu.isMachineAdaptationEnabled(), "server action toggles and syncs");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void fluidSideChangesAndSinkRefusal(GameTestHelper h) {
        var machine = machine(h, POS, "dissolution_chamber");
        var input = tank(machine, "input_fluid");
        var output = tank(machine, "output_fluid");
        input.setFluidStack(new FluidStack(Fluids.WATER, 1000));
        output.setFluidStack(new FluidStack(Fluids.WATER, 2000));
        var side = FacingUtil.getFacingRelative(machine.getFacingDirection(), Direction.NORTH);
        var sink = new Sink(0, true);
        extract(h, machine, sink, true);
        h.assertTrue(output.getFluidAmount() == 2000 && sink.total == 0, "network refusal must not extract");
        machine.getMultiTankComponent().handleFacingChange("output_fluid", side, FaceMode.NONE.getIndex());
        sink = new Sink(Long.MAX_VALUE, true);
        extract(h, machine, sink, true);
        h.assertTrue(output.getFluidAmount() == 2000 && sink.total == 0, "disabled output side remains closed");
        machine.getMultiTankComponent().handleFacingChange("output_fluid", side, FaceMode.ENABLED.getIndex());
        extract(h, machine, sink, true);
        h.assertTrue(output.isEmpty() && sink.total == 2000 && input.getFluidAmount() == 1000,
                "live side reopening returns product only, without waiting for wrapper TTL");
        output.setFluidStack(new FluidStack(Fluids.WATER, 750));
        var changingSink = new Sink(Long.MAX_VALUE, false);
        extract(h, machine, changingSink, true);
        h.assertTrue(output.isEmpty() && changingSink.overflow == 750 && input.getFluidAmount() == 1000,
                "DRAIN-only output refuses compensation; remainder reaches overflow, never input");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void itemSideChangesPredicatesAndCompensation(GameTestHelper h) {
        var machine = machine(h, POS, "ore_laser_base");
        var input = inventory(machine, "lens");
        var output = inventory(machine, "output");
        input.setStackInSlot(0, new ItemStack(Items.STONE, 11));
        output.setStackInSlot(0, new ItemStack(Items.STONE, 23));
        var side = FacingUtil.getFacingRelative(machine.getFacingDirection(), Direction.NORTH);
        machine.getMultiInventoryComponent().handleFacingChange("output", side, FaceMode.NONE.getIndex());
        var sink = new Sink(Long.MAX_VALUE, true);
        extract(h, machine, sink, true);
        h.assertTrue(sink.total == 0 && input.getStackInSlot(0).getCount() == 11, "closed item output side");
        machine.getMultiInventoryComponent().handleFacingChange("output", side, FaceMode.ENABLED.getIndex());
        output.setOutputFilter((stack, slot) -> false);
        extract(h, machine, sink, true);
        h.assertTrue(sink.total == 0 && output.getStackInSlot(0).getCount() == 23, "obey output extraction predicate");
        output.setOutputFilter((stack, slot) -> true);
        var rejectingSink = new Sink(Long.MAX_VALUE, false);
        extract(h, machine, rejectingSink, true);
        h.assertTrue(rejectingSink.overflow == 23 && input.getStackInSlot(0).getCount() == 11,
                "output insertion predicate rejects compensation without filling lens slots");
        output.setStackInSlot(0, new ItemStack(Items.STONE, 23));
        output.setInputFilter((stack, slot) -> true);
        var changingSink = new Sink(Long.MAX_VALUE, false);
        extract(h, machine, changingSink, true);
        h.assertTrue(output.getStackInSlot(0).getCount() == 23 && changingSink.overflow == 0,
                "compensation writes only to a permitted output");
        h.assertTrue(input.getStackInSlot(0).getCount() == 11, "compensation leaves same-item input intact");
        extract(h, machine, sink, true);
        h.assertTrue(sink.total == 23, "live item view returns real output");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void latexInputsAndMultipleLegitimateProductTanks(GameTestHelper h) {
        var latex = machine(h, POS, "latex_processing_unit");
        // Both inputs are BOTH, not FILL. Use equal fluids deliberately to detect key-only filtering.
        tank(latex, "water").setFluidStack(new FluidStack(Fluids.WATER, 1000));
        tank(latex, "latex").setFluidStack(new FluidStack(Fluids.WATER, 500));
        var sink = new Sink(Long.MAX_VALUE, true);
        extract(h, latex, sink, true);
        h.assertTrue(sink.total == 0, "two BOTH input tanks protected");
        var slaughter = machine(h, POS.east(2), "mob_slaughter_factory");
        tank(slaughter, "meat").setFluidStack(new FluidStack(Fluids.WATER, 1200));
        tank(slaughter, "pink_slime").setFluidStack(new FluidStack(Fluids.WATER, 800));
        extract(h, slaughter, sink, true);
        h.assertTrue(sink.total == 2000, "both legitimate outputs returned even with identical fluids");
        var rancher = machine(h, POS.south(2), "animal_rancher");
        tank(rancher, "fluid_output").setFluidStack(new FluidStack(Fluids.WATER, 600));
        var changingSink = new Sink(Long.MAX_VALUE, false);
        extract(h, rancher, changingSink, true);
        h.assertTrue(tank(rancher, "fluid_output").getFluidAmount() == 600 && changingSink.overflow == 0,
                "audited BOTH output is eligible and supports permitted compensation");
        extract(h, rancher, sink, true);
        h.assertTrue(sink.total == 2600, "BOTH product is not blanket-blocked");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 160)
    public static void ejectAndNormalInputDeliveryRemainUnchanged(GameTestHelper h) {
        var provider = provider(h);
        var machine = machine(h, POS.south(), "ore_laser_base");
        provider.setReturnMode(ReturnMode.EJECT);
        inventory(machine, "lens").setStackInSlot(0, new ItemStack(Items.STONE, 11));
        inventory(machine, "output").setStackInSlot(0, new ItemStack(Items.STONE, 23));
        var target = appeng.helpers.patternprovider.PatternProviderTarget.get(h.getLevel(), machine.getBlockPos(),
                machine, Direction.NORTH, IActionSource.empty());
        h.assertTrue(target != null && target.insert(STONE, 2, Actionable.MODULATE) == 2,
                "normal input delivery still reaches input inventory while adaptation is enabled");
        h.runAfterDelay(100, () -> {
            h.assertTrue(stored(provider, STONE) == 0 && inventory(machine, "output").getStackInSlot(0).getCount() == 23,
                    "EJECT never actively extracts the machine");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void sharedTargetModesAndFluidOutputFilter(GameTestHelper h) {
        var machine = machine(h, POS, "dissolution_chamber");
        var input = tank(machine, "input_fluid");
        var output = tank(machine, "output_fluid");
        input.setFluidStack(new FluidStack(Fluids.WATER, 1000));
        output.setFluidStack(new FluidStack(Fluids.WATER, 2000));
        var filter = new AllowedOutputFilter();
        filter.allowStrict(AEFluidKey.of(Fluids.LAVA));
        var sink = new Sink(Long.MAX_VALUE, true);
        MachineAdapterRegistry.find(h.getLevel(), machine.getBlockPos()).extractOutputs(h.getLevel(),
                machine.getBlockPos(), Direction.NORTH, filter, IActionSource.empty(), sink, true);
        h.assertTrue(sink.total == 0 && output.getFluidAmount() == 2000, "pattern filter still rejects unmatched products");
        extract(h, machine, sink, true);
        h.assertTrue(sink.total == 2000 && input.getFluidAmount() == 1000, "adapted first view");
        extract(h, machine, sink, false);
        h.assertTrue(sink.total == 3000 && input.isEmpty(), "generic second view in same tick");
        input.setFluidStack(new FluidStack(Fluids.WATER, 1000));
        output.setFluidStack(new FluidStack(Fluids.WATER, 2000));
        extract(h, machine, sink, true);
        h.assertTrue(sink.total == 5000 && input.getFluidAmount() == 1000,
                "two providers with different settings cannot contaminate each other's cache");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty")
    public static void unidentifiedStoneworkStagesAndInputOnlyMachine(GameTestHelper h) {
        var machine = machine(h, POS, "material_stonework_factory");
        var stages = List.of("inventoryGenerator", "inventoryFirst", "inventorySecond", "inventoryThird", "inventoryFour");
        for (var name : stages) inventory(machine, name).setStackInSlot(0, new ItemStack(Items.STONE, 3));
        var sink = new Sink(Long.MAX_VALUE, true);
        extract(h, machine, sink, true);
        h.assertTrue(sink.total == 15, "ambiguous multi-stage machine retains generic item behavior");
        var placer = machine(h, POS.east(2), "block_placer");
        inventory(placer, "input").setStackInSlot(0, new ItemStack(Items.STONE, 5));
        extract(h, placer, sink, true);
        h.assertTrue(sink.total == 15, "input-only machine protected");
        extract(h, placer, sink, false);
        h.assertTrue(sink.total == 20, "input-only protection can be disabled");
        h.succeed();
    }

    @GameTest(templateNamespace = "ae2lt", template = "wireless_io_empty", timeoutTicks = 300)
    public static void fullNetworkAndReturnBufferKeepProductsInMachine(GameTestHelper h) {
        var provider = provider(h);
        provider.setReturnMode(ReturnMode.AUTO);
        var drive = (DriveBlockEntity) h.getBlockEntity(POS.west());
        drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
        drive.getInternalInventory().setItemDirect(1, ItemStack.EMPTY);
        var machine = machine(h, POS.south(), "dissolution_chamber");
        var buffer = ((com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic) provider.getLogic()).getInternalReturnInv();
        h.runAfterDelay(40, () -> {
            var keys = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR && item != Items.STONE)
                    .limit(buffer.size()).toList();
            for (int i = 0; i < buffer.size(); i++) buffer.setStack(i, new GenericStack(AEItemKey.of(keys.get(i)), 1));
            tank(machine, "input_fluid").setFluidStack(new FluidStack(Fluids.WATER, 1000));
            tank(machine, "output_fluid").setFluidStack(new FluidStack(Fluids.WATER, 2000));
            inventory(machine, "output").setStackInSlot(0, new ItemStack(Items.STONE, 23));
        });
        h.runAfterDelay(130, () -> {
            h.assertTrue(provider.getMainNode().isActive(), "powered provider remains active with full destination");
            h.assertTrue(tank(machine, "output_fluid").getFluidAmount() == 2000
                    && inventory(machine, "output").getStackInSlot(0).getCount() == 23,
                    "full network and return buffer must leave products in machine");
            h.assertTrue(stored(provider, WATER) == 0 && stored(provider, STONE) == 0, "destination refused both types");
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
        });
        h.runAfterDelay(240, () -> {
            h.assertTrue(stored(provider, WATER) == 2000 && stored(provider, STONE) == 23, "return resumes after storage becomes available");
            h.assertTrue(tank(machine, "input_fluid").getFluidAmount() == 1000, "resuming preserves input");
            h.succeed();
        });
    }

    private static Method streamMethod(String name) throws ReflectiveOperationException {
        var method = OverloadedPatternProviderBlockEntity.class.getDeclaredMethod(name, RegistryFriendlyByteBuf.class);
        method.setAccessible(true);
        return method;
    }

    private static long inputAmount(ActiveTile<?> tile, boolean fluid) {
        return fluid ? tank(tile, "input_fluid").getFluidAmount() : inventory(tile, "lens").getStackInSlot(0).getCount();
    }

    private static OverloadedPatternProviderBlockEntity provider(GameTestHelper h) {
        h.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.SOUTH));
        h.setBlock(POS.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) h.getBlockEntity(POS.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
        return (OverloadedPatternProviderBlockEntity) h.getBlockEntity(POS);
    }

    private static ActiveTile<?> machine(GameTestHelper h, BlockPos pos, String name) {
        var id = ResourceLocation.fromNamespaceAndPath("industrialforegoing", name);
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "actual IF dependency must be installed: " + id);
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(id));
        var tile = (ActiveTile<?>) h.getBlockEntity(pos);
        if (name.equals("ore_laser_base")) {
            // Lenses are disabled by default, but the machine UI can expose them.
            tile.getMultiInventoryComponent().handleFacingChange("lens",
                    FacingUtil.getFacingRelative(tile.getFacingDirection(), Direction.NORTH),
                    FaceMode.ENABLED.getIndex());
        }
        return tile;
    }

    private static FluidTankComponent<?> tank(ActiveTile<?> tile, String name) {
        return tile.getMultiTankComponent().getTanks().stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    private static InventoryComponent<?> inventory(ActiveTile<?> tile, String name) {
        return tile.getMultiInventoryComponent().getInventoryHandlers().stream().filter(i -> i.getName().equals(name)).findFirst().orElseThrow();
    }

    private static long stored(OverloadedPatternProviderBlockEntity provider, AEKey key) {
        return provider.getMainNode().getGrid().getStorageService().getInventory()
                .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private static void extract(GameTestHelper h, ActiveTile<?> machine, Sink sink, boolean enabled) {
        var adapter = MachineAdapterRegistry.find(h.getLevel(), machine.getBlockPos());
        h.assertTrue(adapter != null, "native adapter available");
        adapter.extractOutputs(h.getLevel(), machine.getBlockPos(), Direction.NORTH,
                AllowedOutputFilter.unrestricted(), IActionSource.empty(), sink, enabled);
    }

    private static final class Sink implements MachineAdapter.OutputSink {
        private final long cap;
        private final boolean accept;
        private long total;
        private long overflow;
        private Sink(long cap, boolean accept) { this.cap = cap; this.accept = accept; }
        public long maxAccept(AEKey key, long available) { return Math.min(cap, available); }
        public long accept(AEKey key, long amount) { if (!accept) return 0; total += amount; return amount; }
        public void acceptOverflow(AEKey key, long amount) { overflow += amount; }
    }
}
