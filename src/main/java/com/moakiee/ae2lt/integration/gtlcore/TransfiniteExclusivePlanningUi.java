package com.moakiee.ae2lt.integration.gtlcore;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.network.chat.Component;

import com.mojang.logging.LogUtils;

/**
 * Reflection-only LDLIB widgets so Transfinite can cycle the exclusive planning
 * lock without a GTCEu / LDLIB compile dependency.
 */
public final class TransfiniteExclusivePlanningUi {
    public static final int BUTTON_X = 4;
    public static final int BUTTON_Y = 83;
    public static final int BUTTON_WIDTH = 174;
    public static final int BUTTON_HEIGHT = 18;
    public static final String CYCLE_COMPONENT_ID = "ae2lt_cycle_algorithm";

    private static final Logger LOG = LogUtils.getLogger();
    private static final String PANEL_CLASS = "com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget";
    private static final String WIDGET_CLASS = "com.lowdragmc.lowdraglib.gui.widget.Widget";

    private TransfiniteExclusivePlanningUi() {
    }

    public static void attach(
            @Nullable Object mainPage,
            boolean remote,
            Consumer<List<Component>> algorithmText,
            Runnable cycleOnServer) {
        if (mainPage == null || algorithmText == null || cycleOnServer == null) {
            return;
        }
        try {
            Object screen = firstChild(mainPage);
            if (screen == null) {
                return;
            }
            Class<?> panelClass = Class.forName(PANEL_CLASS);
            Constructor<?> ctor = panelClass.getConstructor(int.class, int.class, Consumer.class);
            Object panel = ctor.newInstance(BUTTON_X, BUTTON_Y, algorithmText);
            Method textSupplier = panelClass.getMethod("textSupplier", Consumer.class);
            textSupplier.invoke(panel, remote ? null : algorithmText);
            Method setMaxWidth = panelClass.getMethod("setMaxWidthLimit", int.class);
            setMaxWidth.invoke(panel, BUTTON_WIDTH);
            Method clickHandler = panelClass.getMethod("clickHandler", BiConsumer.class);
            clickHandler.invoke(panel, (BiConsumer<String, Object>) (id, clickData) -> {
                if (CYCLE_COMPONENT_ID.equals(id) && !remote) {
                    cycleOnServer.run();
                }
            });
            addWidget(screen, panel);
        } catch (Throwable unavailable) {
            LOG.warn("Could not attach Transfinite exclusive-algorithm switch", unavailable);
        }
    }

    public static Component clickableAlgorithmLine(Component label) {
        try {
            Class<?> panelClass = Class.forName(PANEL_CLASS);
            Method withButton = panelClass.getMethod("withButton", Component.class, String.class);
            Object clickable = withButton.invoke(null, label, CYCLE_COMPONENT_ID);
            Method withHover = panelClass.getMethod(
                    "withHoverTextTranslate", Component.class, Component.class);
            Object hovered = withHover.invoke(
                    null,
                    clickable,
                    Component.translatable("ae2lt.gtl.gui.algorithm.tooltip"));
            return hovered instanceof Component component ? component : label;
        } catch (Throwable unavailable) {
            return label;
        }
    }

    @Nullable
    private static Object firstChild(Object group) throws Exception {
        List<?> widgets = widgetsOf(group);
        return widgets == null || widgets.isEmpty() ? null : widgets.get(0);
    }

    @Nullable
    private static List<?> widgetsOf(Object group) throws Exception {
        Class<?> type = group.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("widgets");
                field.setAccessible(true);
                Object value = field.get(group);
                return value instanceof List<?> list ? list : null;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static void addWidget(Object group, Object widget) throws Exception {
        Class<?> widgetClass = Class.forName(WIDGET_CLASS);
        Class<?> type = group.getClass();
        while (type != null) {
            try {
                Method addWidget = type.getMethod("addWidget", widgetClass);
                addWidget.invoke(group, widget);
                return;
            } catch (NoSuchMethodException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException("addWidget(Widget) on " + group.getClass().getName());
    }
}
