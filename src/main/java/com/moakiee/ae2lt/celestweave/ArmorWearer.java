package com.moakiee.ae2lt.celestweave;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import appeng.api.networking.security.IActionSource;

/** Separates the actual wearer from the player identity used for network access. */
public final class ArmorWearer {
    private static final String OWNER_ID = "ae2lt.armor_player_owner";
    private static final String OWNER_NAME = "ae2lt.armor_player_owner_name";

    private ArmorWearer() {
    }

    public static boolean isEligible(@Nullable LivingEntity wearer) {
        return wearer != null && (wearer instanceof Player || ownerProfile(wearer) != null);
    }

    public static boolean canRun(@Nullable LivingEntity wearer) {
        return wearer != null && !wearer.level().isClientSide() && isEligible(wearer);
    }

    /** A direct player owner is required; ownership chains do not confer access. */
    public static @Nullable GameProfile ownerProfile(LivingEntity wearer) {
        if (wearer instanceof Player player) {
            return player.getGameProfile();
        }
        Entity owner;
        UUID ownerId;
        if (wearer instanceof OwnableEntity ownable) {
            owner = ownable.getOwner();
            ownerId = ownable.getOwnerUUID();
        } else if (wearer instanceof TraceableEntity traceable) {
            owner = traceable.getOwner();
            ownerId = owner == null ? null : owner.getUUID();
        } else {
            return null;
        }
        if (owner instanceof Player player) {
            rememberOwner(wearer, player.getGameProfile());
            return player.getGameProfile();
        }
        if (owner != null || ownerId == null || wearer.level().isClientSide()) {
            return null;
        }
        var server = wearer.getServer();
        if (server == null) {
            return null;
        }
        var online = server.getPlayerList().getPlayer(ownerId);
        if (online != null) {
            rememberOwner(wearer, online.getGameProfile());
            return online.getGameProfile();
        }
        // Persist only a verified player identity, and invalidate it as soon as ownership changes.
        // This lets loaded companions keep working while their player owner is offline.
        var data = wearer.getPersistentData();
        if (data.hasUUID(OWNER_ID) && ownerId.equals(data.getUUID(OWNER_ID))) {
            return new GameProfile(ownerId, data.getString(OWNER_NAME));
        }
        var profiles = server.getProfileCache();
        var profile = profiles == null ? null : profiles.get(ownerId).orElse(null);
        if (profile != null) {
            rememberOwner(wearer, profile);
        }
        return profile;
    }

    private static void rememberOwner(LivingEntity wearer, GameProfile profile) {
        if (wearer.level().isClientSide()) {
            return;
        }
        var data = wearer.getPersistentData();
        if (!data.hasUUID(OWNER_ID) || !profile.getId().equals(data.getUUID(OWNER_ID))
                || !profile.getName().equals(data.getString(OWNER_NAME))) {
            data.putUUID(OWNER_ID, profile.getId());
            data.putString(OWNER_NAME, profile.getName());
        }
    }

    /** Only network attribution uses this player. Never use it as the wearer or equipment source. */
    public static @Nullable IActionSource actionSource(LivingEntity wearer) {
        if (!(wearer.level() instanceof ServerLevel level)) {
            return null;
        }
        if (wearer instanceof Player player) {
            return IActionSource.ofPlayer(player);
        }
        var profile = ownerProfile(wearer);
        if (profile == null) {
            return null;
        }
        var online = level.getServer().getPlayerList().getPlayer(profile.getId());
        return IActionSource.ofPlayer(online != null ? online : FakePlayerFactory.get(level, profile));
    }

    public static boolean isOwnedBy(LivingEntity wearer, Player player) {
        var profile = ownerProfile(wearer);
        return profile != null && profile.getId().equals(player.getUUID());
    }
}
