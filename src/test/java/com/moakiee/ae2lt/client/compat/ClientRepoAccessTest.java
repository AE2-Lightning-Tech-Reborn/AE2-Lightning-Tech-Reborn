package com.moakiee.ae2lt.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ClientRepoAccessTest {
    private static final String ACCESS = "com.moakiee.ae2lt.client.compat.ClientRepoAccess";
    private static final String REPO = "appeng.menu.me.common.IClientRepo";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void bothBinaryApisKeepAllEntriesLiveAndForwardUpdates(boolean collectionApi) throws Exception {
        var loader = new RepoApiLoader(collectionApi);
        var api = loader.loadClass(REPO);
        var access = loader.loadClass(ACCESS);
        var bySerial = new LinkedHashMap<Long, GridInventoryEntry>();
        var oldSet = new LinkedHashSet<GridInventoryEntry>();
        Collection<GridInventoryEntry> actual = collectionApi ? bySerial.values() : oldSet;
        var first = new GridInventoryEntry(1, null, 10, 0, false);
        var second = new GridInventoryEntry(2, null, 20, 0, false);
        bySerial.put(1L, first);
        oldSet.add(first);
        var ingredientMatches = List.of(first);
        Set<AEKey> craftable = Set.of();
        var dirty = new AtomicInteger();
        var updates = new AtomicInteger();
        var delegate = Proxy.newProxyInstance(loader, new Class<?>[]{api}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getAllEntries" -> actual;
                case "getByIngredient" -> ingredientMatches;
                case "getCraftableKeys" -> craftable;
                case "getByKey" -> first;
                case "handleUpdate" -> {
                    assertEquals(updates.get() + 1, dirty.get(), "dirty hook runs before delegate update");
                    updates.incrementAndGet();
                    if ((boolean) args[0]) {
                        bySerial.clear();
                        oldSet.clear();
                    }
                    for (var entry : (List<GridInventoryEntry>) args[1]) {
                        bySerial.put(entry.getSerial(), entry);
                        oldSet.add(entry);
                    }
                    yield null;
                }
                default -> throw new AssertionError(method);
            };
        });

        assertSame(actual, access.getMethod("allEntries", api).invoke(null, delegate),
                "the direct getter returns the real live repository collection");
        var tracked = access.getMethod("trackUpdates", api, Runnable.class)
                .invoke(null, delegate, (Runnable) dirty::incrementAndGet);
        var getter = api.getMethod("getAllEntries");
        assertEquals(collectionApi ? Collection.class : Set.class, getter.getReturnType());
        var live = (Collection<GridInventoryEntry>) getter.invoke(tracked);
        assertEquals(List.of(first), List.copyOf(live));
        if (!collectionApi) assertSame(actual, live, "keep the original Set view in Dev23");

        var returns = Arrays.stream(tracked.getClass().getDeclaredMethods())
                .filter(method -> method.getName().equals("getAllEntries"))
                .map(java.lang.reflect.Method::getReturnType).toList();
        assertTrue(returns.contains(Set.class), "the old JVM descriptor is present");
        assertTrue(returns.contains(Collection.class), "the covariant JVM bridge is present");

        bySerial.put(2L, second);
        oldSet.add(second);
        assertEquals(List.of(first, second), List.copyOf(live), "additions remain visible");
        bySerial.remove(1L);
        oldSet.remove(first);
        assertEquals(List.of(second), List.copyOf(live), "removals remain visible");
        var iterator = live.iterator();
        assertSame(second, iterator.next());
        iterator.remove();
        assertTrue(actual.isEmpty(), "the iterator is backed by the actual repository");
        assertFalse(live.contains(first));

        api.getMethod("handleUpdate", boolean.class, List.class).invoke(tracked, true, List.of(first, second));
        assertEquals(1, updates.get());
        assertEquals(List.of(first, second), List.copyOf(live), "full network updates do not leave a stale snapshot");
        assertSame(craftable, api.getMethod("getCraftableKeys").invoke(tracked));
        assertSame(first, api.getMethod("getByKey", AEKey.class).invoke(tracked, new Object[]{null}));
        assertSame(ingredientMatches, api.getMethod("getByIngredient", Ingredient.class)
                .invoke(tracked, new Object[]{null}));
    }

    /** Only getAllEntries' return descriptor changes between the two real supported APIs. */
    private static final class RepoApiLoader extends ClassLoader {
        private final boolean collectionApi;

        RepoApiLoader(boolean collectionApi) {
            super(ClientRepoAccessTest.class.getClassLoader());
            this.collectionApi = collectionApi;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(REPO) && !name.equals(ACCESS) && !name.startsWith(ACCESS + "$")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                var loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        if (name.equals(REPO)) bytes = adaptApi(bytes);
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        private byte[] adaptApi(byte[] bytes) {
            var writer = new ClassWriter(0);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.equals("getAllEntries")) {
                        descriptor = collectionApi ? "()Ljava/util/Collection;" : "()Ljava/util/Set;";
                        signature = null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            return writer.toByteArray();
        }
    }
}
