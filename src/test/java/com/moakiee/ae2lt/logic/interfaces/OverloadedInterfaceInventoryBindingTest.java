package com.moakiee.ae2lt.logic.interfaces;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogic;
import appeng.util.ConfigInventory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

class OverloadedInterfaceInventoryBindingTest {
    @Test
    void storageBindingCanReadTheProxyWithoutReenteringTheConfigGetter() throws Exception {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var types = Class.forName("appeng.api.stacks.AEKeyTypesInternal").getDeclaredField("allTypes");
        types.setAccessible(true);
        var previousTypes = types.get(null);
        types.set(null, Set.of(AEKeyType.items(), AEKeyType.fluids()));
        try {
            var grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(),
                    new Class<?>[] {IGrid.class}, (proxy, method, args) -> {
                        throw new AssertionError("Empty proxy must not query the network: " + method.getName());
                    });
            var node = (IManagedGridNode) Proxy.newProxyInstance(IManagedGridNode.class.getClassLoader(),
                    new Class<?>[] {IManagedGridNode.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getGrid")) return grid;
                        if (method.getReturnType() == IManagedGridNode.class) return proxy;
                        throw new AssertionError("Unexpected node operation: " + method.getName());
                    });
            var logic = new BindingLogic(node);
            logic.bindOnRead = true;
            var config = logic.getConfig();
            assertSame(config, logic.getProxiedStorage().cfg());

            // A decorator may replace config before wrapping storage. Internal
            // reads must follow that replacement rather than an original snapshot.
            var replacement = ConfigInventory.configStacks(1).build();
            var key = AEItemKey.of(Items.DIAMOND);
            replacement.setStack(0, new GenericStack(key, 8));
            var field = InterfaceLogic.class.getDeclaredField("config");
            field.setAccessible(true);
            field.set(logic, replacement);
            assertSame(replacement, logic.getProxiedStorage().cfg());
            assertSame(key, logic.getProxiedStorage().getKey(0));
        } finally {
            types.set(null, previousTypes);
        }
    }

    /** Models a getter injection that reads storage before its wrapper is assigned. */
    private static final class BindingLogic extends OverloadedInterfaceLogic {
        private boolean bindOnRead;
        private boolean binding;

        BindingLogic(IManagedGridNode node) {
            super(node, null, Items.STONE, 1);
        }

        @Override
        public ConfigInventory getConfig() {
            if (bindOnRead) {
                if (binding) throw new AssertionError("Inventory binding re-entered getConfig()");
                binding = true;
                try {
                    assertNull(getProxiedStorage().getStack(0));
                } finally {
                    binding = false;
                }
            }
            return super.getConfig();
        }
    }
}
