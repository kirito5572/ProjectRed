package mrtjp.projectred.expansion.block;

import codechicken.lib.vec.Rotation;
import mrtjp.projectred.core.block.ProjectRedBlock;
import mrtjp.projectred.expansion.init.ExpansionBlocks;
import mrtjp.projectred.expansion.tile.ProjectBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import javax.annotation.Nullable;

public class ProjectBenchBlock extends ProjectRedBlock {

    public ProjectBenchBlock() {
        super(STONE_MACHINE_PROPERTIES);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProjectBenchBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return ExpansionBlocks.PROJECT_BENCH_BLOCK_ENTITY.get();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int r = Rotation.rotationTo(0, context.getHorizontalDirection().ordinal());
        return this.defaultBlockState()
                .setValue(ROTATION, r);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }
}
