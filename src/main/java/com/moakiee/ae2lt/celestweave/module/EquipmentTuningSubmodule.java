package com.moakiee.ae2lt.celestweave.module;

import java.util.Arrays;
import java.util.List;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class EquipmentTuningSubmodule extends AbstractCelestweaveArmorSubmodule {
    public static final EquipmentTuningSubmodule SPEED = new EquipmentTuningSubmodule("acceleration");
    public static final EquipmentTuningSubmodule DISTANCE = new EquipmentTuningSubmodule("range");
    private final String id;

    private EquipmentTuningSubmodule(String id) { this.id = id; }
    @Override public String id() { return id; }
    @Override public String nameKey() { return "item.ae2lt.railgun_module_" + id; }
    @Override public String descriptionKey() { return "ae2lt.equipment." + id; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public int getMaxInstallAmount() { return 1; }

    private List<String> keys(ItemStack armor) {
        if (this == DISTANCE) return List.of("jump_height", "automatic_step_height");
        return switch (CelestweaveArmorState.armorPart(armor)) {
            case CHEST -> List.of("mining_speed");
            case LEGS -> List.of("walk_speed_multiplier", "sprint_speed_multiplier", "sneak_speed_multiplier");
            case FEET -> List.of("underwater_speed", "swim_speed");
            case HEAD -> List.of();
        };
    }

    public double multiplier(ItemStack armor, String key) {
        return MovementSpeedOption.fromTag(getOptions(armor).get(key)).multiplier();
    }

    public double stepHeight(ItemStack armor) {
        return StepHeightOption.fromTag(getOptions(armor).get("automatic_step_height")).height();
    }

    @Override public List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        var options = getOptions(armor);
        return keys(armor).stream().map(key -> {
            var label = Component.translatable("ae2lt.celestweave.config." + key);
            if (key.equals("automatic_step_height")) {
                var choices = Arrays.stream(StepHeightOption.values()).map(value -> choice(value.toTag(),
                        Component.translatable("ae2lt.celestweave.config.value.blocks", value.label()))).toList();
                return config(key, label, StepHeightOption.fromTag(options.get(key)).toTag(), choices, null);
            }
            return config(key, label, MovementSpeedOption.fromTag(options.get(key)).toTag(), multiplierChoices(), null);
        }).toList();
    }

    static List<CelestweaveArmorSubmoduleConfigChoice> multiplierChoices() {
        return Arrays.stream(MovementSpeedOption.values()).map(value -> new CelestweaveArmorSubmoduleConfigChoice(
                value.toTag(), Component.translatable("ae2lt.celestweave.config.value.multiplier", value.label()))).toList();
    }

    @Override public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (!keys(armor).contains(key)) return false;
        var options = getOptions(armor);
        options.put(key, key.equals("automatic_step_height") ? StepHeightOption.fromTag(value).toTag()
                : MovementSpeedOption.fromTag(value).toTag());
        setOptions(armor, options);
        return true;
    }
}
