package com.moakiee.ae2lt.client.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moakiee.ae2lt.mixin.client.ProviderWidgetContainerMixin;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProviderPanelMergeTest {
    @BeforeAll
    static void bootstrap() {
        com.moakiee.ae2lt.testing.MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void compatibilityProfileUsesTheRequestedOptionalModClasspath() {
        var expected = System.getProperty("ae2lt.test.expectAppFlux");
        if (expected != null) {
            var appFluxClass = getClass().getClassLoader().getResource(
                    "com/glodblock/github/appflux/mixins/MixinPatternProviderScreen.class");
            assertEquals(Boolean.parseBoolean(expected), appFluxClass != null);
        }
    }

    @Test
    void repeatedRegistrationDoesNotDuplicateSlotsOrLoseOtherModsSlots() {
        var inventory = new AppEngInternalInventory(null, 3);
        Slot induction = new AppEngSlot(inventory, 0);
        Slot capacity = new AppEngSlot(inventory, 1);
        Slot extra = new AppEngSlot(inventory, 2);
        var first = new ArrayList<>(List.of(induction, capacity));
        var second = new ArrayList<>(List.of(capacity, extra, induction));

        var merged = ProviderPanelMerge.mergeSlots(first, second);

        assertEquals(List.of(induction, capacity, extra), merged);
        assertEquals(merged, ProviderPanelMerge.mergeSlots(merged, second));
        assertEquals(List.of(induction, capacity), first);
        assertEquals(List.of(capacity, extra, induction), second);
    }

    @Test
    void differentSlotObjectsRemainIndependentEvenAtTheSameInventoryIndex() {
        var first = new AppEngSlot(new AppEngInternalInventory(null, 1), 0);
        var second = new AppEngSlot(new AppEngInternalInventory(null, 1), 0);
        assertEquals(List.of(first, second), ProviderPanelMerge.mergeSlots(List.of(first), List.of(second)));
        assertEquals(List.of(first), ProviderPanelMerge.mergeSlots(List.of(), List.of(first)));
    }

    @Test
    void tooltipSuppliersRemainLazyAndKeepBothModsCurrentDescriptions() {
        var generation = new AtomicInteger();
        var calls = new AtomicInteger();
        var heading = Component.literal("Compatible Upgrades");
        var merged = ProviderPanelMerge.mergeTooltips(
                () -> { calls.incrementAndGet(); return List.of(heading, Component.literal("Induction " + generation.get())); },
                () -> { calls.incrementAndGet(); return List.of(heading.copy(), Component.literal("Capacity")); });

        assertEquals(0, calls.get());
        assertEquals(List.of(heading, Component.literal("Induction 0"), Component.literal("Capacity")), merged.get());
        generation.incrementAndGet();
        assertEquals(List.of(heading, Component.literal("Induction 1"), Component.literal("Capacity")), merged.get());
        assertEquals(4, calls.get());
    }

    @Test
    void unrelatedIdsAndWidgetTypesKeepAe2DuplicateChecks() {
        var panel = new UpgradesPanel(List.of());
        var other = new ICompositeWidget() {
            @Override public void setPosition(appeng.client.Point position) {}
            @Override public void setSize(int width, int height) {}
            @Override public net.minecraft.client.renderer.Rect2i getBounds() {
                return new net.minecraft.client.renderer.Rect2i(0, 0, 0, 0);
            }
        };
        assertNull(ProviderPanelMerge.merge("adapterCard", panel, panel));
        assertNull(ProviderPanelMerge.merge("machineRecognition", other, other));
        assertNull(ProviderPanelMerge.merge("upgrades", panel, other));
        assertNull(ProviderPanelMerge.merge("upgrades", other, panel));
        assertNull(ProviderPanelMerge.merge("toolbox", panel, panel));
        assertNull(ProviderPanelMerge.merge("upgrades", null, panel));
        assertSame(panel, ProviderPanelMerge.merge("upgrades", panel, panel));
    }

    @Test
    void duplicateHandlingRequiresAnExplicitlyOwnedContainer() throws ReflectiveOperationException {
        var hook = new ProviderWidgetContainerMixin() {};
        var panels = new LinkedHashMap<String, ICompositeWidget>();
        var panel = new UpgradesPanel(List.of());
        panels.put("upgrades", panel);
        var method = ProviderWidgetContainerMixin.class.getDeclaredMethod("ae2lt$mergeSharedProviderPanels",
                Map.class, Object.class, Object.class, Operation.class);
        method.setAccessible(true);
        Operation<Object> put = args -> panels.put((String) args[1], (ICompositeWidget) args[2]);

        // AE2 throws when put returns a previous widget. Other screens keep that result.
        assertSame(panel, method.invoke(hook, panels, "upgrades", panel, put));
        hook.ae2lt$enableSharedProviderPanels();
        assertNull(method.invoke(hook, panels, "upgrades", panel, put));
        assertSame(panel, panels.get("upgrades"));
        assertEquals(1, panels.size());
        panels.put("adapterCard", panel);
        assertSame(panel, method.invoke(hook, panels, "adapterCard", panel, put));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolboxReplacementKeepsSizingAndDoesNotOverrideCustomSubclasses() throws ReflectiveOperationException {
        var style = new ScreenStyle();
        var images = ScreenStyle.class.getDeclaredField("images");
        images.setAccessible(true);
        ((Map<String, Blitter>) images.get(style)).put("toolbox", Blitter.texture("guis/extra_panels.png", 128, 128));
        var first = new ToolboxPanel(style, Component.literal("First"));
        var incoming = new ToolboxPanel(style, Component.literal("Current"));
        incoming.setSize(59, 66);

        assertSame(incoming, ProviderPanelMerge.merge("toolbox", first, incoming));
        assertEquals(59, incoming.getBounds().getWidth());
        assertEquals(66, incoming.getBounds().getHeight());
        assertNull(ProviderPanelMerge.merge("toolbox", new ToolboxPanel(style, Component.literal("Custom")) {}, incoming));
    }
}
