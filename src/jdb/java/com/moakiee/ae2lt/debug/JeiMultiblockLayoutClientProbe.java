package com.moakiee.ae2lt.debug;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;
import com.moakiee.ae2lt.integration.jei.multiblock.InteractiveMultiblockWidget;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.InteractiveMultiblockPreview;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.RecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Verifies the transformed JEI layout, cache, widget and side-button geometry. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class JeiMultiblockLayoutClientProbe {
    private static int ticks;
    private static boolean done;
    private static InteractiveMultiblockWidget sceneWidget;
    private static boolean sceneChecked;
    private static Throwable sceneFailure;
    private static int cleanFrames;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done
                || !Boolean.getBoolean("ae2lt.multiblockLayoutProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null) return;
        if (++ticks < 100) return;
        try {
            if (ticks > 100) {
                if (!sceneChecked) return;
                if (sceneFailure != null) throw new AssertionError("LDLib scene probe", sceneFailure);
                if (cleanFrames < 2) return;
                var screenshotPath = Path.of("screenshots/multiblock-layout-verified.png");
                Files.createDirectories(screenshotPath.getParent());
                try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    screenshot.writeToFile(screenshotPath);
                }
                Files.writeString(Path.of("multiblock-layout-result.txt"),
                        "PASS: real JEI mixin; growth/shrink/minimum; cache reuse/invalidation; widget, transfer and bookmark geometry; LDLib nonzero projection, visible framebuffer pixels, rotation, zoom, pan, shell/layer world visibility.\n");
                done = true;
                mc.setScreen(null);
                mc.stop();
                return;
            }
            var gui = (RecipesGui) Internal.getJeiRuntime().getRecipesGui();
            gui.showTypes(List.of(MultiblockStructureCategory.TYPE));
            var logic = (RecipeGuiLogic) field(gui, "logic");
            var category = (MultiblockStructureCategory) logic.getSelectedRecipeCategory();
            var small = verify(logic, category, 210);
            require(logic.getVisibleRecipeLayoutsWithButtons(210, 10, null).get(0).recipeLayout()
                    == small.recipeLayout(), "unchanged dimensions must retain the cache");
            var tall = verify(logic, category, 410);
            require(tall.recipeLayout() != small.recipeLayout(), "height increase must replace the cache");
            require(small.recipeLayout().getRect().getHeight() == 184,
                    "existing layout bounds must remain frozen after category growth");
            var shrunk = verify(logic, category, 250);
            require(shrunk.recipeLayout() != tall.recipeLayout(), "height decrease must replace the cache");
            verify(logic, category, 100);
            // Let JEI rebuild from the actual screen's available area before rendering.
            gui.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 10, 10);
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("multiblock-layout-result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
            done = true;
            mc.setScreen(null);
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void render(net.minecraftforge.client.event.ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("ae2lt.multiblockLayoutProbe") || done
                || ticks < 100 || sceneWidget == null || !(event.getScreen() instanceof RecipesGui)) return;
        if (sceneChecked) {
            cleanFrames++;
            return;
        }
        var graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        try {
            var preview = (InteractiveMultiblockPreview) field(sceneWidget, "preview");
            var adapter = field(preview, "scene");
            var scene = (SceneWidget) field(adapter, "scene");
            var recipe = (MultiblockStructureRecipe) field(preview, "recipe");
            require(scene.getRenderer() != null, "LDLib renderer must initialize on the client");
            require(Float.isFinite(scene.getRange()) && scene.getRange() > 0,
                    "orthographic projection must have a positive range");
            double diameter = Math.sqrt(recipe.sizeX() * recipe.sizeX()
                    + recipe.sizeY() * recipe.sizeY() + recipe.sizeZ() * recipe.sizeZ());
            double aspect = ((int) field(preview, "viewWidth") - 2.0)
                    / ((int) field(preview, "viewHeight") - 2.0);
            double horizontalRange = 2 * scene.getRange() * scene.getZoom();
            require(horizontalRange >= diameter && horizontalRange / aspect >= diameter,
                    "default camera must fit the complete structure in both viewport dimensions");
            preview.drawWidget(graphics, 100, 80);
            graphics.flush();
            requireVisibleStructure((int) field(preview, "viewWidth"), (int) field(preview, "viewHeight"));
            float yaw = scene.getRotationPitch();
            float pitch = scene.getRotationYaw();
            require(preview.handleMouseDragged(100, 80, 0, 12, 7), "viewport rotation event");
            require(Math.abs(scene.getRotationPitch() - yaw - 12) < 0.001F, "LDLib azimuth mapping");
            require(Math.abs(scene.getRotationYaw() - pitch - 7) < 0.001F, "LDLib elevation mapping");
            float zoom = scene.getZoom();
            require(preview.handleMouseScrolled(100, 80, 0, 1), "viewport zoom event");
            require(scene.getZoom() < zoom, "scroll must zoom in");
            var center = new org.joml.Vector3f(scene.getCenter());
            require(preview.handleMouseDragged(100, 80, 2, 10, 5), "viewport pan event");
            require(center.distanceSquared(scene.getCenter()) > 0, "pan must move the camera center");
            require(preview.handleMouseClick(40, 18, 0, false), "hide-shell control");
            preview.drawWidget(graphics, 100, 80);
            int shells = 0;
            for (var cell : recipe.cells()) {
                if (cell.state().isAir()) continue;
                require(scene.getDummyWorld().getBlockState(cell.localPos()).isAir() == cell.shell(),
                        "hidden shells must be absent from the picking world");
                if (cell.shell()) shells++;
            }
            require(shells > 0, "probe recipe must exercise shell hiding");
            require(preview.handleMouseClick(40, 18, 0, false), "show-shell control");
            require(preview.handleMouseClick(58, 18, 0, false), "layer-mode control");
            preview.drawWidget(graphics, 100, 80);
            int layer = (int) field(preview, "layer");
            for (var cell : recipe.cells()) {
                if (cell.state().isAir()) continue;
                require(scene.getDummyWorld().getBlockState(cell.localPos()).isAir()
                                == (cell.localPos().getY() > layer),
                        "layer filtering must also update the picking world");
            }
        } catch (Throwable failure) {
            sceneFailure = failure;
        } finally {
            graphics.pose().popPose();
            sceneChecked = true;
        }
    }

    private static void requireVisibleStructure(int width, int height) {
        var mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int colored = 0;
        try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            // Inspect only the viewport interior, excluding its border, toolbar and material list.
            for (int y = (int) (34 * scale); y < (31 + height - 3) * scale; y++) {
                for (int x = (int) (5 * scale); x < (2 + width - 3) * scale; x++) {
                    int pixel = screenshot.getPixelRGBA(x, y);
                    if ((pixel & 255) > 48 || ((pixel >>> 8) & 255) > 48
                            || ((pixel >>> 16) & 255) > 48) colored++;
                }
            }
        }
        require(colored > 100, "viewport must contain visible structure pixels, found " + colored);
    }

    private static RecipeLayoutWithButtons<?> verify(RecipeGuiLogic logic,
            MultiblockStructureCategory category, int availableHeight) throws Exception {
        int expected = Math.max(MultiblockStructureCategory.HEIGHT, availableHeight - 28);
        var layouts = logic.getVisibleRecipeLayoutsWithButtons(availableHeight, 10, null);
        require(!layouts.isEmpty(), "structure recipes must be registered");
        require(category.getHeight() == expected, "category height: " + category.getHeight());
        for (var buttons : layouts) {
            var layout = buttons.recipeLayout();
            require(layout.getRect().getHeight() == expected, "recipe rectangle height");
            var widgets = (List<?>) field(layout, "allWidgets");
            var widget = (InteractiveMultiblockWidget) widgets.stream()
                    .filter(InteractiveMultiblockWidget.class::isInstance).findFirst().orElseThrow();
            if (sceneWidget == null) sceneWidget = widget;
            require(widget.getArea().height() == expected, "widget drawing/hit-test height");
            require(layout.getRecipeTransferButtonArea().getY() == expected - 14,
                    "transfer button must follow the lower corner");
            require(layout.getRecipeBookmarkButtonArea().getY() < layout.getRecipeTransferButtonArea().getY(),
                    "bookmark button must remain above transfer");
        }
        return layouts.get(0);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
