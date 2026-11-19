package com.moakiee.ae2lt.client.ae2wtlib;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

class FrequencyTerminalButtonTest {
    @Test
    void ignoresScreensWithoutWirelessTerminalCapability() {
        assertNull(FrequencyTerminalButton.findTerminalHost(new Object()));
        assertNull(FrequencyTerminalButton.findTerminalHost(null));
    }

    @Test
    void acceptsMissingHost() {
        assertNull(FrequencyTerminalButton.findTerminalHost(terminal(() -> null)));
    }

    @Test
    void skipsWiredTerminalWhoseHostAccessorCastsToWirelessHost() {
        var screen = terminal(() -> {
            // Reproduce AE2CS's shared screen returning a wired part as WTMenuHost.
            return (WTMenuHost) new Object();
        });

        assertNull(FrequencyTerminalButton.findTerminalHost(screen));
    }

    @Test
    void propagatesUnrelatedHostFailures() {
        var failure = new IllegalStateException("Host initialization failed");
        var screen = terminal(() -> {
            throw failure;
        });

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> FrequencyTerminalButton.findTerminalHost(screen)));
    }

    @Test
    void propagatesHostErrors() {
        var failure = new AssertionError("Invalid host state");
        var screen = terminal(() -> {
            throw failure;
        });

        assertSame(failure, assertThrows(AssertionError.class,
                () -> FrequencyTerminalButton.findTerminalHost(screen)));
    }

    private static IUniversalTerminalCapable terminal(Supplier<WTMenuHost> host) {
        return new IUniversalTerminalCapable() {
            @Override
            public WTMenuHost getHost() {
                return host.get();
            }

            @Override
            public boolean isHandlingRightClick() {
                return false;
            }

            @Override
            public void storeState() {
            }
        };
    }
}
