package com.moakiee.ae2lt.integration.eaep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.thunderbolt.core.crafting.plan.LoopCraftingPlan;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "ae2lt.eaepTestJar", matches = ".+")
class EaepActualJarContractTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void realPacketAndMenuCapabilityMatchTheOptionalBridge() throws Exception {
        try (var loader = loader()) {
            var menuType = loader.loadClass("com.extendedae_plus.api.crafting.IForceCraftStartSync");
            var packetType = loader.loadClass("com.extendedae_plus.network.crafting.ForceCraftStartFlagC2SPacket");
            var bridge = EaepForceCraftingBridge.resolve(menuType, packetType);
            assertEquals("extendedae_plus:force_craft_start_flag", bridge.channel().toString());
            assertEquals(true, packetType.getMethod("forceStart").invoke(bridge.packet(true)));
            assertEquals(false, packetType.getMethod("forceStart").invoke(bridge.packet(false)));
        }
    }

    @Test
    void realWrapperRetainsOriginalPlanAndDetachedManualMissingSnapshot() throws Exception {
        try (var loader = loader()) {
            var missing = new KeyCounter();
            var key = AEItemKey.of(Items.COBBLESTONE);
            missing.add(key, 3);
            var original = plan(missing);
            var wrapped = wrap(loader, original);
            assertFalse(wrapped.simulation());
            var data = EaepForcedCraftingPlanAccess.read(wrapped);
            assertNotNull(data);
            assertSame(original, data.original());
            assertEquals(3, data.manualMissing().get(key));
            data.manualMissing().add(key, 10);
            assertEquals(3, EaepForcedCraftingPlanAccess.read(wrapped).manualMissing().get(key));
            assertTrue(wrapped.missingItems().isEmpty());
        }
    }

    @Test
    void realWrapperPreservesLoopSeedsAndCpuAdmissionRestrictions() throws Exception {
        try (var loader = loader()) {
            var key = AEItemKey.of(Items.COBBLESTONE);
            var missing = new KeyCounter();
            missing.add(key, 3);
            var delegate = new CraftingPlan(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1),
                    8, true, false, new KeyCounter(), new KeyCounter(), missing, Map.of());
            var allowed = new LoopCraftingPlan(delegate, List.of(host -> true), Map.of(key, 3L),
                    Map.of(), List.of());
            var denied = new LoopCraftingPlan(delegate, List.of(host -> false), Map.of(key, 3L),
                    Map.of(), List.of());
            var wrapped = wrap(loader, allowed);
            var data = EaepForcedCraftingPlanAccess.read(wrapped);
            assertNotNull(data);
            assertSame(allowed, data.original());
            assertEquals(3L, ((LoopCraftingPlan) data.original()).totalReusableSeeds().get(key));
            assertEquals(3, data.manualMissing().get(key));
            var pool = new TimeWheelCraftingCpuPool(null, 1024, 0, 1, false);
            assertTrue(pool.canHandle(wrapped));
            assertFalse(pool.canHandle(wrap(loader, denied)));
        }
    }

    @Test
    void forcedWrapperCannotHideAnExactPreviewFromTheCpu() throws Exception {
        try (var loader = loader()) {
            var original = plan(new KeyCounter());
            ExactPlanReports.attach(original, new ExactPlanReport(BigInteger.ZERO, Map.of(), false));
            assertNull(EaepForcedCraftingPlanAccess.read(wrap(loader, original)));
        }
    }

    private static URLClassLoader loader() throws Exception {
        URL jar = Path.of(System.getProperty("ae2lt.eaepTestJar")).toUri().toURL();
        return new URLClassLoader(new URL[] {jar}, EaepActualJarContractTest.class.getClassLoader());
    }

    private static ICraftingPlan wrap(ClassLoader loader, ICraftingPlan original) throws Exception {
        return (ICraftingPlan) loader.loadClass("com.extendedae_plus.crafting.ForcedCraftingPlan")
                .getConstructor(ICraftingPlan.class).newInstance(original);
    }

    private static ICraftingPlan plan(KeyCounter missing) {
        return (ICraftingPlan) Proxy.newProxyInstance(ICraftingPlan.class.getClassLoader(),
                new Class<?>[] {ICraftingPlan.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "finalOutput" -> new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
                    case "bytes" -> 0L;
                    case "simulation" -> true;
                    case "multiplePaths" -> false;
                    case "missingItems" -> missing;
                    case "usedItems", "emittedItems" -> new KeyCounter();
                    case "patternTimes" -> Map.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
