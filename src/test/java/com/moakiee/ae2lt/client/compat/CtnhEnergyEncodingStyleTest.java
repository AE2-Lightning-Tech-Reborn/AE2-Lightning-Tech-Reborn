package com.moakiee.ae2lt.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class CtnhEnergyEncodingStyleTest {
    private static final String ENCODING_TERMINAL = "/screens/terminals/pattern_encoding_terminal.json";

    @Test
    void addsOnlyTheMissingCircuitWidget() {
        var style = JsonParser.parseString("{\"widgets\":{\"processingClearPattern\":{\"left\":82}}}").getAsJsonObject();
        var widgets = style.getAsJsonObject("widgets");
        var clear = widgets.get("processingClearPattern");

        assertSame(style, CtnhEnergyEncodingStyle.addMissingCircuitWidget(style, ENCODING_TERMINAL));

        var circuit = widgets.getAsJsonObject("enableCircuit");
        assertEquals(92, circuit.get("left").getAsInt());
        assertEquals(163, circuit.get("bottom").getAsInt());
        assertSame(clear, widgets.get("processingClearPattern"));
    }

    @Test
    void preservesCustomCircuitLayout() {
        var style = JsonParser.parseString("{\"widgets\":{\"enableCircuit\":{\"left\":71}}}").getAsJsonObject();
        var original = style.deepCopy();

        CtnhEnergyEncodingStyle.addMissingCircuitWidget(style, ENCODING_TERMINAL);

        assertEquals(original, style);
    }

    @Test
    void leavesOtherScreensUnchanged() {
        var style = new JsonObject();
        CtnhEnergyEncodingStyle.addMissingCircuitWidget(style, "/screens/terminals/terminal.json");
        assertFalse(style.has("widgets"));
    }
}
