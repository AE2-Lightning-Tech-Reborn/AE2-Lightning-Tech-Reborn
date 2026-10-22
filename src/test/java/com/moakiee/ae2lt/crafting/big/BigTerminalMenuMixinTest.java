package com.moakiee.ae2lt.crafting.big;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.KeyCounter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes the production injection bytecode against side-aware menu/transport doubles.
 * This does not replace the opt-in real WCWT client probe.
 */
class BigTerminalMenuMixinTest {
    private static final String MIXIN = "com.moakiee.ae2lt.mixin.big.BigTerminalMenuMixin";
    private static final String MENU = "appeng.menu.me.common.MEStorageMenu";
    private static final String STOCK = "com.moakiee.ae2lt.crafting.big.BigTerminalStock";
    @TempDir Path temporaryDirectory;

    @Test
    void clientBroadcastNeverReadsServerInventoryOrSynchronizes() throws Exception {
        exercise(false, 0);
    }

    @Test
    void serverBroadcastStillForwardsTheAvailableSnapshot() throws Exception {
        exercise(true, 3);
    }

    private void exercise(boolean server, int expectedCalls) throws Exception {
        var menuSource = temporaryDirectory.resolve("MEStorageMenu.java");
        Files.writeString(menuSource, """
                package appeng.menu.me.common;
                import appeng.api.stacks.KeyCounter;
                public class MEStorageMenu extends com.moakiee.ae2lt.mixin.big.BigTerminalMenuMixin {
                    private final boolean server;
                    public int reads, syncs;
                    public final KeyCounter available = new KeyCounter();
                    public MEStorageMenu(boolean server) { this.server = server; }
                    public boolean isServerSide() { return server; }
                    public boolean isClientSide() { return !server; }
                    protected KeyCounter getPreviousAvailableStacks() {
                        reads++;
                        if (!server) throw new IllegalStateException("server-only getter called on client");
                        return available;
                    }
                }
                """);
        var stockSource = temporaryDirectory.resolve("BigTerminalStock.java");
        Files.writeString(stockSource, """
                package com.moakiee.ae2lt.crafting.big;
                import appeng.api.stacks.*;
                import appeng.menu.me.common.MEStorageMenu;
                import java.math.BigInteger;
                import java.util.Map;
                public final class BigTerminalStock {
                    public BigInteger get(AEKey key) { return null; }
                    public void apply(Map<AEKey, BigInteger> changes) {}
                    public void sync(MEStorageMenu menu, KeyCounter available) {
                        if (available != menu.available) throw new AssertionError("snapshot replaced");
                        menu.syncs++;
                    }
                }
                """);
        var parent = getClass().getClassLoader();
        var mixin = Class.forName(MIXIN, false, parent);
        String classpath = Path.of(mixin.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator
                + Path.of(KeyCounter.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-proc:none", "-classpath", classpath,
                "-d", temporaryDirectory.toString(), menuSource.toString(), stockSource.toString()));
        var loader = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!List.of(MIXIN, MENU, STOCK).contains(name)) return super.loadClass(name, resolve);
                var loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        byte[] bytes;
                        if (name.equals(MIXIN)) {
                            try (var input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                                if (input == null) throw new ClassNotFoundException(name);
                                bytes = input.readAllBytes();
                            }
                        } else {
                            bytes = Files.readAllBytes(temporaryDirectory.resolve(name.replace('.', '/') + ".class"));
                        }
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException error) {
                        throw new ClassNotFoundException(name, error);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        };
        var menuType = loader.loadClass(MENU);
        var menu = menuType.getConstructor(boolean.class).newInstance(server);
        var callback = loader.loadClass(MIXIN).getDeclaredMethod("ae2lt$syncBigStock",
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        callback.setAccessible(true);
        for (int i = 0; i < 3; i++) callback.invoke(menu, new Object[] {null});
        assertEquals(expectedCalls, menuType.getField("reads").get(menu));
        assertEquals(expectedCalls, menuType.getField("syncs").get(menu));
    }
}
