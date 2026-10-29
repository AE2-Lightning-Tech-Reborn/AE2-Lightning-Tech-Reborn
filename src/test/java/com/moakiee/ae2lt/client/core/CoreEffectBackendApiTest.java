package com.moakiee.ae2lt.client.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CoreEffectBackendApiTest {
    @Test
    void primitiveBooleanApiTracksActualShaderPackState() throws Exception {
        var detector = CoreEffectBackend.createReflectiveDetector(ValidApi.class);
        ValidApi.active = false;
        assertFalse(detector.isShaderPackInUse());
        ValidApi.active = true;
        assertTrue(detector.isShaderPackInUse());
    }

    @Test
    void rejectsWrongReturnTypeAndMissingSingletonBeforeUnsafeRendering() {
        assertThrows(NoSuchMethodException.class, () -> CoreEffectBackend.createReflectiveDetector(WrongApi.class));
        assertThrows(NoSuchMethodException.class, () -> CoreEffectBackend.createReflectiveDetector(InstanceFactoryApi.class));
        assertThrows(IllegalStateException.class, () -> CoreEffectBackend.createReflectiveDetector(NullApi.class));
    }

    @Test
    void runtimeQueryFailureLatchesShaderPackSafeFallback() throws Exception {
        var detector = CoreEffectBackend.createReflectiveDetector(FailingApi.class);
        assertTrue(detector.isShaderPackInUse());
        assertTrue(detector.isShaderPackInUse());
        assertEquals(1, FailingApi.calls);
    }

    public static class ValidApi {
        static boolean active;
        public static ValidApi getInstance() { return new ValidApi(); }
        public boolean isShaderPackInUse() { return active; }
    }
    public static class WrongApi {
        public static WrongApi getInstance() { return new WrongApi(); }
        public Object isShaderPackInUse() { return null; }
    }
    public static class InstanceFactoryApi {
        public InstanceFactoryApi getInstance() { return this; }
        public boolean isShaderPackInUse() { return false; }
    }
    public static class NullApi {
        public static NullApi getInstance() { return null; }
        public boolean isShaderPackInUse() { return false; }
    }
    public static class FailingApi {
        static int calls;
        public static FailingApi getInstance() { return new FailingApi(); }
        public boolean isShaderPackInUse() { calls++; throw new NoSuchMethodError("query API missing"); }
    }
}
