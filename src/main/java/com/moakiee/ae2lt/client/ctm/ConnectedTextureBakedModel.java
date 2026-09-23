package com.moakiee.ae2lt.client.ctm;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Transparency;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import org.jspecify.annotations.Nullable;

/** Connected-texture model with the original six-face neighbour and quadrant rules. */
public final class ConnectedTextureBakedModel implements DynamicBlockStateModel {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final float OVERLAY_OFFSET = 1.0F / 1024.0F;

    private final Material.Baked baseMaterial;
    private final Material.Baked ctmMaterial;
    private final Material.@Nullable Baked overlayMaterial;
    private final ConnectionPredicate predicate;
    private final Transparency transparency;
    private final boolean ambientOcclusion;
    @SuppressWarnings("unused") private final boolean gui3d;
    @SuppressWarnings("unused") private final boolean usesBlockLight;

    public ConnectedTextureBakedModel(Material.Baked baseMaterial, Material.Baked ctmMaterial,
            Material.@Nullable Baked overlayMaterial, ConnectionPredicate predicate,
            String renderType, boolean ambientOcclusion, boolean gui3d, boolean usesBlockLight) {
        this.baseMaterial = baseMaterial;
        this.ctmMaterial = ctmMaterial;
        this.overlayMaterial = overlayMaterial;
        this.predicate = predicate;
        this.transparency = switch (renderType) {
            case "solid", "minecraft:solid" -> Transparency.NONE;
            case "cutout", "minecraft:cutout", "cutout_mipped", "minecraft:cutout_mipped" -> Transparency.TRANSPARENT;
            default -> Transparency.TRANSLUCENT;
        };
        this.ambientOcclusion = ambientOcclusion;
        this.gui3d = gui3d;
        this.usesBlockLight = usesBlockLight;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                             RandomSource random, List<BlockStateModelPart> output) {
        output.add(new Part(connectionState(level, pos, state)));
    }

    @Override
    public Material.Baked particleMaterial() {
        return baseMaterial;
    }

    @Override
    public int materialFlags() {
        int flags = transparency.hasTranslucent() ? BakedQuad.FLAG_TRANSLUCENT : 0;
        if (baseMaterial.sprite().contents().isAnimated()
                || ctmMaterial.sprite().contents().isAnimated()
                || overlayMaterial != null && overlayMaterial.sprite().contents().isAnimated()) {
            flags |= BakedQuad.FLAG_ANIMATED;
        }
        return flags;
    }

    @Nullable
    private CtmConnectionState connectionState(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (!predicate.isActive(level, pos, state)) {
            return null;
        }
        boolean[] culled = new boolean[DIRECTIONS.length];
        int[] edges = new int[DIRECTIONS.length];
        int[] corners = new int[DIRECTIONS.length];
        for (Direction face : DIRECTIONS) {
            int idx = face.get3DDataValue();
            culled[idx] = predicate.connects(level, pos, state, face);
            int mask = 0;
            for (int edge = 0; edge < 4; edge++) {
                if (predicate.connects(level, pos, state, CtmFaceGeometry.neighborDir(face, edge))) {
                    mask |= 1 << edge;
                }
            }
            edges[idx] = mask;
            int cornerMask = 0;
            for (var quadrant : CtmTileSelector.Quadrant.values()) {
                if (predicate.connects(level, pos, state, CtmFaceGeometry.cornerPos(pos, face, quadrant))) {
                    cornerMask |= 1 << quadrant.ordinal();
                }
            }
            corners[idx] = cornerMask;
        }
        return new CtmConnectionState(culled, edges, corners);
    }

    private final class Part implements BlockStateModelPart {
        @Nullable private final CtmConnectionState connections;

        private Part(@Nullable CtmConnectionState connections) {
            this.connections = connections;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction side) {
            if (side == null) {
                return List.of();
            }
            if (connections == null) {
                if (overlayMaterial == null) {
                    return List.of(CtmFaceGeometry.fullFace(side, baseMaterial, transparency));
                }
                return List.of(
                        CtmFaceGeometry.fullFace(side, baseMaterial, transparency),
                        CtmFaceGeometry.fullFace(side, overlayMaterial, Transparency.TRANSPARENT, OVERLAY_OFFSET));
            }
            if (connections.culled(side)) {
                return List.of();
            }
            int edges = connections.edges(side);
            int corners = connections.corners(side);
            List<BakedQuad> quads = new ArrayList<>(overlayMaterial == null ? 4 : 5);
            for (int sq = 0; sq < 2; sq++) {
                for (int tq = 0; tq < 2; tq++) {
                    var tile = CtmTileSelector.select(CtmTileSelector.quadrant(sq, tq), edges, corners);
                    Material.Baked material = tile.source() == CtmTileSelector.Source.BASE ? baseMaterial : ctmMaterial;
                    quads.add(CtmFaceGeometry.quadrant(side, sq, tq, tile, material, transparency));
                }
            }
            if (overlayMaterial != null) {
                quads.add(CtmFaceGeometry.fullFace(side, overlayMaterial, Transparency.TRANSPARENT, OVERLAY_OFFSET));
            }
            return quads;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return ambientOcclusion;
        }

        @Override
        public Material.Baked particleMaterial() {
            return baseMaterial;
        }

        @Override
        public int materialFlags() {
            return ConnectedTextureBakedModel.this.materialFlags();
        }
    }
}
