package com.moakiee.ae2lt.machine.largeoverload;

import static org.junit.jupiter.api.Assertions.*;
import static com.moakiee.ae2lt.machine.largeoverload.LargeFactoryRecipeAccess.Process.*;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LargeFactoryRecipeAccessTest {
    @Test
    void ordinaryBaseTypesNeedNoProcessCore() {
        for (var core : new LargeFactoryComponent[]{LargeFactoryComponent.CORE_T1, LargeFactoryComponent.CORE_T2,
                LargeFactoryComponent.CORE_T3, LargeFactoryComponent.CORE_T4}) {
            var access = new LargeFactoryRecipeAccess(core, Set.of());
            assertTrue(access.allows(OVERLOAD));
            assertTrue(access.allows(REACTION));
            assertFalse(access.allows(CATALYZER));
            assertFalse(access.allows(FIRMAMENT));
        }
    }

    @Test
    void unlockingOneCrystalProcessDoesNotUnlockOtherProcessesFromTheSameMod() {
        var access = new LargeFactoryRecipeAccess(LargeFactoryComponent.CORE_T2, Set.of(CRYSTAL_AGGREGATOR));
        assertTrue(access.allows(CRYSTAL_AGGREGATOR.type()));
        assertFalse(access.allows(CRYSTAL_PULVERIZER.type()));
        assertFalse(access.allows(CIRCUIT_ETCHER.type()));
        assertFalse(access.allows(CRYSTAL_ASSEMBLER.type()));
        assertFalse(access.allows(new ResourceLocation("ae2cs:unknown_recipe")));
    }

    @Test
    void permissionUsesSourceTypeAndNeverModNamespaceOrOutputOwnership() {
        var access = new LargeFactoryRecipeAccess(LargeFactoryComponent.CORE_T1, Set.of(CRYSTAL_ASSEMBLER));
        assertTrue(access.allows(new ResourceLocation("extendedae:crystal_assembler")));
        assertTrue(access.allows(new ResourceLocation("advanced_ae:reaction")));
        assertFalse(access.allows(new ResourceLocation("extendedae:cutter")));
        assertFalse(access.allows(new ResourceLocation("neoecoae:integrated_working_station")));
    }

    @Test
    void firmamentCoreIgnoresEveryProcessUnlockIncludingOrdinaryBaseTypes() {
        var access = new LargeFactoryRecipeAccess(LargeFactoryComponent.FIRMAMENT_CORE,
                EnumSet.allOf(LargeFactoryRecipeAccess.Process.class));
        for (var process : LargeFactoryRecipeAccess.Process.values()) {
            assertEquals(process == FIRMAMENT, access.allows(process), process.name());
        }
        assertFalse(new LargeFactoryRecipeAccess(LargeFactoryComponent.CORE_T4, Set.of(FIRMAMENT))
                .allows(FIRMAMENT));
    }

    @Test
    void accessSnapshotIsImmutableAndRejectsNonCoreComponents() {
        var mutable = EnumSet.of(SIMULATION);
        var access = new LargeFactoryRecipeAccess(LargeFactoryComponent.CORE_T1, mutable);
        mutable.add(ASSEMBLY);
        assertFalse(access.allows(ASSEMBLY));
        assertThrows(UnsupportedOperationException.class, () -> access.unlocked().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new LargeFactoryRecipeAccess(LargeFactoryComponent.PROCESS_CORE_HATCH, Set.of()));
    }
}
