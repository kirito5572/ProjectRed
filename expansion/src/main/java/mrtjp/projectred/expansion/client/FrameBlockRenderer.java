package mrtjp.projectred.expansion.client;

import codechicken.lib.model.PerspectiveModelState;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.block.ICCBlockRenderer;
import codechicken.lib.render.buffer.TransformingVertexConsumer;
import codechicken.lib.render.item.IItemRenderer;
import codechicken.lib.util.TransformUtils;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mrtjp.projectred.expansion.init.ExpansionBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class FrameBlockRenderer implements ICCBlockRenderer, IItemRenderer {

    public static final FrameBlockRenderer INSTANCE = new FrameBlockRenderer();

    public FrameBlockRenderer() {
    }

    //region ICCBlockRenderer
    @Override
    public boolean canHandleBlock(BlockAndTintGetter world, BlockPos pos, BlockState blockState, @Nullable RenderType renderType) {
        return blockState.getBlock() == ExpansionBlocks.FRAME_BLOCK.get();
    }

    @Override
    public void renderBlock(BlockState state, BlockPos pos, BlockAndTintGetter world, PoseStack mStack, VertexConsumer builder, RandomSource random, ModelData data, @Nullable RenderType renderType) {
        CCRenderState ccrs = CCRenderState.instance();
        ccrs.reset();
        ccrs.bind(new TransformingVertexConsumer(builder, mStack), DefaultVertexFormat.BLOCK);
        ccrs.lightMatrix.locate(world, pos);
        ccrs.setBrightness(world, pos);

        FrameModelRenderer.renderStatic(ccrs, 0);
    }
    //endregion

    //region IItemRenderer
    @Override
    public void renderItem(ItemStack stack, ItemDisplayContext transformType, PoseStack mStack, MultiBufferSource source, int packedLight, int packedOverlay) {
        CCRenderState ccrs = CCRenderState.instance();
        ccrs.reset();
        ccrs.brightness = packedLight;
        ccrs.overlay = packedOverlay;
        ccrs.bind(ItemBlockRenderTypes.getRenderType(stack, true), source, mStack);

        FrameModelRenderer.renderStatic(ccrs, 0);
    }



    //@formatter:off
    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public @Nullable PerspectiveModelState getModelState() { return TransformUtils.DEFAULT_BLOCK; }
    //@formatter:on
    //endregion
}
