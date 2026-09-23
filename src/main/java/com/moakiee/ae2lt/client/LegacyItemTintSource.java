package com.moakiee.ae2lt.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import appeng.api.util.AEColor;
import appeng.client.item.StorageCellStateTintSource;

/** Preserves the old tint-index colors in the 26.1 item model definition format. */
public record LegacyItemTintSource(String kind, int index) implements ItemTintSource {
    public static final MapCodec<LegacyItemTintSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(LegacyItemTintSource::kind),
            Codec.INT.fieldOf("index").forGetter(LegacyItemTintSource::index))
            .apply(instance, LegacyItemTintSource::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        return switch (kind) {
            case "terminal" -> ARGB.opaque(AEColor.TRANSPARENT.getVariantByTintIndex(index));
            case "cell" -> index == 1 ? new StorageCellStateTintSource().calculate(stack, level, owner) : -1;
            default -> -1;
        };
    }

    @Override
    public MapCodec<LegacyItemTintSource> type() {
        return CODEC;
    }
}
