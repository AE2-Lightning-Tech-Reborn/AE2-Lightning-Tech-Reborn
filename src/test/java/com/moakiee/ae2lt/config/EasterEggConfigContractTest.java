package com.moakiee.ae2lt.config;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class EasterEggConfigContractTest {
    @Test void explosionRemainsInCoreWhileResearchIsOptional() throws Exception {
        String config = Files.readString(Path.of("src/main/java/com/moakiee/ae2lt/config/AE2LTCommonConfig.java"));
        String tnt = Files.readString(Path.of("src/main/java/com/moakiee/ae2lt/entity/OverloadTntEntity.java"));
        assertTrue(config.contains("eastereggitem"));
        assertTrue(tnt.contains("AE2LTCommonConfig.easterEggEnabled()"));
        assertFalse(Files.exists(Path.of("src/main/java/com/moakiee/ae2lt/item/ResearchNoteItem.java")));
        assertFalse(Files.exists(Path.of("src/main/resources/data/ae2lt/advancement/main/pig_zip.json")));
    }
}
