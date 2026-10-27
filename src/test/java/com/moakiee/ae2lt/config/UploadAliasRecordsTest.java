package com.moakiee.ae2lt.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class UploadAliasRecordsTest {
    @Test
    void deletingOneSourcePreservesOtherSourcesWithTheSameAliasAndMalformedData() {
        var raw = List.of("mod:first=Factory", "mod:second=Factory", " MOD:FIRST =duplicate", "broken", "=unknown");
        assertEquals(List.of("mod:second=Factory", "broken", "=unknown"),
                UploadAliasRecords.remove(raw, " MOD:FIRST "));
        assertEquals(raw, UploadAliasRecords.remove(raw, ""));
    }

    @Test
    void migrationReplacesBothSourceKeysInOneResultAndPreservesUnrelatedEntries() {
        var raw = List.of("old:key=one", "old:key=duplicate", "new:key=two", "other:key=two", "broken");
        assertEquals(List.of("other:key=two", "broken", "new:key=New Alias"),
                UploadAliasRecords.replace(raw, " OLD:KEY ", " NEW:KEY ", " New Alias "));
        assertEquals(5, raw.size());
    }

    @Test
    void snapshotKeepsFirstRecordAndReportsDuplicatesWithoutMergingEqualAliases() {
        var entries = UploadAliasRecords.snapshot(List.of("z:key=Factory", "A:KEY=Factory", "a:key=Other", "bad"));
        assertEquals(List.of(new UploadAliasRecords.Entry("a:key", "Factory", 2),
                new UploadAliasRecords.Entry("z:key", "Factory", 1)), entries);
        assertEquals("", UploadAliasRecords.snapshot(List.of("a:key=", "a:key=later")).getFirst().alias());
    }

    @Test
    void validationHonorsBothFieldAndLegacySerializedLimits() {
        assertTrue(UploadAliasRecords.valid("a".repeat(255), "b".repeat(256)));
        assertFalse(UploadAliasRecords.valid("a".repeat(256), "b".repeat(256)));
        assertFalse(UploadAliasRecords.valid("a".repeat(257), "b"));
        assertFalse(UploadAliasRecords.valid("a", "b".repeat(257)));
        assertFalse(UploadAliasRecords.valid(" ", "b"));
        assertFalse(UploadAliasRecords.valid("a", " "));
        assertFalse(UploadAliasRecords.valid("a=b", "c"));
        assertTrue(UploadAliasRecords.valid("unloaded:viewer_category", "A=B"));
        assertEquals(List.of("unloaded:viewer_category=A=B"),
                UploadAliasRecords.replace(List.of(), null, " UNLOADED:VIEWER_CATEGORY ", " A=B "));
        assertThrows(IllegalArgumentException.class, () -> UploadAliasRecords.replace(List.of("keep=old"), null, "bad=key", "value"));
    }
}
