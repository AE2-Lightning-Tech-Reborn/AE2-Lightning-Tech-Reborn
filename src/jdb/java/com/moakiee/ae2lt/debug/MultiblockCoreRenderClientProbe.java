package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.block.MatrixControllerBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockTemplate;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Actual render-loop regression, including controllers outside the camera frustum. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class MultiblockCoreRenderClientProbe {
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<BlockPos, Integer> RENDERS = new HashMap<>();
    private static final Map<BlockPos, Integer> GEOMETRY = new HashMap<>();
    private static final Map<BlockPos, Integer> FALLBACK = new HashMap<>();
    private static volatile Throwable serverFailure;
    private static volatile boolean built;
    private static volatile boolean ready;
    private static boolean initialized;
    private static boolean done;
    private static int ticks;
    private static int viewTick;
    private static int current = -1;
    private static int serverTicks;
    private static int configTicks;
    private static boolean configChangeChecked;
    private static boolean deformationRequested;
    private static int reloadStage;
    private static int reloadTicks;
    private static java.util.concurrent.CompletableFuture<Void> reload;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done
                || !Boolean.getBoolean("ae2lt.multiblockCoreRenderProbe")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        try {
            if (serverFailure != null) throw new AssertionError("Server fixture failed", serverFailure);
            if (++ticks > 1800) throw new AssertionError("Client fixture timed out");
            if (!initialized) {
                initialized = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.fov().set(30);
                instrument(mc);
                mc.getSingleplayerServer().execute(() -> {
                    try { build(mc); } catch (Throwable failure) { serverFailure = failure; }
                });
                return;
            }
            if (!ready || ticks < 100) return;
            if (current == FIXTURES.size()) {
                if (Boolean.getBoolean("ae2lt.coreReloadCheck") && !verifyReload(mc)) return;
                if (Boolean.getBoolean("ae2lt.coreShadersDisabled") && !configChangeChecked) {
                    var last = FIXTURES.get(FIXTURES.size() - 1);
                    if (configTicks++ == 0) {
                        var setting = AE2LTClientConfig.class.getDeclaredField("DISABLE_CORE_SHADER_RENDERING");
                        setting.setAccessible(true);
                        ((net.minecraftforge.common.ForgeConfigSpec.BooleanValue) setting.get(null)).set(false);
                        resetCounters(last.pos());
                        return;
                    }
                    if (configTicks < 40) return;
                    check(GEOMETRY.getOrDefault(last.pos(), 0) > 0,
                            "Enabling shaders before a resource reload must keep rendering the core");
                    check(FALLBACK.getOrDefault(last.pos(), 0) > 0,
                            "Unregistered custom shaders must keep using the vanilla fallback");
                    configChangeChecked = true;
                    System.out.println("CORE_SHADER_CONFIG_CHANGE geometry=" + GEOMETRY.getOrDefault(last.pos(), 0)
                            + " fallback=" + FALLBACK.getOrDefault(last.pos(), 0));
                }
                if (!deformationRequested) {
                    deformationRequested = true;
                    viewTick = 0;
                    deformLastPair(mc);
                    return;
                }
                if (++viewTick < 40) return;
                for (var f : FIXTURES.subList(FIXTURES.size() - 2, FIXTURES.size())) {
                    var be = mc.level.getBlockEntity(f.pos());
                    check(!be.getBlockState().getValue(f.matrix() ? MatrixControllerBlock.FORMED
                            : TianshuSupercomputerControllerBlock.FORMED), f + ": client must receive deformation");
                    check(be.getRenderBoundingBox().equals(new AABB(f.pos())),
                            f + ": deformed bounds must shrink to the controller");
                }
                finish(mc, FAILURES.isEmpty() ? "PASS: 8 formed machines, all four horizontal orientations; "
                        + "complete finite bounds, global section registration and actual offscreen-controller renders; "
                        + "both machines shrink their bounds after deformation; shader mode="
                        + (Boolean.getBoolean("ae2lt.coreShadersDisabled") ? "disabled at startup" : "native") + "."
                        : "FAIL: " + String.join("\n", FAILURES));
                return;
            }
            if (current < 0) {
                current = 0;
                lookAt(mc, FIXTURES.get(current));
                return;
            }
            if (++viewTick == 20) resetCounters(FIXTURES.get(current).pos());
            if (viewTick < 40) return;
            var f = FIXTURES.get(current);
            if (mc.level.getBlockEntity(f.pos()) == null) {
                if (viewTick < 200) return;
                throw new AssertionError("Client chunk did not load: " + f);
            }
            verifyBoundsAndRegistration(mc, f);
            var frustum = (Frustum) field(mc.levelRenderer, "cullingFrustum");
            check(!frustum.isVisible(new AABB(f.pos())), f + ": controller must be outside the view");
            check(frustum.isVisible(new AABB(f.center()).inflate(2)), f + ": effect must be in view");
            int renders = RENDERS.getOrDefault(f.pos(), 0);
            check(renders > 0, f + ": effect renderer was culled with its offscreen controller");
            check(GEOMETRY.getOrDefault(f.pos(), 0) > 0, f + ": renderer emitted no core geometry");
            if (Boolean.getBoolean("ae2lt.coreShadersDisabled"))
                check(FALLBACK.getOrDefault(f.pos(), 0) > 0, f + ": disabled shaders must use vanilla fallback");
            else
                check(FALLBACK.getOrDefault(f.pos(), 0) == 0, f + ": default profile must retain native shaders");
            System.out.println("CORE_RENDER_VIEW " + f + " frames=" + renders);
            System.out.println("CORE_SHADER_GEOMETRY " + f + " buffers=" + GEOMETRY.getOrDefault(f.pos(), 0)
                    + " fallback=" + FALLBACK.getOrDefault(f.pos(), 0));
            Screenshot.grab(mc.gameDirectory, f.name() + ".png", mc.getMainRenderTarget(), ignored -> {});
            if (++current < FIXTURES.size()) {
                viewTick = 0;
                lookAt(mc, FIXTURES.get(current));
            } else {
                viewTick = 0;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(mc, "FAIL: " + failure);
        }
    }

    private static boolean verifyReload(Minecraft mc) throws Exception {
        if (reloadStage >= 6) return true;
        var fixture = FIXTURES.get(FIXTURES.size() - 1);
        var setting = AE2LTClientConfig.class.getDeclaredField("DISABLE_CORE_SHADER_RENDERING");
        setting.setAccessible(true);
        var value = (net.minecraftforge.common.ForgeConfigSpec.BooleanValue) setting.get(null);
        if (reload == null) {
            value.set(reloadStage == 0 || reloadStage == 4);
            System.out.println("CORE_RELOAD_BEGIN stage=" + reloadStage + " disabled=" + value.get());
            reload = mc.reloadResourcePacks();
            return false;
        }
        if (!reload.isDone() || mc.getOverlay() != null) return false;
        reload.join();
        if (reloadTicks++ == 0) {
            instrument(mc);
            resetCounters(fixture.pos());
        }
        if (reloadTicks < 40) return false;
        boolean fallbackExpected = reloadStage == 0 || reloadStage == 4;
        check(GEOMETRY.getOrDefault(fixture.pos(), 0) > 0, "Resource reload lost core geometry at stage " + reloadStage);
        check((FALLBACK.getOrDefault(fixture.pos(), 0) > 0) == fallbackExpected,
                "Resource reload picked the wrong shader at stage " + reloadStage);
        System.out.println("CORE_RELOAD_PASS stage=" + reloadStage + " buffers="
                + GEOMETRY.getOrDefault(fixture.pos(), 0) + " fallback=" + FALLBACK.getOrDefault(fixture.pos(), 0));
        // Repeated native reloads must also refresh the shader supplied by the existing render types.
        reloadStage++;
        reloadTicks = 0;
        reload = null;
        return reloadStage >= 6;
    }

    private static void resetCounters(BlockPos pos) {
        RENDERS.put(pos, 0);
        GEOMETRY.put(pos, 0);
        FALLBACK.put(pos, 0);
    }

    private static void deformLastPair(Minecraft mc) {
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var level = player.serverLevel();
                for (var fixture : FIXTURES.subList(FIXTURES.size() - 2, FIXTURES.size())) {
                    var corner = fixture.matrix()
                            ? MatrixMultiblockScanner.worldPos(fixture.pos(), BlockPos.ZERO, fixture.facing())
                            : TianshuMultiblockScanner.worldPos(fixture.pos(), BlockPos.ZERO, fixture.facing());
                    level.setBlockAndUpdate(corner, Blocks.AIR.defaultBlockState());
                    if (level.getBlockEntity(fixture.pos()) instanceof MatrixControllerBlockEntity matrix)
                        matrix.scanAndForm(player);
                    else ((TianshuSupercomputerControllerBlockEntity) level.getBlockEntity(fixture.pos())).scanNow();
                }
            } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !built || ready || serverFailure != null
                || ++serverTicks < 10) return;
        try {
            var mc = Minecraft.getInstance();
            var level = mc.getSingleplayerServer().overworld();
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            // Forge runs BlockEntity.onLoad after placement; let real server ticks claim ownership first.
            for (var f : FIXTURES) {
                if (level.getBlockEntity(f.pos()) instanceof MatrixControllerBlockEntity matrix) {
                    matrix.scanAndForm(player);
                    if (!matrix.isFormed()) throw new AssertionError("Matrix did not form: " + f);
                } else if (level.getBlockEntity(f.pos()) instanceof TianshuSupercomputerControllerBlockEntity tianshu) {
                    tianshu.scanNow();
                    if (!tianshu.isFormed()) throw new AssertionError("Tianshu did not form: " + f + " " + tianshu.issueText());
                }
            }
            ready = true;
        } catch (Throwable failure) {
            serverFailure = failure;
        }
    }

    private static void verifyBoundsAndRegistration(Minecraft mc, Fixture f) throws Exception {
        var be = mc.level.getBlockEntity(f.pos());
        check(be.getBlockState().getValue(f.matrix() ? MatrixControllerBlock.FORMED
                : TianshuSupercomputerControllerBlock.FORMED), f + ": client formed state");
        var bounds = be.getRenderBoundingBox();
        structure: for (int x = 0; x < 7; x++) for (int y = 0; y < (f.matrix() ? 11 : 7); y++)
            for (int z = 0; z < 7; z++) {
                var local = new BlockPos(x, y, z);
                var world = f.matrix() ? MatrixMultiblockScanner.worldPos(f.pos(), local, f.facing())
                        : TianshuMultiblockScanner.worldPos(f.pos(), local, f.facing());
                if (!bounds.contains(Vec3.atCenterOf(world))) {
                    check(false, f + ": render bounds miss structure position " + world + ", bounds=" + bounds);
                    break structure;
                }
            }
        check(bounds.getXsize() == 7 && bounds.getYsize() == (f.matrix() ? 11 : 7)
                && bounds.getZsize() == 7, f + ": render bounds must stay finite and local");
        var globals = (Collection<?>) field(mc.levelRenderer, "globalBlockEntities");
        check(globals.contains(be), f + ": renderer must survive controller-section culling");
    }

    private static void lookAt(Minecraft mc, Fixture f) {
        var center = Vec3.atCenterOf(f.center());
        var delta = center.subtract(Vec3.atCenterOf(f.pos()));
        // Keep the matrix camera inside its opaque middle frame so terrain cannot mask the core.
        var offset = new Vec3(delta.z, 0, -delta.x).normalize().scale(f.matrix() ? 2.4 : 4);
        var camera = center.add(offset);
        float yaw = (float) Math.toDegrees(Math.atan2(offset.x, -offset.z));
        float pitch = (float) Math.toDegrees(Math.atan2(offset.y, offset.horizontalDistance()));
        resetCounters(f.pos());
        mc.setScreen(null);
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.teleportTo(player.serverLevel(), camera.x, camera.y - player.getEyeHeight(), camera.z,
                    java.util.Set.of(), yaw, pitch);
        });
    }

    @SuppressWarnings("unchecked")
    private static void instrument(Minecraft mc) throws Exception {
        var dispatcher = mc.getBlockEntityRenderDispatcher();
        var field = dispatcher.getClass().getDeclaredField("renderers");
        field.setAccessible(true);
        var renderers = new HashMap<>((Map<BlockEntityType<?>, BlockEntityRenderer<?>>) field.get(dispatcher));
        for (var type : List.of(ModBlockEntities.MATRIX_CONTROLLER.get(),
                ModBlockEntities.TIANSHU_SUPERCOMPUTER_CONTROLLER.get())) {
            var delegate = (BlockEntityRenderer<BlockEntity>) renderers.get(type);
            renderers.put(type, new BlockEntityRenderer<BlockEntity>() {
                @Override
                public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                   int light, int overlay) {
                    RENDERS.merge(be.getBlockPos(), 1, Integer::sum);
                    delegate.render(be, partialTick, pose, renderType -> {
                        GEOMETRY.merge(be.getBlockPos(), 1, Integer::sum);
                        if (renderType.toString().contains("_shader_pack_fallback"))
                            FALLBACK.merge(be.getBlockPos(), 1, Integer::sum);
                        return buffers.getBuffer(renderType);
                    }, light, overlay);
                }

                @Override
                public boolean shouldRenderOffScreen(BlockEntity be) {
                    return delegate.shouldRenderOffScreen(be);
                }

                @Override
                public boolean shouldRender(BlockEntity be, Vec3 camera) {
                    return delegate.shouldRender(be, camera);
                }
            });
        }
        field.set(dispatcher, renderers);
    }

    private static void build(Minecraft mc) {
        var level = mc.getSingleplayerServer().overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(0, 6000, false, false);
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.setGameMode(GameType.SPECTATOR);
        int index = 0;
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (boolean matrix : new boolean[] { true, false }) {
                // Put the Tianshu controller and its center in different vertical sections.
                var pos = new BlockPos(16 + (index % 4) * 24, matrix ? 104 : 111, 16 + (index / 4) * 24);
                var f = new Fixture(pos, facing, matrix);
                FIXTURES.add(f);
                if (matrix) buildMatrix(level, f); else buildTianshu(level, f);
                index++;
            }
        }
        built = true;
    }

    private static void buildMatrix(ServerLevel level, Fixture f) {
        boolean patternPlaced = false;
        for (var entry : MatrixMultiblockTemplate.entries()) {
            var local = entry.localPos();
            Block block = switch (entry.role()) {
                case CASING -> ModBlocks.MATTER_WARPING_MATRIX_CASING.get();
                case CONSTRAINT_FRAME -> ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get();
                case GLASS -> ModBlocks.MATTER_WARPING_MATRIX_GLASS.get();
                case CONTROLLER -> ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get();
                case PORT_CANDIDATE -> local.equals(new BlockPos(6, 5, 3))
                        ? ModBlocks.MATTER_WARPING_MATRIX_PORT.get() : ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get();
                case PATTERN_BAY -> {
                    if (patternPlaced) yield Blocks.AIR;
                    patternPlaced = true;
                    yield ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1.get();
                }
                case CRAFTING_BAY -> local.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)
                        ? ModBlocks.MATTER_WARPING_MATRIX_STABLE_MAIN_CORE.get()
                        : local.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL.offset(1, 0, 0))
                                ? ModBlocks.MATTER_WARPING_MATRIX_THREAD_UNIT_T1.get() : ModBlocks.TIANSHU_BLANK_UNIT.get();
                case EMPTY -> Blocks.AIR;
            };
            var state = block.defaultBlockState();
            if (block instanceof MatrixControllerBlock) state = state.setValue(MatrixControllerBlock.FACING, f.facing());
            level.setBlock(MatrixMultiblockScanner.worldPos(f.pos(), local, f.facing()), state, Block.UPDATE_CLIENTS);
        }
    }

    private static void buildTianshu(ServerLevel level, Fixture f) {
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 7; z++) {
            var local = new BlockPos(x, y, z);
            Block block = switch (TianshuMultiblockTemplate.roleAt(local)) {
                case CASING -> ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get();
                case COOLING -> ModBlocks.PHASE_CHANGE_COOLING_UNIT.get();
                case GLASS -> ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get();
                case CONTROLLER -> ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get();
                case PORT_CANDIDATE -> local.equals(TianshuMultiblockTemplate.UPPER_PORT)
                        ? ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get() : ModBlocks.PHASE_CHANGE_COOLING_UNIT.get();
                case CORE_RESERVED -> local.equals(new BlockPos(3, 3, 3))
                        ? ModBlocks.BASELINE_SUPERCOMPUTING_UNIT.get() : ModBlocks.TIANSHU_BLANK_UNIT.get();
                case IGNORED -> Blocks.AIR;
            };
            var state = block.defaultBlockState();
            if (block instanceof TianshuSupercomputerControllerBlock)
                state = state.setValue(TianshuSupercomputerControllerBlock.FACING, f.facing());
            level.setBlock(TianshuMultiblockScanner.worldPos(f.pos(), local, f.facing()), state, Block.UPDATE_CLIENTS);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            FAILURES.add(message);
            System.out.println("CORE_RENDER_CHECK_FAILED " + message);
        }
    }

    private static void finish(Minecraft mc, String result) {
        done = true;
        System.out.println("CORE_RENDER_RESULT " + result);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("multiblock-core-render-result.txt"), result + "\n"); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }

    private record Fixture(BlockPos pos, Direction facing, boolean matrix) {
        BlockPos center() {
            return matrix ? MatrixMultiblockScanner.worldPos(pos, MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL, facing)
                    : TianshuMultiblockScanner.worldPos(pos, new BlockPos(3, 3, 3), facing);
        }

        String name() { return (matrix ? "matrix-" : "tianshu-") + facing.getName(); }
    }
}
