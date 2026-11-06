package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.client.provider.OverloadedPatternProviderScreen;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.mixin.client.UpgradesPanelAccessor;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Exercises the transformed AE2 and AppliedFlux screen constructors in a real client. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class ProviderPanelsClientProbe {
    private static int ticks;
    private static int renderedTicks;
    private static boolean opened;
    private static boolean done;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ae2lt.providerPanelsProbe") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 2400) throw new AssertionError("Client world did not become ready");
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            if (!opened) {
                var host = new OverloadedPatternProviderBlockEntity(BlockPos.ZERO,
                        ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());
                host.setLevel(mc.level);
                var menu = new OverloadedPatternProviderMenu(1, mc.player.getInventory(), host);
                mc.player.containerMenu = menu;
                var screen = new OverloadedPatternProviderScreen<>(menu, mc.player.getInventory(),
                        Component.literal("Provider panels"),
                        StyleManager.loadStyleDoc("/screens/overloaded_pattern_provider.json"));
                mc.setScreen(screen);
                opened = true;
                return;
            }
            if (!(mc.screen instanceof OverloadedPatternProviderScreen<?> screen)) {
                throw new AssertionError("Provider screen closed before verification");
            }
            if (++renderedTicks < 30) return;
            var widgetsField = AEBaseScreen.class.getDeclaredField("widgets");
            widgetsField.setAccessible(true);
            var panelsField = WidgetContainer.class.getDeclaredField("compositeWidgets");
            panelsField.setAccessible(true);
            var panels = (Map<?, ?>) panelsField.get(widgetsField.get(screen));
            if (!(panels.get("upgrades") instanceof UpgradesPanel panel)) {
                throw new AssertionError("Missing upgrades panel");
            }
            var slots = ((UpgradesPanelAccessor) (Object) panel).ae2lt$getSlots();
            var identities = Collections.newSetFromMap(new IdentityHashMap<Slot, Boolean>());
            identities.addAll(slots);
            if (identities.size() != slots.size()
                    || !identities.containsAll(screen.getMenu().getSlots(SlotSemantics.UPGRADE))) {
                throw new AssertionError("Shared panel duplicated or lost upgrade slots");
            }
            Files.writeString(mc.gameDirectory.toPath().resolve("provider-panels-result.txt"),
                    "PASS: provider screen rendered; unique upgrade slots=" + slots.size()
                            + "; appflux=" + ModList.get().isLoaded("appflux") + "\n");
            done = true;
            mc.stop();
        } catch (Throwable failure) {
            failure.printStackTrace();
            done = true;
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("provider-panels-result.txt"), "FAIL: " + failure);
            } catch (Exception ignored) {
            }
            mc.stop();
        }
    }
}
