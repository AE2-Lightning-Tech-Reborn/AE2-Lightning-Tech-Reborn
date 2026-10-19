package com.moakiee.ae2lt.integration.emi;

import appeng.api.client.AEStackRendering;
import com.moakiee.ae2lt.me.key.LightningKey;
import dev.emi.emi.api.render.EmiRenderable;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moakiee.ae2lt.client.compat.LegacyGui;

/** Animated lightning icon shared by EMI category headers and recipe pages. */
final class EmiLightningIcon implements EmiRenderable {
    private final boolean extreme;

    EmiLightningIcon(boolean extreme) {
        this.extreme = extreme;
    }

    @Override
    public void render(PoseStack graphics, int x, int y, float delta) {
        AEStackRendering.drawInGui(
                Minecraft.getInstance(),
                graphics,
                x,
                y,
                0,
                extreme ? LightningKey.EXTREME_HIGH_VOLTAGE : LightningKey.HIGH_VOLTAGE);
    }
}
