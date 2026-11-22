package com.moakiee.ae2lt.integration.mekanism;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import com.moakiee.ae2lt.celestweave.ArmorWearer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import mekanism.api.lasers.ILaserDissipation;
import mekanism.api.radiation.IRadiationManager;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.api.radiation.capability.IRadiationShielding;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.radiation.RadiationScale;
import mekanism.common.util.UnitDisplayUtils;

import com.moakiee.ae2lt.celestweave.MekanismProtectionRules;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.MekanismProtectionSubmodule;
import com.moakiee.ae2lt.celestweave.module.PurificationSubmodule;
import com.moakiee.ae2lt.celestweave.module.ResistanceSubmodule;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;
import com.moakiee.ae2lt.celestweave.service.CelestweaveAdvancementService;
import com.moakiee.ae2lt.celestweave.service.ArmorEnergyService;
import com.moakiee.ae2lt.celestweave.state.ArmorRuntimeRegistry;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;

/**
 * Optional native Mekanism protection capabilities for Celestweave.
 *
 * <p>Damage-event cancellation alone is too late for Mekanism's mechanics: radiation has already
 * been added to the entity and a laser has already passed its dissipation phase. These providers
 * participate in Mekanism's own calculations first; the generic armor damage capability remains
 * a final safety net for already accumulated radiation and direct damage calls.
 */
public final class MekanismArmorIntegration {
    private static final String LAST_RADIATION_PULSE = "ae2lt.last_radiation_pulse";
    private static final String DOSE_PULSES_LEFT = "ae2lt.dose_pulses_left";
    private static final int MAX_DOSE_PULSES = 60;
    private static final IRadiationShielding FULL_RADIATION_SHIELDING = () -> 1.0D;
    private static final CelestweaveLaserDissipation FULL_LASER_DISSIPATION =
            new CelestweaveLaserDissipation();

    public static final class CelestweaveLaserDissipation implements ILaserDissipation {
        private CelestweaveLaserDissipation() {
        }

        @Override
        public double getDissipationPercent() {
            return 1.0D;
        }

        @Override
        public double getRefractionPercent() {
            return 0.0D;
        }
    }

