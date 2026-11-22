package com.moakiee.ae2lt.client.render;

import com.lowdragmc.lowdraglib2.client.renderer.impl.IModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

/** Registers a standalone model for a layer drawn by a custom render pass. */
final class LdlibStandaloneModel extends IModelRenderer {
    LdlibStandaloneModel(ResourceLocation location) {
        super(location);
    }

    @Override
    public void registerEvent() {
        // ModEntityRenderers calls onAdditionalModel during the model event.
        // This also covers the first reload, when this renderer may be created during that event.
    }

    BakedModel bakedModel() {
        // Use the model from the completed bake, including other mods' replacements.
        return Minecraft.getInstance().getModelManager()
                .getModel(ModelResourceLocation.standalone(getModelLocation()));
    }
}
