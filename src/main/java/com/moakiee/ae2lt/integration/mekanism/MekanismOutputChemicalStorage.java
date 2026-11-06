package com.moakiee.ae2lt.integration.mekanism;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.tile.interfaces.chemical.IGasTile;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.common.tile.interfaces.chemical.IInfusionTile;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.common.tile.interfaces.chemical.IPigmentTile;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.common.tile.interfaces.chemical.ISlurryTile;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** AUTO-return view of the four Forge Mekanism chemical families, preserving sided tank permissions. */
public final class MekanismOutputChemicalStorage implements MEStorage {
    private static final ResourceLocation CHEMICAL_KEY_TYPE = new ResourceLocation("appmek", "chemical");
    private final MEStorage discovery;
    private final List<ProductView<?, ?, ?>> views;

    MekanismOutputChemicalStorage(MEStorage discovery, List<ProductView<?, ?, ?>> views) {
        this.discovery = discovery;
        this.views = List.copyOf(views);
    }

    public static MEStorage restrict(BlockEntity blockEntity, Direction face, MEStorage fallback) {
        // 1.20 storage tanks register the same store as a non-extractable INPUT too.
        // Their contents are storage, so retain the original sided AppMek view.
        if (blockEntity instanceof TileEntityChemicalTank) {
            return fallback;
        }
        if (!(blockEntity instanceof ISideConfiguration configuration) || configuration.getConfig() == null) {
            return fallback;
        }
        var views = new ArrayList<ProductView<?, ?, ?>>();
        var config = configuration.getConfig();
        if (blockEntity instanceof IGasTile machine && machine.canHandleGas()
                && config.getConfig(TransmissionType.GAS) != null) {
            views.add(new ProductView<>(Gas.class, () -> machine.getGasTanks(face),
                    () -> blockEntity.getCapability(Capabilities.GAS_HANDLER, face).orElse(null),
                    () -> config.getConfig(TransmissionType.GAS), GasStack::new));
        }
        if (blockEntity instanceof IInfusionTile machine && machine.canHandleInfusion()
                && config.getConfig(TransmissionType.INFUSION) != null) {
            views.add(new ProductView<>(InfuseType.class, () -> machine.getInfusionTanks(face),
                    () -> blockEntity.getCapability(Capabilities.INFUSION_HANDLER, face).orElse(null),
                    () -> config.getConfig(TransmissionType.INFUSION), InfusionStack::new));
        }
        if (blockEntity instanceof IPigmentTile machine && machine.canHandlePigment()
                && config.getConfig(TransmissionType.PIGMENT) != null) {
            views.add(new ProductView<>(Pigment.class, () -> machine.getPigmentTanks(face),
                    () -> blockEntity.getCapability(Capabilities.PIGMENT_HANDLER, face).orElse(null),
                    () -> config.getConfig(TransmissionType.PIGMENT), PigmentStack::new));
        }
        if (blockEntity instanceof ISlurryTile machine && machine.canHandleSlurry()
                && config.getConfig(TransmissionType.SLURRY) != null) {
            views.add(new ProductView<>(Slurry.class, () -> machine.getSlurryTanks(face),
                    () -> blockEntity.getCapability(Capabilities.SLURRY_HANDLER, face).orElse(null),
                    () -> config.getConfig(TransmissionType.SLURRY), SlurryStack::new));
        }
        return views.isEmpty() ? fallback : new MekanismOutputChemicalStorage(fallback, views);
    }

    private ProductView<?, ?, ?> view(Object chemical) {
        for (var view : views) {
            if (view.type.isInstance(chemical)) return view;
        }
        return null;
    }

    private static boolean isChemicalKey(AEKey key) {
        return CHEMICAL_KEY_TYPE.equals(key.getType().getId());
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var amounts = new IdentityHashMap<Object, Long>();
        for (var view : views) view.collect(amounts);
        var all = new KeyCounter();
        discovery.getAvailableStacks(all);
        for (var entry : all) {
            var key = entry.getKey();
            if (!isChemicalKey(key)) continue;
            // AppMek 1.4 keys expose the actual Gas/InfuseType/Pigment/Slurry as their primary key.
            // Identity also distinguishes chemicals sharing a registry id across different families.
            var primary = key.getPrimaryKey();
            long amount = view(primary) == null ? entry.getLongValue() : amounts.getOrDefault(primary, 0L);
            if (amount > 0) out.add(key, amount);
        }
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !isChemicalKey(what)) return 0;
        var view = view(what.getPrimaryKey());
        return view == null ? discovery.extract(what, amount, mode, source)
                : view.extract(what.getPrimaryKey(), amount, Action.get(mode == Actionable.MODULATE));
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !isChemicalKey(what)) return 0;
        var view = view(what.getPrimaryKey());
        return view == null ? discovery.insert(what, amount, mode, source)
                : view.insert(what.getPrimaryKey(), amount, Action.get(mode == Actionable.MODULATE));
    }

    @Override
    public Component getDescription() {
        return discovery.getDescription();
    }

    static final class ProductView<C extends Chemical<C>, S extends ChemicalStack<C>, T extends IChemicalTank<C, S>> {
        private final Class<C> type;
        private final Supplier<? extends List<T>> tanks;
        private final Supplier<? extends IChemicalHandler<C, S>> exposed;
        private final Supplier<ConfigInfo> config;
        private final BiFunction<C, Long, S> stackFactory;

        ProductView(Class<C> type, Supplier<? extends List<T>> tanks,
                Supplier<? extends IChemicalHandler<C, S>> exposed, Supplier<ConfigInfo> config,
                BiFunction<C, Long, S> stackFactory) {
            this.type = type;
            this.tanks = tanks;
            this.exposed = exposed;
            this.config = config;
            this.stackFactory = stackFactory;
        }

        private int[] productTanks(IChemicalHandler<C, S> handler) {
            if (handler == null) return new int[0];
            var tanks = this.tanks.get();
            var current = config.get();
            if (current == null || handler.getTanks() != tanks.size()) return new int[0];
            int count = 0;
            var result = new int[tanks.size()];
            for (int i = 0; i < tanks.size(); i++) {
                if (MekanismOutputRoles.isProductTank(current, tanks.get(i))) result[count++] = i;
            }
            return Arrays.copyOf(result, count);
        }

        void collect(Map<Object, Long> amounts) {
            var handler = exposed.get();
            for (int tank : productTanks(handler)) {
                var stack = handler.getChemicalInTank(tank);
                if (!stack.isEmpty()) amounts.merge(stack.getType(), stack.getAmount(), Long::sum);
            }
        }

        long extract(Object chemical, long amount, Action action) {
            var handler = exposed.get();
            long extracted = 0;
            for (int tank : productTanks(handler)) {
                if (extracted >= amount) break;
                var stored = handler.getChemicalInTank(tank);
                if (stored.isEmpty() || stored.getType() != chemical) continue;
                var taken = handler.extractChemical(tank, amount - extracted, action);
                if (!taken.isEmpty() && taken.getType() == chemical) extracted += taken.getAmount();
            }
            return extracted;
        }

        long insert(Object chemical, long amount, Action action) {
            var handler = exposed.get();
            long inserted = 0;
            for (int tank : productTanks(handler)) {
                if (inserted >= amount) break;
                var offered = stackFactory.apply(type.cast(chemical), amount - inserted);
                var remainder = handler.insertChemical(tank, offered, action);
                inserted += offered.getAmount() - remainder.getAmount();
            }
            return inserted;
        }
    }
}
