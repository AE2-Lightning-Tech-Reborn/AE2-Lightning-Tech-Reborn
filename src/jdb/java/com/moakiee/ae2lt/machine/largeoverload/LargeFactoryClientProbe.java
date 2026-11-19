package com.moakiee.ae2lt.machine.largeoverload;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.client.machine.LargeFactoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Disposable client QA: real menus, slot paging, server-validated buttons and resource rendering. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class LargeFactoryClientProbe {
    private static final BlockPos CONTROLLER = new BlockPos(4, 103, 0);
    private static final BlockPos HATCH = CONTROLLER.south(2);
    private static final BlockPos STANDALONE = CONTROLLER.east(6).below();
    private static int phase, ticks;
    private static boolean done;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private static volatile boolean retryPhase;
    private static int formationWaits;
    private static int patternWaits;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.largeFactoryClientProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks % 20 != 0 || pending) return;
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (ticks == 200) {
                capture("factory-client-loading.png");
                com.mojang.logging.LogUtils.getLogger().info("Large factory client waiting at {}", mc.screen);
            }
            return;
        }
        try {
            if (failure != null) throw new AssertionError(failure);
            if (retryPhase) { phase--; retryPhase = false; }
            if (phase == 3 && Boolean.getBoolean("ae2lt.largeFactoryPreviewOnly")) phase = 15;
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("LARGE FACTORY QA - disposable world");
                    mc.getWindow().setWindowed(1400, 1000); mc.options.guiScale().set(2); mc.resizeDisplay();
                    // Keep the automated capture window separate from interactive clients.
                    org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    mc.options.fov().set(50); mc.options.gamma().set(0.0);
                    mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn");
                    pending = true;
                    mc.reloadResourcePacks().whenComplete((unused, error) -> { if (error != null) failure = error; pending = false; });
                }
                case 1 -> server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, mc.getSingleplayerServer());
                    level.setDayTime(6000);
                    // Rebuild the disposable fixture through physical block changes:
                    // resetting only visual properties would keep a saved formation's ownership.
                    for (var cell : LargeFactoryStructure.cells())
                        level.setBlockAndUpdate(LargeFactoryStructure.worldPosition(CONTROLLER, cell.localPosition(), Direction.NORTH),
                                Blocks.AIR.defaultBlockState());
                    for (int x = 1; x <= 8; x++) for (int z = -2; z <= 5; z++)
                        level.setBlockAndUpdate(new BlockPos(x, 101, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int x = 9; x <= 11; x++) for (int z = -1; z <= 1; z++)
                        level.setBlockAndUpdate(new BlockPos(x, 101, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    level.setBlockAndUpdate(STANDALONE, LargeFactoryRegistration.block(LargeFactoryComponent.CONTROLLER).defaultBlockState());
                    for (var cell : LargeFactoryStructure.cells()) {
                        var part = switch (cell.role()) {
                            case CONTROLLER -> LargeFactoryComponent.CONTROLLER;
                            case FRAME -> LargeFactoryComponent.FRAME;
                            case CORE -> LargeFactoryComponent.CORE_T4;
                            case CASING, HATCH -> LargeFactoryComponent.CASING;
                            case AIR -> LargeFactoryComponent.AIR;
                        };
                        var pos = LargeFactoryStructure.worldPosition(CONTROLLER, cell.localPosition(), Direction.NORTH);
                        if (pos.equals(HATCH)) part = LargeFactoryComponent.EXPANDED_PATTERN_HATCH;
                        level.setBlockAndUpdate(pos, part == LargeFactoryComponent.AIR ? Blocks.AIR.defaultBlockState() : LargeFactoryRegistration.block(part).defaultBlockState());
                    }
                    level.setBlockAndUpdate(HATCH.south(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(CONTROLLER).inflate(12)).forEach(net.minecraft.world.entity.Entity::discard);
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(LargeFactoryRegistration.block(LargeFactoryComponent.CONTROLLER)));
                    player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(LargeFactoryRegistration.block(LargeFactoryComponent.CASING)));
                    player.teleportTo(level, 4.5, 103, -2.5, Set.of(), 0, 0);
                });
                case 2 -> server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    var hatch = (LargeFactoryHatchBlockEntity) level.getBlockEntity(HATCH);
                    if (!hatch.ready()) {
                        if (++formationWaits > 15) throw new AssertionError("QA factory not ready after bounded scan wait");
                        retryPhase = true; return;
                    }
                    if (hatch.passive()) hatch.togglePassive();
                    for (int i = 0; i < 144; i++) hatch.inventory().setItemDirect(i, PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(AEItemKey.of(Items.STONE), i + 1)), List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), (i + 1) * 2))));
                    var controller = (LargeFactoryControllerBlockEntity) level.getBlockEntity(CONTROLLER);
                    controller.receiveEnergy(50_000_000, false);
                    open(CONTROLLER);
                    // The drawn console lies in the lower front frame cell. Its
                    // real block interaction must resolve the owning controller.
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.closeContainer();
                    var console = CONTROLLER.below();
                    level.getBlockState(console).useWithoutItem(level, player,
                            new net.minecraft.world.phys.BlockHitResult(console.getCenter(), Direction.NORTH, console, false));
                });
                case 3 -> {
                    assertScreen();
                    if (((LargeFactoryMenu) mc.player.containerMenu).snapshot.operationsPerTick() != LargeFactoryOperationBudget.UNLIMITED)
                        throw new AssertionError("unlimited T4 status missing");
                    capture("factory-controller-scale2.png"); server(() -> open(HATCH));
                }
                case 4 -> {
                    assertScreen();
                    var menu = (LargeFactoryMenu) mc.player.containerMenu;
                    // Native pattern indexing is intentionally sliced across server ticks.
                    if (menu.snapshot.entryCount() != 144) {
                        if (++patternWaits > 20) throw new AssertionError("pattern indexing did not finish: " + menu.snapshot);
                        return;
                    }
                    assertPatternDisplay(menu, 0);
                    capture("factory-expanded-page1-scale2.png");
                    mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, LargeFactoryMenu.MODE);
                    ((LargeFactoryMenu) mc.player.containerMenu).setPage(1);
                    mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, LargeFactoryMenu.PAGE_NEXT);
                }
                case 5 -> {
                    var menu = (LargeFactoryMenu) mc.player.containerMenu;
                    if (!menu.snapshot.passive() || menu.page() != 1 || menu.snapshot.entryCount() != 144) throw new AssertionError("mode/page/status packets failed: " + menu.page() + " / " + menu.snapshot);
                    assertPatternDisplay(menu, 36);
                    capture("factory-expanded-page2-passive-scale2.png");
                    mc.options.guiScale().set(3); mc.resizeDisplay();
                }
                case 6 -> {
                    capture("factory-expanded-scale3.png");
                    mc.getWindow().setWindowed(1280, 720); mc.resizeDisplay();
                }
                case 7 -> {
                    capture("factory-expanded-720p-scale3.png");
                    mc.player.closeContainer(); mc.options.hideGui = true;
                    server(() -> {
                        // The external test power cell would obscure the factory's rear artwork.
                        mc.getSingleplayerServer().overworld().setBlockAndUpdate(HATCH.south(), Blocks.AIR.defaultBlockState());
                        showWorld(false, false, false);
                    });
                }
                case 8 -> {
                    verifyBakedModels();
                    capture("factory-front-idle-day.png");
                    server(() -> showWorld(false, true, false));
                }
                case 9 -> {
                    assertWorking(); capture("factory-front-active-day.png");
                    server(() -> showWorld(false, true, false));
                }
                case 10 -> {
                    assertWorking();
                    server(() -> showWorld(false, true, true));
                }
                case 11 -> {
                    assertWorking(); capture("factory-front-active-night.png");
                    server(() -> showWorld(true, true, true));
                }
                case 12 -> {
                    assertWorking(); capture("factory-rear-active-night.png");
                    server(() -> showWorld(true, true, false));
                }
                case 13 -> {
                    assertWorking(); capture("factory-rear-active-day.png");
                    mc.options.hideGui = false;
                    server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        level.setBlockAndUpdate(CONTROLLER.offset(-1, -1, 0), Blocks.AIR.defaultBlockState());
                        open(CONTROLLER);
                    });
                }
                case 14 -> {
                    if (((LargeFactoryMenu) mc.player.containerMenu).snapshot.formed()) throw new AssertionError("unformed UI stale");
                    capture("factory-controller-missing-frame-scale3.png");
                    mc.player.closeContainer(); mc.options.hideGui = true;
                    server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        level.setDayTime(6000);
                        camera(8.2, 103.2, -2.8, STANDALONE.getCenter());
                    });
                }
                case 15 -> {
                    capture("factory-controller-block.png");
                    mc.options.hideGui = false;
                    mc.options.guiScale().set(2); mc.resizeDisplay();
                    server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        for (var cell : LargeFactoryStructure.cells()) {
                            if (cell.role() != LargeFactoryStructure.Role.CONTROLLER)
                                level.setBlockAndUpdate(LargeFactoryStructure.worldPosition(STANDALONE,
                                        cell.localPosition(), Direction.NORTH), Blocks.AIR.defaultBlockState());
                        }
                        open(STANDALONE);
                    });
                }
                case 16 -> {
                    assertScreen();
                    var action = LargeFactoryScreen.class.getDeclaredMethod("send", int.class);
                    action.setAccessible(true); action.invoke(mc.screen, LargeFactoryMenu.PREVIEW);
                }
                case 17 -> {
                    if (!(mc.screen instanceof com.moakiee.ae2lt.client.machine.LargeFactoryPreviewScreen))
                        throw new AssertionError("controller preview did not open the shared 3D preview");
                    capture("factory-interactive-preview.png");
                    int left = (mc.getWindow().getGuiScaledWidth() - 300) / 2;
                    int top = (mc.getWindow().getGuiScaledHeight() - 184 - 24) / 2;
                    mc.screen.mouseClicked(left + 172, top + 22, 0);
                }
                case 18 -> {
                    if (!com.moakiee.ae2lt.client.machine.LargeFactoryProjection.active() || mc.screen != null)
                        throw new AssertionError("projection button did not enable world rendering and close the preview");
                    capture("factory-world-projection.png");
                    server(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.getInventory().clearContent(); player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(Items.DIAMOND));
                        ((LargeFactoryControllerBlockEntity) player.serverLevel().getBlockEntity(STANDALONE)).startBuild(player);
                    });
                }
                case 19 -> server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    int frames = 0, casings = 0;
                    for (var cell : LargeFactoryStructure.cells()) {
                        var state = level.getBlockState(LargeFactoryStructure.worldPosition(STANDALONE, cell.localPosition(), Direction.NORTH));
                        if (state.is(LargeFactoryRegistration.block(LargeFactoryComponent.FRAME))) frames++;
                        if (state.is(LargeFactoryRegistration.block(LargeFactoryComponent.CASING))) casings++;
                    }
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    if (frames != 12 || casings != 13 || !player.getMainHandItem().is(Items.DIAMOND)
                            || player.getMainHandItem().getCount() != 1)
                        throw new AssertionError("creative shell build changed held item or failed: " + frames + "/" + casings);
                    var wrong = LargeFactoryStructure.worldPosition(STANDALONE, new BlockPos(0, 1, 0), Direction.NORTH);
                    level.setBlockAndUpdate(wrong, Blocks.STONE.defaultBlockState());
                });
                case 20 -> {
                    capture("factory-projection-wrong-block.png");
                    if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                        EmiPreview.open();
                    } else {
                        var type = com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory.TYPE;
                        var field = Class.forName("com.moakiee.ae2lt.integration.jei.JeiBookmarkAccessImpl").getDeclaredField("runtime");
                        field.setAccessible(true);
                        var runtime = (mezz.jei.api.runtime.IJeiRuntime) field.get(null);
                        var recipes = runtime.getRecipeManager().createRecipeLookup(type).get()
                                .filter(r -> r.id().getPath().startsWith("large_overload_factory")).toList();
                        if (recipes.size() != 2) throw new AssertionError("JEI did not register both factory previews: " + recipes.size());
                        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(type), recipes, List.of());
                    }
                }
                case 21 -> {
                    String viewer = net.neoforged.fml.ModList.get().isLoaded("emi") ? "emi" : "jei";
                    capture("factory-" + viewer + "-structure-page.png");
                    Files.writeString(mc.gameDirectory.toPath().resolve("factory-client-result.txt"),
                            "PASS: controller shared interactive preview, world projection toggle and missing/wrong-block captures, creative shell build with no materials and preserved held item, two registered " + viewer + " factory previews and native recipe screen.\n");
                    done = true; mc.stop();
                }
            }
            phase++;
        } catch (Throwable error) {
            capture("factory-client-failure.png");
            error.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("factory-client-result.txt"), "FAIL at phase " + phase + ": " + error); } catch (Exception ignored) { }
            done = true; mc.stop();
        }
    }
    private static void assertScreen() {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof LargeFactoryScreen))
            throw new AssertionError("factory screen missing: " + mc.screen + " at " + mc.player.position());
    }
    /** Keep optional EMI method signatures out of the event subscriber's reflected methods. */
    private static final class EmiPreview {
        static void open() {
            var recipes = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes();
            long previews = recipes.stream().filter(r -> r.getId() != null
                    && r.getId().getNamespace().equals("ae2lt") && r.getId().getPath().startsWith("/large_overload_factory")).count();
            if (previews != 2) throw new AssertionError("EMI did not register both factory previews: " + previews);
            dev.emi.emi.api.EmiApi.displayRecipe(recipes.stream().filter(r -> r.getId() != null
                    && r.getId().toString().equals("ae2lt:/large_overload_factory")).findFirst().orElseThrow());
        }
    }
    private static void assertPatternDisplay(LargeFactoryMenu menu, int index) {
        var slot = (appeng.menu.slot.AppEngSlot) menu.slots.get(index);
        if (!slot.isActive() || !PatternDetailsHelper.isEncodedPattern(slot.getItem()) || !slot.getDisplayStack().is(Items.DIAMOND))
            throw new AssertionError("pattern slot must keep its encoded stack and display its output: " + index);
    }
    private static void assertWorking() {
        var state = Minecraft.getInstance().level.getBlockState(CONTROLLER);
        if (!state.getValue(LargeFactoryPartBlock.FORMED) || !state.getValue(LargeFactoryControllerBlock.WORKING))
            throw new AssertionError("working model state did not reach the client");
    }
    private static void showWorld(boolean rear, boolean working, boolean night) {
        var mc = Minecraft.getInstance();
        var level = mc.getSingleplayerServer().overworld();
        level.setDayTime(night ? 18000 : 6000);
        var controller = (LargeFactoryControllerBlockEntity) level.getBlockEntity(CONTROLLER);
        if (working) controller.completed();
        camera(0, 104.4, rear ? 8 : -5, new net.minecraft.world.phys.Vec3(4.5, 103.5, 1.5));
    }
    private static void camera(double x, double y, double z, net.minecraft.world.phys.Vec3 target) {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        double dx = target.x - x, dz = target.z - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - y - player.getEyeHeight(), Math.hypot(dx, dz)));
        player.teleportTo(player.serverLevel(), x, y, z, Set.of(), yaw, pitch);
    }
    private static void verifyBakedModels() throws java.io.IOException {
        var mc = Minecraft.getInstance();
        var results = new java.util.LinkedHashMap<String, Object>();
        for (var facing : Direction.Plane.HORIZONTAL) {
            var state = LargeFactoryRegistration.block(LargeFactoryComponent.CONTROLLER).defaultBlockState()
                    .setValue(LargeFactoryControllerBlock.FACING, facing);
            var model = mc.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
            results.put("controller_" + facing.getName(), verifyControllerFaces(model, state));
        }
        var stack = new net.minecraft.world.item.ItemStack(LargeFactoryRegistration.block(LargeFactoryComponent.CONTROLLER));
        results.put("controller_item", verifyControllerFaces(mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0), null));
        for (var facing : Direction.Plane.HORIZONTAL) for (boolean working : new boolean[]{false, true}) {
            var state = LargeFactoryRegistration.block(LargeFactoryComponent.CONTROLLER).defaultBlockState()
                    .setValue(LargeFactoryControllerBlock.FACING, facing)
                    .setValue(LargeFactoryPartBlock.FORMED, true)
                    .setValue(LargeFactoryControllerBlock.WORKING, working);
            var model = mc.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
            var quads = model.getQuads(state, null, net.minecraft.util.RandomSource.create(1),
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            if (quads.size() != (working ? 54 : 50))
                throw new AssertionError("unexpected factory geometry: " + quads.size());
            String atlas = "ae2lt:block/large_overload_factory/" + (working ? "big_factory_on" : "big_factory");
            double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (var quad : quads) {
                if (!quad.getSprite().contents().name().toString().equals(atlas))
                    throw new AssertionError("unexpected factory atlas: " + quad.getSprite().contents().name());
                var vertices = quad.getVertices();
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * (vertices.length / 4);
                    for (int axis = 0; axis < 3; axis++) {
                        double v = Float.intBitsToFloat(vertices[offset + axis]);
                        min[axis] = Math.min(min[axis], v); max[axis] = Math.max(max[axis], v);
                    }
                }
            }
            var a = LargeFactoryStructure.worldPosition(BlockPos.ZERO, BlockPos.ZERO, facing);
            var b = LargeFactoryStructure.worldPosition(BlockPos.ZERO, LargeFactoryStructure.MAX_CORNER, facing);
            for (int axis = 0; axis < 3; axis++) {
                var direction = Direction.Axis.values()[axis];
                if (Math.abs(min[axis] - Math.min(a.get(direction), b.get(direction))) > 0.0001
                        || Math.abs(max[axis] - Math.max(a.get(direction), b.get(direction)) - 1) > 0.0001)
                    throw new AssertionError("factory model origin/rotation mismatch: " + facing + " " + axis);
            }
            results.put(facing.getName() + (working ? "_on" : "_off"),
                    java.util.Map.of("quads", quads.size(), "atlas", atlas, "min", min, "max", max));
        }
        Files.writeString(mc.gameDirectory.toPath().resolve("factory-model-validation.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
    }
    private static List<String> verifyControllerFaces(net.minecraft.client.resources.model.BakedModel model,
            net.minecraft.world.level.block.state.BlockState state) {
        var quads = new java.util.ArrayList<>(model.getQuads(state, null, net.minecraft.util.RandomSource.create(1)));
        for (var side : Direction.values()) quads.addAll(model.getQuads(state, side, net.minecraft.util.RandomSource.create(1)));
        if (quads.size() != 6) throw new AssertionError("controller must have six complete cube faces: " + quads.size());
        var sprites = quads.stream().map(q -> q.getSprite().contents().name().toString()).toList();
        if (sprites.stream().anyMatch(s -> s.contains("missingno") || s.contains("overload_processing_factory"))
                || !sprites.contains("ae2lt:block/large_overload_factory/controller_front"))
            throw new AssertionError("controller texture did not resolve: " + sprites);
        return sprites;
    }
    private static void open(BlockPos pos) {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.teleportTo(player.serverLevel(), pos.getX() + .5, pos.getY(), pos.getZ() + (pos.equals(HATCH) ? 2.5 : -2.5), Set.of(), 0, 0);
        LargeFactoryMenu.open(player.serverLevel(), pos, player);
    }
    private static void server(Runnable action) {
        pending = true;
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable error) { failure = error; } finally { pending = false; }
        });
    }
    private static void capture(String name) {
        var mc = Minecraft.getInstance(); Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> { });
    }
}
