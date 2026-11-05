package com.moakiee.ae2lt.blockentity;

import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.logic.interfaces.BufferedInterfaceInput;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import me.ramidzkh.mekae2.MekCapabilities;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Optional JDB profile: real Mekanism tanks, AppMek capabilities and chemical cells. */
@GameTestHolder("ae2lt_interface_input")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"rawtypes", "unchecked"})
public final class OverloadedInterfaceMekanismProbe {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final Direction FACE = Direction.NORTH;

    private enum Form {
        GAS(TransmissionType.GAS), INFUSION(TransmissionType.INFUSION),
        PIGMENT(TransmissionType.PIGMENT), SLURRY(TransmissionType.SLURRY);
        final TransmissionType transmission;
        Form(TransmissionType transmission) { this.transmission = transmission; }
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void normalGasPull(GameTestHelper h) { pullChemical(h, Form.GAS, false); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void wirelessGasPull(GameTestHelper h) { pullChemical(h, Form.GAS, true); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void normalInfusionPull(GameTestHelper h) { pullChemical(h, Form.INFUSION, false); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void wirelessInfusionPull(GameTestHelper h) { pullChemical(h, Form.INFUSION, true); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void normalPigmentPull(GameTestHelper h) { pullChemical(h, Form.PIGMENT, false); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void wirelessPigmentPull(GameTestHelper h) { pullChemical(h, Form.PIGMENT, true); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void normalSlurryPull(GameTestHelper h) { pullChemical(h, Form.SLURRY, false); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void wirelessSlurryPull(GameTestHelper h) { pullChemical(h, Form.SLURRY, true); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void normalFluidPull(GameTestHelper h) { pullFluid(h, false); }
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void wirelessFluidPull(GameTestHelper h) { pullFluid(h, true); }

    private static void pullChemical(GameTestHelper h, Form form, boolean wireless) {
        var owner = fixture(h, true);
        var pos = wireless ? POS.south(3) : POS.south();
        var tank = chemicalTank(h, pos, form, 1_000, DataType.OUTPUT);
        bind(owner, h, pos, wireless);
        var key = key(form);
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            check(handler(tank, form).extractChemical(offer(form, 1_000), Action.SIMULATE).getAmount() == 1_000,
                    "fixture must expose an extractable " + form + " output on the bound face");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO);
        }).thenExecuteAfter(80, () -> {
            check(chemicalAmount(tank, form) == 0, "source was not drained: " + form + "/" + wireless);
            check(stored(owner, key) == 1_000 && owner.benchmarkBufferedImportAmount() == 0,
                    "chemical import ownership mismatch: " + form + "/" + wireless);
            passed(form + " " + (wireless ? "wireless" : "normal") + " active import");
        }).thenSucceed();
    }

    private static void pullFluid(GameTestHelper h, boolean wireless) {
        var owner = fixture(h, true);
        drive(h).getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
        var pos = wireless ? POS.south(3) : POS.south();
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(new ResourceLocation("mekanism", "basic_fluid_tank")));
        var tank = (TileEntityFluidTank) h.getBlockEntity(pos);
        tank.fluidTank.setStack(new FluidStack(Fluids.WATER, 1_000));
        bind(owner, h, pos, wireless);
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            var fluid = tank.getCapability(ForgeCapabilities.FLUID_HANDLER, FACE)
                    .orElseThrow(() -> new GameTestAssertException("missing fluid capability"));
            check(fluid.drain(1_000, IFluidHandler.FluidAction.SIMULATE).getAmount() == 1_000,
                    "Mekanism tank must expose a fluid output on the bound face");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO);
        }).thenExecuteAfter(80, () -> {
            check(tank.fluidTank.isEmpty() && stored(owner, AEFluidKey.of(Fluids.WATER)) == 1_000,
                    "fluid import ownership mismatch: wireless=" + wireless);
            passed("fluid " + (wireless ? "wireless" : "normal") + " active import");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void passiveChemicalSimulationUsesOwnedBuffer(GameTestHelper h) {
        var owner = fixture(h, true);
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            for (var form : Form.values()) {
                var input = handler(owner, form);
                check(input.insertChemical(offer(form, 100), Action.SIMULATE).isEmpty(), "simulation rejected " + form);
                check(passive(owner).amount(key(form)) == 0 && stored(owner, key(form)) == 0,
                        "simulation changed ownership: " + form);
                check(input.insertChemical(offer(form, 100), Action.EXECUTE).isEmpty(), "input rejected " + form);
                check(passive(owner).amount(key(form)) == 100 && stored(owner, key(form)) == 0,
                        "chemical handler bypassed owned staging: " + form);
            }
        }).thenExecuteAfter(30, () -> {
            for (var form : Form.values()) check(stored(owner, key(form)) == 100, "input did not flush " + form);
            check(passive(owner).isEmpty(), "successful chemical flush retained ownership");
            passed("all four passive chemical forms: simulation, owned staging and flush");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void chemicalCapabilitiesRespectFilter(GameTestHelper h) {
        var owner = fixture(h, true);
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            setFilter(owner, key(Form.PIGMENT));
            for (var form : Form.values()) {
                var remainder = handler(owner, form).insertChemical(offer(form, 123), Action.EXECUTE);
                check(remainder.getAmount() == (form == Form.PIGMENT ? 0 : 123),
                        "chemical capability bypassed filter: " + form);
                check(stored(owner, key(form)) == 0, "passive input wrote directly to ME");
            }
            check(passive(owner).amount(key(Form.PIGMENT)) == 123, "allowed chemical was not staged");
            passed("filter across all four AppMek capabilities");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void passiveChemicalFormsSurviveReloadAndRecovery(GameTestHelper h) {
        var owner = fixture(h, false);
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            for (var form : Form.values()) {
                check(handler(owner, form).insertChemical(offer(form, 700 + form.ordinal()), Action.EXECUTE).isEmpty(),
                        "full-network staging rejected " + form);
            }
            var saved = owner.saveWithoutMetadata();
            passive(owner).clear();
            owner.loadTag(saved);
            owner.loadTag(saved);
            for (var form : Form.values()) {
                check(passive(owner).amount(key(form)) == 700 + form.ordinal(), "reload lost/duplicated " + form);
                check(stored(owner, key(form)) == 0, "empty drive accepted chemical");
            }
            installChemicalCell(h);
        }).thenExecuteAfter(60, () -> {
            for (var form : Form.values()) check(stored(owner, key(form)) == 700 + form.ordinal(), "recovery lost " + form);
            check(passive(owner).isEmpty(), "recovered cell did not drain owned chemical input");
            passed("four chemical forms: NBT round trip and storage recovery");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void chemicalFormsShareFiniteBackpressureBudget(GameTestHelper h) {
        var owner = fixture(h, false);
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            long capacity = BufferedInterfaceInput.capacity(key(Form.GAS).getType());
            check(handler(owner, Form.GAS).insertChemical(offer(Form.GAS, capacity), Action.SIMULATE).isEmpty(),
                    "full-capacity simulation rejected");
            check(passive(owner).isEmpty(), "capacity simulation reserved resources");
            check(handler(owner, Form.GAS).insertChemical(offer(Form.GAS, capacity - 7), Action.EXECUTE).isEmpty(),
                    "failed to fill shared budget");
            check(handler(owner, Form.INFUSION).insertChemical(offer(Form.INFUSION, 10), Action.EXECUTE).getAmount() == 3,
                    "wrong partial-insertion remainder across chemical forms");
            check(handler(owner, Form.SLURRY).insertChemical(offer(Form.SLURRY, 1), Action.EXECUTE).getAmount() == 1,
                    "different chemical form bypassed shared capacity");
            check(passive(owner).amount(key(Form.GAS)) == capacity - 7
                    && passive(owner).amount(key(Form.INFUSION)) == 7, "partial admission changed quantities");
            passed("shared chemical backpressure, partial receipt and simulation");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void genericCapabilityInvalidationRestoresFilteredView(GameTestHelper h) {
        var owner = fixture(h, true);
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            var old = owner.getCapability(Capabilities.GENERIC_INTERNAL_INV, FACE);
            check(old.orElseThrow(() -> new GameTestAssertException("missing generic capability"))
                    == owner.getExposedGenericInv(), "generic capability exposed raw storage");
            owner.invalidateCaps();
            check(!old.isPresent(), "invalidated generic capability stayed alive");
            owner.reviveCaps();
            var fresh = owner.getCapability(Capabilities.GENERIC_INTERNAL_INV, FACE);
            check(fresh.isPresent() && fresh != old && fresh.orElseThrow(
                    () -> new GameTestAssertException("missing revived capability")) == owner.getExposedGenericInv(),
                    "revived generic capability lost the admission wrapper");
            check(handler(owner, Form.GAS).insertChemical(offer(Form.GAS, 37), Action.EXECUTE).isEmpty(),
                    "revived AppMek adapter rejected valid input");
            check(passive(owner).amount(key(Form.GAS)) == 37, "revived adapter bypassed staging");
            passed("generic capability invalidation and AppMek adapter revival");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void activeChemicalBufferSurvivesNetworkRejectionAndReload(GameTestHelper h) {
        var owner = fixture(h, false);
        owner.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
        var tanks = new TileEntityChemicalTank[Form.values().length];
        for (var form : Form.values()) {
            var pos = POS.offset(3 + form.ordinal(), 0, 3);
            tanks[form.ordinal()] = chemicalTank(h, pos, form, 900 + form.ordinal(), DataType.OUTPUT);
            bind(owner, h, pos, true);
        }
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() ->
                owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO))
                .thenExecuteAfter(70, () -> {
                    for (var form : Form.values()) {
                        check(chemicalAmount(tanks[form.ordinal()], form) == 0, "source retained " + form);
                        check(active(owner).getOrDefault(key(form), 0L) == 900 + form.ordinal(), "buffer lost " + form);
                    }
                    var saved = owner.saveWithoutMetadata();
                    owner.clearImportBuffer();
                    owner.loadTag(saved);
                    for (var form : Form.values()) {
                        check(active(owner).getOrDefault(key(form), 0L) == 900 + form.ordinal(), "reload lost " + form);
                    }
                    installChemicalCell(h);
                }).thenExecuteAfter(70, () -> {
                    for (var form : Form.values()) check(stored(owner, key(form)) == 900 + form.ordinal(), "recovery lost " + form);
                    check(active(owner).isEmpty(), "active import buffer did not recover");
                    passed("four active chemical forms: full network, NBT round trip and recovery");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void chemicalSideChangesRespectLivePermissions(GameTestHelper h) {
        var owner = fixture(h, true);
        var tank = chemicalTank(h, POS.south(), Form.GAS, 1_000, DataType.INPUT);
        h.startSequence().thenWaitUntil(() -> ready(owner)).thenExecute(() -> {
            check(handler(tank, Form.GAS).extractChemical(offer(Form.GAS, 1_000), Action.SIMULATE).isEmpty(),
                    "input-only side must reject extraction");
            owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.AUTO);
        }).thenExecuteAfter(60, () -> {
            check(chemicalAmount(tank, Form.GAS) == 1_000 && stored(owner, key(Form.GAS)) == 0,
                    "AUTO bypassed Mekanism side permissions");
            configure(tank, Form.GAS, DataType.OUTPUT);
        }).thenExecuteAfter(100, () -> {
            check(chemicalAmount(tank, Form.GAS) == 0 && stored(owner, key(Form.GAS)) == 1_000,
                    "live side change did not refresh the AppMek view");
            passed("live Mekanism sided capability permissions");
        }).thenSucceed();
    }

    private static ChemicalStack<?> offer(Form form, long amount) {
        return switch (form) {
            case GAS -> new GasStack(MekanismAPI.gasRegistry().getValue(new ResourceLocation("mekanism", "oxygen")), amount);
            case INFUSION -> new InfusionStack(MekanismAPI.infuseTypeRegistry().getValue(new ResourceLocation("mekanism", "redstone")), amount);
            case PIGMENT -> MekanismAPI.pigmentRegistry().getValues().stream().map(c -> new PigmentStack(c, amount))
                    .filter(s -> !s.isEmpty()).findFirst().orElseThrow();
            case SLURRY -> MekanismAPI.slurryRegistry().getValues().stream().map(c -> new SlurryStack(c, amount))
                    .filter(s -> !s.isEmpty()).findFirst().orElseThrow();
        };
    }

    private static MekanismKey key(Form form) {
        var result = MekanismKey.of(offer(form, 1));
        if (result == null) throw new GameTestAssertException("Empty test chemical for " + form);
        return result;
    }

    private static IChemicalHandler handler(BlockEntity target, Form form) {
        Capability capability = switch (form) {
            case GAS -> MekCapabilities.GAS_HANDLER_CAPABILITY;
            case INFUSION -> MekCapabilities.INFUSION_HANDLER_CAPABILITY;
            case PIGMENT -> MekCapabilities.PIGMENT_HANDLER_CAPABILITY;
            case SLURRY -> MekCapabilities.SLURRY_HANDLER_CAPABILITY;
        };
        var result = (IChemicalHandler) target.getCapability(capability, FACE).orElse(null);
        if (result == null) throw new GameTestAssertException("Missing real " + form + " capability on " + target);
        return result;
    }

    private static TileEntityChemicalTank chemicalTank(GameTestHelper h, BlockPos pos,
                                                       Form form, long amount, DataType mode) {
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(new ResourceLocation("mekanism", "basic_chemical_tank")));
        var tank = (TileEntityChemicalTank) h.getBlockEntity(pos);
        switch (form) {
            case GAS -> tank.getGasTank().setStack((GasStack) offer(form, amount));
            case INFUSION -> tank.getInfusionTank().setStack((InfusionStack) offer(form, amount));
            case PIGMENT -> tank.getPigmentTank().setStack((PigmentStack) offer(form, amount));
            case SLURRY -> tank.getSlurryTank().setStack((SlurryStack) offer(form, amount));
        }
        configure(tank, form, mode);
        return tank;
    }

    private static void configure(TileEntityChemicalTank tank, Form form, DataType mode) {
        var config = tank.getConfig().getConfig(form.transmission);
        check(config != null, "missing Mekanism side config for " + form);
        var relative = RelativeSide.fromDirections(tank.getDirection(), FACE);
        config.setEjecting(false);
        config.setDataType(mode, relative);
        tank.getConfig().sideChanged(form.transmission, relative);
    }

    private static long chemicalAmount(TileEntityChemicalTank tank, Form form) {
        return switch (form) {
            case GAS -> tank.getGasTank().getStored();
            case INFUSION -> tank.getInfusionTank().getStored();
            case PIGMENT -> tank.getPigmentTank().getStored();
            case SLURRY -> tank.getSlurryTank().getStored();
        };
    }

    private static void bind(OverloadedInterfaceBlockEntity owner, GameTestHelper h, BlockPos pos, boolean wireless) {
        if (!wireless) return;
        owner.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS);
        check(owner.addOrUpdateConnection(new OverloadedInterfaceBlockEntity.WirelessConnection(
                h.getLevel().dimension(), h.absolutePos(pos), FACE)), "wireless tank binding failed");
    }

    private static OverloadedInterfaceBlockEntity fixture(GameTestHelper h, boolean withCell) {
        h.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS, ModBlocks.OVERLOADED_INTERFACE.get());
        h.setBlock(POS.west(), AEBlocks.DRIVE.block());
        if (withCell) installChemicalCell(h);
        var owner = (OverloadedInterfaceBlockEntity) h.getBlockEntity(POS);
        owner.setImportMode(OverloadedInterfaceBlockEntity.ImportMode.OFF);
        owner.setExportMode(OverloadedInterfaceBlockEntity.ExportMode.OFF);
        return owner;
    }

    private static void installChemicalCell(GameTestHelper h) {
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation("appmek", "chemical_storage_cell_1k"));
        check(!new ItemStack(item).isEmpty(), "missing real AppMek chemical storage cell");
        check(drive(h).getInternalInventory().insertItem(0, new ItemStack(item), false).isEmpty(),
                "chemical storage cell was rejected by the real drive");
    }

    private static DriveBlockEntity drive(GameTestHelper h) { return (DriveBlockEntity) h.getBlockEntity(POS.west()); }
    private static void ready(OverloadedInterfaceBlockEntity owner) { check(owner.getMainNode().isActive(), "waiting for powered grid"); }
    private static long stored(OverloadedInterfaceBlockEntity owner, AEKey key) {
        return owner.getMainNode().getGrid().getStorageService().getInventory()
                .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }
    private static void setFilter(OverloadedInterfaceBlockEntity owner, AEKey key) {
        var filter = new ItemStack(ModItems.OVERLOADED_FILTER_COMPONENT.get());
        ((ICellWorkbenchItem) filter.getItem()).getConfigInventory(filter).setStack(0, new GenericStack(key, 1));
        owner.getFilterInv().setItemDirect(0, filter);
        owner.rebuildFilter();
    }
    private static BufferedInterfaceInput passive(OverloadedInterfaceBlockEntity owner) { return field(owner, "passiveInput"); }
    private static Map<AEKey, Long> active(OverloadedInterfaceBlockEntity owner) { return field(owner, "importBuffer"); }
    private static <T> T field(OverloadedInterfaceBlockEntity owner, String name) {
        try {
            var field = OverloadedInterfaceBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void passed(String message) { System.out.println("INTERFACE_MEK_PASS " + message); }
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
