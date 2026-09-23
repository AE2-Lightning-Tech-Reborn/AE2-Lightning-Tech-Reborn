package com.moakiee.ae2lt.celestweave.service;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import com.moakiee.ae2lt.AE2LightningTech;

public final class CelestweaveAdvancementService {
    private static final Identifier RADIATION_ASSIMILATION =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "main/radiation_assimilation");
    private static final String RADIATION_HEALING_CRITERION = "radiation_healing";

    private CelestweaveAdvancementService() {
    }

    public static void awardRadiationAssimilation(ServerPlayer player) {
        var advancement = player.level().getServer().getAdvancements().get(RADIATION_ASSIMILATION);
        if (advancement != null) {
            player.getAdvancements().award(advancement, RADIATION_HEALING_CRITERION);
        }
    }
}
