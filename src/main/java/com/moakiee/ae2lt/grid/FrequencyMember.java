package com.moakiee.ae2lt.grid;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public class FrequencyMember {

    private final UUID playerUUID;
    private String cachedName;
    private FrequencyAccessLevel accessLevel;

    private FrequencyMember(UUID playerUUID, String cachedName, FrequencyAccessLevel accessLevel) {
        this.playerUUID = playerUUID;
        this.cachedName = cachedName;
        this.accessLevel = accessLevel;
    }

    public FrequencyMember(CompoundTag tag) {
        this.playerUUID = com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.get(tag, "uuid");
        this.cachedName = tag.getStringOr("name", "");
        this.accessLevel = FrequencyAccessLevel.fromId(tag.getByteOr("access", (byte) 0));
    }

    public static FrequencyMember create(Player player, FrequencyAccessLevel access) {
        return new FrequencyMember(player.getUUID(), player.getGameProfile().name(), access);
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getCachedName() {
        return cachedName;
    }

    public FrequencyAccessLevel getAccessLevel() {
        return accessLevel;
    }

    public boolean setAccessLevel(FrequencyAccessLevel level) {
        if (this.accessLevel != level) {
            this.accessLevel = level;
            return true;
        }
        return false;
    }

    public void writeNBT(CompoundTag tag) {
        com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.put(tag, "uuid", playerUUID);
        tag.putString("name", cachedName);
        tag.putByte("access", accessLevel.getId());
    }
}
