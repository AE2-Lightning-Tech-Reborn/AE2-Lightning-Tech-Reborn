package com.moakiee.ae2lt.client.ldlib;

import java.lang.reflect.Field;
import java.util.Map;

import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Access to LDLib 2.2's public view map, after its menu mixin has been applied. */
public final class LdlibSlotRegistry {
    private LdlibSlotRegistry() {
    }

    private static final class Access {
        private static final Field SLOT_VIEWS = findSlotViews();

        private static Field findSlotViews() {
            try {
                return AbstractContainerMenu.class.getField("ldlib2$itemSlotMap");
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("LDLib's public menu slot-view registry is unavailable", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<Slot, ItemSlot> views(AbstractContainerMenu menu) {
        try {
            return (Map<Slot, ItemSlot>) Access.SLOT_VIEWS.get(menu);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read LDLib's public menu slot-view registry", e);
        }
    }
}
