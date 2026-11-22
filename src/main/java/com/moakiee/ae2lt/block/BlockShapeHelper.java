package com.moakiee.ae2lt.block;

import java.util.EnumMap;

import com.lowdragmc.lowdraglib2.utils.ShapeUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

final class BlockShapeHelper {
    private static final Vector3f X_AXIS = new Vector3f(1, 0, 0);

    private BlockShapeHelper() {
    }

    static VoxelShape or(VoxelShape... shapes) {
        VoxelShape result = Shapes.empty();
        for (var shape : shapes) {
            result = Shapes.or(result, shape);
        }
        return result;
    }

    static EnumMap<Direction, VoxelShape> createAllFacingShapes(VoxelShape upShape) {
        var northShape = snapToVoxelGrid(ShapeUtils.rotate(upShape, X_AXIS, -90));
        var shapes = createHorizontalFacingShapes(northShape);
        shapes.put(Direction.UP, upShape);
        shapes.put(Direction.DOWN, snapToVoxelGrid(ShapeUtils.rotate(upShape, X_AXIS, 180)));
        return shapes;
    }

    static EnumMap<Direction, VoxelShape> createHorizontalFacingShapes(VoxelShape northShape) {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        shapes.put(Direction.NORTH, northShape);
        shapes.put(Direction.EAST, snapToVoxelGrid(ShapeUtils.rotate(northShape, Direction.EAST)));
        shapes.put(Direction.SOUTH, snapToVoxelGrid(ShapeUtils.rotate(northShape, Direction.SOUTH)));
        shapes.put(Direction.WEST, snapToVoxelGrid(ShapeUtils.rotate(northShape, Direction.WEST)));
        return shapes;
    }

    // These machine shapes have integral Block.box coordinates. Restore the exact boundaries
    // after LDLib's floating-point rotation so boxes still meet the same voxel faces.
    private static VoxelShape snapToVoxelGrid(VoxelShape rotated) {
        VoxelShape shape = Shapes.empty();
        for (var box : rotated.toAabbs()) {
            shape = Shapes.or(shape, Shapes.box(
                    snap(box.minX), snap(box.minY), snap(box.minZ),
                    snap(box.maxX), snap(box.maxY), snap(box.maxZ)));
        }
        return shape;
    }

    private static double snap(double coordinate) {
        return Math.round(coordinate * 16.0) / 16.0;
    }
}
