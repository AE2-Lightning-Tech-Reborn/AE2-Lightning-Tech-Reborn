package com.moakiee.ae2lt.integration.mekanism;

import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.interfaces.ISideConfiguration;

/**
 * AUTO-return view of a Mekanism machine's product chemical tanks.
 * <p>
 * Applied Mekanistics is only a runtime dependency, so its key class is not
 * referenced: keys are discovered through the original Applied Mekanistics
 * storage view and matched by key type id plus chemical registry id. All
 * transfers go through the face's Mekanism capability by tank index, which keeps
 * Mekanism's side permissions and per-tank predicates.
 */
public final class MekanismOutputChemicalStorage implements MEStorage {
    private static final ResourceLocation CHEMICAL_KEY_TYPE =
            ResourceLocation.fromNamespaceAndPath("appmek", "chemical");

    private final MEStorage discovery;
    private final IChemicalHandler exposed;
    private final IMekanismChemicalHandler machine;
    private final Supplier<ConfigInfo> config;
    private final Direction face;

    MekanismOutputChemicalStorage(MEStorage discovery, IChemicalHandler exposed,
                                  IMekanismChemicalHandler machine, Supplier<ConfigInfo> config,
                                  Direction face) {
        this.discovery = discovery;
        this.exposed = exposed;
        this.machine = machine;
        this.config = config;
        this.face = face;
    }

    /** Called only when Mekanism is installed; non-configurable chemical holders keep the usual view. */
    @Nullable
    public static MEStorage restrict(BlockEntity blockEntity, Direction face, MEStorage fallback) {
        if (!(blockEntity instanceof ISideConfiguration configuration)
                || !(blockEntity instanceof IMekanismChemicalHandler machine)
                || !machine.canHandleChemicals()
                || configuration.getConfig() == null
                || configuration.getConfig().getConfig(TransmissionType.CHEMICAL) == null) {
            return fallback;
        }
        var level = blockEntity.getLevel();
        var handler = level == null ? null : level.getCapability(
                Capabilities.CHEMICAL.block(), blockEntity.getBlockPos(),
                blockEntity.getBlockState(), blockEntity, face);
        return handler == null ? null : new MekanismOutputChemicalStorage(
                fallback, handler, machine,
                () -> configuration.getConfig().getConfig(TransmissionType.CHEMICAL), face);
    }

    static boolean isChemicalKey(AEKey key) {
        return CHEMICAL_KEY_TYPE.equals(key.getType().getId());
    }

    @Nullable
    private static ResourceLocation chemicalId(ChemicalStack stack) {
        return stack.getChemicalHolder().unwrapKey().map(key -> key.location()).orElse(null);
    }

    /** Product tank indices in the face capability, or none if the live view is inconsistent. */
    private int[] productTanks() {
        // Resolve the live sided list: a side-config change can alter both the
        // exposed tanks and their indices without replacing this wrapper.
        var tanks = machine.getChemicalTanks(face);
        var chemicalConfig = config.get();
        if (chemicalConfig == null || exposed.getChemicalTanks() != tanks.size()) {
            return new int[0];
        }
        int count = 0;
        var result = new int[tanks.size()];
        for (int i = 0; i < tanks.size(); i++) {
            if (MekanismOutputRoles.isProductTank(chemicalConfig, tanks.get(i))) {
                result[count++] = i;
            }
        }
        return Arrays.copyOf(result, count);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var products = productTanks();
        if (products.length == 0) {
            return;
        }
        var amounts = new HashMap<ResourceLocation, Long>();
        for (int tank : products) {
            var stack = exposed.getChemicalInTank(tank);
            var id = stack.isEmpty() ? null : chemicalId(stack);
            if (id != null) {
                amounts.merge(id, stack.getAmount(), Long::sum);
            }
        }
        if (amounts.isEmpty()) {
            return;
        }
        var all = new KeyCounter();
        discovery.getAvailableStacks(all);
        for (var entry : all) {
            var key = entry.getKey();
            if (!isChemicalKey(key)) {
                continue;
            }
            Long amount = amounts.get(key.getId());
            if (amount != null && amount > 0) {
                out.add(key, amount);
            }
        }
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !isChemicalKey(what)) {
            return 0;
        }
        var action = Action.get(mode == Actionable.MODULATE);
        long extracted = 0;
        for (int tank : productTanks()) {
            if (extracted >= amount) {
                break;
            }
            var stored = exposed.getChemicalInTank(tank);
            if (stored.isEmpty() || !what.getId().equals(chemicalId(stored))) {
                continue;
            }
            var taken = exposed.extractChemical(tank, amount - extracted, action);
            if (!taken.isEmpty() && what.getId().equals(chemicalId(taken))) {
                extracted += taken.getAmount();
            }
        }
        return extracted;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // Compensation only: return into product tanks under their normal external
        // permissions (usually refused), never into input or extra tanks.
        if (amount <= 0 || !isChemicalKey(what)) {
            return 0;
        }
        var chemical = MekanismAPI.CHEMICAL_REGISTRY.getHolder(what.getId()).orElse(null);
        if (chemical == null || chemical.is(MekanismAPI.EMPTY_CHEMICAL_KEY)) {
            return 0;
        }
        var action = Action.get(mode == Actionable.MODULATE);
        long inserted = 0;
        for (int tank : productTanks()) {
            if (inserted >= amount) {
                break;
            }
            var offered = new ChemicalStack(chemical, amount - inserted);
            var remainder = exposed.insertChemical(tank, offered, action);
            inserted += offered.getAmount() - remainder.getAmount();
        }
        return inserted;
    }

    @Override
    public Component getDescription() {
        return discovery.getDescription();
    }
}
