package com.moakiee.ae2lt.client.ctm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jspecify.annotations.Nullable;

/** Unbaked 26.1 blockstate model for the original AE2LT connected textures. */
public record ConnectedTextureGeometry(
        Identifier connection,
        Identifier base,
        Identifier ctm,
        @Nullable Identifier overlay,
        String renderType,
        boolean ambientOcclusion,
        boolean gui3d,
        boolean usesBlockLight) implements CustomUnbakedBlockStateModel {
    public static final MapCodec<ConnectedTextureGeometry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("connection", Identifier.fromNamespaceAndPath("ae2lt", "same_block"))
                    .forGetter(ConnectedTextureGeometry::connection),
            Identifier.CODEC.fieldOf("base").forGetter(ConnectedTextureGeometry::base),
            Identifier.CODEC.fieldOf("ctm").forGetter(ConnectedTextureGeometry::ctm),
            Identifier.CODEC.optionalFieldOf("overlay").forGetter(model -> java.util.Optional.ofNullable(model.overlay())),
            Codec.STRING.optionalFieldOf("render_type", "minecraft:translucent")
                    .forGetter(ConnectedTextureGeometry::renderType),
            Codec.BOOL.optionalFieldOf("ambientocclusion", true)
                    .forGetter(ConnectedTextureGeometry::ambientOcclusion),
            Codec.BOOL.optionalFieldOf("gui3d", true).forGetter(ConnectedTextureGeometry::gui3d),
            Codec.BOOL.optionalFieldOf("uses_block_light", true).forGetter(ConnectedTextureGeometry::usesBlockLight))
            .apply(instance, (connection, base, ctm, overlay, renderType, ambientOcclusion, gui3d, usesBlockLight) ->
                    new ConnectedTextureGeometry(connection, base, ctm, overlay.orElse(null), renderType,
                            ambientOcclusion, gui3d, usesBlockLight)));

    @Override
    public void resolveDependencies(ResolvableModel.Resolver resolver) {
    }

    @Override
    public BlockStateModel bake(ModelBaker baker) {
        var materials = baker.materials();
        var baseMaterial = materials.get(new Material(base), () -> "ae2lt CTM base " + base);
        var ctmMaterial = materials.get(new Material(ctm), () -> "ae2lt CTM sheet " + ctm);
        var overlayMaterial = overlay == null ? null
                : materials.get(new Material(overlay), () -> "ae2lt CTM overlay " + overlay);
        return new ConnectedTextureBakedModel(baseMaterial, ctmMaterial, overlayMaterial,
                ConnectionPredicates.get(connection), renderType, ambientOcclusion, gui3d, usesBlockLight);
    }

    @Override
    public MapCodec<ConnectedTextureGeometry> codec() {
        return CODEC;
    }
}
