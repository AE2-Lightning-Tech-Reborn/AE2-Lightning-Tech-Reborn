package com.moakiee.ae2lt.item.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

class RailgunSettingsTest {
    @Test
    void playerTargetingRequiresItemOptInAndServerPermission() {
        assertTrue(new RailgunSettings(false, true, true, false, true, true).allowsPlayerTargets(true));
        assertFalse(new RailgunSettings(false, false, true, false, true, true).allowsPlayerTargets(true));
        assertFalse(new RailgunSettings(false, true, true, false, true, true).allowsPlayerTargets(false));
        assertFalse(new RailgunSettings(false, false, true, false, true, true).allowsPlayerTargets(false));
    }

    @Test
    void overloadRemovalDefaultsToNormalDeathAndCanBeSwitchedIndependently() {
        assertEquals(RailgunExecutionMode.NORMAL, RailgunSettings.DEFAULT.executionMode());
        assertFalse(RailgunSettings.DEFAULT.forceOverloadRemoval());

        RailgunSettings forced = RailgunSettings.DEFAULT.withExecutionMode(RailgunExecutionMode.FORCED);
        assertTrue(forced.forceOverloadRemoval());
        assertFalse(forced.terrainDestruction());
        assertFalse(forced.pvp());
        assertTrue(forced.soundEnabled());
        assertTrue(forced.chainDamage());
    }

    @Test
    void executionModeCyclesThroughFourStates() {
        assertEquals(RailgunExecutionMode.PERCENTAGE, RailgunExecutionMode.OFF.next());
        assertEquals(RailgunExecutionMode.NORMAL, RailgunExecutionMode.PERCENTAGE.next());
        assertEquals(RailgunExecutionMode.NORMAL, RailgunExecutionMode.OFF.next(false));
        assertEquals(RailgunExecutionMode.NORMAL, RailgunExecutionMode.PERCENTAGE.forMultidimensional());
        assertFalse(RailgunExecutionMode.PERCENTAGE.entersExecutionFlow());
        assertFalse(RailgunExecutionMode.PERCENTAGE.forcesRemoval());
        assertEquals(RailgunExecutionMode.FORCED, RailgunExecutionMode.NORMAL.next());
        assertEquals(RailgunExecutionMode.OFF, RailgunExecutionMode.FORCED.next());
    }

    @Test
    void legacyBooleanExecutionSettingMigratesWithoutChangingBehavior() {
        RailgunSettings legacyNormal = RailgunSettings.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"terrain\":false,\"pvp\":false,"
                        + "\"force_overload_removal\":false}"))
                .result()
                .orElseThrow();
        RailgunSettings legacyForced = RailgunSettings.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"terrain\":false,\"pvp\":false,"
                        + "\"force_overload_removal\":true}"))
                .result()
                .orElseThrow();

        assertEquals(RailgunExecutionMode.NORMAL, legacyNormal.executionMode());
        assertEquals(RailgunExecutionMode.FORCED, legacyForced.executionMode());
    }

    @Test
    void currentCodecPersistsExplicitOffMode() {
        var encoded = RailgunSettings.CODEC.encodeStart(
                JsonOps.INSTANCE,
                RailgunSettings.DEFAULT.withExecutionMode(RailgunExecutionMode.OFF))
                .result()
                .orElseThrow()
                .getAsJsonObject();

        assertEquals("off", encoded.get("execution_mode").getAsString());
        assertFalse(encoded.has("force_overload_removal"));
    }

    @Test
    void chainDamageDefaultsOnAndCanBeSwitchedIndependently() {
        assertTrue(RailgunSettings.DEFAULT.chainDamage());

        RailgunSettings disabled = RailgunSettings.DEFAULT.withChainDamage(false);
        assertFalse(disabled.chainDamage());
        assertTrue(disabled.chargedSplash());
        assertEquals(RailgunExecutionMode.NORMAL, disabled.executionMode());
    }

    @Test
    void chargedSplashDefaultsOnAndCanBeSwitchedIndependently() {
        RailgunSettings disabled = RailgunSettings.DEFAULT.withChargedSplash(false);

        assertFalse(disabled.chargedSplash());
        assertTrue(disabled.chainDamage());
        assertEquals(RailgunExecutionMode.NORMAL, disabled.executionMode());
    }

    @Test
    void oldSavedRailgunsKeepBeamDisabledAndNewModesRoundTripIndependently() {
        var old = RailgunSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"terrain\":false,\"pvp\":false,\"execution_mode\":\"forced\"}"))
                .result().orElseThrow();
        assertEquals(RailgunExecutionMode.FORCED, old.executionMode());
        assertFalse(old.ehvBeamEnabled());
        var original = old.withEhvBeam(true).withExecutionMode(RailgunExecutionMode.PERCENTAGE)
                .withSound(false).withChainDamage(false).withChargedSplash(false).withTerrain(true).withPvp(true);
        assertTrue(original.ehvBeamEnabled());
        var encoded = RailgunSettings.CODEC.encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();
        assertEquals(original, RailgunSettings.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
        assertEquals(RailgunExecutionMode.PERCENTAGE, original.withEhvBeam(false).executionMode());
        assertEquals(0, RailgunExecutionMode.OFF.ordinal());
        assertEquals(1, RailgunExecutionMode.NORMAL.ordinal());
        assertEquals(2, RailgunExecutionMode.FORCED.ordinal());
    }
}
