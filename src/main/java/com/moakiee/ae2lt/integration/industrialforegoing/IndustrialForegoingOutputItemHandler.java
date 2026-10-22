package com.moakiee.ae2lt.integration.industrialforegoing;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.MEStorage;
import appeng.me.storage.ExternalStorageFacade;

import com.hrznstudio.titanium.block.tile.ActiveTile;
import com.hrznstudio.titanium.component.inventory.MultiInventoryComponent.MultiInvCapabilityHandler;
import com.hrznstudio.titanium.component.sideness.IFacingComponent;
import com.hrznstudio.titanium.util.FacingUtil;

/** Audited IF component roles; extraction permission by itself does not identify a product. */
public final class IndustrialForegoingOutputItemHandler implements IItemHandler {
    private static final Set<String> STANDARD_OUTPUT = Set.of(
            "plant_gatherer", "animal_rancher", "simulated_hydroponic_bed", "hydroponic_bed",
            "mob_crusher", "resourceful_furnace", "marine_fisher", "fluid_sieving_machine",
            "sludge_refiner", "block_breaker", "ore_laser_base", "potion_brewer", "dye_mixer",
            "spores_recreator", "dissolution_chamber", "latex_processing_unit",
            "enchantment_applicator", "enchantment_factory");
    private static final Set<String> INPUT_ONLY = Set.of(
            "mob_duplicator", "animal_feeder", "plant_fertilizer", "wither_builder", "plant_sower",
            "bioreactor", "pitiful_generator", "washing_factory", "block_placer",
            "fluid_laser_base", "infinity_charger");
    private static final Map<String, Set<String>> OTHER_OUTPUTS = Map.of(
            "sewage_composter", Set.of("fertilizer"),
            "enchantment_sorter", Set.of("noEnchanted", "enchanted"),
            "enchantment_extractor", Set.of("outputNoEnchantedItem", "outputEnchantedBook"));
    // Material Stonework Factory is deliberately absent: any intermediate stage
    // may be the configured final product. Component names cannot identify that role.

    private final Supplier<MultiInvCapabilityHandler<?>> handler;
    private final Supplier<FacingUtil.Sideness> side;
    private final Set<String> outputNames;

    IndustrialForegoingOutputItemHandler(Supplier<MultiInvCapabilityHandler<?>> handler,
            Supplier<FacingUtil.Sideness> side, Set<String> outputNames) {
        this.handler = handler;
        this.side = side;
        this.outputNames = outputNames;
    }

    public static MEStorage restrict(BlockEntity blockEntity, Direction face, MEStorage fallback) {
        var id = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        if (!id.getNamespace().equals("industrialforegoing") || !(blockEntity instanceof ActiveTile<?> tile)) {
            return fallback;
        }
        var names = outputNames(id.getPath());
        if (names == null) return fallback; // Unknown machine: ordinary capability behavior.
        return ExternalStorageFacade.of(new IndustrialForegoingOutputItemHandler(() -> {
            var level = tile.getLevel();
            if (level == null || tile.isRemoved()) return null;
            if (level.getBlockEntity(tile.getBlockPos()) != tile) return null;
            var exposed = tile.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
            // Use the authoritative sided capability for every operation, including its predicates.
            return exposed instanceof MultiInvCapabilityHandler<?> inventory ? inventory : null;
        }, () -> FacingUtil.getFacingRelative(tile.getFacingDirection(), face), names));
    }

    static Set<String> outputNames(String machine) {
        if (STANDARD_OUTPUT.contains(machine)) return Set.of("output");
        if (INPUT_ONLY.contains(machine)) return Set.of();
        return OTHER_OUTPUTS.get(machine);
    }

    private boolean isOutput(MultiInvCapabilityHandler<?> current, int slot) {
        if (current == null || slot < 0 || slot >= current.getSlots()) return false;
        var component = current.getFromSlot(slot);
        if (component == null || !outputNames.contains(component.getName())) return false;
        return !(component instanceof IFacingComponent sided)
                || sided.getFacingModes().get(side.get()) != null
                && sided.getFacingModes().get(side.get()).allowsConnection();
    }

    @Override
    public int getSlots() {
        var current = handler.get();
        return current == null ? 0 : current.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        var current = handler.get();
        return isOutput(current, slot) ? current.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        var current = handler.get();
        return isOutput(current, slot) ? current.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        var current = handler.get();
        return isOutput(current, slot) ? current.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        var current = handler.get();
        return isOutput(current, slot) ? current.getSlotLimit(slot) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        var current = handler.get();
        return isOutput(current, slot) && current.isItemValid(slot, stack);
    }
}
