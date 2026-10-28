package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import com.moakiee.ae2lt.mixin.GtoHandshakePendingMessagesMixin;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class GtoHandshakePendingMessagesTest {
    @Test
    void realForgeAcknowledgementReproducesConcurrentArrayListFailure() throws Exception {
        assertInstanceOf(ConcurrentModificationException.class,
                concurrentReply(new ArrayList<>(List.of(1)), false));
    }

    @Test
    void protectedRealForgeAcknowledgementKeepsTheConcurrentlySentIndex() throws Exception {
        var pending = protect(new ArrayList<>(List.of(1)));
        assertNull(concurrentReply(pending, true));
        assertEquals(List.of(2), pending, "only the acknowledged index may be removed");
        forgeCopy(pending).acknowledge(() -> 2);
        assertTrue(pending.isEmpty(), "the next acknowledgement must still complete negotiation");
    }

    @Test
    void duplicateAndOutOfOrderAcknowledgementsKeepOtherMessagesPending() throws Exception {
        var pending = protect(new ArrayList<>(List.of(3, 5)));
        var forge = forgeCopy(pending);
        forge.acknowledge(() -> 5);
        forge.acknowledge(() -> 5);
        pending.add(6);
        assertEquals(List.of(3, 6), pending);
        forge.acknowledge(() -> 3);
        assertEquals(List.of(6), pending);
        forge.acknowledge(() -> 6);
        assertTrue(pending.isEmpty());
    }

    private static Throwable concurrentReply(List<Integer> pending, boolean protectedList) throws Exception {
        var forge = forgeCopy(pending);
        var inPredicate = new CountDownLatch(1);
        var releasePredicate = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var calls = new AtomicInteger();
        var acknowledgement = new Thread(() -> {
            try {
                forge.acknowledge(() -> {
                    // Forge first asks for the index for its log, then in removeIf.
                    if (calls.incrementAndGet() == 2) {
                        inPredicate.countDown();
                        await(releasePredicate);
                    }
                    return 1;
                });
            } catch (Throwable thrown) { failure.set(thrown); }
        }, "handshake-acknowledgement");
        var sender = new Thread(() -> pending.add(2), "handshake-next-message");
        acknowledgement.start();
        try {
            assertTrue(inPredicate.await(5, TimeUnit.SECONDS));
            sender.start();
            if (protectedList) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (sender.isAlive() && sender.getState() != Thread.State.BLOCKED
                        && System.nanoTime() < deadline) Thread.onSpinWait();
                assertEquals(Thread.State.BLOCKED, sender.getState(),
                        "concurrent add must wait until the real Forge removeIf finishes");
            } else {
                sender.join(5000);
                assertFalse(sender.isAlive(), "unprotected send must overlap the acknowledgement");
            }
        } finally {
            releasePredicate.countDown();
            acknowledgement.join(5000);
            sender.join(5000);
        }
        assertFalse(acknowledgement.isAlive());
        assertFalse(sender.isAlive());
        return failure.get();
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }

    private static final class MixinProbe extends GtoHandshakePendingMessagesMixin { }

    @SuppressWarnings("unchecked")
    private static List<Integer> protect(List<Integer> pending) throws Exception {
        var probe = new MixinProbe();
        var field = GtoHandshakePendingMessagesMixin.class.getDeclaredField("sentMessages");
        field.setAccessible(true);
        field.set(probe, pending);
        var hook = GtoHandshakePendingMessagesMixin.class
                .getDeclaredMethod("ae2lt$protectPendingLoginMessages", CallbackInfo.class);
        hook.setAccessible(true);
        hook.invoke(probe, new CallbackInfo("<init>", false));
        return (List<Integer>) field.get(probe);
    }

    private record ForgeCopy(Object instance, Method handle) {
        void acknowledge(IntSupplier index) {
            try { handle.invoke(instance, index, null); }
            catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new AssertionError(failure.getCause());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }

    private static ForgeCopy forgeCopy(List<Integer> pending) throws Exception {
        // Execute Forge's actual reply method and lambda unchanged, without starting
        // its unrelated registries, channels or full login constructor in JUnit.
        var original = new ClassNode();
        try (var input = GtoHandshakePendingMessagesTest.class.getClassLoader()
                .getResourceAsStream("net/minecraftforge/network/HandshakeHandler.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(original, 0);
        }
        var copy = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        copy.visit(original.version, Opcodes.ACC_PUBLIC, original.name, null, "java/lang/Object", null);
        for (var field : original.fields) {
            if (field.name.equals("sentMessages") || field.name.equals("LOGGER")
                    || field.name.equals("FMLHSMARKER")) field.accept(copy);
        }
        for (var method : original.methods) {
            if (method.name.equals("handleIndexedMessage") || method.name.startsWith("lambda$handleIndexedMessage$")) {
                method.access = (method.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                method.accept(copy);
            }
        }
        var init = copy.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/util/List;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitFieldInsn(Opcodes.PUTFIELD, original.name, "sentMessages", "Ljava/util/List;");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        var clinit = copy.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitLdcInsn("ForgeHandshakeRegression");
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, "org/apache/logging/log4j/LogManager", "getLogger",
                "(Ljava/lang/String;)Lorg/apache/logging/log4j/Logger;", false);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, original.name, "LOGGER", "Lorg/apache/logging/log4j/Logger;");
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();
        copy.visitEnd();
        byte[] bytes = copy.toByteArray();
        var type = new ClassLoader(GtoHandshakePendingMessagesTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        return new ForgeCopy(type.getConstructor(List.class).newInstance(pending),
                type.getMethod("handleIndexedMessage", IntSupplier.class, Supplier.class));
    }
}
