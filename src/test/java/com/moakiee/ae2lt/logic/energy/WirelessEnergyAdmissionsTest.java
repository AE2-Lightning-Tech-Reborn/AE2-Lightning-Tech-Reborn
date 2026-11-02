package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WirelessEnergyAdmissionsTest {
    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "WAITING", "NO_INCREMENT", "NO_STORED_ENERGY"})
    void legacyAndPendingRecordsRemainTerminalAcrossSaveAndReload(String state) {
        var row = new CompoundTag();
        var generation = UUID.randomUUID();
        var actor = UUID.randomUUID();
        row.putUUID("generation", generation);
        row.putUUID("actor", actor);
        row.putInt("frequency", 3);
        row.putString("dimension", "minecraft:overworld");
        row.putLong("pos", new BlockPos(1, 64, 2).asLong());
        row.putString("face", "north");
        row.putString("state", state);
        row.putLong("offered", 100);
        row.putLong("accepted", 30);
        var attempts = new ListTag();
        attempts.add(row);
        var root = new CompoundTag();
        root.put("attempts", attempts);

        String expected = state.equals("QUEUED") || state.equals("WAITING") ? "UNVERIFIED" : "NO_STORED_ENERGY";
        for (int reload = 0; reload < 2; reload++) {
            root = WirelessEnergyAdmissions.load(root).save(new CompoundTag());
            var saved = root.getList("attempts", Tag.TAG_COMPOUND);
            assertEquals(1, saved.size());
            var retained = saved.getCompound(0);
            assertEquals(expected, retained.getString("state"));
            assertEquals(generation, retained.getUUID("generation"));
            assertEquals(actor, retained.getUUID("actor"));
            assertEquals(100, retained.getLong("offered"));
            assertEquals(30, retained.getLong("accepted"));
        }
    }
}
