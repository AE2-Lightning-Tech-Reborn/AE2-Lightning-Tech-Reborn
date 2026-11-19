package com.moakiee.ae2lt.debug;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.NightVisionSubmodule;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the real item inventory tick, module state and vanilla effect lifetime. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class NightVisionArmorGameTests {
    @GameTest(template = "workstation_test")
    public static void normalRefreshStaysAboveVanillaFlickerThreshold(GameTestHelper helper) {
        var fixture = equip(helper);
        for (int tick = 1; tick <= 1200; tick++) {
            fixture.player().tickCount = tick;
            tick(fixture, fixture.helmet());
            var effect = fixture.player().getEffect(MobEffects.NIGHT_VISION);
            helper.assertTrue(effect != null && !effect.endsWithin(200),
                    "normal powered night vision entered the vanilla flicker window at tick " + tick);
            effect.tick(fixture.player(), () -> {});
        }
        finish(helper, fixture);
    }

    @GameTest(template = "workstation_test")
    public static void slowServerRefreshStaysAboveClientFlickerThreshold(GameTestHelper helper) {
        var fixture = equip(helper);
        var clientEffect = new MobEffectInstance(fixture.player().getEffect(MobEffects.NIGHT_VISION));
        for (int serverTick = 1; serverTick <= 1200; serverTick++) {
            fixture.player().tickCount = serverTick;
            var effect = fixture.player().getEffect(MobEffects.NIGHT_VISION);
            int previousDuration = effect.getDuration();
            effect.tick(fixture.player(), () -> {});
            tick(fixture, fixture.helmet());
            effect = fixture.player().getEffect(MobEffects.NIGHT_VISION);
            if (effect.getDuration() > previousDuration) {
                // Vanilla replaces the client's effect duration when the server renews it.
                clientEffect = new MobEffectInstance(effect);
            }
            // A client keeps 20 TPS while an overloaded server may manage only 5 TPS.
            for (int clientTick = 0; clientTick < 4; clientTick++) {
                clientEffect.tick(fixture.player(), () -> {});
                helper.assertTrue(!clientEffect.endsWithin(200),
                        "slow server let the client enter the flicker window at server tick " + serverTick);
            }
        }
        finish(helper, fixture);
    }

    @GameTest(template = "workstation_test")
    public static void strongerAndInfiniteNightVisionArePreserved(GameTestHelper helper) {
        var fixture = equip(helper);
        fixture.player().removeEffect(MobEffects.NIGHT_VISION);
        fixture.player().addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100, 2));
        tick(fixture, fixture.helmet());
        var renewed = fixture.player().getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(renewed != null && renewed.getAmplifier() == 2 && !renewed.endsWithin(200),
                "stronger night vision was downgraded or left near expiry");
        fixture.player().removeEffect(MobEffects.NIGHT_VISION);
        fixture.player().addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 1));
        tick(fixture, fixture.helmet());
        var infinite = fixture.player().getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(infinite != null && infinite.isInfiniteDuration() && infinite.getAmplifier() == 1,
                "infinite night vision was replaced");
        fixture.player().removeEffect(MobEffects.NIGHT_VISION);
        tick(fixture, fixture.helmet());
        assertActive(helper, fixture, "missing night vision was not restored");
        finish(helper, fixture);
    }

    @GameTest(template = "workstation_test")
    public static void creativeCopyCannotDeactivateEquippedHelmet(GameTestHelper helper) {
        var fixture = equip(helper);
        var copy = fixture.helmet().copy();
        fixture.player().getInventory().setItem(0, copy);
        for (int iteration = 0; iteration < 20; iteration++) {
            // Exercise both orders: main inventory before armor, and an idle copy after armor.
            tick(fixture, copy);
            assertActive(helper, fixture, "backpack copy removed the equipped helmet's night vision");
            tick(fixture, fixture.helmet());
            tick(fixture, copy);
            assertActive(helper, fixture, "backpack copy reset the equipped module's runtime state");
        }
        finish(helper, fixture);
    }

    @GameTest(template = "workstation_test")
    public static void swappingToCreativeCopyKeepsNightVision(GameTestHelper helper) {
        var fixture = equip(helper);
        var copy = fixture.helmet().copy();
        fixture.player().setItemSlot(EquipmentSlot.HEAD, copy);
        tick(fixture, fixture.helmet());
        assertActive(helper, fixture, "old stack deactivated the replacement with the same armor id");
        tick(fixture, copy);
        assertActive(helper, fixture, "replacement helmet lost night vision");
        finish(helper, fixture);
    }

    @GameTest(template = "workstation_test")
    public static void unequippingHelmetStillRemovesNightVision(GameTestHelper helper) {
        var fixture = equip(helper);
        var copy = fixture.helmet().copy();
        fixture.player().setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        tick(fixture, copy);
        tick(fixture, fixture.helmet());
        assertInactive(helper, fixture, "unequipped night vision remained active");
        finish(helper, fixture);
    }

    @GameTest(template = "workstation_test")
    public static void disablingModuleStillRemovesNightVision(GameTestHelper helper) {
        var fixture = equip(helper);
        CelestweaveArmorState.setSubmoduleEnabled(fixture.helmet(), NightVisionSubmodule.INSTANCE, false);
        tick(fixture, fixture.helmet());
        assertInactive(helper, fixture, "disabled night vision remained active");
        finish(helper, fixture);
    }

    @GameTest(template = "workstation_test")
    public static void emptyEnergyBufferStillRemovesNightVision(GameTestHelper helper) {
        var fixture = equip(helper);
        ArmorEnergyBuffer.write(fixture.helmet(), 0);
        tick(fixture, fixture.helmet());
        assertInactive(helper, fixture, "unpowered night vision remained active");
        finish(helper, fixture);
    }

    private static Fixture equip(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "NightVisionQA"));
        var helmet = new ItemStack(ModItems.CELESTWEAVE_OCULUS.get());
        var registries = helper.getLevel().registryAccess();
        CelestweaveArmorState.setSlot(helmet, registries, CelestweaveArmorState.SLOT_CORE,
                new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        helper.assertTrue(CelestweaveArmorState.installOneModule(helmet, registries,
                new ItemStack(ModItems.ENERGY_MODULE_T3.get())), "energy module rejected");
        helper.assertTrue(CelestweaveArmorState.installOneModule(helmet, registries,
                new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_NIGHT_VISION.get())), "night vision module rejected");
        ArmorEnergyBuffer.write(helmet, registries, 20_000_000_000L);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        var fixture = new Fixture(player, helmet);
        tick(fixture, helmet);
        assertActive(helper, fixture, "powered equipped helmet did not grant night vision");
        return fixture;
    }

    private static void tick(Fixture fixture, ItemStack armor) {
        ((BaseCelestweaveArmorItem) armor.getItem()).inventoryTick(
                armor, fixture.player().level(), fixture.player(), 0, false);
    }

    private static void assertActive(GameTestHelper helper, Fixture fixture, String message) {
        helper.assertTrue(fixture.player().hasEffect(MobEffects.NIGHT_VISION)
                && CelestweaveArmorState.isSubmoduleRuntimeActive(fixture.helmet(), "night_vision"), message);
    }

    private static void assertInactive(GameTestHelper helper, Fixture fixture, String message) {
        helper.assertTrue(!fixture.player().hasEffect(MobEffects.NIGHT_VISION)
                && !CelestweaveArmorState.isSubmoduleRuntimeActive(fixture.helmet(), "night_vision"), message);
    }

    private static void finish(GameTestHelper helper, Fixture fixture) {
        fixture.player().setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        fixture.player().getInventory().clearContent();
        tick(fixture, fixture.helmet());
        CelestweaveArmorState.clearTransientRuntimeAndCaches(fixture.helmet());
        helper.succeed();
    }

    private record Fixture(ServerPlayer player, ItemStack helmet) {}
}
