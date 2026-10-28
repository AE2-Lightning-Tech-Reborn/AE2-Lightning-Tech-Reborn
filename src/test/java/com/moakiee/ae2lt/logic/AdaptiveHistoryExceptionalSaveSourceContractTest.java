package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdaptiveHistoryExceptionalSaveSourceContractTest {
    @Test
    void bothAdaptiveDispatchPathsSaveHistoryInFinally() throws Exception {
        var source = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/logic/provider/OverloadedPatternProviderLogic.java"),
                StandardCharsets.UTF_8);

        int normal = source.indexOf("private ProviderTarget.BatchDispatchResult dispatchNormalBatchRamp");
        int wireless = source.indexOf("private ProviderTarget.BatchStepResult dispatchWirelessBatchStep");
        int save = source.indexOf("saveAdaptiveBatchStateIfDirty(context.target());", normal);
        int wirelessSave = source.indexOf("saveAdaptiveBatchStateIfDirty(context.target());", wireless);

        assertTrue(normal >= 0 && wireless > normal && save > normal && wirelessSave > wireless);
        assertTrue(source.indexOf("try {", normal) < save);
        assertTrue(source.indexOf("finally {", normal) < save);
        assertTrue(source.indexOf("try {", wireless) < wirelessSave);
        assertTrue(source.indexOf("finally {", wireless) < wirelessSave);
    }
}
