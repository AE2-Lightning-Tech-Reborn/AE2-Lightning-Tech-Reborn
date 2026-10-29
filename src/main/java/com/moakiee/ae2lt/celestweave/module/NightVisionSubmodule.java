package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;

public final class NightVisionSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final NightVisionSubmodule INSTANCE = new NightVisionSubmodule();

    // Client effects keep counting down while server ticks or refresh packets stall. Renew our
    // grant after 60 server ticks, leaving headroom above vanilla's 200-tick fade window.
    private static final int EFFECT_DURATION_TICKS = 1_200;
    private static final int REFRESH_THRESHOLD_TICKS = EFFECT_DURATION_TICKS - 60;

    private NightVisionSubmodule() {}

    @Override
    public String id() {
        return "night_vision";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.night_vision.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.night_vision.desc";
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
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            applyEffect(player);
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            applyEffect(player);
        }
        return 0;
    }

    private static void applyEffect(Player player) {
        var currentEffect = player.getEffect(MobEffects.NIGHT_VISION);
        if (currentEffect != null && !currentEffect.endsWithin(REFRESH_THRESHOLD_TICKS)) {
            return;
        }

        // Matching the existing amplifier lets vanilla extend stronger night vision too.
        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                EFFECT_DURATION_TICKS,
                currentEffect == null ? 0 : currentEffect.getAmplifier(),
                false,
                false,
                true));
    }
}