    private MekanismArmorIntegration() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
                Capabilities.RADIATION_SHIELDING,
                (stack, context) -> (isProtectionActive(stack, PurificationSubmodule.INSTANCE.id())
                        || isProtectionActive(stack, MekanismProtectionSubmodule.RADIATION.id()))
                        ? FULL_RADIATION_SHIELDING
                        : null,
                ModItems.CELESTWEAVE_CORE.get(),
                ModItems.PHASE_LOCK_PROJECTION.get());
        event.registerItem(
                Capabilities.LASER_DISSIPATION,
                (stack, context) -> hasLaserProtection(stack)
                        ? FULL_LASER_DISSIPATION
                        : null,
                ModItems.CELESTWEAVE_CORE.get(),
                ModItems.PHASE_LOCK_PROJECTION.get());
    }

    private static boolean hasLaserProtection(ItemStack stack) {
        return isProtectionActive(stack, ResistanceSubmodule.T1.id())
                || isProtectionActive(stack, ResistanceSubmodule.T2.id())
                || isProtectionActive(stack, MekanismProtectionSubmodule.LASER.id());
    }

    static boolean isProtectionActive(ItemStack stack, String submoduleId) {
        var projectionLink = stack.get(ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get());
        if (projectionLink != null) {
            return ArmorRuntimeRegistry.isSubmoduleRuntimeActive(projectionLink.armorId(), submoduleId);
        }
        return CelestweaveArmorState.isSubmoduleRuntimeActive(stack, submoduleId);
    }

    public static void tickRadiationRegeneration(LivingEntity player) {
        long now = player.level().getGameTime();
        int interval = MekanismProtectionRules.RADIATION_REGEN_INTERVAL_TICKS;
        if (now % interval != 0 || !player.isAlive()) return;
        var radiationManager = IRadiationManager.INSTANCE;
        if (!radiationManager.isRadiationEnabled()) return;
        var data = player.getPersistentData();
        long lastPulse = data.getLong(LAST_RADIATION_PULSE);
        if (data.contains(LAST_RADIATION_PULSE) && lastPulse == now) return;
        data.putLong(LAST_RADIATION_PULSE, now);
        int pulsesLeft = data.getInt(DOSE_PULSES_LEFT);
        if (lastPulse != now - interval || pulsesLeft < 1 || pulsesLeft > MAX_DOSE_PULSES) {
            pulsesLeft = MAX_DOSE_PULSES;
        }
        var body = player.getCapability(Capabilities.RADIATION_ENTITY);
        double removed = cleanseDose(body, radiationManager.baselineRadiation(), pulsesLeft);
        if (removed > 0 && pulsesLeft > 1) {
            data.putInt(DOSE_PULSES_LEFT, pulsesLeft - 1);
        } else {
            data.remove(DOSE_PULSES_LEFT);
        }
        if (player.getHealth() >= player.getMaxHealth()) return;
        double radiationLevel = radiationManager.getRadiationLevel(player);
        double minimum = radiationManager.minRadiationMagnitude();
        float healing = 0;
        if (MekanismProtectionRules.shouldRegenerate(now, radiationLevel, minimum, player.getHealth(), player.getMaxHealth())) {
            healing = MekanismProtectionRules.radiationHealing(RadiationScale.getScaledDoseSeverity(radiationLevel));
        }
        if (removed > 0) {
            double doseHealing = MekanismProtectionRules.radiationHealing(RadiationScale.getScaledDoseSeverity(removed))
                    * Math.min(1.0D, removed / minimum);
            healing = Math.max(healing, (float) doseHealing);
        }
        if (healing <= 0) return;
        float healthBefore = player.getHealth();
        player.heal(healing);
        if (player.getHealth() > healthBefore && player instanceof ServerPlayer serverPlayer) {
            CelestweaveAdvancementService.awardRadiationAssimilation(serverPlayer);
        }
    }

    private static double cleanseDose(IRadiationEntity body, double baseline, int pulsesLeft) {
        if (body == null) return 0;
        double dose = body.getRadiation();
        if (!Double.isFinite(dose) || dose <= baseline) return 0;
        // Re-read the dose each second so Mekanism's own decay and external treatments still apply.
        double next = pulsesLeft == 1 ? baseline : Math.max(baseline, dose - (dose - baseline) / pulsesLeft);
        // A sub-ULP installment can round back to the same dose. Still make measurable progress.
        if (next == dose) next = Math.max(baseline, Math.nextDown(dose));
        body.set(next);
        return dose - body.getRadiation();
    }

    /**
     * Called from the optional laser mixin at Mekanism's dissipation calculation, before the
     * absorbed Joules are removed from the beam.
     */
    public static void absorbLaserEnergy(
            ILaserDissipation dissipation,
            LivingEntity target,
            long availableJoules,
            double dissipationPercent) {
        if (dissipation != FULL_LASER_DISSIPATION || !ArmorWearer.canRun(target)) {
            return;
        }
        ItemStack chest = CelestweaveEquipmentAccess.findArmor(target, EquipmentSlot.CHEST);
        if (chest.isEmpty() || !hasLaserProtection(chest)) {
            return;
        }
        long absorbedJoules = MekanismProtectionRules.absorbedJoules(
                availableJoules,
                dissipationPercent);
        long forgeEnergy = MekanismProtectionRules.joulesToForgeEnergy(
                absorbedJoules,
                UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.getConversion());
        if (isProtectionActive(chest, ResistanceSubmodule.T2.id())
                && !isProtectionActive(chest, MekanismProtectionSubmodule.LASER.id())) {
            int percent = com.moakiee.ae2lt.config.AE2LTCommonConfig.phaseShieldLaserRecoveryPercent();
            forgeEnergy = (forgeEnergy / 100) * percent + (forgeEnergy % 100) * percent / 100;
        }
        ArmorEnergyService.receiveExternalEnergy(target, chest, forgeEnergy);
    }
}
