package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.core.MatrixCoreEffectRenderer;
import com.moakiee.ae2lt.client.core.TianshuCoreEffectRenderer;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModFumos;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class ModEntityRenderers {
    private ModEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.OVERLOAD_TNT.get(), TntRenderer::new);
        event.registerEntityRenderer(ModEntities.FLOATING_MATTER.get(), ItemEntityRenderer::new);
        event.registerEntityRenderer(
                ModEntities.RITUAL_HYPERDIMENSIONAL_PIGMEE.get(),
                RitualHyperdimensionalPigmeeRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                LightningSimulationChamberRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                LightningAssemblyChamberRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.CRYSTAL_CATALYZER.get(),
                CrystalCatalyzerRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.PIGMEE_CRYSTAL_CATALYZER.get(),
                CrystalCatalyzerRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.FUMO.get(),
                FumoBlockRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.PIGMEE_MOLECULAR_ASSEMBLER.get(),
                PigmeeMolecularAssemblerRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.MATRIX_CONTROLLER.get(),
                MatrixCoreEffectRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.TIANSHU_SUPERCOMPUTER_CONTROLLER.get(),
                TianshuCoreEffectRenderer::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterStandalone event) {
        event.register(PigmeeMolecularAssemblerRenderer.LIGHTS_MODEL,
                SimpleUnbakedStandaloneModel.simpleModelWrapper(
                        PigmeeMolecularAssemblerRenderer.LIGHTS_MODEL_ID));
        event.register(HyperdimensionalPigmeeTextureLayer.MODEL,
                SimpleUnbakedStandaloneModel.quadCollection(
                        HyperdimensionalPigmeeTextureLayer.MODEL_ID));
    }

    @SubscribeEvent
    public static void wrapFumoItemModels(ModelEvent.ModifyBakingResult event) {
        wrapFumoItemModel(event, "moakiee_fumo");
        wrapFumoItemModel(event, "cystrysu_fumo");
        wrapFumoItemModel(event, "pigmee_fumo");
        wrapFumoItemModel(event, "creative_pigmee_fumo");
        wrapFumoItemModel(event, "hyperdimensional_pigmee_fumo");
    }

    private static void wrapFumoItemModel(ModelEvent.ModifyBakingResult event, String itemId) {
        Identifier id = Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, itemId);
        event.getBakingResult().itemStackModels().computeIfPresent(id, (ignored, model) ->
                itemId.equals("hyperdimensional_pigmee_fumo")
                        ? new HyperdimensionalPigmeeBakedModel(model)
                        : new SpinningFumoBakedModel(model));
    }
}
