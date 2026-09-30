package com.moakiee.ae2lt.debug;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.phase.PhaseLockService;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Exercises actual creative slot packets across projection collapse, with no network or FE. */
@net.neoforged.neoforge.gametest.GameTestHolder("ae2lt_phase_lock")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class PhaseLockCreativeRecoveryGameTests {
    private static final List<EquipmentSlot> SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void emptyArmorSurvivesCreativeProjectionEcho(GameTestHelper helper) {
        for (EquipmentSlot removedSlot : SLOTS) {
            var player = player(helper);
            var armor = equipArmor(player);
            PhaseLockService.tick(player);
            require(PhaseLockService.hasPrivateArmor(player), "armor did not enter the phase vault");
            ItemStack oldProjection = player.getItemBySlot(removedSlot).copy();
            require(oldProjection.getItem() instanceof PhaseLockProjectionItem, "missing initial projection");

            upload(player, menuSlot(removedSlot), ItemStack.EMPTY);
            require(player.getItemBySlot(removedSlot).isEmpty(), "creative removal packet did not execute");
            PhaseLockService.tick(player);
            require(!PhaseLockService.hasPrivateArmor(player), "zero FE must collapse the phase lock");
            for (EquipmentSlot slot : SLOTS) {
                require(player.getItemBySlot(slot) == armor.get(slot), "collapse lost the live armor: " + slot);
            }

            // A creative inventory listener can upload its previous slot snapshot after collapse.
            upload(player, menuSlot(removedSlot), oldProjection);
            require(player.getItemBySlot(removedSlot) == armor.get(removedSlot),
                    "stale creative projection overwrote the restored real armor: " + removedSlot);
            PhaseLockService.tick(player);
            for (EquipmentSlot slot : SLOTS) {
                require(PhaseLockService.getPrivateArmor(player, slot) == armor.get(slot),
                        "relocking lost the authoritative armor: " + slot);
            }
            PhaseLockService.release(player);
            player.getInventory().clearContent();
        }
        System.out.println("PHASE_LOCK_RECOVERY_PASS all four empty armor slots survive stale creative projections");
        helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void creativeEchoProtectionPreservesRealEdits(GameTestHelper helper) {
        var player = player(helper);
        var armor = equipArmor(player);
        var chest = armor.get(EquipmentSlot.CHEST);
        var staleArmor = chest.copy();
        staleArmor.set(DataComponents.CUSTOM_NAME, Component.literal("stale client name"));
        upload(player, 6, staleArmor);
        require(player.getItemBySlot(EquipmentSlot.CHEST) == chest, "same-UUID armor echo was accepted");

        PhaseLockService.tick(player);
        var projection = player.getItemBySlot(EquipmentSlot.CHEST).copy();
        upload(player, 6, projection.copy());
        PhaseLockService.tick(player);
        require(PhaseLockService.getPrivateArmor(player, EquipmentSlot.CHEST) == chest,
                "an equipped projection echo damaged the private armor");
        PhaseLockService.release(player);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.getInventory().setItem(0, chest);
        for (int menuSlot = 9; menuSlot <= 45; menuSlot++) {
            var slot = player.inventoryMenu.getSlot(menuSlot);
            slot.set(chest.copy());
            var edited = chest.copy();
            edited.set(DataComponents.CUSTOM_NAME, Component.literal("Valid inventory edit " + menuSlot));
            upload(player, menuSlot, edited);
            require(slot.getItem() == edited, "same-UUID inventory edit rejected in slot " + menuSlot);
            var movedProjection = projection.copy();
            upload(player, menuSlot, movedProjection);
            require(slot.getItem() == movedProjection, "inventory replacement rejected in slot " + menuSlot);
            upload(player, menuSlot, ItemStack.EMPTY);
            require(slot.getItem().isEmpty(), "inventory removal rejected in slot " + menuSlot);
        }

        var replacement = new ItemStack(ModItems.CELESTWEAVE_CORE.get());
        CelestweaveArmorState.ensureArmorId(replacement);
        upload(player, 36, replacement);
        require(player.getInventory().getItem(0) == replacement, "a different real armor UUID was rejected");
        var ordinary = new ItemStack(net.minecraft.world.item.Items.DIAMOND);
        upload(player, 36, ordinary);
        require(player.getInventory().getItem(0) == ordinary, "ordinary creative replacement was rejected");
        upload(player, 36, ItemStack.EMPTY);
        require(player.getInventory().getItem(0).isEmpty(), "ordinary creative removal was rejected");
        player.getInventory().clearContent();
        System.out.println("PHASE_LOCK_RECOVERY_PASS real creative edits and same-UUID echo protection preserved");
        helper.succeed();
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace = "ae2lt", template = "workstation_test")
    public static void phaseLockAllowsSwimmingInput(GameTestHelper helper) {
        var player = new SwimmingPlayer(helper.getLevel());
        try {
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlockAndUpdate(pos.above(y), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            }
            player.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
            player.baseTick();
            require(player.isInWater(), "swimming fixture is not in water");
            player.setJumping(true);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.aiStep();
            double ordinaryRise = player.movementAtTravel.y;
            require(ordinaryRise > 0, "ordinary player did not get a water jump impulse");

            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            equipArmor(player);
            PhaseLockService.tick(player);
            require(PhaseLockService.hasPrivateArmor(player), "swimming armor did not lock");
            // Enable the same runtime protection used by the phase-lock module, independently
            // of the test server's config and without requiring an ME energy network.
            com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard.updatePhaseLockProtection(player, true, false);
            player.aiStep();
            require(Math.abs(player.movementAtTravel.y - ordinaryRise) < 0.000001,
                    "phase lock blocked the native water jump input");
            var beforeForce = player.getDeltaMovement();
            player.setDeltaMovement(5, 5, 5);
            require(player.getDeltaMovement().equals(beforeForce), "swimming authorization leaked to external force");
            player.jumpInFluid(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value());
            require(player.getDeltaMovement().equals(beforeForce), "external fluid impulse bypassed phase lock");
            com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard.runAsSelfMovement(player,
                    () -> player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO));
            player.descendInWater();
            require(player.getDeltaMovement().y < 0, "phase lock blocked the native water descend input");
            require(!com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard.isSelfMovementAuthorized(player),
                    "fluid movement left authorization open");
        } finally {
            PhaseLockService.release(player);
            com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard.clear(player);
        }
        System.out.println("PHASE_LOCK_SWIM_PASS native water jump and descend work while external forces stay blocked");
        helper.succeed();
    }

    private static final class SwimmingPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        private net.minecraft.world.phys.Vec3 movementAtTravel = net.minecraft.world.phys.Vec3.ZERO;

        private SwimmingPlayer(net.minecraft.server.level.ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "PhaseSwim"));
        }

        @Override
        public void travel(net.minecraft.world.phys.Vec3 input) {
            // Observe the result of native aiStep's liquid-input branch before gravity/drag.
            movementAtTravel = getDeltaMovement();
        }

        private void descendInWater() { goDownInWater(); }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PhaseRecovery"));
        // FakePlayer's own listener ignores incoming creative packets. Use vanilla's
        // real handler with only outgoing traffic discarded; its mixins remain active.
        new net.minecraft.server.network.ServerGamePacketListenerImpl(
                helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                player, net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        return player;
    }

    private static EnumMap<EquipmentSlot, ItemStack> equipArmor(ServerPlayer player) {
        var armor = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = new ItemStack(switch (slot) {
                case HEAD -> ModItems.CELESTWEAVE_OCULUS.get();
                case CHEST -> ModItems.CELESTWEAVE_CORE.get();
                case LEGS -> ModItems.CELESTWEAVE_CONDUIT.get();
                case FEET -> ModItems.CELESTWEAVE_STRIDE.get();
                default -> throw new AssertionError(slot);
            });
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Keep original " + slot));
            CelestweaveArmorState.setSlot(stack, player.registryAccess(), CelestweaveArmorState.SLOT_CORE,
                    new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
            CelestweaveArmorState.ensureArmorId(stack);
            if (slot == EquipmentSlot.CHEST) {
                require(CelestweaveArmorState.installOneModule(stack, player.registryAccess(),
                        new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_PHASE_LOCK.get())), "cannot install phase lock");
            }
            require(ArmorEnergyBuffer.read(stack, player.registryAccess()) == 0, "fixture has FE");
            player.setItemSlot(slot, stack);
            armor.put(slot, stack);
        }
        return armor;
    }

    private static int menuSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> throw new AssertionError(slot);
        };
    }

    private static void upload(ServerPlayer player, int slot, ItemStack stack) {
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(slot, stack));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
