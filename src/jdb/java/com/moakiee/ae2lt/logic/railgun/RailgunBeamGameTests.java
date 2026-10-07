package com.moakiee.ae2lt.logic.railgun;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.item.railgun.RailgunModuleStorage;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.item.railgun.RailgunStructuralCore;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.network.railgun.RailgunBeamChainFxPacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamUpdatePacket;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real ME grid and beam settlement, with a mounted fault-injection store for rollback checks. */
@GameTestHolder("ae2lt_railgun")
@PrefixGameTestTemplate(false)
public final class RailgunBeamGameTests {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void compensatesOnlyMissingEhvWithoutChangingDamage(GameTestHelper helper) {
        fixture(helper, true, 10, 512, 100_000, f -> {
            check(f.fire(), "mixed EHV/HV payment succeeds");
            f.expect(0, 416, 96_000);
            check(f.target.getHealth() == 824, "compensated beam keeps 200 EHV damage");
            check(f.fire(), "full HV compensation succeeds");
            f.expect(0, 160, 92_000);
            check(f.target.getHealth() == 624, "compensated beam retains EHV hurt-cooldown bypass");
            check(!f.fire(), "insufficient HV stops instead of downgrading");
            f.expect(0, 160, 92_000);
            check(f.target.getHealth() == 624, "failed payment deals no damage");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void missingCoreCannotCompensate(GameTestHelper helper) {
        fixture(helper, false, 8, 1024, 100_000, f -> {
            check(!f.fire(), "EHV beam module alone does not grant compensation");
            f.expect(8, 1024, 100_000);
            check(RailgunModuleStorage.INSTANCE.installOne(f.gun, ModItems.RAILGUN_MODULE_CORE.toStack()), "install core");
            check(f.fire(), "installing core enables compensation");
            f.expect(0, 896, 96_000);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void insufficientFeLeavesBothLightningStocksIntact(GameTestHelper helper) {
        fixture(helper, true, 8, 128, 0, f -> {
            check(!f.fire(), "missing FE stops beam");
            f.expect(8, 128, 0);
            check(f.target.getHealth() == 1024, "no unpaid damage");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void modulationShortfallsRefundBothStocksAndFe(GameTestHelper helper) {
        fixture(helper, true, 8, 128, 100_000, f -> {
            f.storage.shortKey = LightningKey.EXTREME_HIGH_VOLTAGE;
            check(!f.fire(), "EHV modulation shortfall rejects shot");
            f.expect(8, 128, 100_000);
            f.storage.shortKey = LightningKey.HIGH_VOLTAGE;
            check(!f.fire(), "HV modulation shortfall rejects shot");
            f.expect(8, 128, 100_000);
            check(f.target.getHealth() == 1024, "rollback does not deal damage");
            f.storage.shortKey = null;
            check(f.fire(), "shot succeeds after storage recovers");
            f.expect(0, 0, 96_000);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void beamPacketsPreserveModeAcrossWire(GameTestHelper helper) {
        var id = UUID.randomUUID();
        var from = new Vec3(1, 2, 3);
        var to = new Vec3(4, 5, 6);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (boolean ehv : new boolean[]{false, true}) {
                for (boolean active : new boolean[]{false, true}) {
                    var packet = new RailgunBeamUpdatePacket(id, from, to, active, ehv);
                    RailgunBeamUpdatePacket.STREAM_CODEC.encode(buf, packet);
                    check(packet.equals(RailgunBeamUpdatePacket.STREAM_CODEC.decode(buf)), "beam mode wire round trip");
                    check(buf.readableBytes() == 0, "no unread beam payload");
                }
                var chain = new RailgunBeamChainFxPacket(id, from, List.of(from, to), false, ehv);
                RailgunBeamChainFxPacket.STREAM_CODEC.encode(buf, chain);
                check(chain.equals(RailgunBeamChainFxPacket.STREAM_CODEC.decode(buf)), "chain mode wire round trip");
                check(buf.readableBytes() == 0, "no unread chain payload");
            }
        } finally { buf.release(); }
        helper.succeed();
    }

    private static void fixture(GameTestHelper helper, boolean core, long ehv, long hv, long fe, Consumer<Fixture> test) {
        var level = helper.getLevel();
        level.setWeatherParameters(10000, 0, false, false);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BeamPaymentQA"));
        player.getInventory().clearContent();
        var gun = ModItems.ELECTROMAGNETIC_RAILGUN.toStack();
        RailgunStructuralCore.setCore(gun, ModItems.ULTIMATE_OVERLOAD_CORE.toStack());
        check(RailgunModuleStorage.INSTANCE.installOne(gun, ModItems.ENERGY_MODULE_T3.toStack()), "install FE module");
        check(RailgunModuleStorage.INSTANCE.installOne(gun, ModItems.RAILGUN_MODULE_EHV_BEAM.toStack()), "install EHV module");
        if (core) check(RailgunModuleStorage.INSTANCE.installOne(gun, ModItems.RAILGUN_MODULE_CORE.toStack()), "install core");
        gun.set(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT.withEhvBeam(true).withChainDamage(false).withSound(false));
        RailgunEnergyBuffer.write(gun, fe);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        var base = helper.absolutePos(new BlockPos(2, 2, 2));
        var wap = base.north();
        level.setBlockAndUpdate(base, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(wap, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        gun.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), wap));
        player.setPos(base.getX() + .5, base.getY() + 1, base.getZ() + .5);
        player.setYRot(0);
        player.setXRot(8);
        var cow = new Cow(EntityType.COW, level);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        cow.setHealth(1024);
        cow.setPos(base.getX() + .5, base.getY() + 1, base.getZ() + 6.5);
        level.addFreshEntity(cow);
        var storage = new FaultStorage();
        storage.contents.set(LightningKey.EXTREME_HIGH_VOLTAGE, ehv);
        storage.contents.set(LightningKey.HIGH_VOLTAGE, hv);
        helper.runAfterDelay(50, () -> {
            var bound = RailgunBinding.resolve(gun, player);
            check(bound.success(), "real ME wireless access point active");
            var service = bound.grid().getStorageService();
            IStorageProvider provider = mounts -> mounts.mount(storage);
            service.addGlobalStorageProvider(provider);
            try {
                test.accept(new Fixture(level, player, gun, cow, storage));
                helper.succeed();
            } catch (Throwable failure) {
                failure.printStackTrace();
                helper.fail(failure.toString());
            } finally { service.removeGlobalStorageProvider(provider); }
        });
    }

    private record Fixture(ServerLevel level, ServerPlayer player, ItemStack gun, Cow target, FaultStorage storage) {
        boolean fire() {
            try {
                var settle = RailgunBeamService.class.getDeclaredMethod("settle", ServerLevel.class, ServerPlayer.class,
                        ItemStack.class, RailgunBeamService.BeamState.class, int.class);
                settle.setAccessible(true);
                return (boolean) settle.invoke(null, level, player, gun,
                        new RailgunBeamService.BeamState(InteractionHand.MAIN_HAND, player.tickCount), 5);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }

        void expect(long ehv, long hv, long fe) {
            check(storage.contents.get(LightningKey.EXTREME_HIGH_VOLTAGE) == ehv, "EHV stock mismatch");
            check(storage.contents.get(LightningKey.HIGH_VOLTAGE) == hv, "HV stock mismatch");
            check(RailgunEnergyBuffer.read(gun) == fe, "FE stock mismatch");
        }
    }

    private static final class FaultStorage implements MEStorage {
        final KeyCounter contents = new KeyCounter();
        AEKey shortKey;

        @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long got = Math.min(amount, contents.get(what));
            if (mode == Actionable.MODULATE) {
                if (what.equals(shortKey) && got > 0) got--;
                contents.remove(what, got);
            }
            return got;
        }

        @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) contents.add(what, amount);
            return amount;
        }

        @Override public void getAvailableStacks(KeyCounter out) { out.addAll(contents); }
        @Override public Component getDescription() { return Component.literal("Beam payment test storage"); }
    }
}
