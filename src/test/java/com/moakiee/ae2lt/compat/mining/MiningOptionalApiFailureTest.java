package com.moakiee.ae2lt.compat.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class MiningOptionalApiFailureTest {
    @Test
    void absentSilentGearApiDoesNotPoisonStaticInitialization() throws Exception {
        assertRepeatableOrdinaryFailure(SilentGearMiningTool.class);
    }

    @Test
    void absentBloodMagicApiDoesNotPoisonStaticInitialization() throws Exception {
        assertRepeatableOrdinaryFailure(BloodMagicMiningTool.class);
    }

    private static void assertRepeatableOrdinaryFailure(Class<?> integration) throws Exception {
        // Unit runtime deliberately lacks these optional jars. Do not invent addon API types.
        var access = Class.forName(integration.getName() + "$Access", true, integration.getClassLoader());
        var ensure = access.getDeclaredMethod("ensureSupported");
        ensure.setAccessible(true);
        for (int i = 0; i < 2; i++) {
            var thrown = assertThrows(InvocationTargetException.class, () -> ensure.invoke(null));
            assertInstanceOf(IllegalStateException.class, thrown.getCause());
            assertInstanceOf(ClassNotFoundException.class, thrown.getCause().getCause());
        }
    }
}
