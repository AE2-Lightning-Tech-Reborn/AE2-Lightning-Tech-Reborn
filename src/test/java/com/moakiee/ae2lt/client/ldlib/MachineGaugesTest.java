package com.moakiee.ae2lt.client.ldlib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MachineGaugesTest {
    @Test
    void verticalGaugeFillsUpAsProgressIncreases() {
        assertEquals(0, MachineGauges.verticalFillPixels(17, 0, 100));
        assertEquals(5, MachineGauges.verticalFillPixels(17, 35, 100));
        assertEquals(17, MachineGauges.verticalFillPixels(17, 100, 100));
        assertEquals(17, MachineGauges.verticalFillPixels(17, 150, 100));
        assertEquals(0, MachineGauges.verticalFillPixels(17, 1, 0));
        assertEquals(17, MachineGauges.verticalFillPixels(17, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
