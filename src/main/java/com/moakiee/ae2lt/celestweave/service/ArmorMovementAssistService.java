package com.moakiee.ae2lt.celestweave.service;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.MovementAssistRules;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.EquipmentTuningSubmodule;
import net.neoforged.neoforge.common.NeoForgeMod;
import com.moakiee.ae2lt.celestweave.FlightSneakMovement;
import com.moakiee.ae2lt.celestweave.module.MovementAssistSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector.ActiveCapability;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

public final class ArmorMovementAssistService {
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID,
            "celestweave_movement_assist_speed");
    private static final ResourceLocation STEP_HEIGHT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID,
            "celestweave_movement_assist_step_height");
    private static final ResourceLocation MINING = ResourceLocation.fromNamespaceAndPath("ae2lt", "module_mining");
    private static final ResourceLocation JUMP = ResourceLocation.fromNamespaceAndPath("ae2lt", "module_jump");
    private static final ResourceLocation SWIM = ResourceLocation.fromNamespaceAndPath("ae2lt", "module_swim");
    private static final double EPSILON = 1.0E-6D;

    private ArmorMovementAssistService() {
    }

    public static void tick(LivingEntity player, List<ActiveCapability> capabilities) {
        double mining = 1.0D;
        double jump = 1.0D;
        double swim = 1.0D;
        Double legMovement = null;
        Double legStep = null;
        boolean active = false;
        double movementMultiplier = 1.0D;
        double stepHeight = MovementAssistRules.VANILLA_STEP_HEIGHT;
        boolean flightSneaking = player instanceof Player realPlayer && FlightSneakMovement.isActive(realPlayer);
        boolean suppressGroundMovement = player instanceof Player realPlayer && realPlayer.getAbilities().flying && !flightSneaking
                || player.isFallFlying()
                || player.isSwimming();

        for (var capability : capabilities) {
            if (!(capability.capability() instanceof DeviceCapability.MovementAssist)) {
                continue;
            }
            var speed = EquipmentTuningSubmodule.SPEED;
            var armor = capability.armor();
            if (speed.id().equals(capability.submoduleId())) {
                switch (CelestweaveArmorState.armorPart(armor)) {
                    case CHEST -> mining = speed.multiplier(armor, "mining_speed");
                    case LEGS -> legMovement = MovementAssistRules.movementMultiplier(
                            suppressGroundMovement || player.isInWater(), flightSneaking || player.isCrouching(),
                            player.isSprinting(), speed.multiplier(armor, "walk_speed_multiplier"),
                            speed.multiplier(armor, "sprint_speed_multiplier"), speed.multiplier(armor, "sneak_speed_multiplier"));
                    case FEET -> swim = speed.multiplier(armor, player.isSwimming() ? "swim_speed" : "underwater_speed");
                    case HEAD -> {}
                }
                continue;
            }
            var distance = EquipmentTuningSubmodule.DISTANCE;
            if (distance.id().equals(capability.submoduleId())) {
                legStep = distance.stepHeight(armor);
                jump = distance.multiplier(armor, "jump_height");
                continue;
            }
            double candidateMovementMultiplier = MovementAssistRules.movementMultiplier(
                    suppressGroundMovement,
                    flightSneaking || player.isCrouching(),
                    player.isSprinting(),
                    MovementAssistSubmodule.walkSpeedMultiplier(capability.armor()),
                    MovementAssistSubmodule.sprintSpeedMultiplier(capability.armor()),
                    MovementAssistSubmodule.sneakSpeedMultiplier(capability.armor()));
            double candidateStepHeight = MovementAssistSubmodule.automaticStepHeight(capability.armor());
            if (!active) {
                movementMultiplier = candidateMovementMultiplier;
                stepHeight = candidateStepHeight;
                active = true;
            } else {
                movementMultiplier = Math.max(movementMultiplier, candidateMovementMultiplier);
                stepHeight = Math.max(stepHeight, candidateStepHeight);
            }
        }

        if (legMovement != null) movementMultiplier = legMovement;
        if (legStep != null) stepHeight = legStep;
        updateModifier(player, Attributes.BLOCK_BREAK_SPEED, MINING, mining - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        updateModifier(player, Attributes.JUMP_STRENGTH, JUMP, Math.sqrt(jump) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        updateModifier(player, NeoForgeMod.SWIM_SPEED, SWIM, player.isInWater() ? swim - 1 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        updateModifier(
                player,
                Attributes.MOVEMENT_SPEED,
                SPEED_MODIFIER_ID,
                MovementAssistRules.speedModifierAmount(movementMultiplier),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        updateModifier(
                player,
                Attributes.STEP_HEIGHT,
                STEP_HEIGHT_MODIFIER_ID,
                MovementAssistRules.stepHeightModifierAmount(stepHeight),
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static void updateModifier(
            LivingEntity player,
            Holder<Attribute> attribute,
            ResourceLocation id,
            double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(id);
        if (Math.abs(amount) < EPSILON) {
            if (existing != null) {
                instance.removeModifier(existing);
            }
            return;
        }

        if (existing != null
                && Math.abs(existing.amount() - amount) < EPSILON
                && existing.operation() == operation) {
            return;
        }

        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }
}
