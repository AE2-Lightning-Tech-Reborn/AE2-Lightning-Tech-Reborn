package com.moakiee.ae2lt.api.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.runtime.model.*;
import com.moakiee.ae2lt.overload.runtime.pattern.*;
import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;

class Ae2LtExternalOverloadAccountingTest {
    private static java.lang.reflect.Field keyRegistry;
    private static Object previousRegistry;
    private final Object owner = new Object();
    private final UUID jobId = UUID.randomUUID();
    private final KeyCounter waiting = new KeyCounter();

    @BeforeAll static void bootstrap() throws Exception {
        if (LoadingModList.get() == null)
            LoadingModList.of(List.of(), List.of(), new net.minecraftforge.fml.loading.EarlyLoadingException("test", null, List.of()));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        keyRegistry = Class.forName("appeng.api.stacks.AEKeyTypesInternal").getDeclaredField("registry");
        keyRegistry.setAccessible(true);
        previousRegistry = keyRegistry.get(null);
        var name = new ResourceLocation("ae2lt", "external_overload_test_keys");
        var create = net.minecraftforge.registries.RegistryManager.class.getDeclaredMethod("createRegistry",
                ResourceLocation.class, net.minecraftforge.registries.RegistryBuilder.class);
        create.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registry = (net.minecraftforge.registries.IForgeRegistry<AEKeyType>) create.invoke(
                new net.minecraftforge.registries.RegistryManager("external-overload-test"), name,
                new net.minecraftforge.registries.RegistryBuilder<AEKeyType>().setName(name).disableSaving().disableSync());
        registry.register(AEItemKey.of(Items.PAPER).getType().getId(), AEItemKey.of(Items.PAPER).getType());
        keyRegistry.set(null, (java.util.function.Supplier<net.minecraftforge.registries.IForgeRegistry<AEKeyType>>) () -> registry);
    }

    @AfterAll static void restoreRegistry() throws Exception { keyRegistry.set(null, previousRegistry); }

    @AfterEach void clear() { Ae2LtCraftingIntegration.clearOverloadState(owner); }

    @Test void previewIsPureAndPartialDeliveryConsumesOnlyTheAcceptedPrefix() {
        var expected = namedKey("expected");
        register(expected, 7, jobId);
        var actual = namedKey("actual");
        BiFunction<GenericStack, Actionable, Long> receiver = (stack, mode) -> {
            assertEquals(expected, stack.what());
            return Math.min(2, stack.amount());
        };
        assertEquals(2, Ae2LtCraftingIntegration.claimOverloadOutput(owner, jobId, actual, 7,
                Actionable.SIMULATE, receiver));
        assertEquals(7, pending(expected));
        assertEquals(2, Ae2LtCraftingIntegration.claimOverloadOutput(owner, jobId, actual, 7,
                Actionable.MODULATE, receiver));
        assertEquals(5, pending(expected));
    }

    @Test void exactTemplatesAreExcludedFromStrictMatchingUntilBothLedgersCanRetire() {
        var key = namedKey("expected");
        register(key, 4, jobId);
        assertEquals(0, Ae2LtCraftingIntegration.strictOutputMatch(owner, key, 4, 4));
        assertEquals(3, Ae2LtCraftingIntegration.strictOutputMatch(owner, key, 7, 7));
        assertEquals(4, Ae2LtCraftingIntegration.strictOutputMatch(owner, namedKey("actual"), 4, 0),
                "unrelated third-party matching must be preserved");
    }

    @Test void wrongJobOrDifferentItemCannotReachTheReceiver() {
        var key = namedKey("expected");
        register(key, 4, jobId);
        BiFunction<GenericStack, Actionable, Long> receiver = (stack, mode) -> fail("unexpected output claim");
        assertEquals(0, Ae2LtCraftingIntegration.claimOverloadOutput(owner, UUID.randomUUID(), key, 4,
                Actionable.MODULATE, receiver));
        assertEquals(0, Ae2LtCraftingIntegration.claimOverloadOutput(owner, jobId, AEItemKey.of(Items.STONE), 4,
                Actionable.MODULATE, receiver));
        assertEquals(4, pending(key));
    }

