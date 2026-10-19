package com.moakiee.ae2lt.compat;
public final class GameTestAssertions {
    private GameTestAssertions() {}
    public static void assertTrue(boolean condition, String message) { if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message); }
    public static void assertFalse(boolean condition, String message) { assertTrue(!condition, message); }
}
