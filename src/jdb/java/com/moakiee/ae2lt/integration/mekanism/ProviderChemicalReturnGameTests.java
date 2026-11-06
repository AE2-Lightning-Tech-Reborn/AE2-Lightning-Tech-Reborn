package com.moakiee.ae2lt.integration.mekanism;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.registry.ModBlocks;
import me.ramidzkh.mekae2.MekCapabilities;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.tile.interfaces.chemical.IGasTile;
import mekanism.common.tile.interfaces.chemical.IInfusionTile;
import mekanism.common.tile.interfaces.chemical.IPigmentTile;
import mekanism.common.tile.interfaces.chemical.ISlurryTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real AppMek storage and Mekanism sided tanks, including all four Forge chemical families. */
@GameTestHolder("ae2lt_provider_chemical")
@PrefixGameTestTemplate(false)
public final class ProviderChemicalReturnGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final Direction FACE = Direction.NORTH;

    private enum Form {
        GAS(TransmissionType.GAS), INFUSION(TransmissionType.INFUSION),
        PIGMENT(TransmissionType.PIGMENT), SLURRY(TransmissionType.SLURRY);
        final TransmissionType transmission;
        Form(TransmissionType transmission) { this.transmission = transmission; }
    }

    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 420)
    public static void gasProductsReturn(GameTestHelper h) { products(h, Form.GAS, false); }
    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 420)
    public static void infusionProductsReturnWirelessly(GameTestHelper h) { products(h, Form.INFUSION, true); }
    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 420)
    public static void pigmentProductsReturn(GameTestHelper h) { products(h, Form.PIGMENT, false); }
    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 420)
    public static void slurryProductsReturnWirelessly(GameTestHelper h) { products(h, Form.SLURRY, true); }
    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 420)
    public static void oxidizerGasOutputReturnsWirelessly(GameTestHelper h) {
        products(h, "chemical_oxidizer", Form.GAS, true, 0);
    }
    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 420)
    public static void extractorPigmentOutputReturns(GameTestHelper h) {
        products(h, "pigment_extractor", Form.PIGMENT, false, 0);
    }
    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 420)
    public static void washerSlurryOutputReturnsWirelessly(GameTestHelper h) {
        products(h, "chemical_washer", Form.SLURRY, true, 1);
    }
    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 460)
    public static void compressorKeepsSecondaryGas(GameTestHelper h) {
        protectedInput(h, "osmium_compressor", Form.GAS, false);
    }
    @GameTest(templateNamespace = "ae2lt_provider_chemical", template = "empty", timeoutTicks = 460)
    public static void wirelessInfuserKeepsInfusionInput(GameTestHelper h) {
        protectedInput(h, "metallurgic_infuser", Form.INFUSION, true);
    }

    private static void products(GameTestHelper h, Form form, boolean wireless) {
        products(h, "basic_chemical_tank", form, wireless, 0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void products(GameTestHelper h, String id, Form form, boolean wireless, int tankIndex) {
        var provider = provider(h);
        var machine = machine(h, id, form, wireless, provider);
        var tank = tank(machine, form, tankIndex);
        var offer = offer(form, 100);
        var key = MekanismKey.of(offer);
        h.runAfterDelay(40, () -> {
            tank.setStack(offer);
            h.assertTrue(handler(machine, form).extractChemical(100, Action.SIMULATE).getAmount() == 100,
                    "native output-side capability exposes the product");
        });
        h.runAfterDelay(320, () -> {
            h.assertTrue(tank.getStored() == 0 && stored(provider, key) == 100,
                    id + " " + form + " must return exactly once; tank=" + tank.getStored()
                            + ", network=" + stored(provider, key));
            h.succeed();
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void protectedInput(GameTestHelper h, String id, Form form, boolean wireless) {
        var provider = provider(h);
        var machine = machine(h, id, form, wireless, provider);
        var tank = tank(machine, form);
        var offer = offer(form, 100);
        var key = MekanismKey.of(offer);
        h.runAfterDelay(40, () -> {
            tank.setStack(offer);
            h.assertTrue(handler(machine, form).extractChemical(100, Action.SIMULATE).getAmount() == 100,
                    "native output-side capability exposes the secondary input");
        });
        h.runAfterDelay(320, () -> {
            h.assertTrue(tank.getStored() == 100 && stored(provider, key) == 0,
                    "AUTO must preserve secondary chemical input");
            provider.setMachineAdaptationEnabled(false);
        });
        h.runAfterDelay(430, () -> {
            h.assertTrue(tank.getStored() == 0 && stored(provider, key) == 100,
                    "disabling adaptation must restore the native generic extraction path");
            h.succeed();
        });
    }

    private static OverloadedPatternProviderBlockEntity provider(GameTestHelper h) {
        h.setBlock(POS.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(POS, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.SOUTH));
        h.setBlock(POS.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) h.getBlockEntity(POS.west());
        drive.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(
                new ResourceLocation("appmek", "chemical_storage_cell_1k"))));
        var provider = (OverloadedPatternProviderBlockEntity) h.getBlockEntity(POS);
        provider.setFilteredImport(false);
        provider.setReturnMode(ReturnMode.AUTO);
        return provider;
    }

    private static BlockEntity machine(GameTestHelper h, String id, Form form, boolean wireless,
            OverloadedPatternProviderBlockEntity provider) {
        var pos = wireless ? POS.offset(4, 0, 0) : POS.south();
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(new ResourceLocation("mekanism", id)));
        var machine = h.getBlockEntity(pos);
        var sided = (ISideConfiguration) machine;
        var config = sided.getConfig().getConfig(form.transmission);
        config.setEjecting(false);
        var side = RelativeSide.fromDirections(sided.getDirection(), FACE);
        config.setDataType(mekanism.common.tile.component.config.DataType.OUTPUT, side);
        sided.getConfig().sideChanged(form.transmission, side);
        if (wireless) {
            provider.setProviderMode(ProviderMode.WIRELESS);
            h.assertTrue(provider.addOrUpdateConnection(h.getLevel().dimension(), h.absolutePos(pos), FACE),
                    "wireless target bound");
        }
        return machine;
    }

    @SuppressWarnings("rawtypes")
    private static IChemicalTank tank(BlockEntity machine, Form form) {
        return tank(machine, form, 0);
    }

    @SuppressWarnings("rawtypes")
    private static IChemicalTank tank(BlockEntity machine, Form form, int index) {
        return switch (form) {
            case GAS -> ((IGasTile) machine).getGasTanks(null).get(index);
            case INFUSION -> ((IInfusionTile) machine).getInfusionTanks(null).get(index);
            case PIGMENT -> ((IPigmentTile) machine).getPigmentTanks(null).get(index);
            case SLURRY -> ((ISlurryTile) machine).getSlurryTanks(null).get(index);
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IChemicalHandler handler(BlockEntity machine, Form form) {
        Capability capability = switch (form) {
            case GAS -> MekCapabilities.GAS_HANDLER_CAPABILITY;
            case INFUSION -> MekCapabilities.INFUSION_HANDLER_CAPABILITY;
            case PIGMENT -> MekCapabilities.PIGMENT_HANDLER_CAPABILITY;
            case SLURRY -> MekCapabilities.SLURRY_HANDLER_CAPABILITY;
        };
        return (IChemicalHandler) java.util.Objects.requireNonNull(
                machine.getCapability(capability, FACE).orElse(null), "Missing chemical capability");
    }

    private static ChemicalStack<?> offer(Form form, long amount) {
        return switch (form) {
            case GAS -> new GasStack(MekanismAPI.gasRegistry().getValue(new ResourceLocation("mekanism", "osmium")), amount);
            case INFUSION -> new InfusionStack(MekanismAPI.infuseTypeRegistry().getValue(new ResourceLocation("mekanism", "redstone")), amount);
            case PIGMENT -> MekanismAPI.pigmentRegistry().getValues().stream().map(c -> new PigmentStack(c, amount))
                    .filter(s -> !s.isEmpty()).findFirst().orElseThrow();
            case SLURRY -> MekanismAPI.slurryRegistry().getValues().stream().map(c -> new SlurryStack(c, amount))
                    .filter(s -> !s.isEmpty()).findFirst().orElseThrow();
        };
    }

    private static long stored(OverloadedPatternProviderBlockEntity provider, MekanismKey key) {
        return provider.getMainNode().getGrid().getStorageService().getInventory()
                .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }
}