    @Test void forgeCheckpointRoundTripKeepsJobOwnershipAndVariantClaims() {
        var expected = namedKey("expected");
        register(expected, 7, jobId);
        var saved = Ae2LtCraftingIntegration.saveOverloadState(owner, jobId);
        Ae2LtCraftingIntegration.clearOverloadState(owner);
        assertEquals(0, pending(expected));
        assertThrows(IllegalArgumentException.class, () -> Ae2LtCraftingIntegration.loadOverloadState(
                owner, UUID.randomUUID(), saved));
        assertEquals(0, pending(expected));
        Ae2LtCraftingIntegration.loadOverloadState(owner, jobId, saved);
        assertEquals(7, pending(expected));
        assertEquals(3, Ae2LtCraftingIntegration.claimOverloadOutput(owner, jobId, namedKey("actual"), 3,
                Actionable.MODULATE, (stack, mode) -> stack.amount()));
        assertEquals(4, pending(expected));
    }

    @Test void callbackReentryCannotClaimTheCommittedOutputAgain() {
        var key = namedKey("expected");
        register(key, 4, jobId);
        assertEquals(4, Ae2LtCraftingIntegration.claimOverloadOutput(owner, jobId, key, 4,
                Actionable.MODULATE, (stack, mode) -> {
                    if (mode == Actionable.MODULATE) {
                        assertEquals(0, pending(key));
                        assertEquals(0, Ae2LtCraftingIntegration.claimOverloadOutput(owner, jobId, key, 4,
                                Actionable.MODULATE, (nested, action) -> fail("duplicate claim")));
                    }
                    return stack.amount();
                }));
    }

    @Test void rejectedOutputKeepsItsEntireRegistration() {
        var key = namedKey("expected");
        register(key, 4, jobId);
        assertEquals(0, Ae2LtCraftingIntegration.claimOverloadOutput(owner, jobId, key, 4,
                Actionable.MODULATE, (stack, mode) -> 0L));
        assertEquals(4, pending(key));
        assertThrows(IllegalStateException.class, () -> Ae2LtCraftingIntegration.claimOverloadOutput(
                owner, jobId, key, 4, Actionable.MODULATE, (stack, mode) -> stack.amount() + 1));
        assertEquals(4, pending(key));
    }

    @Test void replacingOwnerJobNeverCarriesOldPendingIntoTheNewJob() {
        var key = namedKey("expected");
        register(key, 4, jobId);
        var replacement = UUID.randomUUID();
        waiting.clear();
        register(key, 2, replacement);
        assertEquals(2, pending(key));
        assertEquals(replacement, OverloadCpuStateManager.INSTANCE.snapshotPending(owner).getFirst().key().craftingId());
        Ae2LtCraftingIntegration.clearOverloadState(owner);
        assertEquals(0, pending(key));
    }

    private void register(AEItemKey output, long copies, UUID id) {
        var registration = Ae2LtCraftingIntegration.prepareOverload(owner, pattern(output), id, null, waiting);
        assertTrue(registration.canDispatch());
        assertTrue(registration.registerAccepted(copies));
        waiting.add(output, copies);
    }

    private long pending(AEItemKey key) {
        return OverloadCpuStateManager.INSTANCE.getRemainingForItem(owner, key.getId());
    }

    private static IPatternDetails pattern(AEItemKey output) {
        var definition = AEItemKey.of(Items.PAPER);
        IPatternDetails source = new IPatternDetails() {
            public AEItemKey getDefinition() { return definition; }
            public IInput[] getInputs() { return new IInput[0]; }
            public GenericStack[] getOutputs() { return new GenericStack[]{new GenericStack(output, 1)}; }
        };
        var parsed = new ParsedPatternDefinition(new SourcePatternSnapshot(
                new ResourceLocation("test:external"), null, null), List.of(),
                List.of(new ParsedPatternOutput(0, output.toStack(), true)));
        return new Ae2OverloadPatternDetails(definition, new OverloadPatternDetails(parsed,
                EncodedOverloadPattern.builder().output(0, MatchMode.ID_ONLY).build()), source);
    }

    private static AEItemKey namedKey(String name) {
        var stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.setHoverName(Component.literal(name));
        return AEItemKey.of(stack);
    }
}
