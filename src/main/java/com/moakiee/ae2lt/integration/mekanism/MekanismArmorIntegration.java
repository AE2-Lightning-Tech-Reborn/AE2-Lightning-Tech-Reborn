package com.moakiee.ae2lt.integration.mekanism;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import com.moakiee.ae2lt.celestweave.ArmorWearer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import mekanism.api.lasers.ILaserDissipation;
import mekanism.api.radiation.IRadiationManager;
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
        var radiationManager = IRadiationManager.INSTANCE;
        double radiationLevel = radiationManager.getRadiationLevel(player);
        if (!MekanismProtectionRules.shouldRegenerate(
                player.level().getGameTime(),
                radiationLevel,
                radiationManager.minRadiationMagnitude(),
                player.getHealth(),
                player.getMaxHealth())) {
            return;
        }
        float healthBefore = player.getHealth();
        player.heal(MekanismProtectionRules.radiationHealing(
                RadiationScale.getScaledDoseSeverity(radiationLevel)));
        if (player.getHealth() > healthBefore && player instanceof ServerPlayer serverPlayer) {
            CelestweaveAdvancementService.awardRadiationAssimilation(serverPlayer);
        }
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
