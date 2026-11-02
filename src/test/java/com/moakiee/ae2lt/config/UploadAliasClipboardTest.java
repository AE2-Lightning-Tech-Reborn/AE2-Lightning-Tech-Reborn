package com.moakiee.ae2lt.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class UploadAliasClipboardTest {
    @Test void unicodeAndWildcardRoundTripDoesNotExportAutomaticMode() {
        var entries = List.of(new UploadAliasRecords.Entry("mod:crusher", "矿物 粉碎*", 1),
                new UploadAliasRecords.Entry("other:smelter", "mod:furnace", 1));
        String json = UploadAliasClipboard.exportAliases(entries);
        assertEquals(entries, UploadAliasClipboard.parse(json));
        assertFalse(json.contains("ANY_MATCH"));
        assertFalse(json.contains("uploadMatchMode"));
    }

    @Test void invalidTailAndDuplicateSourcesRejectTheEntireImport() {
        for (String aliases : List.of(
                "[{\"source\":\"mod:a\",\"alias\":\"valid\"},{\"source\":\"bad=source\",\"alias\":\"x\"}]",
                "[{\"source\":\"mod:a\",\"alias\":\"first\"},{\"source\":\"MOD:A\",\"alias\":\"second\"}]",
                "[{\"source\":\"mod:a\",\"alias\":false}]", "[null]", "{}")) {
            assertThrows(IllegalArgumentException.class, () -> UploadAliasClipboard.parse(
                    "{\"format\":\"ae2lt-upload-aliases\",\"version\":1,\"aliases\":" + aliases + "}"));
        }
    }

    @Test void malformedUnsupportedOrOversizedDocumentsAreRejected() {
        for (String value : List.of("", "null", "[]", "{", "{}",
                "{\"format\":\"ae2lt-upload-aliases\",\"version\":2,\"aliases\":[]}",
                "{\"format\":\"ae2lt-upload-aliases\",\"version\":1.5,\"aliases\":[]}",
                "x".repeat(UploadAliasClipboard.MAX_CHARACTERS + 1))) {
            assertThrows(IllegalArgumentException.class, () -> UploadAliasClipboard.parse(value));
        }
    }

    @Test void savedAliasModeNeverFallsBackToAGuess() {
        assertEquals("", TianshuUploadMatchMode.OFF.query("saved", "guessed"));
        assertEquals("", TianshuUploadMatchMode.SAVED_ALIAS.query(null, "guessed"));
        assertEquals("saved", TianshuUploadMatchMode.SAVED_ALIAS.query(" saved ", "guessed"));
        assertEquals("saved", TianshuUploadMatchMode.ANY_MATCH.query("saved", "guessed"));
        assertEquals("guessed", TianshuUploadMatchMode.ANY_MATCH.query(null, "guessed"));
        assertEquals(TianshuUploadMatchMode.OFF, TianshuUploadMatchMode.OFF.next().next().next());
    }
}
