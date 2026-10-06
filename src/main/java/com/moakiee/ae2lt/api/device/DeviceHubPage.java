package com.moakiee.ae2lt.api.device;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** A custom held-device page using the hub's existing module list and settings renderer. */
public interface DeviceHubPage {
    ResourceLocation id();
    Status inspect(ServerPlayer player, ItemStack device);
    /** Called only after session, permission, setting identity, expected value and bounds checks. */
    boolean setValue(ServerPlayer player, ItemStack device, ResourceLocation setting, int value);
    default boolean canConfigure(ServerPlayer player, ItemStack device) { return true; }

    record Module(String translationKey, int count, boolean enabled) {
        public Module {
            Objects.requireNonNull(translationKey);
            if (count < 0 || translationKey.length() > 256) throw new IllegalArgumentException("invalid module");
        }
    }

    record Setting(ResourceLocation id, String labelTranslationKey, String displayValue,
            int value, int min, int max, boolean editable) {
        public Setting {
            Objects.requireNonNull(id); Objects.requireNonNull(labelTranslationKey); Objects.requireNonNull(displayValue);
            if (min > max || value < min || value > max || id.toString().length() > 128
                    || labelTranslationKey.length() > 256 || displayValue.length() > 256) {
                throw new IllegalArgumentException("invalid setting");
            }
        }
        public int step(int direction) {
            return direction < 0 ? (value == min ? max : value - 1) : (value == max ? min : value + 1);
        }
    }

    record Status(String displayName, boolean hasCore, boolean powered, List<Module> modules, List<Setting> settings) {
        public Status {
            Objects.requireNonNull(displayName);
            modules = List.copyOf(modules); settings = List.copyOf(settings);
            if (displayName.length() > 256 || modules.size() > 64 || settings.size() > 64
                    || settings.stream().map(Setting::id).distinct().count() != settings.size()) {
                throw new IllegalArgumentException("invalid device status");
            }
        }
    }
}
