package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.registry.ModFumos;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/** Extra pass for the hyperdimensional Fumo item. */
final class HyperdimensionalPigmeeItemRenderer implements NoDataSpecialModelRenderer {
    static final HyperdimensionalPigmeeItemRenderer INSTANCE = new HyperdimensionalPigmeeItemRenderer();
    private HyperdimensionalPigmeeItemRenderer() {}

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector,
                       int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        var state = ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO.get().defaultBlockState();
        var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        HyperdimensionalPigmeePortalLayer.submit(model, poseStack, collector);
        HyperdimensionalPigmeeTextureLayer.submitItem(poseStack, collector, packedOverlay);
    }

    @Override
    public void getExtents(java.util.function.Consumer<Vector3fc> output) {
        output.accept(new org.joml.Vector3f(0, 0, 0));
        output.accept(new org.joml.Vector3f(1, 1, 1));
    }
}
