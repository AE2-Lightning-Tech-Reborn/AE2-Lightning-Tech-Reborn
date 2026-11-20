package com.moakiee.ae2lt.client.compat;

import com.google.gson.JsonObject;

public final class CtnhEnergyEncodingStyle {
    private static final String ENCODING_TERMINAL = "/screens/terminals/pattern_encoding_terminal.json";
    private static final String CIRCUIT_WIDGET = "enableCircuit";

    private CtnhEnergyEncodingStyle() {
    }

    public static JsonObject addMissingCircuitWidget(JsonObject style, String path) {
        if (!ENCODING_TERMINAL.equals(path)) {
            return style;
        }
        var widgets = style.getAsJsonObject("widgets");
        if (widgets == null) {
            widgets = new JsonObject();
            style.add("widgets", widgets);
        }
        if (!widgets.has(CIRCUIT_WIDGET)) {
            var circuit = new JsonObject();
            circuit.addProperty("left", 92);
            circuit.addProperty("bottom", 163);
            widgets.add(CIRCUIT_WIDGET, circuit);
        }
        return style;
    }
}
