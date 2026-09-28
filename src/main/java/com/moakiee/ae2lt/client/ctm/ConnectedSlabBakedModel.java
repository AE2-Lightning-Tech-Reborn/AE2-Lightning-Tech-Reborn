package com.moakiee.ae2lt.client.ctm;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** Native CTM parts mapped onto half-height slabs; the inset face is never neighbour-culled. */
public final class ConnectedSlabBakedModel extends ConnectedTextureBakedModel {
    private final SlabType type;
    public ConnectedSlabBakedModel(Material.Baked base, Material.Baked ctm,
            Material.@Nullable Baked overlay, String renderType,
            boolean ambientOcclusion, boolean gui3d, boolean usesBlockLight, SlabType type) {
        super(base, ctm, overlay, ConnectionPredicates.SAME_SLAB, renderType,
                ambientOcclusion, gui3d, usesBlockLight);
        this.type = type;
    }
    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockStateModelPart> output) {
        if (type == SlabType.DOUBLE) { super.collectParts(level, pos, state, random, output); return; }
        var parts = new ArrayList<BlockStateModelPart>();
        super.collectParts(level, pos, state, random, parts);
        for (var original : parts) output.add(new BlockStateModelPart() {
            @Override public List<BakedQuad> getQuads(@Nullable Direction side) {
                Direction inset = type == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
                if (side == inset) return List.of();
                return original.getQuads(side == null ? inset : side).stream()
                        .map(ConnectedSlabBakedModel.this::shape).toList();
            }
            @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
            @Override public Material.Baked particleMaterial() { return original.particleMaterial(); }
            @Override public int materialFlags() { return original.materialFlags(); }
        });
    }
    private BakedQuad shape(BakedQuad q) {
        return new BakedQuad(shape(q.position0()), shape(q.position1()), shape(q.position2()), shape(q.position3()),
                q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), q.direction(), q.materialInfo(),
                q.bakedNormals(), q.bakedColors());
    }
    private Vector3fc shape(Vector3fc p) {
        return new Vector3f(p.x(), p.y() * 0.5F + (type == SlabType.TOP ? 0.5F : 0), p.z());
    }
}
