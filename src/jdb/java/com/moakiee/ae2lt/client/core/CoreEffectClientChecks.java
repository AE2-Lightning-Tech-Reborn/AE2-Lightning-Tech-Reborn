package com.moakiee.ae2lt.client.core;

import java.lang.reflect.Proxy;
import java.util.List;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.Vec3;

/** Native dispatcher identity, mesh construction and GPU shader checks, without loading a saved world. */
public final class CoreEffectClientChecks {
    public static void verify(Minecraft mc) {
        bindPreviewComponents();
        var dispatcher = mc.getBlockEntityRenderDispatcher();
        var pos = new BlockPos(37, 90, -23);
        for (Block block : List.of(ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get(),
                ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get(), ModBlocks.CRYSTAL_CATALYZER.get(),
                ModBlocks.PIGMEE_CRYSTAL_CATALYZER.get(), ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get(),
                ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get(), ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get())) {
            var be = ((EntityBlock) block).newBlockEntity(pos, block.defaultBlockState());
            var renderer = dispatcher.getRenderer(be);
            require(renderer != null, "missing renderer: " + block);
            var state = renderer.createRenderState();
            renderer.extractRenderState(be, state, 0.5F, Vec3.ZERO, null);
            require(dispatcher.getRenderer(state) == renderer, "render-state dispatch lost type: " + block);
            require(state.blockPos.equals(pos), "render-state position lost: " + block);
            require(state.lightCoords == LightCoordsUtil.FULL_BRIGHT, "preview light lost: " + block);

            // Detached entities are deliberately invisible. Supply effect parameters separately
            // to exercise the real dispatch -> submit -> native BufferBuilder path.
            if (state instanceof TianshuCoreEffectRenderer.State core) {
                core.visible = true;
                core.x = core.y = core.z = 0.5;
                core.stepPhase = 2.5;
                core.spinDegrees = 45;
                verifySubmission(mc, state, 1);
            } else if (state instanceof MatrixCoreEffectRenderer.State core) {
                core.visible = true;
                core.x = core.y = core.z = 0.5;
                core.palette = new CoreEffectPalette(.3F, .2F, .5F, .8F, .5F, 1F);
                core.animation = new CoreEffectAnimationState().sample(120, true,
                        new CoreEffectAnimationState.MotionProfile(8, 48, 36000, 22, 84, 36000, 1.2, 4.2));
                verifySubmission(mc, state, 2);
            }
        }
        for (var pipeline : List.of(CoreEffectShaders.tianshu(), CoreEffectShaders.matrixCore(),
                CoreEffectShaders.matrixGlow(), CoreEffectShaders.tianshuFallback(),
                CoreEffectShaders.matrixCoreFallback(), CoreEffectShaders.matrixGlowFallback())) {
            require(RenderSystem.getDevice().precompilePipeline(pipeline).isValid(),
                    "invalid core shader pipeline: " + pipeline.getLocation());
        }
    }

    private static void bindPreviewComponents() {
        // Item defaults are supplied by datapacks in 26.1 and are not bound at the title screen.
        var packs = net.minecraft.server.packs.repository.ServerPacksSource.createVanillaTrustedRepository();
        var config = new net.minecraft.server.WorldLoader.InitConfig(
                new net.minecraft.server.WorldLoader.PackConfig(
                        packs, net.minecraft.world.level.WorldDataConfiguration.DEFAULT, false, false),
                net.minecraft.commands.Commands.CommandSelection.DEDICATED,
                net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
        net.minecraft.server.WorldLoader.load(config,
                data -> new net.minecraft.server.WorldLoader.DataLoadOutput<>(null, data.datapackDimensions()),
                (resources, managers, registries, cookie) -> {
                    resources.close();
                    return true;
                }, Runnable::run, Runnable::run).join();
    }

    private static void verifySubmission(Minecraft mc, BlockEntityRenderState state, int expected) {
        int[] submissions = {0};
        var collector = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                    if (method.getName().equals("order")) return proxy;
                    require(method.getName().equals("submitCustomGeometry"), "unexpected core submission: " + method);
                    var type = (RenderType) args[1];
                    try (var bytes = new ByteBufferBuilder(262144)) {
                        var buffer = new BufferBuilder(bytes, type.mode(), type.format());
                        ((SubmitNodeCollector.CustomGeometryRenderer) args[2]).render(((PoseStack) args[0]).last(), buffer);
                        try (var mesh = buffer.buildOrThrow()) {
                            require(mesh.drawState().vertexCount() > 0, "empty core geometry");
                        }
                    }
                    submissions[0]++;
                    return null;
                });
        mc.getBlockEntityRenderDispatcher().submit(state, new PoseStack(), collector, new CameraRenderState());
        require(submissions[0] == expected, "core effect lost during native dispatch");
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
