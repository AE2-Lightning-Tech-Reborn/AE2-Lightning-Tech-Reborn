package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.Tag;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class ReflectSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final ReflectSubmodule INSTANCE = new ReflectSubmodule();

    static CelestweaveArmorSubmoduleConfig shieldConfig(ItemStack armor) {
        return INSTANCE.config("reflect", INSTANCE.name(),
                ByteTag.valueOf(CelestweaveArmorState.isSubmoduleEnabled(armor, INSTANCE)), INSTANCE.booleanChoices(), null);
    }

    static boolean setShieldConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (!"reflect".equals(key)) return false;
        CelestweaveArmorState.setSubmoduleEnabled(armor, INSTANCE,
                !(value instanceof ByteTag flag) || flag.getAsByte() != 0);
        return true;
    }

    private ReflectSubmodule() {}

    @Override
    public String id() {
        return "reflect";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.reflect.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.reflect.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        // Reflect is handled by CelestweaveArmorDamageHandler reading ReflectTuning.
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }
}
