package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

import com.moakiee.ae2lt.registry.ModFumos;

/**
 * Client-only Curios bridge for Pigmee head rendering.
 *
 * <p>The caller must check that Curios is loaded before touching this class. Keeping all Curios
 * types behind that boundary lets AE2LT continue to run when the optional dependency is absent.</p>
 */
final class PigmeeCuriosClientBridge {
    private PigmeeCuriosClientBridge() {
    }

    static void registerRenderers() {
        CuriosRendererRegistry.register(ModFumos.PIGMEE_FUMO_ITEM.get(), PigmeeHeadRenderer::new);
        CuriosRendererRegistry.register(
                ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get(), PigmeeHeadRenderer::new);
        CuriosRendererRegistry.register(
                ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get(), PigmeeHeadRenderer::new);
    }

    private static final class PigmeeHeadRenderer implements ICurioRenderer {
        @Override
        public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(
                ItemStack stack,
                SlotContext slotContext,
                PoseStack poseStack,
                SubmitNodeCollector collector,
                int packedLight,
                S renderState,
                RenderLayerParent<S, M> renderLayerParent,
                EntityRendererProvider.Context context,
                float yRot,
                float xRot) {
            if (!(renderLayerParent.getModel() instanceof HeadedModel headedModel)) {
                return;
            }

            LivingEntity wearer = slotContext.entity();
            poseStack.pushPose();
            if (wearer.isBaby() && !(wearer instanceof Villager)) {
                poseStack.translate(0.0F, 0.03125F, 0.0F);
                poseStack.scale(0.7F, 0.7F, 0.7F);
                poseStack.translate(0.0F, 1.0F, 0.0F);
            }

            headedModel.getHead().translateAndRotate(poseStack);
            boolean villagerHead = wearer instanceof Villager || wearer instanceof ZombieVillager;
            CustomHeadLayer.translateToHead(poseStack, villagerHead
                    ? VillagerRenderer.CUSTOM_HEAD_TRANSFORMS : CustomHeadLayer.Transforms.DEFAULT);
            Minecraft.getInstance()
                    .getEntityRenderDispatcher()
                    .getItemInHandRenderer()
                    .renderItem(
                            wearer,
                            stack,
                            ItemDisplayContext.HEAD,
                            poseStack,
                            collector,
                            packedLight);
            poseStack.popPose();
        }
    }
}
