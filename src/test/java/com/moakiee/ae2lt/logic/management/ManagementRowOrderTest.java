package com.moakiee.ae2lt.logic.management;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ManagementRowOrderTest {
    private static List<ManagementRow> rows() {
        var ids = new ArrayList<UUID>();
        long[] edges = {0, 1, Long.MAX_VALUE, Long.MIN_VALUE, -1};
        for (long most : edges) for (long least : edges) ids.add(new UUID(most, least));
        var random = new Random(835194);
        for (int i = 0; i < 4096; i++) ids.add(new UUID(random.nextLong(), random.nextLong()));
        // Duplicate keys must preserve stable input order too.
        ids.add(ids.get(0));
        var result = new ArrayList<ManagementRow>();
        for (int i = 0; i < ids.size(); i++) {
            result.add(new ManagementRow(ids.get(i),
                    new DeviceLocation("minecraft:overworld", new BlockPos(i, 64, 0), -1, "ae2:interface", ""),
                    null, "wireless", 1, i % 2 == 0 ? "idle" : "offline", false, false,
                    new double[]{i % 7, i % 11, i % 3, 0, 0}));
        }
        return result;
    }

    @Test void unsignedIdOrderMatchesLegacyTextOrderAcrossSignBoundariesAndRandomIds() {
        var rows = rows();
        assertEquals(rows.stream().sorted(Comparator.comparing(r -> r.id().toString())).toList(),
                rows.stream().sorted(ManagementSnapshot.ID_ORDER).toList());
    }

    @Test void tiedPowerSortsKeepEveryPageIdenticalInBothDirectionsAndAllWindows() {
        var rows = rows();
        for (int window = 0; window < 5; window++) {
            final int selected = window;
            for (boolean descending : new boolean[]{false, true}) {
                Comparator<ManagementRow> primary = Comparator.comparingDouble(r -> r.rates()[selected]);
                if (descending) primary = primary.reversed();
                var expected = rows.stream().sorted(primary.thenComparing(r -> r.id().toString())).toList();
                var actual = rows.stream().sorted(primary.thenComparing(ManagementSnapshot.ID_ORDER)).toList();
                for (int offset = 0; offset < rows.size(); offset += ManagementQuery.PAGE_SIZE) {
                    int end = Math.min(rows.size(), offset + ManagementQuery.PAGE_SIZE);
                    assertEquals(expected.subList(offset, end), actual.subList(offset, end),
                            "window=" + window + ", descending=" + descending + ", offset=" + offset);
                }
            }
        }
    }
}
