package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

public final class NightVisionSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final NightVisionSubmodule INSTANCE = new NightVisionSubmodule();

    // Client effects keep counting down while server ticks or refresh packets stall. Renew our
    // grant after 60 server ticks, leaving headroom above vanilla's 200-tick fade window.
    private static final int EFFECT_DURATION_TICKS = 1_200;
    private static final int REFRESH_THRESHOLD_TICKS = EFFECT_DURATION_TICKS - 60;

    public static final String NIGHT = "night_vision";
    public static final String FOG = "remove_fog";
    public static final String XRAY = "block_xray";

    public boolean option(ItemStack armor, String key) {
        if (!NIGHT.equals(key) && !FOG.equals(key)) return false;
        var options = getOptions(armor);
        return !options.contains(key, net.minecraft.nbt.Tag.TAG_BYTE) || options.getBoolean(key);
    }

    @Override
    public java.util.List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return java.util.stream.Stream.of(NIGHT, FOG).map(key ->
                config(key, net.minecraft.network.chat.Component.translatable("ae2lt.vision." + key),
                        net.minecraft.nbt.ByteTag.valueOf(option(armor, key)), booleanChoices(), null)).toList();
    }

    private static final String GRANTED = "ae2lt_visual_night_vision";

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
    public void onActivated(@Nullable LivingEntity player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER && option(armor, NIGHT)) {
            applyEffect(player);
            player.getPersistentData().putBoolean(GRANTED, true);
        }
    }

    @Override
    public void onDeactivated(@Nullable LivingEntity player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            clearGrantedEffect(player);
        }
    }

    @Override
    public int tickActive(@Nullable LivingEntity player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER && option(armor, NIGHT)) {
            applyEffect(player);
            player.getPersistentData().putBoolean(GRANTED, true);
        } else if (player != null && dist == Dist.DEDICATED_SERVER) {
            clearGrantedEffect(player);
        }
        return 0;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        onActivated((LivingEntity) player, dist, armor);
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        onDeactivated((LivingEntity) player, dist, armor);
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return tickActive((LivingEntity) player, dist, armor);
    }

    private static void clearGrantedEffect(LivingEntity player) {
        if (player.getPersistentData().getBoolean(GRANTED)) {
            player.getPersistentData().remove(GRANTED);
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    private static void applyEffect(LivingEntity player) {
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
