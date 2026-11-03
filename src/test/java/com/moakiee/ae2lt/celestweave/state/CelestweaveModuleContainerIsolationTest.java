package com.moakiee.ae2lt.celestweave.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.testing.MinecraftTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CelestweaveModuleContainerIsolationTest {
    private static final String SUBMODULE = "qa_module";

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void savedSnapshotsCannotMutateEachOtherOrTheContainer() {
        var container = container();
        var first = container.save();
        var second = container.save();

        state(first).putInt("charges", 99);
        state(first).getCompound("settings").putBoolean("enabled", false);
        state(first).getIntArray("history")[0] = 99;

        assertState(state(second), 7, true, 1);
        assertState(container.submoduleData().get(SUBMODULE), 7, true, 1);
        assertState(state(container.save()), 7, true, 1);

        state(second).putInt("charges", 42);
        assertState(state(first), 99, false, 99);
        assertState(container.submoduleData().get(SUBMODULE), 7, true, 1);
    }

    @Test
    void writingOneContainerToTwoArmorStacksKeepsTheirSubmoduleStateSeparate() {
        var container = container();
        var first = new ItemStack(Items.DIAMOND_CHESTPLATE);
        var second = new ItemStack(Items.NETHERITE_CHESTPLATE);
        var component = ModDataComponents.CELESTWEAVE_MODULES;
        component.set(first, container);
        component.set(second, container);

        var firstState = state(first.getTag().getCompound(component.nbtKey()));
        firstState.putInt("charges", 99);
        firstState.getCompound("settings").putBoolean("enabled", false);
        firstState.getIntArray("history")[0] = 99;

        assertState(ArmorPersistentData.getSubmoduleData(second, SUBMODULE), 7, true, 1);
        assertState(container.submoduleData().get(SUBMODULE), 7, true, 1);

        var copied = first.copy();
        var copiedState = state(copied.getTag().getCompound(component.nbtKey()));
        copiedState.putInt("charges", 42);
        copiedState.getCompound("settings").putBoolean("enabled", true);
        copiedState.getIntArray("history")[0] = 42;
        assertState(ArmorPersistentData.getSubmoduleData(first, SUBMODULE), 99, false, 99);
        assertState(ArmorPersistentData.getSubmoduleData(second, SUBMODULE), 7, true, 1);
        assertState(ArmorPersistentData.getSubmoduleData(copied, SUBMODULE), 42, true, 42);
    }

    private static CelestweaveModuleContainer container() {
        var data = new CompoundTag();
        data.putInt("charges", 7);
        var settings = new CompoundTag();
        settings.putBoolean("enabled", true);
        data.put("settings", settings);
        data.putIntArray("history", new int[] {1, 2, 3});
        return new CelestweaveModuleContainer(Optional.empty(), List.of(),
                Map.of(SUBMODULE, true), Map.of(SUBMODULE, data), Optional.empty());
    }

    private static CompoundTag state(CompoundTag saved) {
        return saved.getCompound("submodule_data").getCompound(SUBMODULE);
    }

    private static void assertState(CompoundTag state, int charges, boolean enabled, int firstHistoryEntry) {
        assertEquals(charges, state.getInt("charges"));
        assertEquals(enabled, state.getCompound("settings").getBoolean("enabled"));
        assertEquals(firstHistoryEntry, state.getIntArray("history")[0]);
    }
}
