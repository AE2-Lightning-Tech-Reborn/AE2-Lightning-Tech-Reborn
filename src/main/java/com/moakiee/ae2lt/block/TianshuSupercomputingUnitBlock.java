package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockUpdateScheduler;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockUpdateScheduler;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TianshuSupercomputingUnitBlock extends Block implements MatrixMultiblockComponentBlock {
    public static final BooleanProperty FORMED = TianshuSupercomputerStructureBlock.FORMED;
    private final TianshuMultiblockComponent component;
    private final MatrixMultiblockComponent matrixComponent;

    public TianshuSupercomputingUnitBlock(Properties properties, TianshuMultiblockComponent component) {
        this(properties, component, MatrixMultiblockComponent.OTHER);
    }

    public TianshuSupercomputingUnitBlock(
            Properties properties,
            TianshuMultiblockComponent component,
            MatrixMultiblockComponent matrixComponent) {
        super(properties);
        this.component = component;
        this.matrixComponent = matrixComponent;
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    public TianshuMultiblockComponent component() {
        return component;
    }

    public void appendItemTooltip(java.util.function.Consumer<Component> tooltip) {
        String description = switch (component) {
            case BLANK_UNIT -> "blank";
            case AMPLIFIER_UNIT -> "amplifier";
            case MAIN_BASELINE -> "baseline";
            case STORAGE_UNIT -> "storage";
            case PARALLEL_UNIT -> "parallel";
            default -> null;
        };
        if (description != null) {
            tooltip.accept(Component.translatable("tooltip.ae2lt.tianshu_unit." + description)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (component == TianshuMultiblockComponent.BLANK_UNIT
                || component == TianshuMultiblockComponent.AMPLIFIER_UNIT) {
            tooltip.accept(Component.translatable("tooltip.ae2lt.tianshu_unit.shared")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @Override
    public MatrixMultiblockComponent matrixComponent(BlockState state) {
        return matrixComponent;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return usesHiddenFormedModel(state) ? Shapes.empty() : super.getOcclusionShape(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return usesHiddenFormedModel(state) || super.propagatesSkylightDown(state);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return usesHiddenFormedModel(state) ? 0 : super.getLightDampening(state);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return usesHiddenFormedModel(state) ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            TianshuMultiblockUpdateScheduler.scheduleNear(level, pos);
            scheduleMatrixUpdate(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        TianshuMultiblockUpdateScheduler.scheduleNear(level, pos);
        scheduleMatrixUpdate(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level,
            net.minecraft.world.level.ScheduledTickAccess tickAccess, BlockPos pos,
            net.minecraft.core.Direction direction, BlockPos neighborPos, BlockState neighborState,
            net.minecraft.util.RandomSource random) {
        if (level instanceof Level world) {
            if (matrixComponent != MatrixMultiblockComponent.OTHER) {
                MatrixMultiblockUpdateScheduler.scheduleNear(world, neighborPos);
            }
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    private void scheduleMatrixUpdate(Level level, BlockPos pos) {
        if (matrixComponent != MatrixMultiblockComponent.OTHER) {
            MatrixMultiblockUpdateScheduler.scheduleNear(level, pos);
        }
    }

    private boolean usesHiddenFormedModel(BlockState state) {
        if (!state.getValue(FORMED)) {
            return false;
        }
        return switch (component) {
            case MAIN_BASELINE,
                    MAIN_QUANTUM,
                    MAIN_OVERLOAD,
                    MAIN_MULTIDIMENSIONAL,
                    BLANK_UNIT,
                    STORAGE_UNIT,
                    PARALLEL_UNIT,
                    AMPLIFIER_UNIT -> true;
            default -> false;
        };
    }
}
