package com.moakiee.ae2lt.logic.management;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ManagementQueryBoundaryTest {
    @Test void emptyTokensIncludeIdleOfflineAndCrossDimensionRowsInEveryMode() {
        var locations = List.of(
                new DeviceLocation("minecraft:overworld", BlockPos.ZERO, -1, "minecraft:air", ""),
                new DeviceLocation("minecraft:the_nether", new BlockPos(-30000000, -64, 30000000), 5,
                        "ae2:interface", "主工厂 İSTANBUL"));
        for (var mode : ManagementMode.values()) {
            var q = ManagementQuery.initial(mode);
            for (var target : locations) {
                for (var kind : List.of("device", "connection", "wireless", "induction", "accessor", "lt_output")) {
                    assertTrue(q.matches(target, null, kind));
                    for (var source : locations) assertTrue(q.matches(target, source, kind));
                }
            }
        }
    }

    private static ManagementQuery query(List<String> expanded, String search, List<ManagementQuery.Token> tokens,
                                         int serial, int sort, int window, int offset) {
        return new ManagementQuery(serial, ManagementMode.ENERGY, false, expanded, search, tokens, sort, false, window, offset);
    }
    @Test void maximumSearchTokensSortingAndPageOffsetAreAccepted() {
        var tokens = IntStream.range(0, 8).mapToObj(i -> new ManagementQuery.Token("x".repeat(128), List.of("a".repeat(256)))).toList();
        assertTrue(query(List.of(), "中".repeat(128), tokens, Integer.MAX_VALUE, 4, 4, 1_000_000).valid());
    }
    @Test void excessAndNegativeClientFieldsAreRejected() {
        assertFalse(query(List.of(), "x".repeat(129), List.of(), 0, 0, 0, 0).valid());
        for (int[] v : new int[][] {{-1, 0, 0, 0}, {0, -1, 0, 0}, {0, 5, 0, 0},
                {0, 0, -1, 0}, {0, 0, 5, 0}, {0, 0, 0, -1}, {0, 0, 0, 1_000_001}})
            assertFalse(query(List.of(), "", List.of(), v[0], v[1], v[2], v[3]).valid());
        assertFalse(query(List.of(), "", java.util.Collections.nCopies(9, new ManagementQuery.Token("", List.of())), 0, 0, 0, 0).valid());
    }
    @Test void expandedTypesEnforceCountUniquenessAndUtf8ByteBudget() {
        assertTrue(ManagementQuery.validExpandedTypes(IntStream.range(0, 128).mapToObj(i -> "type:" + i).toList()));
        assertFalse(ManagementQuery.validExpandedTypes(IntStream.range(0, 129).mapToObj(i -> "type:" + i).toList()));
        assertFalse(ManagementQuery.validExpandedTypes(List.of("same", "same")));
        var unicode = IntStream.range(0, 20).mapToObj(i -> "中".repeat(200) + i).toList();
        assertFalse(ManagementQuery.validExpandedTypes(unicode));
    }
    @Test void malformedNullCollectionsAndTokensFailValidation() {
        assertFalse(query(null, "", List.of(), 0, 0, 0, 0).valid());
        assertFalse(query(List.of(), null, List.of(), 0, 0, 0, 0).valid());
        assertFalse(query(List.of(), "", null, 0, 0, 0, 0).valid());
        var tokens = new ArrayList<ManagementQuery.Token>(); tokens.add(null);
        assertFalse(query(List.of(), "", tokens, 0, 0, 0, 0).valid());
        assertFalse(query(List.of(), "", List.of(new ManagementQuery.Token(null, List.of())), 0, 0, 0, 0).valid());
        assertFalse(query(List.of(), "", List.of(new ManagementQuery.Token("", null)), 0, 0, 0, 0).valid());
    }
    @Test void multilingualNamesDimensionsAndCoordinatesUseAndSemantics() {
        var target = new DeviceLocation("minecraft:the_nether", new BlockPos(-32, 64, 255), 2, "ae2:interface", "主工厂接口");
        var q = query(List.of(), "", List.of(new ManagementQuery.Token("主工厂", List.of()),
                new ManagementQuery.Token("-32", List.of()), new ManagementQuery.Token("NETHER", List.of())), 0, 0, 0, 0);
        assertTrue(q.matches(target, null, "wireless"));
        assertFalse(q.matches(new DeviceLocation("minecraft:overworld", target.pos(), 2, target.type(), target.name()), null, "wireless"));
        var alias = query(List.of(), "", List.of(new ManagementQuery.Token("本地化名称", List.of("ae2:interface"))), 0, 0, 0, 0);
        assertTrue(alias.matches(target, null, "wireless"));
    }
    @Test void wireCollectionBoundsRejectNegativeAndOverLimitSizes() {
        assertEquals(0, ManagementQuery.bounded(0, 5)); assertEquals(5, ManagementQuery.bounded(5, 5));
        assertThrows(IllegalArgumentException.class, () -> ManagementQuery.bounded(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> ManagementQuery.bounded(Integer.MAX_VALUE, 5));
    }
}
