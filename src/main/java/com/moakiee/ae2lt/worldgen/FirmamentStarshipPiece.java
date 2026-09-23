package com.moakiee.ae2lt.worldgen;

import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModStructureTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class FirmamentStarshipPiece extends TemplateStructurePiece {

    public FirmamentStarshipPiece(
            StructureTemplateManager structureTemplateManager,
            Identifier template,
            BlockPos position,
            Rotation rotation) {
        this(structureTemplateManager, template, position, rotation, BlockPos.ZERO);
    }

    public FirmamentStarshipPiece(
            StructureTemplateManager structureTemplateManager,
            Identifier template,
            BlockPos position,
            Rotation rotation,
            BlockPos rotationPivot) {
        super(
                ModStructureTypes.FIRMAMENT_STARSHIP_PIECE.get(),
                0,
                structureTemplateManager,
                template,
                template.toString(),
                makeSettings(rotation, rotationPivot),
                position);
    }

    public FirmamentStarshipPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
        super(
                ModStructureTypes.FIRMAMENT_STARSHIP_PIECE.get(),
                tag,
                structureTemplateManager,
                location -> makeSettings(readRotation(tag), readRotationPivot(tag)));
    }

    private static StructurePlaceSettings makeSettings(Rotation rotation, BlockPos rotationPivot) {
        return new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
                .setRotationPivot(rotationPivot)
                .setRotation(rotation);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().getSerializedName());
        BlockPos rotationPivot = this.placeSettings.getRotationPivot();
        tag.putInt("RPX", rotationPivot.getX());
        tag.putInt("RPY", rotationPivot.getY());
        tag.putInt("RPZ", rotationPivot.getZ());
    }

    private static Rotation readRotation(CompoundTag tag) {
        String serializedName = tag.getStringOr("Rot", "");
        for (Rotation rotation : Rotation.values()) {
            if (rotation.getSerializedName().equals(serializedName)) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    private static BlockPos readRotationPivot(CompoundTag tag) {
        if (!tag.contains("RPX") || !tag.contains("RPY") || !tag.contains("RPZ")) {
            return BlockPos.ZERO;
        }
        return new BlockPos(tag.getIntOr("RPX", 0), tag.getIntOr("RPY", 0), tag.getIntOr("RPZ", 0));
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos,
            BlockPos pivot) {
        super.postProcess(level, structureManager, chunkGenerator, random, chunkBB, chunkPos, pivot);

        for (var blockInfo : this.template.filterBlocks(
                this.templatePosition,
                this.placeSettings,
                ModBlocks.FIRMAMENT_CONVERSION_CORE.get())) {
            if (level.getBlockEntity(blockInfo.pos()) instanceof FirmamentConversionCoreBlockEntity core) {
                core.initializeNaturalLoot(random);
            }
        }
    }

    @Override
    protected void handleDataMarker(
            String markerId,
            BlockPos position,
            ServerLevelAccessor level,
            RandomSource random,
            BoundingBox chunkBB) {
    }
}
