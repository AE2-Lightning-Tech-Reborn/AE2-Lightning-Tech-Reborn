package com.moakiee.ae2lt.item.railgun;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.module.*;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import net.minecraft.world.item.ItemStack;

/** The same speed or distance item uses the installed device's existing module controls. */
public final class SharedEquipmentModuleItem extends RailgunModuleItem implements CelestweaveArmorSubmoduleItem {
    public SharedEquipmentModuleItem(Properties properties, RailgunModuleType type) {
        super(properties, type);
    }

    @Override public ArmorPart armorPart() { return ArmorPart.CHEST; }

    @Override public Set<DeviceKind> acceptableDevices() {
        return Set.of(DeviceKind.RAILGUN, DeviceKind.CELESTWEAVE_CORE, DeviceKind.CELESTWEAVE_CONDUIT,
                DeviceKind.CELESTWEAVE_STRIDE);
    }

    @Override public boolean accepts(DeviceKind kind, DeviceSlotType slot) {
        if (kind == DeviceKind.RAILGUN) return super.accepts(kind, slot);
        for (var part : ArmorPart.values()) {
            if (part.deviceKind() == kind) return part != ArmorPart.HEAD && part.moduleSlot() == slot;
        }
        return false;
    }

    @Override public void collectSubmodules(ItemStack stack, Consumer<CelestweaveArmorSubmodule> output) {
        collectSubmodules(ArmorPart.CHEST, stack, output);
    }

    @Override public void collectSubmodules(ArmorPart part, ItemStack stack, Consumer<CelestweaveArmorSubmodule> output) {
        if (moduleType() == RailgunModuleType.ACCELERATION) {
            if (part != ArmorPart.HEAD) output.accept(EquipmentTuningSubmodule.SPEED);
        } else {
            switch (part) {
                case CHEST -> output.accept(ReachSubmodule.INSTANCE);
                case LEGS -> output.accept(EquipmentTuningSubmodule.DISTANCE);
                case FEET -> output.accept(DashSubmodule.INSTANCE);
                case HEAD -> {}
            }
        }
    }

    @Override public List<DeviceCapability> armorCapabilities(ArmorPart part, ItemStack stack) {
        if (moduleType() == RailgunModuleType.ACCELERATION || part == ArmorPart.LEGS) {
            return List.of(new DeviceCapability.MovementAssist(),
                    new DeviceCapability.PassiveDrain(ArmorOverloadRules.MOVEMENT_ASSIST_PASSIVE_DRAIN_FE));
        }
        return switch (part) {
            case CHEST -> List.of(new DeviceCapability.InteractionRange(),
                    new DeviceCapability.PassiveDrain(ArmorOverloadRules.REACH_EXTENSION_PASSIVE_DRAIN_FE));
            case FEET -> List.of(new DeviceCapability.DashEffect(1.8, 40),
                    new DeviceCapability.ActiveCost("dash", ArmorOverloadRules.DASH_ACTIVE_COST_FE));
            default -> List.of();
        };
    }
}
