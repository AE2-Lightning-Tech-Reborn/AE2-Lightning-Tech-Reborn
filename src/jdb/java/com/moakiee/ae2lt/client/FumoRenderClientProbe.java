package com.moakiee.ae2lt.client;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.mixin.client.ItemStackRenderStateAccessor;
import com.moakiee.ae2lt.registry.ModFumos;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Native model-bake, mixin and submission checks, excluded from release jars.
 * Run with {@code ./gradlew runFumoRenderClient -Pae2ltFumoRenderClientProbe}.
 */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class FumoRenderClientProbe {
    private static boolean done;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.fumoRenderClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)
                || mc.getOverlay() != null) return;
        done = true;
        String result;
        try {
            bindPreviewComponents();
            verifyLightningTooltip(mc);
            var blocks = List.<Block>of(ModFumos.PIGMEE_FUMO.get(), ModFumos.CREATIVE_PIGMEE_FUMO.get(),
                    ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO.get(), ModFumos.RAINBOW_PIGMEE_FUMO.get());
            for (Block block : blocks) {
                verifyPlaced(mc, block);
                verifyItems(mc, block);
            }
            verifyCompositeLayers();
            verifySlabs(mc);
            var guiAtlas = mc.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.GUI);
            for (String name : List.of("electro_chime_crystal", "filter_component", "lightning_collapse_matrix", "mining_tool", "mining_block")) {
                var id = net.minecraft.resources.Identifier.parse("ae2lt:block/slot/" + name);
                require(guiAtlas.getSprite(id).contents().name().equals(id), "empty slot GUI sprite missing: " + id);
            }
            require(com.mojang.blaze3d.systems.RenderSystem.getDevice()
                    .precompilePipeline(RainbowPigmeeShader.PIPELINE).isValid(), "rainbow GPU shader compile failed");
            result = "PASS: 32 placed states (4 Pigmees x 4 facings x static/spinning): renderer dispatch, "
                    + "position, lighting, geometry submission, centered rotation and overlay alignment; "
                    + "16 item contexts, full head-spin revolution, special-layer alignment and composite isolation; "
                    + "both lightning tiers through AE2 storage-cell tooltip and world icon submission; 96 colored slab states, neighbor culling, rainbow GPU shader compilation and all five empty-slot GUI sprites.";
        } catch (Throwable failure) {
            failure.printStackTrace();
            result = "FAIL: " + failure;
        }
        System.out.println("FUMO_RENDER_PROBE " + result);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("fumo-render-result.txt"), result); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }

    private static void bindPreviewComponents() {
        // 26.1 item defaults are data-driven and still unbound at the title screen.
        // Load them just for this disposable probe, without opening a saved world.
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

    private static void verifyLightningTooltip(Minecraft mc) {
        var atlas = mc.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.ITEMS);
        for (String name : List.of("high_voltage_lightning", "extreme_high_voltage_lightning")) {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("ae2lt", "item/" + name);
            var sprite = atlas.getSprite(id);
            require(sprite.contents().name().equals(id), "missing lightning sprite " + id);
            require(sprite.atlasLocation().equals(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_ITEMS),
                    "lightning icon must use the item atlas texture");
        }
        var keys = List.of(com.moakiee.ae2lt.me.key.LightningKey.HIGH_VOLTAGE,
                com.moakiee.ae2lt.me.key.LightningKey.EXTREME_HIGH_VOLTAGE);
        var contents = keys.stream().map(key -> new appeng.api.stacks.GenericStack(key, 128)).toList();
        var tooltip = new appeng.client.render.StorageCellClientTooltipComponent(
                new appeng.items.storage.StorageCellTooltipComponent(List.of(), contents, false, true));
        var guiState = new net.minecraft.client.renderer.state.gui.GuiRenderState();
        var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(mc, guiState, 0, 0);
        // This is the exact AE2 entry point in the reported Invalid atlas id stack.
        tooltip.extractImage(mc.font, 20, 20, tooltip.getWidth(mc.font), tooltip.getHeight(mc.font), graphics);
        int[] images = {0};
        guiState.forEachElement(element -> images[0]++,
                net.minecraft.client.renderer.state.gui.GuiRenderState.TraverseRange.ALL);
        require(images[0] >= 2, "storage-cell tooltip did not submit both lightning icons");
        for (var key : keys) {
            var renderer = LightningKeyRenderHandler.INSTANCE;
            var state = renderer.createState();
            renderer.extract(state, key, null, LightCoordsUtil.FULL_BRIGHT);
            var submissions = new ArrayList<Submission>();
            renderer.submit(new PoseStack(), state, collector(submissions), LightCoordsUtil.FULL_BRIGHT);
            require(submissions.size() == 1, "missing lightning world icon");
        }
    }

    private static void verifyPlaced(Minecraft mc, Block block) {
        BlockPos pos = new BlockPos(37, 90, -23);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean spinning : new boolean[] {false, true}) {
                var blockState = block.defaultBlockState().setValue(FumoBlock.FACING, facing);
                var be = new FumoBlockEntity(pos, blockState);
                if (spinning) {
                    be.toggleSpinning();
                    for (int tick = 0; tick < 15; tick++) FumoBlockEntity.clientTick(null, pos, blockState, be);
                }
                FumoBlockRenderer renderer = (FumoBlockRenderer) mc.getBlockEntityRenderDispatcher()
                        .<FumoBlockEntity, FumoBlockRenderer.State>getRenderer(be);
                require(renderer != null, "missing Fumo renderer");
                var state = renderer.createRenderState();
                renderer.extractRenderState(be, state, 0.5F, Vec3.ZERO, null);
                require(mc.getBlockEntityRenderDispatcher()
                        .<FumoBlockEntity, FumoBlockRenderer.State>getRenderer(state) == renderer,
                        "render-state dispatch lost type");
                require(state.blockPos.equals(pos), "render-state position was not extracted");
                require(state.lightCoords == LightCoordsUtil.FULL_BRIGHT, "missing detached preview lighting");
                require(state.renderedBlockState.getValue(FumoBlock.FACING) == (spinning ? Direction.NORTH : facing),
                        "body and overlay must use the same baked facing");
                require(state.yRotation == (spinning ? 87.0F : 0.0F), "incorrect interpolated rotation");
                var submissions = new ArrayList<Submission>();
                renderer.submit(state, new PoseStack(), collector(submissions), new CameraRenderState());
                require(submissions.size() == (state.hyperdimensional || state.rainbow ? 2 : 1), "missing placed geometry");
                var bodyPose = submissions.getFirst().pose();
                near(bodyPose.transformPosition(new Vector3f(0.5F, 0, 0.5F)), new Vector3f(0.5F, 0, 0.5F));
                if (state.hyperdimensional || state.rainbow) {
                    var overlayPose = submissions.get(1).pose();
                    var expected = new Matrix4f(bodyPose);
                    if (!spinning) {
                        int degrees = switch (facing) {
                            case SOUTH -> 180;
                            case WEST -> 270;
                            case EAST -> 90;
                            default -> 0;
                        };
                        expected.translate(0.5F, 0.5F, 0.5F).rotateY((float) Math.toRadians(-degrees))
                                .translate(-0.5F, -0.5F, -0.5F);
                    }
                    require(expected.equals(overlayPose, 0.00001F), "portal and markings have different rotations");
                } else {
                    require(!state.model.isEmpty(), "empty placed block model");
                }
            }
        }
    }

    private static void verifyItems(Minecraft mc, Block block) {
        boolean hyperdimensional = block == ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO.get()
                || block == ModFumos.RAINBOW_PIGMEE_FUMO.get();
        for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.HEAD,
                ItemDisplayContext.GROUND, ItemDisplayContext.FIXED)) {
            for (int angle = 0; angle < (context == ItemDisplayContext.HEAD ? 360 : 1); angle += 6) {
                var state = new ItemStackRenderState();
                mc.getItemModelResolver().updateForTopItem(state, new ItemStack(block), context, null, null, 0);
                require(!state.isEmpty(), "empty item model");
                List<Vector3f> before = extents(state);
                require(!before.isEmpty(), "missing item extents");
                if (context == ItemDisplayContext.HEAD) {
                    SpinningFumoBakedModel.applyHeadSpin(state, 0, angle);
                    List<Vector3f> after = extents(state);
                    require(before.size() == after.size(), "head spin changed geometry");
                    for (int i = 0; i < before.size(); i++) {
                        near(after.get(i), new Vector3f(before.get(i)).rotateY((float) Math.toRadians(angle)));
                    }
                }
                var submissions = new ArrayList<Submission>();
                state.submit(new PoseStack(), collector(submissions), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                require(submissions.size() == (hyperdimensional ? 2 : 1), "extra opaque shell or missing item geometry");
                if (hyperdimensional) {
                    require(submissions.stream().allMatch(s -> s.method().equals("submitCustomGeometry")), "opaque shell drawn");
                    require(submissions.get(0).pose().equals(submissions.get(1).pose(), 0.00001F), "item layers not aligned");
                }
            }
        }
    }

    private static void verifySlabs(Minecraft mc) {
        var slabs = new ArrayList<Block>();
        com.moakiee.ae2lt.registry.ModBlocks.PIGMEE_BUILDING_SLABS.values().forEach(b -> slabs.add(b.get()));
        com.moakiee.ae2lt.registry.ModBlocks.PIGMEE_FRAMED_BUILDING_SLABS.values().forEach(b -> slabs.add(b.get()));
        for (var block : slabs) for (var type : net.minecraft.world.level.block.state.properties.SlabType.values()) {
            var state = block.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, type);
            var model = mc.getModelManager().getBlockStateModelSet().get(state);
            require(model instanceof com.moakiee.ae2lt.client.ctm.ConnectedSlabBakedModel, "slab CTM model missing: " + state);
            for (boolean neighbor : new boolean[] {false, true}) {
                var level = (net.minecraft.client.renderer.block.BlockAndTintGetter) Proxy.newProxyInstance(
                        net.minecraft.client.renderer.block.BlockAndTintGetter.class.getClassLoader(),
                        new Class<?>[] {net.minecraft.client.renderer.block.BlockAndTintGetter.class}, (proxy, method, args) -> {
                            if (method.getName().equals("getBlockState")) return neighbor && args[0].equals(BlockPos.ZERO.east())
                                    ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                            throw new AssertionError("unexpected model lookup: " + method);
                        });
                var parts = new ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
                ((com.moakiee.ae2lt.client.ctm.ConnectedSlabBakedModel) model).collectParts(level, BlockPos.ZERO, state,
                        net.minecraft.util.RandomSource.create(0), parts);
                require(!parts.isEmpty(), "missing slab parts");
                int quadCount = 0;
                var directions = new ArrayList<Direction>(List.of(Direction.values()));
                directions.add(null);
                for (var part : parts) for (var direction : directions) {
                    var quads = part.getQuads(direction);
                    if (neighbor && direction == Direction.EAST) require(quads.isEmpty(), "coplanar side not culled");
                    if (type != net.minecraft.world.level.block.state.properties.SlabType.DOUBLE && direction == null)
                        require(!quads.isEmpty(), "inset slab face incorrectly culled");
                    for (var quad : quads) {
                        quadCount++;
                        float min = type == net.minecraft.world.level.block.state.properties.SlabType.TOP ? 0.5F : 0;
                        float max = type == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM ? 0.5F : 1;
                        for (var point : List.of(quad.position0(), quad.position1(), quad.position2(), quad.position3()))
                            require(point.y() >= min - 0.002F && point.y() <= max + 0.002F, "slab exceeds half-height bounds");
                    }
                }
                require(quadCount > 0, "empty slab geometry");
            }
        }
    }

    private static void verifyCompositeLayers() {
        var state = new ItemStackRenderState();
        var first = state.newLayer();
        first.setExtents(() -> new Vector3f[] {new Vector3f(0.5F, 0.5F, 0.25F)});
        var second = state.newLayer();
        second.setExtents(() -> new Vector3f[] {new Vector3f(0.5F, 0.5F, 0.25F)});
        SpinningFumoBakedModel.applyHeadSpin(state, 1, 90);
        var points = extents(state);
        near(points.get(0), new Vector3f(0, 0, -0.25F));
        near(points.get(1), new Vector3f(-0.25F, 0, 0));
        require(state.isAnimated(), "spin must invalidate cached item rendering");
        require(((ItemStackRenderStateAccessor) state).ae2lt$activeLayerCount() == 2, "spin changed layer count");
    }

    private record Submission(String method, Matrix4f pose) {}

    private static SubmitNodeCollector collector(List<Submission> submissions) {
        return (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[] {SubmitNodeCollector.class}, (proxy, method, args) -> {
                    if (method.getName().equals("order")) return proxy;
                    if (method.getName().startsWith("submit") && args[0] instanceof PoseStack pose) {
                        submissions.add(new Submission(method.getName(), new Matrix4f(pose.last().pose())));
                        if (method.getName().equals("submitCustomGeometry")) {
                            var type = (net.minecraft.client.renderer.rendertype.RenderType) args[1];
                            try (var bytes = new com.mojang.blaze3d.vertex.ByteBufferBuilder(262144)) {
                                var buffer = new com.mojang.blaze3d.vertex.BufferBuilder(bytes, type.mode(), type.format());
                                ((SubmitNodeCollector.CustomGeometryRenderer) args[2]).render(pose.last(), buffer);
                                try (var mesh = buffer.buildOrThrow()) {
                                    require(mesh.drawState().vertexCount() > 0, "empty submitted mesh");
                                }
                            }
                        }
                        if (method.getName().equals("submitBlockModel")) {
                            require((int) args[4] == LightCoordsUtil.FULL_BRIGHT, "block lighting lost during submit");
                            require((int) args[5] == OverlayTexture.NO_OVERLAY, "incorrect damage overlay");
                            require(!((List<?>) args[2]).isEmpty(), "no submitted block parts");
                        }
                    } else throw new AssertionError("unexpected render call: " + method);
                    return null;
                });
    }

    private static List<Vector3f> extents(ItemStackRenderState state) {
        var points = new ArrayList<Vector3f>();
        state.visitExtents(point -> points.add(new Vector3f(point)));
        return points;
    }

    private static void near(Vector3f actual, Vector3f expected) {
        require(actual.distance(expected) < 0.0001F, "position " + actual + " expected " + expected);
    }

    private static void require(boolean passed, String detail) {
        if (!passed) throw new AssertionError(detail);
    }
}
