package com.moakiee.ae2lt.menu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import appeng.menu.guisync.GuiSync;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InputTransferMenuSyncTest {
    @ParameterizedTest
    @ValueSource(strings = {"LightningSimulationChamberMenu", "LightningAssemblyChamberMenu",
            "OverloadProcessingFactoryMenu", "MiningFactoryMenu"})
    void guiFieldsHaveUniqueIdsIncludingInheritedFields(String name) throws Exception {
        Class<?> menu = Class.forName("com.moakiee.ae2lt.menu." + name, false, getClass().getClassLoader());
        assertTrue(InputTransferMenu.class.isAssignableFrom(menu));
        var fields = new HashMap<Integer, String>();
        for (Class<?> type = menu; type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                var annotation = field.getAnnotation(GuiSync.class);
                if (annotation != null) {
                    assertNull(fields.put((int) annotation.value(), field.getName()), "Duplicate GUI sync ID in " + name);
                }
            }
        }
        assertTrue(fields.containsValue("inputTransferRevision"));
    }
}
