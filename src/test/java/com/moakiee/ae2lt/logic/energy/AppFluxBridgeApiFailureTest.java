package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class AppFluxBridgeApiFailureTest {
    private static final String BRIDGE = "com.moakiee.ae2lt.logic.energy.AppFluxBridge";
    private static final String ACCESS = "com.moakiee.ae2lt.logic.energy.AppFluxAccess";
    private static final String MARKER = "com.glodblock.github.appflux.api.IFluxCell";

    @Test
    void absentOptionalModLeavesSafeDefaults() throws Exception {
        assertDisabled(new IsolatedLoader(false, null));
    }

    @Test
    void markerInterfaceAloneCannotEnableAnUnavailableBridge() throws Exception {
        assertDisabled(new IsolatedLoader(true, null));
    }

    @Test
    void brokenStaticInitializerDoesNotPoisonBridgeClassLoading() throws Exception {
        assertDisabled(new IsolatedLoader(true, accessBytes(true)));
    }

    @Test
    void missingRateFieldCannotPublishPartialAvailability() throws Exception {
        assertDisabled(new IsolatedLoader(true, accessBytes(false)));
    }

    private static void assertDisabled(ClassLoader loader) throws Exception {
        var bridge = Class.forName(BRIDGE, true, loader);
        assertNull(bridge.getField("FE_KEY").get(null));
        assertEquals(0L, bridge.getField("TRANSFER_RATE").getLong(null));
        for (int i = 0; i < 2; i++) {
            assertEquals(false, bridge.getMethod("isAvailable").invoke(null));
            assertEquals(false, bridge.getMethod("canUseEnergyHandler").invoke(null));
            assertEquals(0L, bridge.getMethod("simulateTarget", TargetAccess.class, long.class).invoke(null, null, 10L));
        }
    }

    private static byte[] accessBytes(boolean brokenInitializer) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_FINAL, ACCESS.replace('.', '/'), null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_STATIC, "FE_KEY", "Lappeng/api/stacks/AEKey;", null, null).visitEnd();
        if (brokenInitializer) {
            writer.visitField(Opcodes.ACC_STATIC, "TRANSFER_RATE", "J", null, null).visitEnd();
            var init = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            init.visitCode();
            init.visitTypeInsn(Opcodes.NEW, "java/lang/NoSuchMethodError");
            init.visitInsn(Opcodes.DUP);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/NoSuchMethodError", "<init>", "()V", false);
            init.visitInsn(Opcodes.ATHROW);
            init.visitMaxs(2, 0);
            init.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class IsolatedLoader extends ClassLoader {
        private final boolean marker;
        private final byte[] access;

        IsolatedLoader(boolean marker, byte[] access) {
            super(AppFluxBridgeApiFailureTest.class.getClassLoader());
            this.marker = marker;
            this.access = access;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(BRIDGE) && !name.equals(ACCESS) && !name.equals(MARKER)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                var type = findLoadedClass(name);
                if (type == null) {
                    byte[] bytes;
                    if (name.equals(ACCESS)) {
                        if (access == null) throw new ClassNotFoundException(name);
                        bytes = access;
                    } else if (name.equals(MARKER)) {
                        if (!marker) throw new ClassNotFoundException(name);
                        var writer = new ClassWriter(0);
                        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                                MARKER.replace('.', '/'), null, "java/lang/Object", null);
                        writer.visitEnd();
                        bytes = writer.toByteArray();
                    } else {
                        try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (stream == null) throw new ClassNotFoundException(name);
                            bytes = stream.readAllBytes();
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                    type = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
