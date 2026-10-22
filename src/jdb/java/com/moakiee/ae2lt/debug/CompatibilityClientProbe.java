package com.moakiee.ae2lt.debug;

import dev.emi.emi.api.EmiApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Checks the actual JEI/EMI registry after its asynchronous reload; never shipped. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class CompatibilityClientProbe {
    private static int ticks;
    private static boolean done;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || !Boolean.getBoolean("ae2lt.compatibilityClientProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || ++ticks % 20 != 0) return;
        try {
            if (!(boolean) Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("isLoaded").invoke(null)) {
                if (ticks > 2400) throw new AssertionError("EMI reload timed out");
                return;
            }
            var manager = EmiApi.getRecipeManager();
            var ids = new HashMap<ResourceLocation, Integer>();
            for (var recipe : manager.getRecipes()) {
                var id = recipe.getId();
                if (id != null && "jei".equals(id.getNamespace()) && id.getPath().startsWith("/ae2lt/")) {
                    check(ids.merge(id, 1, Integer::sum) == 1, "Duplicate bridge ID: " + id);
                    check(manager.getRecipe(id) == recipe, "ID lookup lost a recipe: " + id);
                }
            }
            for (var name : List.of("pigmee_building_panels", "pigmee_framed_building_panels", "pigmee_building_slabs")) {
                for (var registry : List.of("item", "block")) {
                    var id = new ResourceLocation("jei", "/ae2lt/tag/minecraft/" + registry + "/" + name);
                    check(ids.getOrDefault(id, 0) == 1, "Missing distinct tag recipe: " + id);
                }
            }
            for (var name : List.of("overloaded_cable", "pigmee_building_materials", "pigmee_building_panels",
                    "pigmee_building_slabs", "pigmee_finished_building_panels", "pigmee_framed_building_panels")) {
                check(I18n.exists("tag.ae2lt." + name), "Missing tag translation: " + name);
            }
            finish(mc, "PASS: unique JEI/EMI tag IDs, six separate item/block lookups, six translated tags; IDs=" + ids);
        } catch (Throwable error) {
            error.printStackTrace();
            finish(mc, "FAIL: " + error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void finish(Minecraft mc, String result) {
        done = true;
        try {
            Files.writeString(Path.of("compatibility-client-result.txt"), result + "\n");
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
        mc.stop();
    }
}
