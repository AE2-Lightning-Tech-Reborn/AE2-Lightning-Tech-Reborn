package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.moakiee.ae2lt.device.energy.LightningCompensationPolicy;
import org.junit.jupiter.api.Test;

class RailgunBeamSettlementSourceContractTest {
    @Test
    void compensationConsumesOnlyTheEhvShortfall() {
        var mixed = LightningCompensationPolicy.plan(0L, 16L, 512L, 10L, 16);
        assertTrue(mixed.canPay());
        assertEquals(10L, mixed.extremeHighVoltageToConsume());
        assertEquals(96L, mixed.highVoltageToConsume());

        var fullyCompensated = LightningCompensationPolicy.plan(0L, 16L, 256L, 0L, 16);
        assertTrue(fullyCompensated.canPay());
        assertEquals(0L, fullyCompensated.extremeHighVoltageToConsume());
        assertEquals(256L, fullyCompensated.highVoltageToConsume());

        var fullySupplied = LightningCompensationPolicy.plan(0L, 16L, 0L, 16L, 0);
        assertTrue(fullySupplied.canPay());
        assertEquals(16L, fullySupplied.extremeHighVoltageToConsume());
        assertEquals(0L, fullySupplied.highVoltageToConsume());
    }

    @Test
    void missingCapabilityShortPaymentAndOverflowCannotBuyAnEhvShot() {
        assertFalse(LightningCompensationPolicy.plan(0L, 16L, Long.MAX_VALUE, 8L, 0).canPay());
        assertFalse(LightningCompensationPolicy.plan(0L, 16L, 127L, 8L, 16).canPay());
        assertFalse(LightningCompensationPolicy.plan(0L, Long.MAX_VALUE, Long.MAX_VALUE, 0L, 16).canPay());
    }

    @Test
    void settlementSimulatesCompensationBeforeSpendingFe() throws Exception {
        String service = serviceSource();
        assertTrue(service.contains("profile.ehv() ? LightningCompensationPolicy.bestRatio(mods.capabilities()) : 0"));
        assertTrue(service.contains("LightningCompensationPolicy.plan(0L, primaryNeeded, Long.MAX_VALUE, primaryAvail, ratio)"));
        assertTrue(service.contains("!payment.canPay() ||"));
        int simulateHv = service.indexOf("LightningKey.HIGH_VOLTAGE, compensationHvNeeded, Actionable.SIMULATE");
        int refillFe = service.indexOf("RailgunEnergyBuffer.refillFromNetwork(");
        int spendFe = service.indexOf("RailgunEnergyBuffer.tryConsume(");
        assertTrue(simulateHv >= 0 && simulateHv < refillFe && refillFe < spendFe);
    }

    @Test
    void modulationShortfallsRefundPaidLightningAndFeBeforeAnyDamage() throws Exception {
        String service = serviceSource();
        int primaryFailure = service.indexOf("if (gotPrimary < takePrimary)");
        int compensationCommit = service.indexOf("if (compensationHvNeeded > 0L)");
        String primaryRollback = service.substring(primaryFailure, compensationCommit);
        assertTrue(primaryRollback.contains("insert(primaryKey, gotPrimary, Actionable.MODULATE, src)"));
        assertTrue(primaryRollback.contains("RailgunEnergyBuffer.refund(stack, feCost)"));
        assertTrue(primaryRollback.contains("return false;"));

        int compensationFailure = service.indexOf("if (gotHv < compensationHvNeeded)");
        int trace = service.indexOf("BeamTrace trace = traceBeam");
        String compensationRollback = service.substring(compensationFailure, trace);
        assertTrue(compensationRollback.contains("insert(primaryKey, gotPrimary, Actionable.MODULATE, src)"));
        assertTrue(compensationRollback.contains("insert(LightningKey.HIGH_VOLTAGE, gotHv, Actionable.MODULATE, src)"));
        assertTrue(compensationRollback.contains("RailgunEnergyBuffer.refund(stack, feCost)"));
        assertTrue(compensationRollback.contains("return false;"));
        assertTrue(trace < service.indexOf("primary.hurt(ds,"));
    }

    @Test
    void compensatedShotsRetainServerModeDamageAndNonExecutionChains() throws Exception {
        String service = serviceSource();
        assertTrue(service.contains("DamageContext.buildBeam(player, mods, level, allowPlayerTargets, profile.damage())"));
        assertTrue(service.contains("beamDamageSource(level, player, profile.ehv())"));
        assertTrue(service.contains("broadcastTrace(level, player, trace, profile.ehv())"));
        assertTrue(service.contains("chain, settings.soundEnabled(), profile.ehv())"));
        assertTrue(service.contains("RailgunFireService.applyAll(level, player, chain, ctx, stack, RailgunChargeTier.HV)"));
        assertTrue(service.contains("new RailgunBeamUpdatePacket(player.getUUID(), Vec3.ZERO, Vec3.ZERO, false, false)"));
        assertEquals(1, service.split("RailgunBeamProfile.resolve", -1).length - 1);
    }

    private static String serviceSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/moakiee/ae2lt/logic/railgun/RailgunBeamService.java"));
    }
}
